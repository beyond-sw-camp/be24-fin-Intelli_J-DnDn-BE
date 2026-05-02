package org.example.dndn.worker.fixture;

import lombok.*;
import org.example.dndn.worker.model.entity.Worker;
import org.example.dndn.worker.model.enums.AffiliationKind;
import org.example.dndn.worker.model.enums.AttendanceStatus;
import org.example.dndn.worker.model.enums.JobRank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 시연용 픽스처 **한 명** 분량. REST Req/Res 가 아님.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerScenarioFixtureRow {

    private String externalCode;
    private String name;
    private String phone;
    private String emergencyPhone;
    private String emergencyRelation;
    private JobRank jobRank;
    private AffiliationKind affiliationKind;
    private String partnerCompany;
    private String subLabel;
    private String site;
    private String siteCode;
    private String bloodType;
    private String profileImageUrl;

    private List<DocumentFixtureRow> documents;
    private List<ZoneHistoryFixtureRow> zoneHistory;
    private List<SanctionFixtureRow> sanctions;
    private List<AccidentFixtureRow> accidents;
    private List<AttendanceFixtureRow> attendanceRecords;

    public Worker toWorkerEntity() {
        return Worker.builder()
                .externalCode(this.externalCode)
                .name(this.name)
                .phone(this.phone)
                .emergencyPhone(this.emergencyPhone)
                .emergencyRelation(this.emergencyRelation)
                .jobRank(this.jobRank)
                .affiliationKind(this.affiliationKind)
                .partnerCompany(this.partnerCompany)
                .subLabel(this.subLabel)
                .site(this.site)
                .bloodType(this.bloodType)
                .profileImageUrl(this.profileImageUrl)
                .build();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DocumentFixtureRow {
        private String title;
        private String fileUrl;
        private String storedFileName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ZoneHistoryFixtureRow {
        private LocalDate assignedAt;
        private String zone;
        private String workType;
        private String partnerCompanySnapshot;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SanctionFixtureRow {
        private LocalDate occurredAt;
        private String type;
        private String reason;
        private String action;
        private boolean active;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AccidentFixtureRow {
        private LocalDate occurredAt;
        private String accidentType;
        private String zone;
        private String resolution;
        private boolean severe;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AttendanceFixtureRow {
        private LocalDate workDate;
        private LocalTime clockIn;
        private LocalTime clockOut;
        private BigDecimal manDays;
        private AttendanceStatus attendanceStatus;
        private String zone;
        private boolean closed;
    }
}