package org.example.dndn.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndn.auth.model.entity.SystemUser;
import org.example.dndn.auth.model.enums.UserRole;
import org.example.dndn.auth.repository.SystemUserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 애플리케이션 시작 시 ADMIN / HEADQUARTOR 계정을 최초 1회만 생성.
 * 이미 loginId가 존재하면 건너뜀.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final SystemUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedIfAbsent("admin",  "Admin1234!", "시스템관리자", UserRole.ADMIN,        null, null);
        seedIfAbsent("hq",     "Hq1234567!", "본사",       UserRole.HEADQUARTOR,   null, null);
    }

    private void seedIfAbsent(String loginId, String rawPw, String name,
                               UserRole role, String siteCode, String trade) {
        if (userRepository.existsByLoginId(loginId)) {
            return;
        }
        userRepository.save(SystemUser.builder()
                .loginId(loginId)
                .password(passwordEncoder.encode(rawPw))
                .name(name)
                .role(role)
                .siteCode(siteCode)
                .trade(trade)
                .active(true)
                .build());
        log.info("[DataInitializer] 기본 계정 생성: loginId={}, role={}", loginId, role);
    }
}
