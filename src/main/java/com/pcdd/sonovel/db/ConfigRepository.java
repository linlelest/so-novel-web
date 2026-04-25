package com.pcdd.sonovel.db;

import java.sql.*;

/**
 * 系统配置 Repository (sys_config 表)
 *
 * @author pcdd
 */
public class ConfigRepository {

    private final DatabaseManager db = DatabaseManager.getInstance();

    public String get(String key) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT value FROM sys_config WHERE key=?")) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("value") : null;
        } catch (Exception e) { return null; }
    }

    public void set(String key, String value) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO sys_config(key,value) VALUES(?,?)")) {
            ps.setString(1, key); ps.setString(2, value); ps.executeUpdate();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

}

