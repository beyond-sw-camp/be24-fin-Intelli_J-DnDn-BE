package org.example.dndncore.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 설정은 SecurityConfig.corsConfigurationSource() 에서 일원화하여 관리한다.
 * Spring Security 필터 체인이 WebMvc CORS 설정보다 먼저 적용되므로
 * 여기서 addCorsMappings 를 중복 선언하지 않는다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
}
