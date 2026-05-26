package org.example.dndncore.auth.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import org.springframework.http.HttpMethod;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .addHeaderWriter((request, response) -> {
                            // 로컬 파일 미리보기 경로는 X-Frame-Options 제거
                            if (request.getRequestURI().startsWith("/document-management/local-files/")) {
                                response.setHeader("X-Frame-Options", "");
                            }
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        // SSE async 재디스패치: Tomcat 내부 dispatch이므로 보안 체크 불필요.
                        // JwtFilter(OncePerRequestFilter)는 async dispatch를 건너뛰어 SecurityContext가
                        // 비어있기 때문에 AuthorizationFilter가 Access Denied를 반환하는 문제를 방지한다.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        // 모바일 작업자 — 로그인은 인증 불필요, 나머지는 ROLE_WORKER 필요
                        .requestMatchers("/mobile/auth/**").permitAll()
                        .requestMatchers("/mobile/worker/**").hasRole("WORKER")
                        .requestMatchers("/mobile/sse/**").hasRole("WORKER")
                        // 관리자 웹 기존 규칙
                        .requestMatchers(HttpMethod.PUT, "/auth/password").authenticated()
                        .requestMatchers("/auth/**","/project/**").permitAll()
                        .requestMatchers("/document-management/local-files/**").permitAll()
                        .requestMatchers("/document-management/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/work-order", "/work-order/slice").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/account-requests").authenticated()
                        .requestMatchers(
                                "/master-schedule/**",
                                "/trade-process/**",
                                "/work-plan/**",
                                "/work-order/**",
                                "/report/**",
                                "/analysis/**",
                                "/schedule-change-request/**"
                        ).authenticated()
                        .anyRequest().permitAll()   // 기존 엔드포인트는 현행 유지 (추후 역할별 제한 추가)
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // ── 에러 응답(401/403)에도 CORS 헤더 보존 ───────────────────────────────
                // sendError() → Tomcat 에러 디스패치 → 새 Response 컨텍스트 생성 → CORS 헤더 소멸.
                // 해결책: sendError() 대신 직접 응답을 작성해서 에러 디스패치를 우회한다.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            String origin = request.getHeader("Origin");
                            if (origin != null) {
                                response.setHeader("Access-Control-Allow-Origin", origin);
                                response.setHeader("Access-Control-Allow-Credentials", "true");
                            }
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"success\":false,\"code\":3001,\"message\":\"인증이 필요합니다.\"}");
                            response.getWriter().flush();
                        })
                        .accessDeniedHandler((request, response, denied) -> {
                            String origin = request.getHeader("Origin");
                            if (origin != null) {
                                response.setHeader("Access-Control-Allow-Origin", origin);
                                response.setHeader("Access-Control-Allow-Credentials", "true");
                            }
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"success\":false,\"code\":3002,\"message\":\"접근 권한이 없습니다.\"}");
                            response.getWriter().flush();
                        })
                )
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:5173",          // 관리자 웹 Vite dev
                "http://localhost:8081",          // 모바일 Vite dev (로컬)
                "http://192.100.200.41:8081",     // 모바일 Vite dev (팀 내부망 IP)
                "http://localhost",               // Capacitor Android WebView
                "capacitor://localhost",          // Capacitor iOS WebView
                "ionic://localhost",              // Ionic 앱 내부 scheme
                "https://www.dndn24.kro.kr"      // 배포 도메인
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Content-Type", "Cache-Control", "X-Accel-Buffering"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
