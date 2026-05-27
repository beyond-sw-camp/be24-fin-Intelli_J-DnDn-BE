package org.example.dndndocumentmanagement.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization");

        SecurityRequirement securityRequirement = new SecurityRequirement().addList("bearerAuth");

        return new OpenAPI()
                .info(new Info()
                        .title("DnDn Document Management API")
                        .description("문서 관리 마이크로서비스 API 명세서")
                        .version("v1.0"))
                // Gateway를 통해 /api/msa/document-management/** 로 접근하므로
                // server URL을 /api/msa 로 설정
                .servers(List.of(
                        new Server().url("https://www.dndn24.kro.kr/api/msa").description("Production"),
                        new Server().url("http://localhost:8082").description("Local")
                ))
                .components(new Components().addSecuritySchemes("bearerAuth", securityScheme))
                .addSecurityItem(securityRequirement);
    }
}
