package org.example.dndn.worker.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.dndn.common.exception.BaseException;
import org.example.dndn.worker.model.dto.WorkerDetailDto;
import org.example.dndn.worker.model.entity.Worker;
import org.example.dndn.worker.repository.WorkerDocumentRepository;
import org.example.dndn.worker.repository.WorkerRepository;
import org.example.dndn.worker.repository.WorkerZoneHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static org.example.dndn.common.model.BaseResponseStatus.FAIL;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkerDetailService {
    private final WorkerRepository workerRepository;
    private final WorkerDocumentRepository documentRepository;
    private final WorkerZoneHistoryRepository zoneHistoryRepository;

    // MANAGEMENT_004 작업자 상세 프로필 조회 (기본 정보 카드)
    public WorkerDetailDto.ProfileRes getProfile(Long workerIdx) {
        Worker w = workerRepository.findById(workerIdx).orElseThrow(() -> new BaseException(FAIL));
        return WorkerDetailDto.ProfileRes.from(w);
    }

    // MANAGEMENT_005 안전 및 서류 현황 조회
    public List<WorkerDetailDto.DocRes> getDocuments(Long workerIdx) {
        ensureExists(workerIdx);
        return documentRepository.findAllByWorkerIdx(workerIdx).stream()
                .map(WorkerDetailDto.DocRes::from)
                .collect(Collectors.toList());
    }

    // MANAGEMENT_007 구역 배치 이력 조회
    public List<WorkerDetailDto.DeploymentRes> getDeployments(Long workerIdx) {
        ensureExists(workerIdx);
        return zoneHistoryRepository.findAllByWorkerIdxOrderByAssignedAtDesc(workerIdx).stream()
                .map(WorkerDetailDto.DeploymentRes::from)
                .collect(Collectors.toList());
    }

    private void ensureExists(Long workerIdx) {
        if (!workerRepository.existsById(workerIdx)) {
            throw new BaseException(FAIL);
        }
    }
}
