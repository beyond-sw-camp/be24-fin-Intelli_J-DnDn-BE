package org.example.dndn.batch.tasklet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndn.domain.worker.fixture.WorkerFixtureGenerator;
import org.example.dndn.domain.worker.repository.AttendanceRecordRepository;
import org.example.dndn.domain.worker.repository.WorkerDocumentRepository;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.time.LocalDate;

/**
 * 병렬 sync 전 데이터 사전 정리 Step.
 *
 * <p><b>목적</b>: 반복 배치 실행(테스트·재시도) 시 동일 syncDate 로스터 충돌을 방지하고,
 * {@code attendance_record}는 당일 스냅샷만 남기도록 과거 행을 정리한다.</p>
 *
 * <p><b>삭제 대상</b>:
 * <ul>
 *   <li>attendance_record — work_date &lt; syncDate (과거 로스터 스냅샷 잔존)</li>
 *   <li>attendance_record — work_date = syncDate (당일 로스터 재생성 전 삭제)</li>
 *   <li>worker_document — 픽스처 제목에 해당하는 서류만 (수동 등록 서류 보존)</li>
 * </ul>
 * {@code attendance_log}는 출결 이벤트·피로도 streak 원천이므로 삭제하지 않는다.</p>
 *
 * <p><b>Job 실행 순서</b>: validationStep → rosterCleanupStep (여기) → workerSyncMasterStep</p>
 */
@Slf4j
@RequiredArgsConstructor
public class RosterCleanupTasklet implements Tasklet {

    private final AttendanceRecordRepository attendanceRepository;
    private final WorkerDocumentRepository   workerDocumentRepository;
    private final LocalDate                  targetDate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        int deletedLegacyRecords = attendanceRepository.deleteAllByWorkDateBefore(targetDate);
        int deletedTodayRecords = attendanceRepository.deleteAllByWorkDate(targetDate);
        int deletedDocs = workerDocumentRepository.deleteAllByTitleIn(
                WorkerFixtureGenerator.FIXTURE_DOCUMENT_TITLES);

        if (deletedLegacyRecords > 0 || deletedTodayRecords > 0 || deletedDocs > 0) {
            log.warn("[RosterCleanup] work_date={} 정리 — " +
                     "attendance_record(과거): {}건, attendance_record(당일): {}건, worker_document: {}건 " +
                     "(테스트 재실행·레거시 정리; attendance_log 미삭제)",
                    targetDate, deletedLegacyRecords, deletedTodayRecords, deletedDocs);
        } else {
            log.info("[RosterCleanup] work_date={} 삭제 대상 없음 (정상 첫 실행)", targetDate);
        }

        return RepeatStatus.FINISHED;
    }
}
