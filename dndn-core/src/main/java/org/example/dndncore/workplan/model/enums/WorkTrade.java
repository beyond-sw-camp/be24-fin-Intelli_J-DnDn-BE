package org.example.dndncore.workplan.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "feat : 작업 공종")
public enum WorkTrade {
    EARTHWORK("토공",   "토공사"),
    FORM     ("형틀",   "골조공사"),
    ELECTRIC ("전기",   "전기공사"),
    WATERPROOF("방수",  "방수공사"),
    FRAME    ("골조",   "골조공사"),
    FACILITY ("설비",   "설비공사"),
    REBAR    ("철근",   "골조공사"),
    MASONRY  ("조적",   "마감공사"),
    PLASTER  ("미장",   "마감공사"),
    PAINT    ("도장",   "마감공사"),
    TILE     ("타일",   "마감공사"),
    LANDSCAPE("조경",   "조경공사"),
    PAVEMENT ("포장",   "포장공사"),
    ETC      ("기타",   "기타");

    @Schema(description = "공종 레이블", example = "토공")
    private final String label;
    @Schema(description = "상위 공종 분류명", example = "토공사")
    private final String category;

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
