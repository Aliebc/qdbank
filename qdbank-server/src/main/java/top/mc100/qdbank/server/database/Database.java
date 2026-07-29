package top.mc100.qdbank.server.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database implements AutoCloseable {
    private final HikariDataSource dataSource;

    public Database(Path file, int poolSize) throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + file);
        config.setMaximumPoolSize(poolSize);
        config.setPoolName("qdbank-sqlite");
        config.setConnectionInitSql("PRAGMA foreign_keys=ON");
        dataSource = new HikariDataSource(config);
        migrate();
    }

    public Connection connection() throws SQLException { return dataSource.getConnection(); }

    private void migrate() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS accounts (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  uuid VARCHAR(36) NOT NULL,
                  player_name VARCHAR(16) NOT NULL,
                  account_name VARCHAR(32) NOT NULL,
                  content BLOB NOT NULL,
                  revision INTEGER NOT NULL DEFAULT 0,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE(uuid, account_name)
                )""");
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS audit_logs (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  uuid VARCHAR(36) NOT NULL,
                  account_name VARCHAR(32) NOT NULL,
                  action_type VARCHAR(16) NOT NULL,
                  item_detail TEXT,
                  client_id VARCHAR(64) NOT NULL,
                  world VARCHAR(128) NOT NULL DEFAULT 'UNKNOWN',
                  x REAL NOT NULL DEFAULT 0,
                  y REAL NOT NULL DEFAULT 0,
                  z REAL NOT NULL DEFAULT 0,
                  server_id VARCHAR(64) NOT NULL DEFAULT 'unknown',
                  before_content BLOB,
                  after_content BLOB,
                  timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_audit_uuid_time ON audit_logs(uuid, timestamp DESC)");
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA busy_timeout=5000");
        }
    }

    @Override public void close() { dataSource.close(); }
}
