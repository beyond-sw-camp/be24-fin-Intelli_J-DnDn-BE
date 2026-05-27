package org.example.dndncore.mobile.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.dndncore.sse.SseEmitterRegistry;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 모바일 작업자 전용 SSE 엔드포인트.
 * 인력배치 확정 시 해당 작업자에게 실시간 push.
 *
 * <pre>
 *   GET /mobile/sse/assignment-stream?token={JWT}
 *   Content-Type: text/event-stream
 * </pre>
 *
 * EventSource API 는 커스텀 헤더를 지원하지 않으므로 JWT 를 query param {@code token} 으로 전달.
 * JwtFilter 가 해당 파라미터를 인식하여 인증을 완료한 후 이 컨트롤러가 실행된다.
 *
 * 이벤트명: {@code assignment}
 * 데이터 형식:
 * <pre>
 *   {
 *     "type":          "ASSIGNED",
 *     "zoneMainTitle": "지상 2층",
 *     "zoneSubTitle":  "복도",
 *     "placement":     "지상 2층 · 복도"
 *   }
 * </pre>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/mobile/sse")
@Tag(name = "Mobile Worker SSE", description = "모바일 작업자 SSE API")
public class MobileWorkerSseController {

    private final SseEmitterRegistry registry;

    @GetMapping(value = "/assignment-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "배치 알림 SSE 구독", description = "모바일 작업자가 배치 알림 스트림(text/event-stream)을 구독합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "구독 성공",
                    content = @Content(schema = @Schema(type = "string", format = "event-stream"))),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public SseEmitter assignmentStream() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long workerIdx = (Long) auth.getPrincipal();
        return registry.subscribeWorker(workerIdx);
    }
}
