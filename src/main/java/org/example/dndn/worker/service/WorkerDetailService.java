package org.example.dndn.worker.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.dndn.common.exception.BaseException;
import org.example.dndn.worker.model.dto.WorkerDetailDto;
import org.example.dndn.worker.model.entity.Worker;
import org.example.dndn.worker.repository.WorkerRepository;
import org.springframework.stereotype.Service;

import static org.example.dndn.common.model.BaseResponseStatus.FAIL;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkerDetailService {
    private final WorkerRepository workerRepository;

    // MANAGEMENT_004 작업자 상세 프로필 조회 (기본 정보 카드)
    public WorkerDetailDto.ProfileRes getProfile(Long workerIdx) {
        Worker w = workerRepository.findById(workerIdx).orElseThrow(() -> new BaseException(FAIL));
        return WorkerDetailDto.ProfileRes.from(w);
    }
}
