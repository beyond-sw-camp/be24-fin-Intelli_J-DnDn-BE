package org.example.dndncore.project.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 최초 업로드 공정표 종류
 * - 마스터 공정표: 전체 공사의 큰 일정 기준
 * - 마일스톤 공정표: 주요 마일스톤 일정
 * - 보할 공정표: 공정별 비중(보할율) 정보
 * - 공종별 시공계획서: 공종별 상세 시공 계획 (협력사명 포함)
 */
@Getter
@AllArgsConstructor
public enum DocType {
    MASTER("마스터 공정표"),
    MILESTONE("마일스톤 공정표"),
    WEIGHT("보할 공정표"),
    TRADE_PLAN("공종별 시공계획서");

    private final String label;

    public static DocType fromLabel(String label) {
        if (label == null) return null;
        for (DocType type : values()) {
            if (type.label.equals(label)) return type;
        }
        return null;
    }
}