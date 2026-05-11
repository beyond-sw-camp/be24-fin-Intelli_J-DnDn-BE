package org.example.dndn.workplan.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
@Getter
@AllArgsConstructor
public enum WorkTrade {
    EARTHWORK("토공"),
    FORM("형틀"),
    ELECTRIC("전기"),
    WATERPROOF("방수"),
    FRAME("골조"),
    FACILITY("설비"),
    REBAR("철근"),
    MASONRY("조적"),
    PLASTER("미장"),
    PAINT("도장"),
    TILE("타일"),
    LANDSCAPE("조경"),
    PAVEMENT("포장"),
    ETC("기타");

    private final String label;

    public static WorkTrade fromLabel(String label) {
        if (label == null) {
            return null;
        }

        for (WorkTrade trade : values()) {
            if (trade.label.equals(label) || trade.name().equalsIgnoreCase(label)) {
                return trade;
            }
        }

        return null;
    }
}
