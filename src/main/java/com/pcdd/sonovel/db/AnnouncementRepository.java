package com.pcdd.sonovel.db;

import com.pcdd.sonovel.model.Announcement;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 公告 Repository
 *
 * @author pcdd
 */
public class AnnouncementRepository {

    private final DatabaseManager db = DatabaseManager.getInstance();

    /**
     * 创建公告
     */
    public Announcement create(String title, String content, int pinned) {
        long now = System.currentTimeMillis();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO announcements (title, content, pinned, created_at, updated_at) VALUES (?,?,?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setString(2, content);
            ps.setInt(3, pinned);
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return Announcement.builder()
                        .id(rs.getInt(1))
                        .title(title)
                        .content(content)
                        .pinned(pinned)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
            }
        } catch (Exception e) {
            throw new RuntimeException("创建公告失败", e);
        }
        return null;
    }

    /**
     * 获取所有公告（按置顶+时间倒序）
     */
    public List<Announcement> findAll() {
        List<Announcement> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT * FROM announcements ORDER BY pinned DESC, created_at DESC")) {
            while (rs.next()) {
                list.add(mapAnnouncement(rs));
            }
        } catch (Exception e) {
            throw new RuntimeException("查询公告列表失败", e);
        }
        return list;
    }

    /**
     * 根据 ID 查找
     */
    public Announcement findById(int id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM announcements WHERE id = ?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapAnnouncement(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException("查询公告失败", e);
        }
        return null;
    }

    /**
     * 更新公告
     */
    public void update(int id, String title, String content, int pinned) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE announcements SET title = ?, content = ?, pinned = ?, updated_at = ? WHERE id = ?")) {
            ps.setString(1, title);
            ps.setString(2, content);
            ps.setInt(3, pinned);
            ps.setLong(4, System.currentTimeMillis());
            ps.setInt(5, id);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("更新公告失败", e);
        }
    }

    /**
     * 删除公告
     */
    public void delete(int id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM announcements WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("删除公告失败", e);
        }
    }

    /**
     * 获取公开公告列表（不含 content 全文，仅标题和摘要）
     */
    public List<Announcement> findPublicList() {
        List<Announcement> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT id, title, pinned, created_at, updated_at FROM announcements ORDER BY pinned DESC, created_at DESC")) {
            while (rs.next()) {
                list.add(Announcement.builder()
                        .id(rs.getInt("id"))
                        .title(rs.getString("title"))
                        .pinned(rs.getInt("pinned"))
                        .createdAt(rs.getLong("created_at"))
                        .updatedAt(rs.getLong("updated_at"))
                        .build());
            }
        } catch (Exception e) {
            throw new RuntimeException("查询公开公告列表失败", e);
        }
        return list;
    }

    private Announcement mapAnnouncement(ResultSet rs) throws SQLException {
        return Announcement.builder()
                .id(rs.getInt("id"))
                .title(rs.getString("title"))
                .content(rs.getString("content"))
                .pinned(rs.getInt("pinned"))
                .createdAt(rs.getLong("created_at"))
                .updatedAt(rs.getLong("updated_at"))
                .build();
    }

}
