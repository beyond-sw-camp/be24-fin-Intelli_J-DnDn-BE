package org.example.dndngateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutes {

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder routes, GatewayRouteProperties properties) {
        return routes.routes()
                .route("document-management-health", route -> route
                        .path("/api/document-management/health")
                        .filters(filter -> filter.rewritePath("/api/(?<path>.*)", "/${path}"))
                        .uri(properties.documentManagementUri()))
                .route("document-management-uploaded", route -> route
                        .path("/api/document-management/*/uploaded")
                        .filters(filter -> filter.rewritePath("/api/(?<path>.*)", "/${path}"))
                        .uri(properties.documentManagementUri()))
                .route("core-api", route -> route
                        .path("/api/**")
                        .filters(filter -> filter.rewritePath("/api/(?<path>.*)", "/${path}"))
                        .uri(properties.coreUri()))
                .build();
    }
}
