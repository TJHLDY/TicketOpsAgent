package com.tzq.ticketops.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MockUserPermissionsToolPersistenceTest {

    @Autowired
    MockUserPermissionsTool permissionsTool;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetPermissions() {
        jdbcTemplate.update("delete from mock_user_permission");
    }

    @Test
    void readsUserPermissionsFromDatabase() {
        jdbcTemplate.update(
                "insert into mock_user_permission(user_id, app_code, permission_code) values (?, ?, ?)",
                "db-only-user",
                "OA",
                "OA_USER"
        );
        jdbcTemplate.update(
                "insert into mock_user_permission(user_id, app_code, permission_code) values (?, ?, ?)",
                "db-only-user",
                "OA",
                "OA_APPROVER"
        );

        UserPermissionsResult result = permissionsTool.getUserPermissions("db-only-user", "OA");

        assertThat(result.userId()).isEqualTo("db-only-user");
        assertThat(result.appCode()).isEqualTo("OA");
        assertThat(result.permissionCodes()).containsExactly("OA_APPROVER", "OA_USER");
    }

    @Test
    void returnsEmptyListWhenUserHasNoPermissionForApp() {
        UserPermissionsResult result = permissionsTool.getUserPermissions("missing-user", "OA");

        assertThat(result.userId()).isEqualTo("missing-user");
        assertThat(result.appCode()).isEqualTo("OA");
        assertThat(result.permissionCodes()).isEmpty();
    }
}
