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
                            if (request.getRequestURI().startsWith("/document-management/local-files/")) {
                                response.setHeader("X-Frame-Options", "");
                            }
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/mobile/auth/**").permitAll()
                        .requestMatchers("/mobile/worker/**").hasRole("WORKER")
                        .requestMatchers("/mobile/sse/**").hasRole("WORKER")

                        // 1. 스웨거 경로는 인증 없이 누구나 접근 가능
                        //    Nginx가 /api prefix를 그대로 전달하는 경우를 대비해 양쪽 경로 모두 허용
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/api/swagger-ui/**",
                                "/api/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // 2. 관리자 웹 규칙
                        .requestMatchers(HttpMethod.PUT, "/auth/password").authenticated()
                        .requestMatchers("/auth/**", "/project/**").permitAll()
                        .requestMatchers("/document-management/local-files/**").permitAll()
                        .requestMatchers("/document-management/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/work-order", "/work-order/slice").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/account-requests").authenticated()

                        // 3. 나머지 비즈니스 로직 (기존 authenticated 설정 유지)
                        .requestMatchers(
                                "/master-schedule/**",
                                "/trade-process/**",
                                "/work-plan/**",
                                "/work-order/**",
                                "/report/**",
                                "/analysis/**",
                                "/schedule-change-request/**"
                        ).authenticated()

                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
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
                "http://localhost:5173",
                "http://localhost:8081",
                "http://192.100.200.41:8081",
                "http://localhost",
                "https://localhost",
                "capacitor://localhost",
                "ionic://localhost",
                "https://www.dndn24.kro.kr",
                "https://www.dndn26.kro.kr",
                "https://dndn26.kro.kr"
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
