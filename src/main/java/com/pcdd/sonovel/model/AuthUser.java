package com.pcdd.sonovel.model;

import lombok.Builder;
import lombok.Data;

/**
 * 认证用户模型
 *
 * @author pcdd
 */
@Data
@Builder
public class AuthUser {

    private Integer id;
    private String username;
    private String passwordHash;
    private String salt;
    private String role;       // "admin" or "user"
    private Integer banned;    // 0=正常, 1=封禁
    private Long createdAt;
    private Long updatedAt;

    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(role);
    }

    public boolean isBanned() {
        return banned != null && banned == 1;
    }

}
