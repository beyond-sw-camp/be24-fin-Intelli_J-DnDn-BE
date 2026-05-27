package org.example.dndncore.workplan.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "feat : 작업 계획 유형")
public enum PlanType {
    YEARLY("연간"),
    MONTHLY("월간"),
    WEEKLY("주간");

    @Schema(description = "계획 유형 레이블", example = "월간")
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
