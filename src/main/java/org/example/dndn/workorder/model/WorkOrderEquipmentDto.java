package org.example.dndn.workorder.model;

import lombok.*;

// feat : 작업 지시서 장비 데이터 전송용 DTO
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkOrderEquipmentDto {
    private Long idx;
    private Integer gateIdx;
    private String equipmentName;
    private Integer equipmentCount;
}
