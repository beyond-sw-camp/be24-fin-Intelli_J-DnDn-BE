package org.example.dndncore.workplan.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "feat : 작업 계획 상태")
public enum PlanStatus {
    PLANNED("진행 예정"),
    IN_PROGRESS("진행 중");

    @Schema(description = "상태 레이블", example = "진행 예정")
    private final String label;

    public static PlanStatus fromLabel(String label) {
        if (label == null) {
            return PLANNED;
        }

        for (PlanStatus status : values()) {
            if (status.label.equals(label) || status.name().equalsIgnoreCase(label)) {
                return status;
            }
        }

        return PLANNED;
    }
}
