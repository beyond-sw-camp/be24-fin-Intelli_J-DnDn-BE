package org.example.dndncore.auth.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.auth.model.enums.RequestStatus;
import org.example.dndncore.auth.model.enums.UserRole;
import org.example.dndncore.common.model.BaseEntity;

@Schema(description = "계정 생성 요청 엔티티")
@Entity
@Table(name = "account_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class AccountRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "요청 ID", example = "1")
    private Long idx;

    /** 요청을 보낸 계정. 본사/현장 총 책임자/공종 책임자가 요청. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_idx", nullable = false)
    @Schema(description = "요청자")
    private SystemUser requester;

    @Column(nullable = false, length = 50)
    @Schema(description = "요청 이름", example = "홍길동")
    private String requestedName;

    @Column(nullable = false, length = 50)
    @Schema(description = "요청 로그인 ID", example = "worker01")
    private String requestedLoginId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    @Schema(description = "요청 권한")
    private UserRole requestedRole;

    @Column(length = 50)
    @Schema(description = "현장 코드", example = "SITE-01")
    private String siteCode;

    @Column(length = 80)
    @Schema(description = "공종", example = "토목")
    private String trade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Schema(description = "요청 상태")
    private RequestStatus status;

    /** 거절 시 사유. */
    @Column(length = 300)
    @Schema(description = "메모", example = "요청 정보가 누락되었습니다.")
    private String note;

    public void approve() {
        this.status = RequestStatus.APPROVED;
    }

    public void reject(String note) {
        this.status = RequestStatus.REJECTED;
        this.note = note;
    }
}
