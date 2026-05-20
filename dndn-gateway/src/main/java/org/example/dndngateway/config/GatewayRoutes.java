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
                .route("document-management-api", route -> route
                        .path("/api/msa/document-management/**")
                        .filters(filter -> filter.rewritePath("/api/msa/(?<path>.*)", "/${path}"))
                        .uri(properties.documentManagementUri()))
                .build();
    }
}
