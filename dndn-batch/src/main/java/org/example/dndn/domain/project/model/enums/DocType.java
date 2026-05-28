package org.example.dndn.domain.project.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

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
