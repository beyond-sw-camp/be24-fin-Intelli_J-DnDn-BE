package org.example.dndncore.auth.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.auth.model.enums.UserRole;
import org.example.dndncore.common.model.BaseEntity;

@Schema(description = "시스템 사용자 엔티티")
@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class SystemUser extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "사용자 ID", example = "1")
    private Long idx;

    @Column(unique = true, nullable = false, length = 50)
    @Schema(description = "로그인 ID", example = "admin")
    private String loginId;

    @Column(nullable = false)
    @Schema(description = "비밀번호")
    private String password;

    @Column(nullable = false, length = 50)
    @Schema(description = "이름", example = "홍길동")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    @Schema(description = "권한")
    private UserRole role;

    /** SITE_DIRECTOR 이상 공종별 계정에 해당하는 현장 코드. ADMIN/HEADQUARTOR 는 null. */
    @Column(length = 50)
    @Schema(description = "현장 코드", example = "SITE-01")
    private String siteCode;

    /** SECTION_LEADER / SECTION_SUPERVISOR 의 공종명 (예: 토목, 골조). 그 외 null. */
    @Column(length = 80)
    @Schema(description = "공종", example = "토목")
    private String trade;

    @Column(length = 20)
    @Schema(description = "전화번호", example = "010-1234-5678")
    private String phone;

    @Column(length = 100)
    @Schema(description = "이메일", example = "user@example.com")
    private String email;

    @Column(nullable = false)
    @Schema(description = "활성화 여부", example = "true")
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
