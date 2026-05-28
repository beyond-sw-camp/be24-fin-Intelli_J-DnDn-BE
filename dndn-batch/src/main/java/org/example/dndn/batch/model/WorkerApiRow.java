package org.example.dndn.batch.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * API 수집 → 스테이징 테이블 → 운영 반영을 위한 행 단위 DTO.
 *
 * <p>tmp_worker_sync_stage 테이블의 논리 행을 Java로 표현한다.
 * rawJson 필드는 API 응답 원문(또는 픽스처 직렬화 결과)을 보관하며,
 * WorkerSyncItemWriter가 이를 WorkerScenarioFixtureRow로 역직렬화해 운영 테이블에 반영한다.</p>
 *
 * <p><b>실제 API 전환 시</b>: rawJson에 외부 API 응답 JSON을 그대로 저장하고,
 * WorkerSyncItemWriter의 역직렬화 로직(또는 별도 변환 서비스)만 교체하면 된다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkerApiRow {

    /** 현장 코드 (예: "A", "B") — SiteCodePartitioner 파티션 키와 동일 */
    private String siteCode;

    /** 외부 식별자 (worker.external_code) — NULL 가능 */
    private String workerId;

    /** 근로자 성명 — StagingValidationTasklet NULL 검증 대상 */
    private String workerName;

    /** API 응답 원문 JSON — 현재는 WorkerScenarioFixtureRow 직렬화 형태 */
    private String rawJson;
}
