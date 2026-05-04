package org.example.dndn.worker.fixture;

import lombok.*;

import java.util.List;

/**
 * 시연용 JSON 최상위 래퍼. 필드명은 샘플 JSON 과 동일하게 `workers`.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkforceScenarioFile {
    private List<WorkerScenarioFixtureRow> workers;
}