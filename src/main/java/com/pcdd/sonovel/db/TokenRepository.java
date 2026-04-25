package com.pcdd.sonovel.db;

import com.pcdd.sonovel.model.ApiToken;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * API Token Repository
 *
 * @author pcdd
 */
public class TokenRepository {

    private final DatabaseManager db = DatabaseManager.getInstance();

    /**
     * 生成唯一 Token
     */
    public String generateToken() {
        return "sonovel_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 创建 Token
     */
    public ApiToken create(int userId, String note) {
        String token = generateToken();
        long now = System.currentTimeMillis();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO api_tokens (user_id, token, note, created_at) VALUES (?,?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, token);
            ps.setString(3, note != null ? note : "");
            ps.setLong(4, now);
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return ApiToken.builder()
                        .id(rs.getInt(1))
                        .userId(userId)
                        .token(token)
                        .note(note)
                        .createdAt(now)
                        .build();
            }
        } catch (Exception e) {
            throw new RuntimeException("创建 Token 失败", e);
        }
        return null;
    }

    /**
     * 根据 Token 字符串查找
     */
    public ApiToken findByToken(String token) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM api_tokens WHERE token = ?")) {
            ps.setString(1, token);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapToken(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException("查询 Token 失败", e);
        }
        return null;
    }

    /**
     * 更新最后使用时间
     */
    public void updateLastUsed(int tokenId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE api_tokens SET last_used_at = ? WHERE id = ?")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setInt(2, tokenId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("更新 Token 使用时间失败", e);
        }
    }

    /**
     * 查询用户的所有 Token
     */
    public List<ApiToken> findByUserId(int userId) {
        List<ApiToken> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM api_tokens WHERE user_id = ? ORDER BY id")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapToken(rs));
            }
        } catch (Exception e) {
            throw new RuntimeException("查询用户 Token 列表失败", e);
        }
        return list;
    }

    /**
     * 删除 Token
     */
    public void delete(int tokenId, int userId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM api_tokens WHERE id = ? AND user_id = ?")) {
            ps.setInt(1, tokenId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("删除 Token 失败", e);
        }
    }

    /**
     * 更新 Token 备注
     */
    public void updateNote(int tokenId, int userId, String note) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE api_tokens SET note = ? WHERE id = ? AND user_id = ?")) {
            ps.setString(1, note);
            ps.setInt(2, tokenId);
            ps.setInt(3, userId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("更新 Token 备注失败", e);
        }
    }

    /**
     * 查找 Token 所属用户 ID
     */
    public Integer findUserIdByToken(String token) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT user_id FROM api_tokens WHERE token = ?")) {
            ps.setString(1, token);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("user_id");
            }
        } catch (Exception e) {
            throw new RuntimeException("查询 Token 所属用户失败", e);
        }
        return null;
    }

    private ApiToken mapToken(ResultSet rs) throws SQLException {
        return ApiToken.builder()
                .id(rs.getInt("id"))
                .userId(rs.getInt("user_id"))
                .token(rs.getString("token"))
                .note(rs.getString("note"))
                .createdAt(rs.getLong("created_at"))
                .lastUsedAt(rs.getLong("last_used_at"))
                .build();
    }

}
