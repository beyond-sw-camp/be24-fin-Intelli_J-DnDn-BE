package org.example.dndn.batch.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.dndn.batch.listener.WorkerSyncChunkListener;
import org.example.dndn.batch.listener.WorkerSyncJobListener;
import org.example.dndn.batch.listener.WorkerSyncStepListener;
import org.example.dndn.batch.model.WorkerApiRow;
import org.example.dndn.batch.reader.ApiWorkerReader;
import org.example.dndn.batch.reader.SiteCodePartitioner;
import org.example.dndn.batch.reader.StagingTableReader;
import org.example.dndn.batch.service.WorkerSyncService;
import org.example.dndn.batch.tasklet.RosterCleanupTasklet;
import org.example.dndn.batch.tasklet.StagingValidationTasklet;
import org.example.dndn.batch.tasklet.TempTableTasklet;
import org.example.dndn.batch.tasklet.ValidationTasklet;
import org.example.dndn.batch.writer.StagingItemWriter;
import org.example.dndn.batch.writer.WorkerSyncItemWriter;
import org.example.dndn.domain.worker.fixture.WorkerFixtureGenerator;
import org.example.dndn.domain.worker.repository.AttendanceRecordRepository;
import org.example.dndn.domain.worker.repository.WorkerDocumentRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.PartitionHandler;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;

@Configuration
@RequiredArgsConstructor
public class WorkerSyncJobConfig {

    // 동시에 처리할 현장 수 — HikariCP maximum-pool-size >= THREAD_COUNT + 여유분 필요 (현재 설정: 10)
    private static final int THREAD_COUNT = 4;

    // Slave Step 내 1회 트랜잭션에 포함할 근로자 수
    private static final int CHUNK_SIZE = 50;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final WorkerSyncJobListener jobListener;
    private final WorkerSyncStepListener stepListener;
    private final WorkerSyncChunkListener chunkListener;
    private final WorkerSyncService workerSyncService;
    private final WorkerFixtureGenerator workerFixtureGenerator;
    private final SiteCodePartitioner siteCodePartitioner;
    private final ValidationTasklet validationTasklet;
    private final TempTableTasklet tempTableTasklet;
    private final StagingValidationTasklet stagingValidationTasklet;
    private final AttendanceRecordRepository attendanceRepository;
    private final WorkerDocumentRepository workerDocumentRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    // ── Job ─────────────────────────────────────────────────────────────────

    /**
     * 전체 Job 흐름 (7 Steps):
     * <pre>
     * Step1 validationStep        — 활성 현장 수 사전 확인
     * Step2 tempTableCreateStep   — 스테이징 테이블 CREATE IF NOT EXISTS + TRUNCATE
     * Step3 dataFetchMasterStep   — API 호출 → 스테이징 적재 (병렬, 운영 테이블 무변경)
     * Step4 stagingValidationStep — 스테이징 데이터 품질 검증 게이트
     * Step5 rosterCleanupStep     — 운영 테이블(attendance_record, worker_document) 사전 정리
     * Step6 workerSyncMasterStep  — 스테이징 → 운영 테이블 반영 (병렬, INSERT만)
     * Step7 tempTableDropStep     — 스테이징 테이블 TRUNCATE
     * </pre>
     *
     * <p>Step4가 실패하면 Step5~7이 실행되지 않으므로 운영 테이블은 변경되지 않는다.</p>
     */
    @Bean
    public Job workerSyncJob(Step validationStep,
                             Step tempTableCreateStep,
                             Step dataFetchMasterStep,
                             Step stagingValidationStep,
                             Step rosterCleanupStep,
                             Step workerSyncMasterStep,
                             Step tempTableDropStep) {
        return new JobBuilder("workerSyncJob", jobRepository)
                .incrementer(new SyncDateJobParametersIncrementer())
                .listener(jobListener)
                .start(validationStep)
                .next(tempTableCreateStep)       // 스테이징 테이블 준비
                .next(dataFetchMasterStep)       // API → 스테이징 (병렬)
                .next(stagingValidationStep)     // 품질 검증 게이트
                .next(rosterCleanupStep)         // 운영 테이블 정리 (검증 통과 후에만 실행)
                .next(workerSyncMasterStep)      // 스테이징 → 운영 (병렬, INSERT만)
                .next(tempTableDropStep)         // 스테이징 정리
                .build();
    }

    // ── Tasklet Steps ────────────────────────────────────────────────────────

    @Bean
    public Step validationStep() {
        return new StepBuilder("validationStep", jobRepository)
                .tasklet(validationTasklet, transactionManager)
                .build();
    }

    /**
     * Step2: tmp_worker_sync_stage 준비.
     * TempTableTasklet이 stepName="tempTableCreateStep"으로 분기하여
     * CREATE IF NOT EXISTS + TRUNCATE를 수행한다.
     */
    @Bean
    public Step tempTableCreateStep() {
        return new StepBuilder("tempTableCreateStep", jobRepository)
                .tasklet(tempTableTasklet, transactionManager)
                .build();
    }

    /**
     * Step4: 스테이징 데이터 품질 검증 게이트.
     * 실패 시 Job FAILED — 운영 테이블 변경 없음.
     */
    @Bean
    public Step stagingValidationStep() {
        return new StepBuilder("stagingValidationStep", jobRepository)
                .tasklet(stagingValidationTasklet, transactionManager)
                .build();
    }

    /**
     * Step5: 운영 테이블 사전 정리 (직렬, 단일 트랜잭션).
     *
     * <p>삭제 대상:
     * <ul>
     *   <li>attendance_record — syncDate 이전·당일 로스터 행 (log는 보존)</li>
     *   <li>worker_document — 픽스처 제목("기초안전보건교육 이수증", "신분증 사본") 전체</li>
     * </ul>
     * </p>
     * <p>이 Step 완료 후 workerSyncMasterStep(병렬)은 INSERT만 수행 → gap lock 데드락 없음.</p>
     * <p>allowStartIfComplete(true): 재시작 시에도 반드시 재실행 — 스킵되면 이전 partial INSERT가
     * 남아 duplicate key / deadlock 재발.</p>
     */
    @Bean
    public Step rosterCleanupStep(RosterCleanupTasklet rosterCleanupTasklet) {
        return new StepBuilder("rosterCleanupStep", jobRepository)
                .tasklet(rosterCleanupTasklet, transactionManager)
                .allowStartIfComplete(true)
                .build();
    }

    @Bean
    @StepScope
    public RosterCleanupTasklet rosterCleanupTasklet(
            @Value("#{jobParameters['syncDate']}") String syncDate) {
        return new RosterCleanupTasklet(attendanceRepository,
                workerDocumentRepository, LocalDate.parse(syncDate));
    }

    /**
     * Step7: 스테이징 테이블 정리.
     * TempTableTasklet이 stepName="tempTableDropStep"으로 분기하여 TRUNCATE를 수행한다.
     */
    @Bean
    public Step tempTableDropStep() {
        return new StepBuilder("tempTableDropStep", jobRepository)
                .tasklet(tempTableTasklet, transactionManager)
                .build();
    }

    // ── Step3: dataFetchMasterStep (API → 스테이징, 병렬) ───────────────────

    /**
     * Step3 Master: SiteCodePartitioner가 현장별 파티션을 생성하고
     * PartitionHandler가 dataFetchSlaveStep을 병렬로 실행한다.
     * 운영 테이블은 전혀 건드리지 않는다.
     */
    @Bean
    public Step dataFetchMasterStep(Step dataFetchSlaveStep) {
        return new StepBuilder("dataFetchMasterStep", jobRepository)
                .partitioner("dataFetchSlaveStep", siteCodePartitioner)
                .step(dataFetchSlaveStep)
                .partitionHandler(dataFetchPartitionHandler(dataFetchSlaveStep))
                .listener(stepListener)
                .build();
    }

    /**
     * Step3 Slave: ApiWorkerReader → StagingItemWriter.
     * 현장 1개를 담당, chunk(50) = 근로자 50명 단위 트랜잭션.
     */
    @Bean
    public Step dataFetchSlaveStep(ApiWorkerReader apiWorkerReader,
                                   StagingItemWriter stagingItemWriter) {
        return new StepBuilder("dataFetchSlaveStep", jobRepository)
                .<WorkerApiRow, WorkerApiRow>chunk(CHUNK_SIZE, transactionManager)
                .reader(apiWorkerReader)
                .writer(stagingItemWriter)
                .listener(chunkListener)
                .build();
    }

    @Bean
    public PartitionHandler dataFetchPartitionHandler(Step dataFetchSlaveStep) {
        TaskExecutorPartitionHandler handler = new TaskExecutorPartitionHandler();
        handler.setStep(dataFetchSlaveStep);
        handler.setTaskExecutor(batchTaskExecutor());
        handler.setGridSize(THREAD_COUNT);
        return handler;
    }

    // ── Step6: workerSyncMasterStep (스테이징 → 운영, 병렬) ────────────────

    /**
     * Step6 Master: SiteCodePartitioner가 현장별 파티션을 생성하고
     * PartitionHandler가 workerSyncSlaveStep을 병렬로 실행한다.
     * stepListener는 Master Step에 부착해 전체 파티션 실행의 시작/종료를 추적한다.
     */
    @Bean
    public Step workerSyncMasterStep(Step workerSyncSlaveStep) {
        return new StepBuilder("workerSyncMasterStep", jobRepository)
                .partitioner("workerSyncSlaveStep", siteCodePartitioner)
                .step(workerSyncSlaveStep)
                .partitionHandler(workerSyncPartitionHandler(workerSyncSlaveStep))
                .listener(stepListener)
                .build();
    }

    /**
     * Step6 Slave: StagingTableReader → WorkerSyncItemWriter.
     * rosterCleanupStep(Step5)에서 사전 삭제 완료 → INSERT만 수행 → gap lock 없음.
     */
    @Bean
    public Step workerSyncSlaveStep(StagingTableReader stagingTableReader,
                                    WorkerSyncItemWriter workerSyncItemWriter) {
        return new StepBuilder("workerSyncSlaveStep", jobRepository)
                .<WorkerApiRow, WorkerApiRow>chunk(CHUNK_SIZE, transactionManager)
                .reader(stagingTableReader)
                .writer(workerSyncItemWriter)
                .listener(stepListener)
                .listener(chunkListener)
                .build();
    }

    /**
     * PartitionHandler: workerSyncSlaveStep을 스레드 풀로 병렬 실행.
     * gridSize = 동시 실행 파티션(현장) 수 — THREAD_COUNT와 동일하게 설정.
     */
    @Bean
    public PartitionHandler workerSyncPartitionHandler(Step workerSyncSlaveStep) {
        TaskExecutorPartitionHandler handler = new TaskExecutorPartitionHandler();
        handler.setStep(workerSyncSlaveStep);
        handler.setTaskExecutor(batchTaskExecutor());
        handler.setGridSize(THREAD_COUNT);
        return handler;
    }

    // ── StepScope Beans ──────────────────────────────────────────────────────

    /**
     * Step3 Reader: @StepScope — stepExecutionContext['siteCode']를 주입받아
     * 파티션마다 독립 인스턴스 생성 → synchronized 불필요.
     * 현재는 픽스처 기반, 실제 API 전환 시 ApiWorkerReader 내부 구현만 교체.
     */
    @Bean
    @StepScope
    public ApiWorkerReader apiWorkerReader(
            @Value("#{stepExecutionContext['siteCode']}") String siteCode) {
        return new ApiWorkerReader(siteCode, workerFixtureGenerator, objectMapper);
    }

    /**
     * Step3 Writer: @StepScope — siteCode(파티션별)를 주입받는다.
     * tmp_worker_sync_stage에 INSERT만 수행, 운영 테이블 무변경.
     */
    @Bean
    @StepScope
    public StagingItemWriter stagingItemWriter(
            @Value("#{stepExecutionContext['siteCode']}") String siteCode) {
        return new StagingItemWriter(jdbcTemplate, siteCode);
    }

    /**
     * Step6 Reader: @StepScope — siteCode(파티션별)를 주입받아
     * tmp_worker_sync_stage에서 해당 현장 데이터를 전체 로드한다.
     */
    @Bean
    @StepScope
    public StagingTableReader stagingTableReader(
            @Value("#{stepExecutionContext['siteCode']}") String siteCode) {
        return new StagingTableReader(jdbcTemplate, siteCode);
    }

    /**
     * Step6 Writer: @StepScope — siteCode(파티션별)·syncDate(배치 실행 당일)를 주입받는다.
     * rawJson을 역직렬화해 WorkerSyncService.syncChunk()에 위임한다.
     */
    @Bean
    @StepScope
    public WorkerSyncItemWriter workerSyncItemWriter(
            @Value("#{stepExecutionContext['siteCode']}") String siteCode,
            @Value("#{jobParameters['syncDate']}") String syncDate) {
        return new WorkerSyncItemWriter(workerSyncService, objectMapper, siteCode, LocalDate.parse(syncDate));
    }

    // ── Infrastructure Beans ─────────────────────────────────────────────────

    @Bean
    public TaskExecutor batchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(THREAD_COUNT);
        executor.setMaxPoolSize(THREAD_COUNT);
        // 현장 수(최대 25)에서 동시 실행 4개 초과분을 큐에서 대기
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("batch-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
