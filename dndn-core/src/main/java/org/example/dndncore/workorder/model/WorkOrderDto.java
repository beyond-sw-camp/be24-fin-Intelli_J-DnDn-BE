package org.example.dndncore.workorder.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

public class WorkOrderDto {

    // [WORKORDER_001] 1단계 : 지시서 작성 요청 DTO
    // feat : 작업 지시서 생성/수정 요청 (Request) DTO
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @Schema(description = "작업 지시서 생성/수정 요청 DTO")
    public static class Req {
        @Schema(description = "현장 ID", example = "1")
        private Long siteIdx;
        @Schema(description = "협력사 ID", example = "10")
        private Long partnerCompanyIdx;
        @Schema(description = "작업 계획 ID", example = "100")
        private Long workPlanId;
        @Schema(description = "공종 유형", example = "CARPENTRY")
        private String tradeType;
        @Schema(description = "제목", example = "철근 배근 작업")
        private String title;
        @Schema(description = "지시 내용", example = "A동 3층 철근 배근 진행")
        private String instructionContent;
        @Schema(description = "작업 내용", example = "철근 결속 및 배근")
        private String workDetail;
        @Schema(description = "작업 시간", example = "08:00-17:00")
        private String workTime;
        @Schema(description = "안전 유의사항", example = "안전모 착용 필수")
        private String safetyContent;
        @Schema(description = "마감일", example = "2026-05-30")
        private LocalDate dueDate;
        @Schema(description = "상태 코드", example = "PENDING")
        private String statusCode;
        @Schema(description = "투입 인원 수", example = "8")
        private Integer workerCount;
        @Schema(description = "등록 장비 목록")
        private List<WorkOrderEquipmentDto> equipments; // 2단계에서 활용됨
    }

    // [WORKORDER_003] 3단계 : 지시서 목록 조회 응답 DTO
    // feat : 작업 지시서 조회 응답 (Response) DTO
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @Schema(description = "작업 지시서 조회 응답 DTO")
    public static class Res {
        @Schema(description = "작업 지시서 ID", example = "1")
        private Long idx;
        @Schema(description = "현장 ID", example = "1")
        private Long siteIdx;
        @Schema(description = "협력사 ID", example = "10")
        private Long partnerCompanyIdx;
        @Schema(description = "작업 계획 ID", example = "100")
        private Long workPlanId;
        @Schema(description = "공종 유형", example = "CARPENTRY")
        private String tradeType;
        @Schema(description = "제목", example = "철근 배근 작업")
        private String title;
        @Schema(description = "지시 내용", example = "A동 3층 철근 배근 진행")
        private String instructionContent;
        @Schema(description = "작업 내용", example = "철근 결속 및 배근")
        private String workDetail;
        @Schema(description = "작업 시간", example = "08:00-17:00")
        private String workTime;
        @Schema(description = "안전 유의사항", example = "안전모 착용 필수")
        private String safetyContent;
        @Schema(description = "마감일", example = "2026-05-30")
        private LocalDate dueDate;
        @Schema(description = "상태 코드", example = "PENDING")
        private String statusCode;
        @Schema(description = "투입 인원 수", example = "8")
        private Integer workerCount;
        @Schema(description = "등록 장비 목록")
        private List<WorkOrderEquipmentDto> equipments;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @Schema(description = "작업 지시서 페이징 응답 DTO")
    public static class SliceRes {
        @Schema(description = "작업 지시서 목록")
        private List<Res> items;
        @Schema(description = "다음 페이지 존재 여부", example = "true")
        private boolean hasNext;
        @Schema(description = "다음 커서 마감일", example = "2026-05-30")
        private LocalDate nextCursorDueDate;
        @Schema(description = "다음 커서 ID", example = "2")
        private Long nextCursorId;
        @Schema(description = "페이지 크기", example = "10")
        private Integer size;
    }

    // [WORKORDER_008] 중장비 입출차/기상관제/ESG 연동용 장비 조회 응답 DTO
    // feat : 작업지시서의 장비, 게이트, 작업구역, 상세내역을 한 번에 전달
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @Schema(description = "게이트 장비 조회 응답 DTO")
    public static class GateEquipmentRes {
        @Schema(description = "장비 ID", example = "1")
        private Long idx;
        @Schema(description = "작업 지시서 ID", example = "10")
        private Long workOrderIdx;
        @Schema(description = "작업 지시서 참조명", example = "WO-20260527-001")
        private String workOrderRef;
        @Schema(description = "지시서 제목", example = "철근 배근 작업")
        private String title;
        @Schema(description = "공종 유형", example = "CARPENTRY")
        private String tradeType;
        @Schema(description = "작업 내용", example = "철근 결속 및 배근")
        private String workDetail;
        @Schema(description = "작업 일자", example = "2026-05-27")
        private LocalDate workDate;
        @Schema(description = "작업 장소", example = "A동 3층")
        private String workLocation;
        @Schema(description = "게이트 ID", example = "5")
        private Integer gateIdx;
        @Schema(description = "장비명", example = "크레인 10톤")
        private String equipmentName;
        @Schema(description = "장비 유형", example = "HEAVY_EQUIPMENT")
        private String equipmentType;
        @Schema(description = "장비 수량", example = "2")
        private Integer equipmentCount;
        @Schema(description = "상태 라벨", example = "진행중")
        private String statusLabel;
    }
}
