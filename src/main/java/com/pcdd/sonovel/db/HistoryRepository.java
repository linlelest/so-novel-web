package com.pcdd.sonovel.db;

import com.pcdd.sonovel.model.DownloadHistory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 下载历史 Repository
 *
 * @author pcdd
 */
public class HistoryRepository {

    private final DatabaseManager db = DatabaseManager.getInstance();

    /**
     * 添加下载历史
     */
    public void add(int userId, String bookName, String author, String sourceName, String format,
                    String fileName, long fileSize) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO download_history (user_id, book_name, author, source_name, format, file_name, file_size, created_at) VALUES (?,?,?,?,?,?,?,?)")) {
            ps.setInt(1, userId);
            ps.setString(2, bookName);
            ps.setString(3, author);
            ps.setString(4, sourceName);
            ps.setString(5, format);
            ps.setString(6, fileName);
            ps.setLong(7, fileSize);
            ps.setLong(8, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("添加下载历史失败", e);
        }
    }

    /**
     * 添加下载日志（管理员全局查看）
     */
    public void addLog(String username, String bookName, String author, String format, String sourceName) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO download_logs (username, book_name, author, format, source_name, created_at) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, username);
            ps.setString(2, bookName);
            ps.setString(3, author);
            ps.setString(4, format);
            ps.setString(5, sourceName);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("添加下载日志失败", e);
        }
    }

    /**
     * 查询用户的下载历史
     */
    public List<DownloadHistory> findByUserId(int userId) {
        List<DownloadHistory> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM download_history WHERE user_id = ? ORDER BY created_at DESC LIMIT 100")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapHistory(rs));
            }
        } catch (Exception e) {
            throw new RuntimeException("查询下载历史失败", e);
        }
        return list;
    }

    /**
     * 查询所有下载日志（管理员用）
     */
    public List<DownloadHistory> findAllLogs() {
        List<DownloadHistory> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT * FROM download_logs ORDER BY created_at DESC LIMIT 200")) {
            while (rs.next()) {
                list.add(DownloadHistory.builder()
                        .id(rs.getInt("id"))
                        .bookName(rs.getString("book_name"))
                        .author(rs.getString("author"))
                        .format(rs.getString("format"))
                        .sourceName(rs.getString("source_name"))
                        .createdAt(rs.getLong("created_at"))
                        .build());
            }
        } catch (Exception e) {
            throw new RuntimeException("查询下载日志失败", e);
        }
        return list;
    }

    private DownloadHistory mapHistory(ResultSet rs) throws SQLException {
        return DownloadHistory.builder()
                .id(rs.getInt("id"))
                .userId(rs.getInt("user_id"))
                .bookName(rs.getString("book_name"))
                .author(rs.getString("author"))
                .sourceName(rs.getString("source_name"))
                .format(rs.getString("format"))
                .fileName(rs.getString("file_name"))
                .fileSize(rs.getLong("file_size"))
                .createdAt(rs.getLong("created_at"))
                .build();
    }

}
