package org.example.dndncore.auth.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.auth.model.enums.UserRole;
import org.example.dndncore.common.model.BaseEntity;

@Entity
@Table(name = "account")
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

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    @Column(nullable = false)
    private boolean active;

    public void update(String name, UserRole role, String siteCode, String trade, boolean active, String phone, String email) {
        this.name = name;
        this.role = role;
        this.siteCode = siteCode;
        this.trade = trade;
        this.active = active;
        this.phone = phone;
        this.email = email;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void deactivate() {
        this.active = false;
    }
}
