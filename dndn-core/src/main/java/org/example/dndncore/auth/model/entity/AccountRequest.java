package org.example.dndncore.auth.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.auth.model.enums.RequestStatus;
import org.example.dndncore.auth.model.enums.UserRole;
import org.example.dndncore.common.model.BaseEntity;

@Entity
@Table(name = "account_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class AccountRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    /** 요청을 보낸 계정. 본사/현장 총 책임자/공종 책임자가 요청. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_idx", nullable = false)
    private SystemUser requester;

    @Column(nullable = false, length = 50)
    private String requestedName;

    @Column(nullable = false, length = 50)
    private String requestedLoginId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private UserRole requestedRole;

    @Column(length = 50)
    private String siteCode;

    @Column(length = 80)
    private String trade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private RequestStatus status;

    /** 거절 시 사유. */
    @Column(length = 300)
    private String note;

    public void approve() {
        this.status = RequestStatus.APPROVED;
    }

    public void reject(String note) {
        this.status = RequestStatus.REJECTED;
        this.note = note;
    }
}
