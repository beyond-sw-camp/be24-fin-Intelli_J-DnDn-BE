package org.example.dndncore.redis.cache;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component("redisCacheKeyProvider")
public class RedisCacheKeyProvider {

    public String esgDashboardKey(LocalDate reportDate, Long projectId) {
        LocalDate targetDate = reportDate != null ? reportDate : LocalDate.now();
        String projectKey = projectId != null ? String.valueOf(projectId) : "auto";
        return "user:" + resolveUserKey()
                + ":project:" + projectKey
                + ":date:" + targetDate;
    }

    private String resolveUserKey() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return "anonymous";
        }
        return String.valueOf(userId);
    }
}
