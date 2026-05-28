package org.example.dndn.domain.worker.repository;

import org.example.dndn.domain.worker.model.enums.EmploymentKind;

// employment_kind 보존용 경량 프로젝션 (벌크 근태 정규화 전 조회)
public interface WorkerEmploymentKindProjection {
    Long getWorkerIdx();
    EmploymentKind getEk();
}
