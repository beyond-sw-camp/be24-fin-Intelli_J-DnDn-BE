package org.example.dndncore.staffing.model;

import org.example.dndncore.workplan.model.enums.WorkTrade;

import java.util.Map;
import java.util.Set;

/**
 * 계정 공종(SystemUser.trade) ↔ 구역/작업자 공종명(zone.tradeName, Worker.trade) 매칭.
 * 계정 생성 시 "건축마감", 구역 WorkPlan category "마감공사" 등 네이밍 차이를 흡수한다.
 */
public final class StaffingTradeMatcher {

    private static final Map<String, String> PROCESS_TO_CATEGORY = Map.ofEntries(
            Map.entry("형틀", "골조공사"),
            Map.entry("철근", "골조공사"),
            Map.entry("골조", "골조공사"),
            Map.entry("목공", "골조공사"),
            Map.entry("목수", "골조공사"),
            Map.entry("용접", "골조공사"),
            Map.entry("미장", "마감공사"),
            Map.entry("조적", "마감공사"),
            Map.entry("도장", "마감공사"),
            Map.entry("타일", "마감공사"),
            Map.entry("전기", "전기공사"),
            Map.entry("설비", "설비공사"),
            Map.entry("방수", "방수공사"),
            Map.entry("토공", "토공사"),
            Map.entry("조경", "조경공사"),
            Map.entry("포장", "포장공사"),
            Map.entry("건축마감", "마감공사"),
            Map.entry("마감", "마감공사"),
            Map.entry("골조공사", "골조공사"),
            Map.entry("마감공사", "마감공사"),
            Map.entry("전기공사", "전기공사"),
            Map.entry("설비공사", "설비공사"),
            Map.entry("방수공사", "방수공사"),
            Map.entry("토공사", "토공사"),
            Map.entry("조경공사", "조경공사"),
            Map.entry("포장공사", "포장공사")
    );

    /** 계정 생성 공종 드롭다운에서 제외할 라벨 (마일스톤 행 등) */
    public static final Set<String> EXCLUDED_ACCOUNT_TRADE_NAMES = Set.of(
            "준공",
            "착공",
            "마일스톤",
            "주요 마일스톤",
            "주요마일스톤",
            "핵심 마일스톤",
            "핵심마일스톤"
    );

    private StaffingTradeMatcher() {
    }

    public static String resolveCategory(String raw) {
        String name = clean(raw);
        if (name.isEmpty()) {
            return "기타";
        }
        if (PROCESS_TO_CATEGORY.containsKey(name)) {
            return PROCESS_TO_CATEGORY.get(name);
        }
        WorkTrade wt = WorkTrade.fromLabel(name);
        if (wt != null) {
            return wt.getCategory();
        }
        return name;
    }

    public static boolean matches(String recordTrade, String assignedTrade) {
        String left = clean(recordTrade);
        String right = clean(assignedTrade);
        if (right.isBlank()) {
            return true;
        }
        if (left.isBlank()) {
            return false;
        }
        if (left.equals(right) || left.contains(right) || right.contains(left)) {
            return true;
        }
        String leftCat = resolveCategory(left);
        String rightCat = resolveCategory(right);
        if ("기타".equals(leftCat) || "기타".equals(rightCat)) {
            return false;
        }
        return leftCat.equals(rightCat)
                || leftCat.contains(rightCat)
                || rightCat.contains(leftCat);
    }

    public static boolean isExcludedAccountTradeName(String tradeName) {
        String name = clean(tradeName);
        if (name.isEmpty()) {
            return true;
        }
        if (EXCLUDED_ACCOUNT_TRADE_NAMES.contains(name)) {
            return true;
        }
        return name.contains("마일스톤");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
