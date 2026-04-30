package org.example.dndn.workplan.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WorkTrade {
    FORM("형틀"),
    ELECTRIC("전기"),
    WATERPROOF("방수"),
    FRAME("골조"),
    FACILITY("설비"),
    REBAR("철근");

    private final String label;

    public static WorkTrade fromLabel(String label) {
        if (label == null) {
            return null;
        }

        for (WorkTrade trade : values()) {
            if (trade.label.equals(label)) {
                return trade;
            }
        }

        return null;
    }
}