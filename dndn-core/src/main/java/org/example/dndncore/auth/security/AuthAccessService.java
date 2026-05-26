package org.example.dndncore.auth.security;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.auth.model.entity.SystemUser;
import org.example.dndncore.auth.model.enums.UserRole;
import org.example.dndncore.auth.repository.SystemUserRepository;
import org.example.dndncore.project.model.entity.Project;
import org.example.dndncore.project.model.entity.TradeProcess;
import org.example.dndncore.project.repository.ProjectRepository;
import org.example.dndncore.workplan.model.entity.WorkPlan;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AuthAccessService {

    private static final Pattern SITE_CODE_PATTERN = Pattern.compile("^\\s*\\[([^\\]]+)]");

    private final SystemUserRepository userRepository;
    private final ProjectRepository projectRepository;

    public Optional<SystemUser> currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long userIdx)) {
            return Optional.empty();
        }
        SystemUser user = userRepository.findById(userIdx)
                .filter(SystemUser::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid or inactive user."));
        return Optional.of(user);
    }

    public boolean isTradeScoped(SystemUser user) {
        if (user == null) return false;
        return user.getRole() == UserRole.SECTION_LEADER
                || user.getRole() == UserRole.SECTION_SUPERVISOR;
    }

    public boolean isSiteScoped(SystemUser user) {
        if (user == null) return false;
        return user.getRole() == UserRole.SITE_MANAGER
                || user.getRole() == UserRole.SITE_DIRECTOR
                || user.getRole() == UserRole.SECTION_LEADER
                || user.getRole() == UserRole.SECTION_SUPERVISOR;
    }

    public Optional<List<Long>> accessibleProjectIds(SystemUser user) {
        if (user == null || !isSiteScoped(user)) {
            return Optional.empty();
        }

        String assignedSiteCode = clean(user.getSiteCode());
        if (assignedSiteCode.isBlank()) {
            return Optional.empty();
        }

        String projectNamePrefix = "[" + assignedSiteCode + "]";
        return Optional.of(projectRepository.findByNamePrefixIgnoreCase(projectNamePrefix).stream()
                .map(Project::getIdx)
                .toList());
    }

    public String effectiveTrade(String requestedTrade) {
        return currentUser()
                .filter(this::isTradeScoped)
                .map(SystemUser::getTrade)
                .filter(trade -> trade != null && !trade.isBlank())
                .orElse(requestedTrade);
    }

    public void assertProjectAccess(Long projectId) {
        currentUser().ifPresent(user -> {
            if (!canAccessProjectId(user, projectId)) {
                throwForbidden();
            }
        });
    }

    public boolean canAccessProjectId(Long projectId) {
        return currentUser()
                .map(user -> canAccessProjectId(user, projectId))
                .orElse(true);
    }

    public boolean canAccessTradeName(String tradeName) {
        return currentUser()
                .map(user -> canAccessTradeName(user, tradeName))
                .orElse(true);
    }

    public void assertTradeAccess(String tradeName) {
        currentUser().ifPresent(user -> {
            if (!canAccessTradeName(user, tradeName)) {
                throwForbidden();
            }
        });
    }

    public void assertWorkPlanAccess(WorkPlan plan) {
        currentUser().ifPresent(user -> {
            if (!canAccessWorkPlan(user, plan)) {
                throwForbidden();
            }
        });
    }

    public boolean canAccessWorkPlan(WorkPlan plan) {
        return currentUser()
                .map(user -> canAccessWorkPlan(user, plan))
                .orElse(true);
    }

    public boolean canAccessTradeProcess(TradeProcess tradeProcess) {
        return currentUser()
                .map(user -> canAccessTradeProcess(user, tradeProcess))
                .orElse(true);
    }

    public void assertTradeProcessAccess(TradeProcess tradeProcess) {
        currentUser().ifPresent(user -> {
            if (!canAccessTradeProcess(user, tradeProcess)) {
                throwForbidden();
            }
        });
    }

    public String workPlanTradeName(WorkPlan plan) {
        if (plan == null) return "";
        if (plan.getTradeProcess() != null && plan.getTradeProcess().getTradeName() != null) {
            return plan.getTradeProcess().getTradeName();
        }
        if (plan.getParentWorkPlan() != null
                && plan.getParentWorkPlan().getTradeProcess() != null
                && plan.getParentWorkPlan().getTradeProcess().getTradeName() != null) {
            return plan.getParentWorkPlan().getTradeProcess().getTradeName();
        }
        return plan.getTrade() != null ? plan.getTrade().getLabel() : "";
    }

    public boolean tradeMatches(String recordTrade, String assignedTrade) {
        String left = clean(recordTrade);
        String right = clean(assignedTrade);
        if (right.isBlank()) return true;
        if (left.isBlank()) return false;
        return left.equals(right) || left.contains(right) || right.contains(left);
    }

    private boolean canAccessWorkPlan(SystemUser user, WorkPlan plan) {
        if (plan == null) return false;
        TradeProcess tradeProcess = plan.getTradeProcess();
        if (tradeProcess != null && !canAccessTradeProcess(user, tradeProcess)) {
            return false;
        }
        WorkPlan parentWorkPlan = plan.getParentWorkPlan();
        if (tradeProcess == null
                && parentWorkPlan != null
                && parentWorkPlan.getTradeProcess() != null
                && !canAccessTradeProcess(user, parentWorkPlan.getTradeProcess())) {
            return false;
        }
        return canAccessTradeName(user, workPlanTradeName(plan));
    }

    private boolean canAccessTradeProcess(SystemUser user, TradeProcess tradeProcess) {
        if (tradeProcess == null) return true;
        Long projectId = tradeProcess.getMasterSchedule() != null
                && tradeProcess.getMasterSchedule().getProject() != null
                ? tradeProcess.getMasterSchedule().getProject().getIdx()
                : null;
        return canAccessProjectId(user, projectId)
                && canAccessTradeName(user, tradeProcess.getTradeName());
    }

    private boolean canAccessProjectId(SystemUser user, Long projectId) {
        if (user == null || projectId == null) return true;
        if (!isSiteScoped(user)) return true;

        String assignedSiteCode = clean(user.getSiteCode());
        if (assignedSiteCode.isBlank()) return true;

        return projectRepository.findById(projectId)
                .map(project -> assignedSiteCode.equalsIgnoreCase(projectSiteCode(project)))
                .orElse(false);
    }

    private boolean canAccessTradeName(SystemUser user, String tradeName) {
        if (!isTradeScoped(user)) return true;
        return tradeMatches(tradeName, user.getTrade());
    }

    private String projectSiteCode(Project project) {
        if (project == null || project.getName() == null) return "";
        Matcher matcher = SITE_CODE_PATTERN.matcher(project.getName());
        return matcher.find() ? clean(matcher.group(1)) : "";
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private void throwForbidden() {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No permission for this site or trade.");
    }
}
