package org.example.dndngateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.routes")
public record GatewayRouteProperties(
        String coreUri,
        String documentManagementUri
) {
    public GatewayRouteProperties {
        coreUri = coreUri == null || coreUri.isBlank() ? "http://localhost:8080" : coreUri;
        documentManagementUri = documentManagementUri == null || documentManagementUri.isBlank()
                ? "http://localhost:8082"
                : documentManagementUri;
    }
}
