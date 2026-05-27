package org.example.dndngateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final List<String> PROTECTED_PREFIXES = List.of(
            "/api/msa/"
    );

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isPublicRequest(request, path) || !requiresAuthentication(request, path)) {
            return chain.filter(exchange);
        }

        String token = resolveToken(request);
        if (token == null) {
            return unauthorized(exchange.getResponse(), 3013, "Authentication is required.");
        }

        try {
            Claims claims = jwtProvider.parse(token);
            String userIdx = String.valueOf(claims.get("idx", Long.class));
            String role = claims.get("role", String.class);

            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Idx", userIdx)
                    .header("X-User-Role", role)
                    .header("X-User-LoginId", claims.getSubject())
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (ExpiredJwtException e) {
            return unauthorized(exchange.getResponse(), 3001, "JWT token has expired.");
        } catch (Exception e) {
            return unauthorized(exchange.getResponse(), 3002, "JWT token is invalid.");
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isPublicRequest(ServerHttpRequest request, String path) {
        if (request.getMethod() == HttpMethod.OPTIONS) {
            return true;
        }
        return path.startsWith("/actuator/")
                || path.equals("/api/msa/document-management/health")
                // document-management Swagger UI 경로 — JWT 없이 접근 가능
                || path.startsWith("/api/msa/document-management/swagger-ui")
                || path.startsWith("/api/msa/document-management/v3/api-docs");
    }

    private boolean requiresAuthentication(ServerHttpRequest request, String path) {
        return PROTECTED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean isPasswordChange(ServerHttpRequest request, String path) {
        return request.getMethod() == HttpMethod.PUT && path.equals("/api/auth/password");
    }

    private String resolveToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring(7);
    }

    private Mono<Void> unauthorized(ServerHttpResponse response, int code, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return writeError(response, code, message);
    }

    private Mono<Void> forbidden(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return writeError(response, 3014, message);
    }

    private Mono<Void> writeError(ServerHttpResponse response, int code, String message) {
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"success":false,"code":%d,"message":"%s","data":null}
                """.formatted(code, message);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }
}
