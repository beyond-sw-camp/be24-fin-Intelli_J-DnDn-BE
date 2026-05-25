package org.example.dndncore.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * SSE Emitter 레지스트리.
 * siteCode 별로 연결된 admin 클라이언트의 SseEmitter 를 관리한다.
 * 출퇴근 이벤트 발생 시 {@link #broadcast(String, Object)} 로 해당 현장 구독자에게 push.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEmitterRegistry {

    private final ObjectMapper objectMapper;

    /** siteCode → 구독 중인 SseEmitter Set (key 는 resolveKey() 로 정규화) */
    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 새 SSE 연결을 등록하고 초기 ping 이벤트를 전송한다.
     *
     * @param siteCode 현장 코드 (빈 문자열이면 "ALL" 키로 처리)
     * @return 등록된 SseEmitter — 컨트롤러에서 반환
     */
    public SseEmitter subscribe(String siteCode) {
        String key = resolveKey(siteCode);
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.computeIfAbsent(key, k -> new CopyOnWriteArraySet<>()).add(emitter);

        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> remove(key, emitter));
        emitter.onError(e -> remove(key, emitter));

        // 연결 직후 ping 전송 — 브라우저가 스트림을 즉시 인식하도록
        try {
            emitter.send(SseEmitter.event().name("ping").data("connected"));
        } catch (IOException e) {
            remove(key, emitter);
        }

        log.debug("[SSE] subscribe siteCode={}, total={}", key,
                emitters.getOrDefault(key, Set.of()).size());
        return emitter;
    }

    /**
     * 특정 현장의 모든 구독자에게 이벤트를 전송한다.
     * 전송에 실패한 emitter 는 자동 제거된다.
     *
     * @param siteCode 현장 코드
     * @param data     전송할 데이터 (Jackson 이 JSON 직렬화)
     */
    public void broadcast(String siteCode, Object data) {
        String key = resolveKey(siteCode);
        Set<SseEmitter> set = emitters.get(key);
        if (set == null || set.isEmpty()) {
            log.debug("[SSE] no subscribers for siteCode={}", key);
            return;
        }

        // JSON 직렬화 (한 번만 수행)
        String json;
        try {
            json = objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.warn("[SSE] JSON serialization failed: {}", e.getMessage());
            return;
        }

        Set<SseEmitter> failed = ConcurrentHashMap.newKeySet();
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name("attendance").data(json));
            } catch (IOException e) {
                log.debug("[SSE] send failed, removing emitter: {}", e.getMessage());
                failed.add(emitter);
            }
        }
        set.removeAll(failed);
        log.debug("[SSE] broadcast siteCode={}, sent={}, failed={}", key,
                set.size(), failed.size());
    }

    private void remove(String key, SseEmitter emitter) {
        Set<SseEmitter> set = emitters.get(key);
        if (set != null) {
            set.remove(emitter);
            log.debug("[SSE] emitter removed, siteCode={}, remaining={}", key, set.size());
        }
    }

    private String resolveKey(String siteCode) {
        return (siteCode == null || siteCode.isBlank()) ? "ALL" : siteCode.trim();
    }
}
