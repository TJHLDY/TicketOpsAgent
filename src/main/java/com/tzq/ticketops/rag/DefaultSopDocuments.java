package com.tzq.ticketops.rag;

import java.util.List;

final class DefaultSopDocuments {

    private static final List<SopDocument> DOCUMENTS = List.of(
            new SopDocument(
                    "SOP-ACCOUNT-LOCKED",
                    "账号锁定处理 SOP",
                    "mock-sop/account-locked.md",
                    "账号锁定 account locked 时会导致 OA 或统一账号无法登录 sign in failure。先查询账号状态，再生成解锁 unlock pending action，等待人工确认。"
            ),
            new SopDocument(
                    "FAQ-LOGIN-FAILED",
                    "登录失败 FAQ",
                    "mock-sop/login-failed.md",
                    "登录失败 login failed 需要区分密码错误 password error、账号锁定、MFA 异常和系统不可用。"
            ),
            new SopDocument(
                    "SOP-MFA-ISSUE",
                    "MFA 异常处理 SOP",
                    "mock-sop/mfa-issue.md",
                    "MFA 多因素验证异常包括 authenticator 或 verification code 失败，需要核验设备、时间同步和备用验证方式。"
            ),
            new SopDocument(
                    "SOP-PERMISSION-REQUEST",
                    "业务系统权限申请 SOP",
                    "mock-sop/permission-request.md",
                    "权限申请 permission request 适用于 access denied、no access 或业务系统无权访问，只生成审批建议，不自动授予真实权限。"
            ),
            new SopDocument(
                    "FAQ-PRIVILEGE-RISK",
                    "越权请求风险 FAQ",
                    "mock-sop/privilege-risk.md",
                    "越权 privilege escalation、管理员权限 admin permission、绕过审批 bypass approval 和批量授权请求应拒绝或转人工安全审核。"
            )
    );

    private DefaultSopDocuments() {
    }

    static List<SopDocument> all() {
        return DOCUMENTS;
    }
}
