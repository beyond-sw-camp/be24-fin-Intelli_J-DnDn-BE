package org.example.dndn.staffing.model;

import org.example.dndn.worker.model.entity.Worker;

import java.util.Locale;

// 배치 가능 직종
public enum Trade {
    CARPENTER,
    REBAR,
    WELDER,
    TILE;

    // 마스터 공종 문자열이 이 직종에 해당하는지 한글 라벨로 근사 판별.
    public boolean matchesWorker(Worker worker) {
        if (worker == null || worker.getSubLabel() == null) {
            return false;
        }
        String s = worker.getSubLabel().trim().toLowerCase(Locale.ROOT);
        return switch (this) {
            case CARPENTER -> s.contains("목공");
            case REBAR -> s.contains("철근");
            case WELDER -> s.contains("용접");
            case TILE -> s.contains("타일");
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
