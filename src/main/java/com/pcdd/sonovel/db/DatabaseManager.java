package com.pcdd.sonovel.db;

import cn.hutool.core.lang.Console;
import lombok.SneakyThrows;

import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * SQLite 数据库管理器
 *
 * @author pcdd
 */
public class DatabaseManager {

    private static final DatabaseManager INSTANCE = new DatabaseManager();
    private final String dbUrl;

    @SneakyThrows
    private DatabaseManager() {
        // 以程序工作目录为数据库根目录
        String dbDir = System.getProperty("user.dir");
        String dbFile = Paths.get(dbDir, "sonovel.db").toAbsolutePath().toString();
        this.dbUrl = "jdbc:sqlite:" + dbFile;
        Console.log("📦 数据库路径: {}", dbFile);
        initTables();
    }

    public static DatabaseManager getInstance() {
        return INSTANCE;
    }

    public Connection getConnection() {
        try {
            Connection conn = DriverManager.getConnection(dbUrl);
            conn.setAutoCommit(true);
            return conn;
        } catch (Exception e) {
            throw new RuntimeException("数据库连接失败", e);
        }
    }

    @SneakyThrows
    private void initTables() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // 用户表
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT NOT NULL UNIQUE,
                        password_hash TEXT NOT NULL,
                        salt TEXT NOT NULL,
                        role TEXT NOT NULL DEFAULT 'user',
                        banned INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            // API Token 表
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS api_tokens (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id INTEGER NOT NULL,
                        token TEXT NOT NULL UNIQUE,
                        note TEXT DEFAULT '',
                        created_at INTEGER NOT NULL,
                        last_used_at INTEGER,
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                    )
                    """);
            // 下载历史表
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS download_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id INTEGER NOT NULL,
                        book_name TEXT NOT NULL,
                        author TEXT DEFAULT '',
                        source_name TEXT DEFAULT '',
                        format TEXT NOT NULL,
                        file_name TEXT DEFAULT '',
                        file_size INTEGER DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                    )
                    """);
            // 下载日志（管理员可见）
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS download_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT NOT NULL,
                        book_name TEXT NOT NULL,
                        author TEXT DEFAULT '',
                        format TEXT NOT NULL,
                        source_name TEXT DEFAULT '',
                        created_at INTEGER NOT NULL
                    )
                    """);
            // 公告表
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS announcements (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        pinned INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            // IP 黑名单
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ip_blacklist (
                        ip TEXT PRIMARY KEY,
                        reason TEXT DEFAULT '',
                        banned_at INTEGER NOT NULL,
                        expires_at INTEGER NOT NULL
                    )
                    """);
            // 被封禁/删除用户记录（用于"严重违规"公告）
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS banned_users_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        action TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);
            // 系统配置
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS sys_config (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """);
            // 邀请码表
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS invite_codes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        code TEXT NOT NULL UNIQUE,
                        max_uses INTEGER NOT NULL DEFAULT 1,
                        used_count INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        created_by TEXT NOT NULL
                    )
                    """);
            // 初始化默认配置
            stmt.execute("INSERT OR IGNORE INTO sys_config VALUES ('contact_info', '请联系管理员')");
            stmt.execute("INSERT OR IGNORE INTO sys_config VALUES ('maintenance_mode', 'false')");
            stmt.execute("INSERT OR IGNORE INTO sys_config VALUES ('maintenance_reason', '')");
            stmt.execute("INSERT OR IGNORE INTO sys_config VALUES ('api_search_rate', '10/60')");
            stmt.execute("INSERT OR IGNORE INTO sys_config VALUES ('api_download_rate', '3/3600')");
            stmt.execute("INSERT OR IGNORE INTO sys_config VALUES ('web_search_rate', '30/60')");
            stmt.execute("INSERT OR IGNORE INTO sys_config VALUES ('web_download_rate', '5/3600')");
            stmt.execute("INSERT OR IGNORE INTO sys_config VALUES ('web_visit_rate', '60/60')");
            stmt.execute("INSERT OR IGNORE INTO sys_config VALUES ('invite_code_enabled', 'false')");
            stmt.execute("INSERT OR IGNORE INTO sys_config VALUES ('invite_code_prompt', '需要邀请码才能注册，请联系管理员获取。')");

            // Migrate: add show_on_login and dismissable columns to announcements if missing
            try { stmt.execute("ALTER TABLE announcements ADD COLUMN show_on_login INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE announcements ADD COLUMN dismissable INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
            // Migrate: add last_ip column to users for IP tracking
            try { stmt.execute("ALTER TABLE users ADD COLUMN last_ip TEXT DEFAULT ''"); } catch (Exception ignored) {}
            // Migrate: add deleted_at column for soft-delete (30min delay)
            try { stmt.execute("ALTER TABLE users ADD COLUMN deleted_at INTEGER DEFAULT 0"); } catch (Exception ignored) {}
        }
    }

}
