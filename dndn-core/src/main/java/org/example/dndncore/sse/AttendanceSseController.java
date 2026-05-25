package org.example.dndncore.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 관리자 웹 전용 SSE 엔드포인트.
 * 모바일 앱에서 출퇴근 처리가 발생하면 해당 현장을 구독 중인 관리자에게 실시간 push.
 *
 * <pre>
 *   GET /management/sse/attendance-stream?siteCode={현장코드}
 *   Content-Type: text/event-stream
 * </pre>
 *
 * 이벤트 이름: {@code attendance}
 * 데이터 형식:
 * <pre>
 *   {
 *     "workerIdx":      1,
 *     "name":           "홍길동",
 *     "action":         "CHECK_IN",   // CHECK_IN | CHECK_OUT
 *     "time":           "09:05",
 *     "siteCode":       "GN-A",
 *     "attendanceStatus": "PRESENT"  // PRESENT | LATE | EARLY_LEAVE | LEAVE
 *   }
 * </pre>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/management/sse")
public class AttendanceSseController {

    private final SseEmitterRegistry registry;

    /**
     * SSE 스트림 구독.
     * siteCode 를 생략하면 "ALL" 키로 등록된다 (특정 현장 코드가 없을 때).
     */
    @GetMapping(value = "/attendance-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter attendanceStream(
            @RequestParam(defaultValue = "") String siteCode
    ) {
        return registry.subscribe(siteCode);
    }
}
