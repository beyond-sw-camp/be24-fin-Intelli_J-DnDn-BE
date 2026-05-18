package org.example.dndncore.worker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

@Data
@Component
@ConfigurationProperties(prefix = "management.attendance")
public class ManagementAttendanceProperties {

    /** 규정 출근 시각 — 지각 데드라인은 {@code officialStart.plusMinutes(lateGraceMinutes)} */
    private LocalTime officialStart = LocalTime.of(8, 0);

    /** 규정 퇴근 시각 — 이보다 일찍 퇴근 인식 시 {@code EARLY_LEAVE}, 이 시각 이후 인식 시 {@code LEAVE}. */
    private LocalTime officialEnd = LocalTime.of(17, 0);

    /** 출근 유예(분). 0 이면 정각 초과 시 지각. */
    private int lateGraceMinutes = 10;
}
