package org.example.dndncore.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.auth.model.dto.AccountDto;
import org.example.dndncore.auth.model.entity.SystemUser;
import org.example.dndncore.auth.repository.SystemUserRepository;
import org.example.dndncore.common.exception.BaseException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static org.example.dndncore.common.model.BaseResponseStatus.ACCOUNT_DUPLICATE_LOGIN_ID;
import static org.example.dndncore.common.model.BaseResponseStatus.ACCOUNT_NOT_FOUND;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final SystemUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public List<AccountDto.Res> getAll() {
        return userRepository.findAll().stream()
                .map(AccountDto.Res::from)
                .collect(Collectors.toList());
    }

    public AccountDto.Res getOne(Long idx) {
        return AccountDto.Res.from(findById(idx));
    }

    @Transactional
    public AccountDto.Res create(AccountDto.CreateReq req) {
        if (userRepository.existsByLoginId(req.getLoginId())) {
            throw new BaseException(ACCOUNT_DUPLICATE_LOGIN_ID);
        }
        SystemUser saved = userRepository.save(SystemUser.builder()
                .loginId(req.getLoginId())
                .password(passwordEncoder.encode(req.getPassword()))
                .name(req.getName())
                .role(req.getRole())
                .siteCode(req.getSiteCode())
                .trade(req.getTrade())
                .phone(req.getPhone())
                .email(req.getEmail())
                .active(true)
                .build());
        return AccountDto.Res.from(saved);
    }

    @Transactional
    public AccountDto.Res update(Long idx, AccountDto.UpdateReq req) {
        SystemUser user = findById(idx);
        user.update(req.getName(), req.getRole(), req.getSiteCode(), req.getTrade(), req.getActive(), req.getPhone(), req.getEmail());
        return AccountDto.Res.from(user);
    }

    /** 논리 삭제 — 계정 비활성화. */
    @Transactional
    public void delete(Long idx) {
        SystemUser user = findById(idx);
        user.deactivate();
    }

    public boolean isEmailAvailable(String email) {
        if (email == null || email.isBlank()) return true;
        return !userRepository.existsByEmail(email.toLowerCase().trim());
    }

    private SystemUser findById(Long idx) {
        return userRepository.findById(idx)
                .orElseThrow(() -> new BaseException(ACCOUNT_NOT_FOUND));
    }
}
