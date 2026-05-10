package org.example.dndn.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.dndn.auth.model.dto.AuthDto;
import org.example.dndn.auth.model.entity.SystemUser;
import org.example.dndn.auth.repository.SystemUserRepository;
import org.example.dndn.auth.security.JwtProvider;
import org.example.dndn.common.exception.BaseException;
import org.example.dndn.project.model.entity.Project;
import org.example.dndn.project.repository.ProjectRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.example.dndn.common.model.BaseResponseStatus.FAIL;
import static org.example.dndn.common.model.BaseResponseStatus.LOGIN_INVALID_USERINFO;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final SystemUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final ProjectRepository projectRepository;

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
                .projectId(resolveProjectId(user.getSiteCode()))
                .name(user.getName())
                .role(user.getRole())
                .siteCode(user.getSiteCode())
                .trade(user.getTrade())
                .build();
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
