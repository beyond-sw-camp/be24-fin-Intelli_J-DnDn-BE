package org.example.dndncore.workplan.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PlanType {
    YEARLY("연간"),
    MONTHLY("월간"),
    WEEKLY("주간");

    private final String label;

    public static PlanType fromLabel(String label) {
        if (label == null) {
            return MONTHLY;
        }

        for (PlanType type : values()) {
            if (type.label.equals(label) || type.name().equalsIgnoreCase(label)) {
                return type;
            }
        }

        return MONTHLY;
    }
}
