package org.example.dndncore.workplan.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

// feat : 작업 인력 직종 (공종과 별개 개념)
@Getter
@RequiredArgsConstructor
@Schema(description = "feat : 작업 인력 직종")
public enum WorkerTrade {

    SKILLED("전공"),
    COMMON("보통공"),
    REBAR("철근공"),
    FORMWORK("형틀공"),
    CARPENTER("목수"),
    PLASTERER("미장공"),
    MASON("조적공"),
    PAINTER("도장공"),
    WATERPROOFER("방수공"),
    TILER("타일공"),
    ELECTRICIAN("전기공"),
    PLUMBER("배관공"),
    WELDER("용접공"),
    OTHER("기타");

    @Schema(description = "직종 레이블", example = "전공")
    private final String label;

    public static WorkerTrade fromLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }

        return Arrays.stream(values())
                .filter(t -> t.label.equals(label))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 직종: " + label));
    }
}