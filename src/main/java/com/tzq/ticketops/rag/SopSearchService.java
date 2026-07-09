package com.tzq.ticketops.rag;

import com.tzq.ticketops.agent.SopReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class SopSearchService {

    private final JdbcTemplate jdbcTemplate;

    private final List<SopDocument> documents = List.of(
            new SopDocument(
                    "SOP-ACCOUNT-LOCKED",
                    "账号锁定处理 SOP",
                    "mock-sop/account-locked.md",
                    "员工反馈 OA 或统一账号已锁定时，先查询账号状态，再生成解锁 pending action，等待人工确认。"
            ),
            new SopDocument(
                    "FAQ-LOGIN-FAILED",
                    "登录失败 FAQ",
                    "mock-sop/login-failed.md",
                    "登录失败需要先区分密码错误、账号锁定、MFA 异常和系统不可用。"
            ),
            new SopDocument(
                    "SOP-MFA-ISSUE",
                    "MFA 异常处理 SOP",
                    "mock-sop/mfa-issue.md",
                    "MFA 异常需要核验设备、时间同步和备用验证方式。"
            ),
            new SopDocument(
                    "SOP-PERMISSION-REQUEST",
                    "业务系统权限申请 SOP",
                    "mock-sop/permission-request.md",
                    "权限申请只生成审批建议，不自动授予真实权限。"
            ),
            new SopDocument(
                    "FAQ-PRIVILEGE-RISK",
                    "越权请求风险 FAQ",
                    "mock-sop/privilege-risk.md",
                    "管理员权限、绕过审批和批量授权请求应拒绝或转人工安全审核。"
            )
    );

    public SopSearchService() {
        this.jdbcTemplate = null;
    }

    @Autowired
    public SopSearchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SopReference findBest(String text) {
        List<SopDocument> availableDocuments = loadDocuments();
        SopDocument document = availableDocuments.stream()
                .filter(item -> matches(text, item))
                .findFirst()
                .orElse(availableDocuments.get(0));
        return new SopReference(document.id(), document.title(), document.source(), 0.92);
    }

    private boolean matches(String text, SopDocument document) {
        String lowerText = text.toLowerCase(Locale.ROOT);
        if ((text.contains("锁定") || lowerText.contains("locked"))
                && document.id().equals("SOP-ACCOUNT-LOCKED")) {
            return true;
        }
        if (containsAny(text, "MFA", "验证码", "多因素")
                || containsAny(lowerText, "mfa", "multi-factor", "multi factor", "verification code", "authenticator")) {
            return document.id().equals("SOP-MFA-ISSUE");
        }
        return (containsAny(text, "权限", "无权访问")
                || containsAny(lowerText, "permission", "access denied", "no access", "not authorized",
                "request access", "cannot access", "can't access"))
                && document.id().equals("SOP-PERMISSION-REQUEST");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private List<SopDocument> loadDocuments() {
        if (jdbcTemplate == null) {
            return documents;
        }
        List<SopDocument> rows = jdbcTemplate.query(
                """
                        select id, title, source, content
                        from sop_document
                        order by id
                        """,
                (rs, rowNum) -> new SopDocument(
                        rs.getString("id"),
                        rs.getString("title"),
                        rs.getString("source"),
                        rs.getString("content")
                )
        );
        if (rows.isEmpty()) {
            return documents;
        }
        return rows;
    }
}
