package com.tzq.ticketops.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MockAccountStatusToolPersistenceTest {

    @Autowired
    MockAccountStatusTool accountStatusTool;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetMockAccounts() {
        jdbcTemplate.update("delete from mock_user_account");
    }

    @Test
    void readsAccountStatusFromMockUserAccountTable() {
        jdbcTemplate.update(
                "insert into mock_user_account(user_id, account_status, display_name) values (?, ?, ?)",
                "db-only-user",
                "LOCKED",
                "Database Only User"
        );

        AccountStatusResult result = accountStatusTool.getAccountStatus("db-only-user");

        assertThat(result.userId()).isEqualTo("db-only-user");
        assertThat(result.status()).isEqualTo(AccountStatus.LOCKED);
    }

    @Test
    void returnsUnknownWhenAccountRowDoesNotExist() {
        AccountStatusResult result = accountStatusTool.getAccountStatus("missing-user");

        assertThat(result.status()).isEqualTo(AccountStatus.UNKNOWN);
    }
}
