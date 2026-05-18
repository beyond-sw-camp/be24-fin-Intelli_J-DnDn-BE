package org.example.dndncore.auth.model.enums;

/**
 * 로그인 화면에서 선택한 진입 모드.
 * <ul>
 *   <li>{@link #SITE} — 현장 로그인 탭. 현장 권한 계정만 허용.</li>
 *   <li>{@link #ADMIN} — 시스템 관리자 / 본사 로그인 탭. ADMIN / HEADQUARTOR 만 허용.</li>
 * </ul>
 */
public enum LoginMode {
    SITE,
    ADMIN
}
