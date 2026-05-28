package org.example.dndn.domain.staffing.model;

import org.example.dndn.domain.worker.model.entity.Worker;

import java.util.Locale;

/**
 * 근로자 공종 분류 enum (배치 모듈 — 피로도 계산 전용).
 * BE 모듈의 Trade와 동일 구조로 유지한다.
 */
public enum Trade {

    // ── 고위험 직종 (15–20pt) ────────────────────────────────────────────────
    WELDER,         // 용접  20pt
    CARPENTER,      // 목공  17pt
    MOKSU,          // 목수  16pt
    FORMWORK,       // 형틀  15pt

    // ── 중위험 직종 (10–14pt) ────────────────────────────────────────────────
    REBAR,          // 철근  14pt
    EQUIPMENT_OP,   // 장비  12pt
    EXCAVATION,     // 굴착  10pt

    // ── 중저위험 직종 (7–9pt) ────────────────────────────────────────────────
    CIVIL,          // 토목   9pt
    EARTHWORK,      // 토공   8pt
    DRAINAGE,       // 배수   7pt

    // ── 저위험 직종 (4–6pt) ─────────────────────────────────────────────────
    TILE,           // 타일   6pt
    FINISHING,      // 마감   5pt
    GENERAL_LABOR,  // 인부   5pt
    COMMON_WORKER,  // 보통공 5pt
    CLEANUP;        // 정리   4pt

    public int fatigueRiskWeight() {
        return switch (this) {
            case WELDER       -> 20;
            case CARPENTER    -> 17;
            case MOKSU        -> 16;
            case FORMWORK     -> 15;
            case REBAR        -> 14;
            case EQUIPMENT_OP -> 12;
            case EXCAVATION   -> 10;
            case CIVIL        ->  9;
            case EARTHWORK    ->  8;
            case DRAINAGE     ->  7;
            case TILE         ->  6;
            case FINISHING    ->  5;
            case GENERAL_LABOR->  5;
            case COMMON_WORKER->  5;
            case CLEANUP      ->  4;
        };
    }

    /** {@code trade == null} 이면 미분류 공종 기본값(5). */
    public static int fatigueRiskWeightOrDefault(Trade trade) {
        return trade == null ? 5 : trade.fatigueRiskWeight();
    }

    public boolean matchesWorker(Worker worker) {
        if (worker == null || worker.getTrade() == null) return false;
        String s = worker.getTrade().trim().toLowerCase(Locale.ROOT);
        return switch (this) {
            case WELDER        -> s.contains("용접");
            case CARPENTER     -> s.contains("목공");
            case MOKSU         -> s.contains("목수");
            case FORMWORK      -> s.contains("형틀");
            case REBAR         -> s.contains("철근");
            case EQUIPMENT_OP  -> s.contains("장비");
            case EXCAVATION    -> s.contains("굴착");
            case CIVIL         -> s.contains("토목");
            case EARTHWORK     -> s.contains("토공");
            case DRAINAGE      -> s.contains("배수");
            case TILE          -> s.contains("타일");
            case FINISHING     -> s.contains("마감");
            case GENERAL_LABOR -> s.contains("인부");
            case COMMON_WORKER -> s.contains("보통공");
            case CLEANUP       -> s.contains("정리");
        };
    }

    public static Trade classifyWorker(Worker worker) {
        if (worker == null) return null;
        for (Trade t : values()) {
            if (t.matchesWorker(worker)) return t;
        }
        return null;
    }
}
