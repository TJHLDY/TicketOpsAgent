package com.tzq.ticketops.tools;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class MockUserPermissionsTool {

    private final Map<String, List<String>> permissions = Map.of(
            key("mock-user-001", "OA"), List.of("OA_USER"),
            key("mock-user-002", "OA"), List.of("OA_APPROVER", "OA_USER"),
            key("mock-user-002", "CRM"), List.of("CRM_VIEW"),
            key("mock-user-003", "VPN"), List.of("VPN_USER"),
            key("mock-user-004", "ERP"), List.of("ERP_APPROVER", "ERP_READ")
    );
    private final JdbcTemplate jdbcTemplate;

    public MockUserPermissionsTool() {
        this.jdbcTemplate = null;
    }

    @Autowired
    public MockUserPermissionsTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UserPermissionsResult getUserPermissions(String userId, String appCode) {
        if (usesJdbc()) {
            return new UserPermissionsResult(userId, appCode, findPermissionsFromDatabase(userId, appCode));
        }
        return new UserPermissionsResult(userId, appCode, permissions.getOrDefault(key(userId, appCode), List.of()));
    }

    private List<String> findPermissionsFromDatabase(String userId, String appCode) {
        return jdbcTemplate.query(
                """
                        select permission_code
                        from mock_user_permission
                        where user_id = ? and app_code = ?
                        order by permission_code
                        """,
                (rs, rowNum) -> rs.getString("permission_code"),
                userId,
                appCode
        );
    }

    private boolean usesJdbc() {
        return jdbcTemplate != null;
    }

    private static String key(String userId, String appCode) {
        return userId + "|" + appCode;
    }
}
