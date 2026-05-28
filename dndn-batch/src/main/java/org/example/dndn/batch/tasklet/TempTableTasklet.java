package org.example.dndn.batch.tasklet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 스테이징 테이블(tmp_worker_sync_stage) 생성·정리 Tasklet.
 *
 * <p>Step 이름에 따라 동작이 분기된다:
 * <ul>
 *   <li>{@code tempTableCreateStep} — 테이블이 없으면 CREATE, 있으면 TRUNCATE로 잔여 데이터 제거</li>
 *   <li>{@code tempTableDropStep}  — TRUNCATE로 스테이징 데이터 정리 (테이블 구조 보존)</li>
 * </ul>
 * DROP 대신 TRUNCATE를 사용하는 이유: 다음 배치 실행 시 CREATE 비용 없이 재사용 가능.</p>
 *
 * <p><b>Step 순서</b>: tempTableCreateStep(Step2) → … → tempTableDropStep(Step7)</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TempTableTasklet implements Tasklet {

    private static final String CREATE_SQL = """
            CREATE TABLE IF NOT EXISTS tmp_worker_sync_stage (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                site_code   VARCHAR(20)  NOT NULL,
                worker_id   VARCHAR(50),
                worker_name VARCHAR(100) NOT NULL,
                raw_json    JSON,
                fetched_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_site_code (site_code)
            )
            """;

    private static final String TRUNCATE_SQL = "TRUNCATE TABLE tmp_worker_sync_stage";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public RepeatStatus execute(StepContribution contribution,
                                ChunkContext chunkContext) {

        String stepName = chunkContext.getStepContext().getStepName();

        if ("tempTableCreateStep".equals(stepName)) {
            jdbcTemplate.execute(CREATE_SQL);
            jdbcTemplate.execute(TRUNCATE_SQL);
            log.info("[TempTable] 스테이징 테이블 준비 완료 (CREATE IF NOT EXISTS + TRUNCATE)");

        } else if ("tempTableDropStep".equals(stepName)) {
            jdbcTemplate.execute(TRUNCATE_SQL);
            log.info("[TempTable] 스테이징 테이블 정리 완료 (TRUNCATE)");

        } else {
            log.warn("[TempTable] 알 수 없는 stepName={} — 아무 작업도 수행하지 않음", stepName);
        }

        return RepeatStatus.FINISHED;
    }
}
