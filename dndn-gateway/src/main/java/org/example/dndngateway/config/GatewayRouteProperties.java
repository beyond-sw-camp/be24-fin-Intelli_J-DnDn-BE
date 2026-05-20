package org.example.dndngateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.routes")
public record GatewayRouteProperties(
        String documentManagementUri
) {
    public GatewayRouteProperties {
        documentManagementUri = documentManagementUri == null || documentManagementUri.isBlank()
                ? "lb://DNDN-DOCUMENT-MANAGEMENT"
                : documentManagementUri;
    }
}
