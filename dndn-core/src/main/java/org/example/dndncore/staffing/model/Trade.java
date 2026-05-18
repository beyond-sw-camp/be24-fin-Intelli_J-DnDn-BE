package org.example.dndncore.staffing.model;

import org.example.dndncore.worker.model.entity.Worker;

import java.util.Locale;

// 배치 가능 직종
public enum Trade {
    CARPENTER,
    REBAR,
    WELDER,
    TILE;

    /**
     * 피로도 산정·자동 배치(STAFFING_001)에서 쓰는 공종별 위험 가중치(5–20 스케일).
     */
    public int fatigueRiskWeight() {
        return switch (this) {
            case TILE -> 6;
            case REBAR -> 14;
            case CARPENTER -> 17;
            case WELDER -> 20;
        };
    }

    /** {@code trade == null} 이면 미분류 공종 기본값(10). */
    public static int fatigueRiskWeightOrDefault(Trade trade) {
        return trade == null ? 10 : trade.fatigueRiskWeight();
    }

    // 마스터 공종 문자열이 이 직종에 해당하는지 한글 라벨로 근사 판별.
    public boolean matchesWorker(Worker worker) {
        if (worker == null || worker.getTrade() == null) {
            return false;
        }
        String s = worker.getTrade().trim().toLowerCase(Locale.ROOT);
        return switch (this) {
            case CARPENTER -> s.contains("목공") || s.contains("목수") || s.contains("형틀");
            case REBAR -> s.contains("철근");
            case WELDER -> s.contains("용접");
            case TILE -> s.contains("타일")
                    || s.contains("마감")
                    || s.contains("토목")
                    || s.contains("인부")
                    || s.contains("보통공")
                    || s.contains("토공")
                    || s.contains("굴착")
                    || s.contains("배수")
                    || s.contains("정리")
                    || s.contains("장비");
        };
    }

    // 투입 인원 카운트용 — 문자열 기준 하나의 직종만 부여(선행 매칭).
    public static Trade classifyWorker(Worker worker) {
        if (worker == null) {
            return null;
        }
        for (Trade t : values()) {
            if (t.matchesWorker(worker)) {
                return t;
            }
        }
        return null;
    }
}
