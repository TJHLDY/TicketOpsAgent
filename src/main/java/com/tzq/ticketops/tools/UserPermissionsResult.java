package com.tzq.ticketops.tools;

import java.util.List;

public record UserPermissionsResult(
        String userId,
        String appCode,
        List<String> permissionCodes
) {
}
