package com.pcdd.sonovel.db;

import cn.hutool.crypto.digest.DigestUtil;
import com.pcdd.sonovel.model.AuthUser;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 用户 Repository
 *
 * @author pcdd
 */
public class UserRepository {

    private final DatabaseManager db = DatabaseManager.getInstance();

    /**
     * 密码加密 (SHA-256 + salt)
     */
    public static String hashPassword(String password, String salt) {
        return DigestUtil.sha256Hex(password + salt);
    }

    public static String generateSalt() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 创建用户
     */
    public AuthUser create(String username, String password, String role) {
        String salt = generateSalt();
        String hash = hashPassword(password, salt);
        long now = System.currentTimeMillis();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO users (username, password_hash, salt, role, banned, created_at, updated_at) VALUES (?,?,?,?,0,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, hash);
            ps.setString(3, salt);
            ps.setString(4, role);
            ps.setLong(5, now);
            ps.setLong(6, now);
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return AuthUser.builder()
                        .id(rs.getInt(1))
                        .username(username)
                        .passwordHash(hash)
                        .salt(salt)
                        .role(role)
                        .banned(0)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
            }
        } catch (Exception e) {
            throw new RuntimeException("创建用户失败", e);
        }
        return null;
    }

    /**
     * 根据用户名查找用户
     */
    public AuthUser findByUsername(String username) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE username = ?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapUser(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException("查询用户失败", e);
        }
        return null;
    }

    /**
     * 根据 ID 查找用户
     */
    public AuthUser findById(int id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapUser(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException("查询用户失败", e);
        }
        return null;
    }

    /**
     * 验证密码
     */
    public boolean verifyPassword(AuthUser user, String password) {
        return hashPassword(password, user.getSalt()).equals(user.getPasswordHash());
    }

    /**
     * 获取所有用户（管理员用）
     */
    public List<AuthUser> findAll() {
        List<AuthUser> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM users ORDER BY id")) {
            while (rs.next()) {
                list.add(mapUser(rs));
            }
        } catch (Exception e) {
            throw new RuntimeException("查询用户列表失败", e);
        }
        return list;
    }

    /**
     * 封禁/解封用户（管理员用）
     */
    public void setBanned(int userId, int banned) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE users SET banned = ?, updated_at = ? WHERE id = ?")) {
            ps.setInt(1, banned);
            ps.setLong(2, System.currentTimeMillis());
            ps.setInt(3, userId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("更新用户状态失败", e);
        }
    }

    /**
     * 检查是否有管理员
     */
    public boolean hasAdmin() {
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE role = 'admin'")) {
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            throw new RuntimeException("查询管理员失败", e);
        }
        return false;
    }

    private AuthUser mapUser(ResultSet rs) throws SQLException {
        return AuthUser.builder()
                .id(rs.getInt("id"))
                .username(rs.getString("username"))
                .passwordHash(rs.getString("password_hash"))
                .salt(rs.getString("salt"))
                .role(rs.getString("role"))
                .banned(rs.getInt("banned"))
                .lastIp(rs.getString("last_ip"))
                .deletedAt(rs.getLong("deleted_at"))
                .createdAt(rs.getLong("created_at"))
                .updatedAt(rs.getLong("updated_at"))
                .build();
    }

}
