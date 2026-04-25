package com.pcdd.sonovel.db;

import cn.hutool.core.lang.Console;
import com.pcdd.sonovel.core.AppConfigLoader;
import lombok.SneakyThrows;

import java.nio.file.Path;
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
        Path dbPath = Paths.get(AppConfigLoader.APP_CONFIG.getDownloadPath()).getParent();
        if (dbPath == null) dbPath = Paths.get(".");
        String dbFile = dbPath.resolve("sonovel.db").toAbsolutePath().toString();
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
        }
    }

}
