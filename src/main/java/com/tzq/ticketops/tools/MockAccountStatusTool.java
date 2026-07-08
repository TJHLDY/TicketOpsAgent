package com.tzq.ticketops.tools;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class MockAccountStatusTool {

    private final Map<String, AccountStatus> accounts = Map.of(
            "mock-user-001", AccountStatus.LOCKED,
            "mock-user-002", AccountStatus.ACTIVE,
            "mock-user-003", AccountStatus.MFA_REQUIRED,
            "mock-user-004", AccountStatus.ACTIVE,
            "mock-user-005", AccountStatus.UNKNOWN
    );
    private final JdbcTemplate jdbcTemplate;

    public MockAccountStatusTool() {
        this.jdbcTemplate = null;
    }

    @Autowired
    public MockAccountStatusTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AccountStatusResult getAccountStatus(String userId) {
        if (usesJdbc()) {
            return new AccountStatusResult(userId, findStatusFromDatabase(userId).orElse(AccountStatus.UNKNOWN));
        }
        return new AccountStatusResult(userId, accounts.getOrDefault(userId, AccountStatus.UNKNOWN));
    }

    private Optional<AccountStatus> findStatusFromDatabase(String userId) {
        return jdbcTemplate.query(
                "select account_status from mock_user_account where user_id = ?",
                (rs, rowNum) -> AccountStatus.valueOf(rs.getString("account_status")),
                userId
        ).stream().findFirst();
    }

    private boolean usesJdbc() {
        return jdbcTemplate != null;
    }
}
