package org.example.dndn.auth.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.auth.model.enums.UserRole;
import org.example.dndn.common.model.BaseEntity;

@Entity
@Table(name = "system_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class SystemUser extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @Column(unique = true, nullable = false, length = 50)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private UserRole role;

    /** SITE_DIRECTOR 이상 공종별 계정에 해당하는 현장 코드. ADMIN/HEADQUARTOR 는 null. */
    @Column(length = 50)
    private String siteCode;

    /** SECTION_LEADER / SECTION_SUPERVISOR 의 공종명 (예: 토목, 골조). 그 외 null. */
    @Column(length = 80)
    private String trade;

    @Column(nullable = false)
    private boolean active;

    public void update(String name, UserRole role, String siteCode, String trade, boolean active) {
        this.name = name;
        this.role = role;
        this.siteCode = siteCode;
        this.trade = trade;
        this.active = active;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void deactivate() {
        this.active = false;
    }
}
