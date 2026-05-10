package org.example.dndn.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.dndn.auth.model.dto.AuthDto;
import org.example.dndn.auth.model.entity.SystemUser;
import org.example.dndn.auth.model.enums.LoginMode;
import org.example.dndn.auth.model.enums.UserRole;
import org.example.dndn.auth.repository.SystemUserRepository;
import org.example.dndn.auth.security.JwtProvider;
import org.example.dndn.common.exception.BaseException;
import org.example.dndn.project.model.entity.Project;
import org.example.dndn.project.repository.ProjectRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

import static org.example.dndn.common.model.BaseResponseStatus.FAIL;
import static org.example.dndn.common.model.BaseResponseStatus.LOGIN_INVALID_USERINFO;
import static org.example.dndn.common.model.BaseResponseStatus.LOGIN_NO_ASSIGNED_SITE;
import static org.example.dndn.common.model.BaseResponseStatus.LOGIN_ROLE_NOT_ALLOWED_FOR_ADMIN;
import static org.example.dndn.common.model.BaseResponseStatus.LOGIN_ROLE_NOT_ALLOWED_FOR_SITE;
import static org.example.dndn.common.model.BaseResponseStatus.LOGIN_SITE_NOT_MATCHED;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final SystemUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final ProjectRepository projectRepository;

    /** 현장 로그인 탭에서 허용되는 역할. */
    private static final Set<UserRole> SITE_ROLES = EnumSet.of(
            UserRole.SITE_MANAGER,
            UserRole.SITE_DIRECTOR,
            UserRole.SECTION_LEADER,
            UserRole.SECTION_SUPERVISOR
    );

    /** 시스템 관리자 / 본사 로그인 탭에서 허용되는 역할. */
    private static final Set<UserRole> ADMIN_ROLES = EnumSet.of(
            UserRole.ADMIN,
            UserRole.HEADQUARTOR
    );

    @Transactional
    public void changePassword(AuthDto.ChangePasswordReq req) {
        Long userIdx = getAuthenticatedIdx();
        SystemUser user = userRepository.findById(userIdx)
                .orElseThrow(() -> new BaseException(FAIL));

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            throw new BaseException(LOGIN_INVALID_USERINFO);
        }

        user.changePassword(passwordEncoder.encode(req.getNewPassword()));
    }

    private Long getAuthenticatedIdx() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Long)) {
            throw new BaseException(FAIL);
        }
        return (Long) auth.getPrincipal();
    }

    public AuthDto.LoginRes login(AuthDto.LoginReq req) {
        SystemUser user = userRepository.findByLoginId(req.getLoginId())
                .orElseThrow(() -> new BaseException(LOGIN_INVALID_USERINFO));

        if (!user.isActive()) {
            throw new BaseException(LOGIN_INVALID_USERINFO);
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BaseException(LOGIN_INVALID_USERINFO);
        }

        // 프론트에서 선택한 로그인 모드(탭)와 실제 계정 권한 일치 여부 검증.
        // 보안/안정성 차원에서 프론트 검증 외에 백엔드에서 한 번 더 보장한다.
        validateLoginMode(user.getRole(), req.getLoginMode());

        Long userProjectId = resolveProjectId(user.getSiteCode());

        // 현장 로그인 탭에서 사용자가 특정 현장을 선택해 보냈을 때만 추가 검증한다.
        if (req.getLoginMode() == LoginMode.SITE && req.getSiteProjectId() != null) {
            validateSiteSelection(userProjectId, req.getSiteProjectId());
        }

        String token = jwtProvider.generate(user.getIdx(), user.getLoginId(), user.getRole());
        return AuthDto.LoginRes.builder()
                .accessToken(token)
                .userIdx(user.getIdx())
                .projectId(userProjectId)
                .name(user.getName())
                .role(user.getRole())
                .siteCode(user.getSiteCode())
                .trade(user.getTrade())
                .build();
    }

    /**
     * 사용자가 선택한 현장(projectId)이 계정에 배정된 현장과 동일한지 검증.
     * - 계정에 배정된 현장이 없으면 로그인 자체를 막는다.
     * - 다른 현장을 선택했다면 “현재 투입 중인 현장을 선택해 주세요.” 안내.
     */
    private void validateSiteSelection(Long userProjectId, Long selectedProjectId) {
        if (userProjectId == null) {
            throw new BaseException(LOGIN_NO_ASSIGNED_SITE);
        }
        if (!userProjectId.equals(selectedProjectId)) {
            throw new BaseException(LOGIN_SITE_NOT_MATCHED);
        }
    }

    /**
     * 로그인 탭(SITE / ADMIN)과 계정 권한이 맞는지 확인.
     * - {@code mode}가 null인 경우는 구버전 클라이언트로 보고 검증을 건너뛴다.
     */
    private void validateLoginMode(UserRole role, LoginMode mode) {
        if (mode == null) {
            return;
        }
        switch (mode) {
            case SITE -> {
                if (!SITE_ROLES.contains(role)) {
                    throw new BaseException(LOGIN_ROLE_NOT_ALLOWED_FOR_SITE);
                }
            }
            case ADMIN -> {
                if (!ADMIN_ROLES.contains(role)) {
                    throw new BaseException(LOGIN_ROLE_NOT_ALLOWED_FOR_ADMIN);
                }
            }
        }
    }

    private Long resolveProjectId(String siteCode) {
        if (siteCode == null || siteCode.isBlank()) {
            return null;
        }
        String prefix = "[" + siteCode.trim() + "]";
        return projectRepository.findAll().stream()
                .filter(project -> startsWithSiteCode(project, prefix))
                .map(Project::getIdx)
                .findFirst()
                .orElse(null);
    }

    private boolean startsWithSiteCode(Project project, String prefix) {
        String name = project.getName();
        return name != null && name.trim().startsWith(prefix);
    }
}
