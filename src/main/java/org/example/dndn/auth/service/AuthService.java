package org.example.dndn.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.dndn.auth.model.dto.AuthDto;
import org.example.dndn.auth.model.entity.SystemUser;
import org.example.dndn.auth.repository.SystemUserRepository;
import org.example.dndn.auth.security.JwtProvider;
import org.example.dndn.common.exception.BaseException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.example.dndn.common.model.BaseResponseStatus.FAIL;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final SystemUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthDto.LoginRes login(AuthDto.LoginReq req) {
        SystemUser user = userRepository.findByLoginId(req.getLoginId())
                .orElseThrow(() -> new BaseException(FAIL));

        if (!user.isActive()) {
            throw new BaseException(FAIL);
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BaseException(FAIL);
        }

        String token = jwtProvider.generate(user.getIdx(), user.getLoginId(), user.getRole());
        return AuthDto.LoginRes.builder()
                .accessToken(token)
                .userIdx(user.getIdx())
                .name(user.getName())
                .role(user.getRole())
                .siteCode(user.getSiteCode())
                .trade(user.getTrade())
                .build();
    }
}
