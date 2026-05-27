package org.example.dndncore.auth.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // [스웨거 보안 예외 처리]
        // 스웨거 관련 요청은 JWT 검사 없이 바로 통과시킵니다.
        String path = request.getRequestURI();
        if (path.startsWith("/swagger-ui/") ||
                path.startsWith("/v3/api-docs") ||
                path.startsWith("/swagger-resources/") ||
                path.equals("/swagger-ui.html")) {
            chain.doFilter(request, response);
            return;
        }

        // Authorization 헤더 우선, 없으면 SSE용 query param 'token' 폴백
        String rawToken = null;
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            rawToken = header.substring(7);
        } else {
            String queryToken = request.getParameter("token");
            if (queryToken != null && !queryToken.isBlank()) {
                rawToken = queryToken;
            }
        }

        if (rawToken != null) {
            try {
                Claims claims = jwtProvider.parse(rawToken);
                String type = claims.get("type", String.class);

                if ("WORKER".equals(type)) {
                    // 모바일 작업자 토큰 — principal = workerIdx, authority = ROLE_WORKER
                    Long workerIdx = claims.get("workerIdx", Long.class);
                    var auth = new UsernamePasswordAuthenticationToken(
                            workerIdx, null, List.of(new SimpleGrantedAuthority("ROLE_WORKER")));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    // 관리자(SystemUser) 토큰 — 기존 로직 유지
                    Long idx = claims.get("idx", Long.class);
                    String role = claims.get("role", String.class);
                    var auth = new UsernamePasswordAuthenticationToken(
                            idx, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ignored) {
                // 유효하지 않은 토큰은 인증 없이 통과 — 이후 접근 제어에서 거절됨
            }
        }
        chain.doFilter(request, response);
    }
}