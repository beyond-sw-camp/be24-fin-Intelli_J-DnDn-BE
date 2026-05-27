package org.example.dndncore.workorder.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

// feat : 작업 지시서 장비 데이터 전송용 DTO
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "작업 지시서 장비 DTO")
public class WorkOrderEquipmentDto {
    @Schema(description = "장비 ID", example = "1")
    private Long idx;
    @Schema(description = "게이트 ID", example = "5")
    private Integer gateIdx;
    @Schema(description = "장비명", example = "크레인 10톤")
    private String equipmentName;
    @Schema(description = "장비 수량", example = "2")
    private Integer equipmentCount;
}
