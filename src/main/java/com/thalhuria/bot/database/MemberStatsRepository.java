package com.thalhuria.bot.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * XP, nível, moedas e contadores de atividade de cada membro.
 */
public class MemberStatsRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemberStatsRepository.class);
    private final DatabaseManager databaseManager;

    public record MemberStats(
            String guildId,
            String userId,
            long xp,
            int level,
            long messages,
            long voiceMinutes,
            long coins,
            long lastXpAt,
            long lastDailyAt,
            int dailyStreak
    ) {
        static MemberStats empty(String guildId, String userId) {
            return new MemberStats(guildId, userId, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    public MemberStatsRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public MemberStats get(String guildId, String userId) {
        String sql = "SELECT * FROM member_stats WHERE guild_id = ? AND user_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (SQLException e) {
            LOGGER.error("Erro ao buscar stats de {} na guild {}: {}", userId, guildId, e.getMessage(), e);
        }
        return MemberStats.empty(guildId, userId);
    }

    public void save(MemberStats s) {
        String sql = """
            INSERT INTO member_stats (guild_id, user_id, xp, level, messages, voice_minutes, coins, last_xp_at, last_daily_at, daily_streak)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(guild_id, user_id) DO UPDATE SET
                xp = excluded.xp, level = excluded.level, messages = excluded.messages,
                voice_minutes = excluded.voice_minutes, coins = excluded.coins, last_xp_at = excluded.last_xp_at,
                last_daily_at = excluded.last_daily_at, daily_streak = excluded.daily_streak
            """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, s.guildId());
            pstmt.setString(2, s.userId());
            pstmt.setLong(3, s.xp());
            pstmt.setInt(4, s.level());
            pstmt.setLong(5, s.messages());
            pstmt.setLong(6, s.voiceMinutes());
            pstmt.setLong(7, s.coins());
            pstmt.setLong(8, s.lastXpAt());
            pstmt.setLong(9, s.lastDailyAt());
            pstmt.setInt(10, s.dailyStreak());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Erro ao salvar stats de {} na guild {}: {}", s.userId(), s.guildId(), e.getMessage(), e);
        }
    }

    /** Métricas de ranking. A coluna vem só deste enum, nunca do usuário (sem risco de SQL injection). */
    public enum Metric {
        XP("xp"), COINS("coins"), VOICE("voice_minutes"), MESSAGES("messages");

        private final String column;

        Metric(String column) {
            this.column = column;
        }

        public long valueOf(MemberStats s) {
            return switch (this) {
                case XP -> s.xp();
                case COINS -> s.coins();
                case VOICE -> s.voiceMinutes();
                case MESSAGES -> s.messages();
            };
        }
    }

    /**
     * Ranking ordenado por XP (ou por moedas).
     */
    public List<MemberStats> top(String guildId, boolean byCoins, int limit) {
        return top(guildId, byCoins ? Metric.COINS : Metric.XP, limit);
    }

    /** Posição (1 = primeiro) de quem tem {@code value} na métrica. */
    public int positionOf(String guildId, Metric metric, long value) {
        String sql = "SELECT COUNT(*) FROM member_stats WHERE guild_id = ? AND " + metric.column + " > ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setLong(2, value);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1) + 1;
            }
        } catch (SQLException e) {
            LOGGER.error("Erro ao calcular posição no ranking: {}", e.getMessage(), e);
        }
        return 0;
    }

    public List<MemberStats> top(String guildId, Metric metric, int limit) {
        String sql = "SELECT * FROM member_stats WHERE guild_id = ? AND " + metric.column + " > 0 ORDER BY " + metric.column + " DESC LIMIT ?";
        List<MemberStats> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setInt(2, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            LOGGER.error("Erro ao montar ranking da guild {}: {}", guildId, e.getMessage(), e);
        }
        return list;
    }

    /**
     * Posição do membro no ranking de XP (1 = primeiro).
     */
    public int rankOf(String guildId, long xp) {
        String sql = "SELECT COUNT(*) FROM member_stats WHERE guild_id = ? AND xp > ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setLong(2, xp);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1) + 1;
            }
        } catch (SQLException e) {
            LOGGER.error("Erro ao calcular posição no ranking: {}", e.getMessage(), e);
        }
        return 0;
    }

    private static MemberStats map(ResultSet rs) throws SQLException {
        return new MemberStats(
                rs.getString("guild_id"),
                rs.getString("user_id"),
                rs.getLong("xp"),
                rs.getInt("level"),
                rs.getLong("messages"),
                rs.getLong("voice_minutes"),
                rs.getLong("coins"),
                rs.getLong("last_xp_at"),
                rs.getLong("last_daily_at"),
                rs.getInt("daily_streak")
        );
    }
}
