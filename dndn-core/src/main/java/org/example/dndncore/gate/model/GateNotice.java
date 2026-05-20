package org.example.dndncore.gate.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GateNotice {
    HUMAN_WASH_MODE("모든 설비 OFF: 현재 인력 세척 모드로 동작 중입니다. (인원 2명당 트럭 3대 수용)"),
    INEFFICIENT("진입 차량 대비 세척 기계가 과하게 가동 중입니다. 기계 1대를 OFF하여 전력을 절감하세요."),
    CRITICAL_GUIDE("혼잡도가 '높음'일 경우 세척 기계 2대를 모두 가동하고 배치 인원을 6명 이상으로 유지하는 것을 권장합니다."),
    OPTIMAL("현재 최적의 상태로 운영 중입니다.");

    private final String message;
}
