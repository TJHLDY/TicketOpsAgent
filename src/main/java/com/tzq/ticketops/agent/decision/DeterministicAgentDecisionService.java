package com.tzq.ticketops.agent.decision;

import com.tzq.ticketops.agent.PendingActionType;
import com.tzq.ticketops.agent.RiskLevel;
import com.tzq.ticketops.agent.TicketCategory;
import com.tzq.ticketops.agent.TicketPriority;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DeterministicAgentDecisionService implements AgentDecisionPort {

    @Override
    public AgentDecision decide(AgentContext context) {
        String text = context.text();
        TicketCategory category = classify(text);
        TicketPriority priority = priorityFor(category);
        RiskLevel riskLevel = riskFor(category, text);
        return new AgentDecision(
                category,
                priority,
                riskLevel,
                sopQueryFor(category),
                toolIntentsFor(category, riskLevel, context),
                pendingActionFor(category, riskLevel),
                "",
                "",
                1.0,
                List.of("deterministic rules")
        );
    }

    private TicketCategory classify(String text) {
        String lowerText = text.toLowerCase(Locale.ROOT);
        if (containsAny(text, "锁定", "账号已锁")
                || containsAny(lowerText, "account locked", "account is locked", "locked")) {
            return TicketCategory.ACCOUNT_LOCKED;
        }
        if (containsAny(text, "MFA", "验证码", "多因素")
                || containsAny(lowerText, "mfa", "multi-factor", "multi factor", "verification code", "authenticator")) {
            return TicketCategory.MFA_ISSUE;
        }
        if (containsAny(text, "权限", "无权访问")
                || containsAny(lowerText, "permission", "access denied", "no access", "not authorized",
                "request access", "cannot access", "can't access")) {
            return TicketCategory.PERMISSION_REQUEST;
        }
        if (containsAny(text, "登录", "登陆")
                || containsAny(lowerText, "login", "log in", "sign in", "sign-in")) {
            return TicketCategory.LOGIN_FAILED;
        }
        return TicketCategory.UNKNOWN;
    }

    private TicketPriority priorityFor(TicketCategory category) {
        return switch (category) {
            case ACCOUNT_LOCKED, MFA_ISSUE -> TicketPriority.P2;
            case LOGIN_FAILED, PERMISSION_REQUEST -> TicketPriority.P3;
            case UNKNOWN -> TicketPriority.P3;
        };
    }

    private RiskLevel riskFor(TicketCategory category, String text) {
        if (containsUnsafePrivilegeRequest(text)) {
            return RiskLevel.REJECT;
        }
        return switch (category) {
            case ACCOUNT_LOCKED, MFA_ISSUE, PERMISSION_REQUEST -> RiskLevel.NEEDS_APPROVAL;
            case LOGIN_FAILED -> RiskLevel.READ_ONLY;
            case UNKNOWN -> RiskLevel.REJECT;
        };
    }

    private boolean containsUnsafePrivilegeRequest(String text) {
        String lowerText = text.toLowerCase(Locale.ROOT);
        return text.contains("绕过审批")
                || text.contains("管理员权限")
                || text.contains("生产系统管理员")
                || text.contains("越权")
                || containsAny(lowerText, "bypass approval", "without approval", "administrator permission",
                "admin permission", "production admin", "privilege escalation", "ignore previous");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String sopQueryFor(TicketCategory category) {
        return switch (category) {
            case ACCOUNT_LOCKED -> "账号锁定处理";
            case PERMISSION_REQUEST -> "权限申请处理";
            case MFA_ISSUE -> "MFA 异常处理";
            case LOGIN_FAILED -> "登录失败处理";
            case UNKNOWN -> "";
        };
    }

    private List<ToolIntent> toolIntentsFor(TicketCategory category, RiskLevel riskLevel, AgentContext context) {
        if (riskLevel == RiskLevel.REJECT) {
            return List.of();
        }
        if (category == TicketCategory.ACCOUNT_LOCKED) {
            return List.of(new ToolIntent("getAccountStatus", Map.of("userId", context.requesterId())));
        }
        if (category == TicketCategory.PERMISSION_REQUEST) {
            return List.of(new ToolIntent(
                    "getUserPermissions",
                    Map.of("userId", context.requesterId(), "appCode", extractAppCode(context.text()))
            ));
        }
        return List.of();
    }

    private PendingActionProposal pendingActionFor(TicketCategory category, RiskLevel riskLevel) {
        if (riskLevel == RiskLevel.REJECT) {
            return null;
        }
        return switch (category) {
            case ACCOUNT_LOCKED -> new PendingActionProposal(PendingActionType.UNLOCK_ACCOUNT, true);
            case PERMISSION_REQUEST -> new PendingActionProposal(PendingActionType.GRANT_PERMISSION, true);
            default -> null;
        };
    }

    private String extractAppCode(String text) {
        if (text.contains("CRM")) {
            return "CRM";
        }
        if (text.contains("ERP")) {
            return "ERP";
        }
        if (text.contains("VPN")) {
            return "VPN";
        }
        return "OA";
    }
}
