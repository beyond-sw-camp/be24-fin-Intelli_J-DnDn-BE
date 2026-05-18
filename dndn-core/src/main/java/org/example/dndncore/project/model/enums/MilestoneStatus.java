package org.example.dndncore.project.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MilestoneStatus {
    PLANNED("예정"),
    DONE("완료"),
    DELAYED("지연");

    private final String label;

    public static MilestoneStatus fromLabel(String label) {
        if (label == null) return PLANNED;
        for (MilestoneStatus status : values()) {
            if (status.label.equals(label)) return status;
        }
        return PLANNED;
    }
}