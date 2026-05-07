package org.example.dndn.worker.fixture;

import lombok.*;
import org.example.dndn.worker.model.entity.Worker;
import org.example.dndn.worker.model.enums.AffiliationKind;
import org.example.dndn.worker.model.enums.AttendanceStatus;
import org.example.dndn.worker.model.enums.EmploymentKind;
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
    private String partnerCompanyDetail;
    private String subLabel;
    private String site;
    private String siteCode;
    private String bloodType;
    private String profileImageUrl;
    /** 최초등록일 — 본인 마스터 신규 생성 시 채워지며, updateFromSync 에서는 덮어쓰지 않는다. */
    private LocalDate registeredAt;

    /** 인사·픽스처 루트에서 받는 상용(REGULAR)·일용(DAILY). 없으면 마스터·근태 모두 {@link EmploymentKind#REGULAR} 로 적재 */
    private EmploymentKind employmentKind;

    private List<DocumentFixtureRow> documents;
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
                .partnerCompanyDetail(this.partnerCompanyDetail)
                .subLabel(this.subLabel)
                .employmentKind(this.employmentKind != null ? this.employmentKind : EmploymentKind.REGULAR)
                .site(this.site)
                .siteCode(this.siteCode)
                .bloodType(this.bloodType)
                .profileImageUrl(this.profileImageUrl)
                .registeredAt(this.registeredAt)
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
        private String zoneMain;
        private String zoneSub;
        private String resolution;
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
        private String zoneMain;
        private String zoneSub;
        private String assignedTrade;
        private EmploymentKind employmentKind;
    }
}