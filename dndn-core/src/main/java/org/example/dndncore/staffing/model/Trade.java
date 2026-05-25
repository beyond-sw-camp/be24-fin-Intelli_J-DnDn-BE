package org.example.dndncore.staffing.model;

import org.example.dndncore.worker.model.entity.Worker;

import java.util.Locale;

/**
 * 근로자 공종 분류 enum.
 *
 * <p><b>피로도 가중치</b> — 각 enum 값이 {@link #fatigueRiskWeight()}를 직접 반환한다.</p>
 *
 * <p><b>배치(Staffing) 카테고리</b> — {@code TradeNeed} DB 컬럼은 {@link #CARPENTER}/{@link #REBAR}/{@link #WELDER}/{@link #TILE}
 * 4개 카테고리 값만 저장한다. 세분화된 직종은 {@link #staffingGroup()}으로 해당 카테고리에 매핑된다.</p>
 */
public enum Trade {

    // ── 고위험 직종 (15–20pt) ────────────────────────────────────────────────
    WELDER,         // 용접       20pt  ← staffing 카테고리
    CARPENTER,      // 목공       17pt  ← staffing 카테고리
    MOKSU,          // 목수       16pt  → staffingGroup: CARPENTER
    FORMWORK,       // 형틀       15pt  → staffingGroup: CARPENTER

    // ── 중위험 직종 (10–14pt) ────────────────────────────────────────────────
    REBAR,          // 철근       14pt  ← staffing 카테고리
    EQUIPMENT_OP,   // 장비       12pt  → staffingGroup: TILE
    EXCAVATION,     // 굴착       10pt  → staffingGroup: TILE

    // ── 중저위험 직종 (7–9pt) ────────────────────────────────────────────────
    CIVIL,          // 토목        9pt  → staffingGroup: TILE
    EARTHWORK,      // 토공        8pt  → staffingGroup: TILE
    DRAINAGE,       // 배수        7pt  → staffingGroup: TILE

    // ── 저위험 직종 (4–6pt) ─────────────────────────────────────────────────
    TILE,           // 타일        6pt  ← staffing 카테고리
    FINISHING,      // 마감        5pt  → staffingGroup: TILE
    GENERAL_LABOR,  // 인부        5pt  → staffingGroup: TILE
    COMMON_WORKER,  // 보통공      5pt  → staffingGroup: TILE
    CLEANUP;        // 정리        4pt  → staffingGroup: TILE

    /**
     * 피로도 산정에 사용하는 공종별 위험 가중치.
     */
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

    /**
     * 배치 구역 공종 요구({@code TradeNeed}) 저장용 4-카테고리 매핑.
     * {@code TradeNeed.trade} DB 컬럼 및 배치 집계 시에만 사용한다.
     */
    public Trade staffingGroup() {
        return switch (this) {
            case WELDER                                                         -> WELDER;
            case CARPENTER, MOKSU, FORMWORK                                     -> CARPENTER;
            case REBAR                                                          -> REBAR;
            case TILE, EQUIPMENT_OP, EXCAVATION, CIVIL, EARTHWORK, DRAINAGE,
                 FINISHING, GENERAL_LABOR, COMMON_WORKER, CLEANUP               -> TILE;
        };
    }

    /**
     * 근로자의 공종 문자열이 이 직종에 해당하는지 한글 라벨로 판별.
     * 각 직종은 고유 키워드를 가지며 중복 매칭 없음.
     */
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

    /**
     * 근로자 공종 문자열을 분류하여 가장 구체적인 Trade 값을 반환.
     * 매칭 없으면 {@code null} (미분류).
     */
    public static Trade classifyWorker(Worker worker) {
        if (worker == null) return null;
        for (Trade t : values()) {
            if (t.matchesWorker(worker)) return t;
        }
        return null;
    }
}
