package org.example.dndncore.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.auth.model.dto.AuthDto;
import org.example.dndncore.auth.model.entity.SystemUser;
import org.example.dndncore.auth.model.enums.LoginMode;
import org.example.dndncore.auth.model.enums.UserRole;
import org.example.dndncore.auth.repository.SystemUserRepository;
import org.example.dndncore.auth.security.JwtProvider;
import org.example.dndncore.common.exception.BaseException;
import org.example.dndncore.project.model.entity.Project;
import org.example.dndncore.project.repository.MasterScheduleRepository;
import org.example.dndncore.project.repository.ProjectRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

import static org.example.dndncore.common.model.BaseResponseStatus.ACCOUNT_NOT_FOUND;
import static org.example.dndncore.common.model.BaseResponseStatus.AUTH_NOT_AUTHENTICATED;
import static org.example.dndncore.common.model.BaseResponseStatus.LOGIN_ACCOUNT_DEACTIVATED;
import static org.example.dndncore.common.model.BaseResponseStatus.LOGIN_INVALID_USERINFO;
import static org.example.dndncore.common.model.BaseResponseStatus.LOGIN_ROLE_NOT_ALLOWED_FOR_ADMIN;
import static org.example.dndncore.common.model.BaseResponseStatus.LOGIN_ROLE_NOT_ALLOWED_FOR_SITE;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final SystemUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final ProjectRepository projectRepository;
    private final MasterScheduleRepository masterScheduleRepository;

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
                .orElseThrow(() -> new BaseException(ACCOUNT_NOT_FOUND));

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            throw new BaseException(LOGIN_INVALID_USERINFO);
        }

        user.changePassword(passwordEncoder.encode(req.getNewPassword()));
    }

    private Long getAuthenticatedIdx() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Long)) {
            throw new BaseException(AUTH_NOT_AUTHENTICATED);
        }
        return (Long) auth.getPrincipal();
    }

    public AuthDto.LoginRes login(AuthDto.LoginReq req) {
        SystemUser user = userRepository.findByLoginId(req.getLoginId())
                .orElseThrow(() -> new BaseException(LOGIN_INVALID_USERINFO));

        if (!user.isActive()) {
            throw new BaseException(LOGIN_ACCOUNT_DEACTIVATED);
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BaseException(LOGIN_INVALID_USERINFO);
        }

        // 프론트에서 선택한 로그인 모드(탭)와 실제 계정 권한 일치 여부 검증.
        // 보안/안정성 차원에서 프론트 검증 외에 백엔드에서 한 번 더 보장한다.
        validateLoginMode(user.getRole(), req.getLoginMode());

        // 현장 계정은 로그인 시 본인 배정 현장으로 자동 진입한다 (siteCode → projectId 매핑).
        // 본사 / 시스템 관리자는 어떤 현장이든 접근 가능하므로, 선택한 현장은 프론트가 별도로
        // 라우팅 쿼리로만 사용한다(백엔드 검증 없음).
        Long userProjectId = resolveProjectId(user.getSiteCode());
        boolean needsInitialUpload = needsInitialUpload(user.getRole(), userProjectId);

        String token = jwtProvider.generate(user.getIdx(), user.getLoginId(), user.getRole());
        return AuthDto.LoginRes.builder()
                .accessToken(token)
                .userIdx(user.getIdx())
                .projectId(userProjectId)
                .name(user.getName())
                .role(user.getRole())
                .siteCode(user.getSiteCode())
                .trade(user.getTrade())
                .needsInitialUpload(needsInitialUpload)
                .build();
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

    private boolean needsInitialUpload(UserRole role, Long projectId) {
        if (!isInitialUploadRole(role) || projectId == null) {
            return false;
        }
        return !masterScheduleRepository.existsByProject_Idx(projectId);
    }

    private boolean isInitialUploadRole(UserRole role) {
        return role == UserRole.SITE_MANAGER || role == UserRole.SITE_DIRECTOR;
    }
}
