package com.thalhuria.bot.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Gerencia a conexão e o esquema do banco de dados SQLite.
 * Tabelas: server_mappings (key do template -> ID do Discord), member_stats (XP e economia) e infractions (moderação).
 *
 * O banco roda em modo WAL com busy_timeout: leituras não bloqueiam escritas e escritas concorrentes
 * (XP de várias mensagens ao mesmo tempo) esperam em vez de falhar com SQLITE_BUSY.
 */
public class DatabaseManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseManager.class);
    private static final String DEFAULT_DB_PATH = "data/server_map.db";

    private final String dbUrl;
    private final SQLiteConfig config;

    public DatabaseManager() {
        this(DEFAULT_DB_PATH);
    }

    public DatabaseManager(String dbFilePath) {
        File dbFile = new File(dbFilePath);
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        this.dbUrl = "jdbc:sqlite:" + dbFilePath;
        this.config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        config.setBusyTimeout(5000);
        initSchema();
    }

    public Connection getConnection() throws SQLException {
        return config.createConnection(dbUrl);
    }

    private void initSchema() {
        List<String> statements = List.of(
                """
                CREATE TABLE IF NOT EXISTS server_mappings (
                    guild_id TEXT NOT NULL,
                    entity_type TEXT NOT NULL,
                    key_name TEXT NOT NULL,
                    discord_id TEXT NOT NULL,
                    display_name TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (guild_id, entity_type, key_name)
                )""",
                "CREATE INDEX IF NOT EXISTS idx_guild_entity ON server_mappings(guild_id, entity_type)",
                """
                CREATE TABLE IF NOT EXISTS member_stats (
                    guild_id TEXT NOT NULL,
                    user_id TEXT NOT NULL,
                    xp INTEGER NOT NULL DEFAULT 0,
                    level INTEGER NOT NULL DEFAULT 0,
                    messages INTEGER NOT NULL DEFAULT 0,
                    voice_minutes INTEGER NOT NULL DEFAULT 0,
                    coins INTEGER NOT NULL DEFAULT 0,
                    last_xp_at INTEGER NOT NULL DEFAULT 0,
                    last_daily_at INTEGER NOT NULL DEFAULT 0,
                    daily_streak INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (guild_id, user_id)
                )""",
                "CREATE INDEX IF NOT EXISTS idx_stats_xp ON member_stats(guild_id, xp DESC)",
                """
                CREATE TABLE IF NOT EXISTS infractions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    guild_id TEXT NOT NULL,
                    user_id TEXT NOT NULL,
                    moderator_id TEXT NOT NULL,
                    type TEXT NOT NULL,
                    reason TEXT,
                    created_at INTEGER NOT NULL,
                    active INTEGER NOT NULL DEFAULT 1
                )""",
                "CREATE INDEX IF NOT EXISTS idx_infractions_user ON infractions(guild_id, user_id)",
                """
                CREATE TABLE IF NOT EXISTS invite_joins (
                    guild_id TEXT NOT NULL,
                    user_id TEXT NOT NULL,
                    inviter_id TEXT,
                    code TEXT,
                    joined_at INTEGER NOT NULL,
                    valid INTEGER NOT NULL DEFAULT 1,
                    verified INTEGER NOT NULL DEFAULT 0,
                    left_guild INTEGER NOT NULL DEFAULT 0,
                    rewarded INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (guild_id, user_id)
                )""",
                "CREATE INDEX IF NOT EXISTS idx_invite_joins_inviter ON invite_joins(guild_id, inviter_id)"
        );

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                stmt.executeUpdate(sql);
            }
            LOGGER.info("Tabelas do banco de dados SQLite inicializadas com sucesso em: {}", dbUrl);
        } catch (SQLException e) {
            LOGGER.error("Falha ao inicializar o banco de dados SQLite", e);
            throw new RuntimeException("Não foi possível inicializar o banco de dados", e);
        }
    }
}
