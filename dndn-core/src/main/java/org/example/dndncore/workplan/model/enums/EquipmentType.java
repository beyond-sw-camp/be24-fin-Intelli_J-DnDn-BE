package org.example.dndncore.workplan.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
@Schema(description = "feat : 작업 장비 종류")
public enum EquipmentType {

    // 굴착·토공
    EXCAVATOR("굴삭기"),
    MINI_EXCAVATOR("미니굴삭기"),
    BACKHOE_LOADER("백호"),
    DRAGLINE_EXCAVATOR("드래그라인"),

    // 운반
    DUMP_TRUCK("덤프트럭"),
    CONCRETE_MIXER_TRUCK("트럭 믹서"),
    TRACTOR("트랙터"),
    TRAILER("트레일러"),
    SCRAPER("스크레이퍼"),

    // 하역·양중
    TOWER_CRANE("타워크레인"),
    MOBILE_CRANE("모바일 크레인"),
    CRAWLER_CRANE("크롤러 크레인"),
    FORKLIFT("지게차"),
    CONSTRUCTION_HOIST("리프트"),

    // 정지·다짐
    BULLDOZER("불도저"),
    MOTOR_GRADER("모터 그레이더"),
    ROAD_ROLLER("롤러"),
    PLATE_COMPACTOR("콤팩터"),

    // 도로·포장
    ASPHALT_PAVER("아스팔트 피니셔"),
    MILLING_MACHINE("밀링 머신"),
    WATER_TRUCK("살수차"),
    CONCRETE_CUTTER("노면 절단기"),

    // 기초·파일
    PILE_DRIVER("파일 드라이버"),
    BORING_MACHINE("보링머신"),
    EARTH_AUGER("어스오거"),
    REVERSE_CIRCULATION_DRILL("RCD"),

    // 콘크리트
    CONCRETE_PUMP_TRUCK("콘크리트 펌프카"),
    BATCHING_PLANT("배치 플랜트"),
    CONCRETE_VIBRATOR("바이브레이터"),

    // 철거·특수
    HYDRAULIC_BREAKER("브레이커"),
    NIBBLER("니블러"),
    CRUSHER("크러셔"),
    AERIAL_WORK_PLATFORM("고소작업차"),
    TUNNEL_BORING_MACHINE("TBM"),

    // 기타
    OTHER("기타");

    @Schema(description = "장비 레이블", example = "타워크레인")
    private final String label;

    public static EquipmentType fromLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }

        return Arrays.stream(values())
                .filter(t -> t.label.equals(label))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 장비: " + label));
    }
}