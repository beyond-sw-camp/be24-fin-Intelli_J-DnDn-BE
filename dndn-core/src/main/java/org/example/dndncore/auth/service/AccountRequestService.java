package org.example.dndncore.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.auth.model.dto.AccountRequestDto;
import java.util.UUID;
import org.example.dndncore.auth.model.entity.AccountRequest;
import org.example.dndncore.auth.model.entity.SystemUser;
import org.example.dndncore.auth.model.enums.RequestStatus;
import org.example.dndncore.auth.repository.AccountRequestRepository;
import org.example.dndncore.auth.repository.SystemUserRepository;
import org.example.dndncore.common.exception.BaseException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static org.example.dndncore.common.model.BaseResponseStatus.ACCOUNT_DUPLICATE_LOGIN_ID;
import static org.example.dndncore.common.model.BaseResponseStatus.ACCOUNT_NOT_FOUND;
import static org.example.dndncore.common.model.BaseResponseStatus.ACCOUNT_REQUEST_ALREADY_PROCESSED;
import static org.example.dndncore.common.model.BaseResponseStatus.ACCOUNT_REQUEST_NOT_FOUND;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountRequestService {

    private final AccountRequestRepository requestRepository;
    private final SystemUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** 인증된 사용자(requesterIdx)가 계정 생성을 요청. */
    @Transactional
    public AccountRequestDto.Res create(Long requesterIdx, AccountRequestDto.CreateReq req) {
        SystemUser requester = userRepository.findById(requesterIdx)
                .orElseThrow(() -> new BaseException(ACCOUNT_NOT_FOUND));

        AccountRequest saved = requestRepository.save(AccountRequest.builder()
                .requester(requester)
                .requestedName(req.getRequestedName())
                .requestedLoginId(req.getRequestedLoginId())
                .requestedRole(req.getRequestedRole())
                .siteCode(req.getSiteCode())
                .trade(req.getTrade())
                .status(RequestStatus.PENDING)
                .build());
        return AccountRequestDto.Res.from(saved);
    }

    public List<AccountRequestDto.Res> getAll(RequestStatus status) {
        List<AccountRequest> list = (status == null)
                ? requestRepository.findAllByOrderByCreatedAtDesc()
                : requestRepository.findAllByStatusOrderByCreatedAtDesc(status);
        return list.stream().map(AccountRequestDto.Res::from).collect(Collectors.toList());
    }

    public AccountRequestDto.Res getOne(Long idx) {
        return AccountRequestDto.Res.from(findById(idx));
    }

    /** 승인: 요청 상태를 APPROVED로 변경하고 계정을 자동 생성. */
    @Transactional
    public AccountRequestDto.Res approve(Long idx, AccountRequestDto.ApproveReq req) {
        AccountRequest request = findById(idx);
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BaseException(ACCOUNT_REQUEST_ALREADY_PROCESSED);
        }
        if (userRepository.existsByLoginId(request.getRequestedLoginId())) {
            throw new BaseException(ACCOUNT_DUPLICATE_LOGIN_ID);
        }

        String rawPassword = (req.getInitialPassword() != null && req.getInitialPassword().length() >= 8)
                ? req.getInitialPassword()
                : UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        userRepository.save(SystemUser.builder()
                .loginId(request.getRequestedLoginId())
                .password(passwordEncoder.encode(rawPassword))
                .name(request.getRequestedName())
                .role(request.getRequestedRole())
                .siteCode(request.getSiteCode())
                .trade(request.getTrade())
                .active(true)
                .build());

        request.approve();
        return AccountRequestDto.Res.from(request);
    }

    /** 거절: 요청 상태를 REJECTED로 변경. */
    @Transactional
    public AccountRequestDto.Res reject(Long idx, AccountRequestDto.RejectReq req) {
        AccountRequest request = findById(idx);
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BaseException(ACCOUNT_REQUEST_ALREADY_PROCESSED);
        }
        request.reject(req.getNote());
        return AccountRequestDto.Res.from(request);
    }

    private AccountRequest findById(Long idx) {
        return requestRepository.findById(idx)
                .orElseThrow(() -> new BaseException(ACCOUNT_REQUEST_NOT_FOUND));
    }
}
