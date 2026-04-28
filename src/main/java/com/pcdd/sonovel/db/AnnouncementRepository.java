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
    public Announcement create(String title, String content, int pinned, int showOnLogin, int dismissable) {
        long now = System.currentTimeMillis();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO announcements (title, content, pinned, show_on_login, dismissable, created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setString(2, content);
            ps.setInt(3, pinned);
            ps.setInt(4, showOnLogin);
            ps.setInt(5, dismissable);
            ps.setLong(6, now);
            ps.setLong(7, now);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return Announcement.builder()
                        .id(rs.getInt(1))
                        .title(title)
                        .content(content)
                        .pinned(pinned)
                        .showOnLogin(showOnLogin)
                        .dismissable(dismissable)
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
    public void update(int id, String title, String content, int pinned, int showOnLogin, int dismissable) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE announcements SET title=?, content=?, pinned=?, show_on_login=?, dismissable=?, updated_at=? WHERE id=?")) {
            ps.setString(1, title);
            ps.setString(2, content);
            ps.setInt(3, pinned);
            ps.setInt(4, showOnLogin);
            ps.setInt(5, dismissable);
            ps.setLong(6, System.currentTimeMillis());
            ps.setInt(7, id);
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
                     "SELECT id, title, pinned, show_on_login, dismissable, created_at, updated_at FROM announcements ORDER BY pinned DESC, created_at DESC")) {
            while (rs.next()) {
                list.add(Announcement.builder()
                        .id(rs.getInt("id"))
                        .title(rs.getString("title"))
                        .pinned(rs.getInt("pinned"))
                        .showOnLogin(rs.getInt("show_on_login"))
                        .dismissable(rs.getInt("dismissable"))
                        .createdAt(rs.getLong("created_at"))
                        .updatedAt(rs.getLong("updated_at"))
                        .build());
            }
        } catch (Exception e) {
            throw new RuntimeException("查询公开公告列表失败", e);
        }
        return list;
    }

    /** 获取需在登录/注册页显示的公告列表 */
    public List<Announcement> findLoginPage() {
        List<Announcement> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT * FROM announcements WHERE show_on_login=1 ORDER BY pinned DESC, created_at DESC")) {
            while (rs.next()) list.add(mapAnnouncement(rs));
        } catch (Exception e) { throw new RuntimeException(e); }
        return list;
    }

    private Announcement mapAnnouncement(ResultSet rs) throws SQLException {
        return Announcement.builder()
                .id(rs.getInt("id"))
                .title(rs.getString("title"))
                .content(rs.getString("content"))
                .pinned(rs.getInt("pinned"))
                .showOnLogin(rs.getInt("show_on_login"))
                .dismissable(rs.getInt("dismissable"))
                .createdAt(rs.getLong("created_at"))
                .updatedAt(rs.getLong("updated_at"))
                .build();
    }

}
