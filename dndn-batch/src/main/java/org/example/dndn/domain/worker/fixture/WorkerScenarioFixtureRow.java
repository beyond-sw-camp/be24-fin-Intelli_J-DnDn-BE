package org.example.dndn.domain.worker.fixture;

import lombok.*;
import org.example.dndn.domain.worker.model.entity.Worker;
import org.example.dndn.domain.worker.model.enums.AffiliationKind;
import org.example.dndn.domain.worker.model.enums.AttendanceStatus;
import org.example.dndn.domain.worker.model.enums.EmploymentKind;
import org.example.dndn.domain.worker.model.enums.JobRank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

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
    private String trade;
    private String site;
    private String siteCode;
    private String bloodType;
    private String profileImageUrl;
    private LocalDate registeredAt;
    private EmploymentKind employmentKind;

    private List<DocumentFixtureRow> documents;
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
                .trade(this.trade)
                .employmentKind(this.employmentKind != null ? this.employmentKind : EmploymentKind.REGULAR)
                .site(this.site)
                .siteCode(this.siteCode)
                .bloodType(this.bloodType)
                .profileImageUrl(this.profileImageUrl)
                .registeredAt(this.registeredAt)
                .build();
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DocumentFixtureRow {
        private String title;
        private String fileUrl;
        private String storedFileName;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AccidentFixtureRow {
        private LocalDate occurredAt;
        private String accidentType;
        private String zoneMain;
        private String zoneSub;
        private String resolution;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AttendanceFixtureRow {
        private LocalDate workDate;
        private LocalTime clockIn;
        private LocalTime clockOut;
        private BigDecimal manDays;
        private AttendanceStatus attendanceStatus;
        private EmploymentKind employmentKind;
    }
}
