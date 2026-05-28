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
 * 스테이징 데이터 품질 검증 게이트 (Step4).
 *
 * <p><b>검증 항목</b>:
 * <ol>
 *   <li>수집 건수 확인 — 0건이면 Job FAILED (운영 테이블 변경 없음)</li>
 *   <li>필수값(worker_name) NULL 검사 — NULL/빈값 존재 시 Job FAILED</li>
 *   <li>현장 코드 유효성 — project 테이블과 대조, 알 수 없는 코드는 경고만(Skip)</li>
 * </ol>
 * </p>
 *
 * <p>이 Step이 실패하면 rosterCleanupStep(Step5)이 실행되지 않으므로
 * 운영 테이블(attendance_record, worker_document)은 변경되지 않는다.</p>
 *
 * <p><b>Step 순서</b>: dataFetchMasterStep(Step3) → stagingValidationStep(Step4) →
 * rosterCleanupStep(Step5)</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StagingValidationTasklet implements Tasklet {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public RepeatStatus execute(StepContribution contribution,
                                ChunkContext chunkContext) throws Exception {

        // ① 수집 건수 확인
        Integer totalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tmp_worker_sync_stage", Integer.class);

        if (totalCount == null || totalCount == 0) {
            throw new IllegalStateException(
                    "[검증 실패] 스테이징 테이블이 비어있습니다. " +
                    "API 수집 단계(dataFetchMasterStep)를 확인하세요.");
        }

        // ② NULL 필수값 검사 (worker_name)
        Integer nullNameCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tmp_worker_sync_stage " +
                "WHERE worker_name IS NULL OR worker_name = ''", Integer.class);

        if (nullNameCount != null && nullNameCount > 0) {
            throw new IllegalStateException(
                    "[검증 실패] worker_name NULL/빈값 데이터 " + nullNameCount + "건 발견. " +
                    "API 응답 또는 픽스처를 확인하세요.");
        }

        // ③ 현장 코드 유효성 검사 (운영 project 테이블과 대조)
        Integer unknownSiteCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM tmp_worker_sync_stage s
                WHERE NOT EXISTS (
                    SELECT 1 FROM project p
                    WHERE p.name LIKE CONCAT('[', s.site_code, ']%')
                    AND p.active = true
                )
                """, Integer.class);

        if (unknownSiteCount != null && unknownSiteCount > 0) {
            // 경고만 하고 통과 (엄격하게 처리하려면 throw로 변경 가능)
            log.warn("[검증 경고] 활성 project에 매칭되지 않는 현장 코드 {}건 존재 — 해당 행 Skip",
                    unknownSiteCount);
        }

        log.info("[검증 통과] 스테이징 총 {}건 — 운영 테이블 반영 진행", totalCount);
        return RepeatStatus.FINISHED;
    }
}
