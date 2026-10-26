package com.thalhuria.bot.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Histórico de moderação (advertências, castigos, expulsões, banimentos).
 */
public class InfractionRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(InfractionRepository.class);
    private final DatabaseManager databaseManager;

    public record Infraction(long id, String guildId, String userId, String moderatorId,
                             String type, String reason, long createdAt, boolean active) {}

    public InfractionRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public long add(String guildId, String userId, String moderatorId, String type, String reason) {
        String sql = "INSERT INTO infractions (guild_id, user_id, moderator_id, type, reason, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            pstmt.setString(3, moderatorId);
            pstmt.setString(4, type);
            pstmt.setString(5, reason);
            pstmt.setLong(6, System.currentTimeMillis() / 1000);
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        } catch (SQLException e) {
            LOGGER.error("Erro ao registrar infração de {}: {}", userId, e.getMessage(), e);
        }
        return -1;
    }

    public List<Infraction> list(String guildId, String userId) {
        String sql = "SELECT * FROM infractions WHERE guild_id = ? AND user_id = ? ORDER BY created_at DESC";
        List<Infraction> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Infraction(rs.getLong("id"), rs.getString("guild_id"), rs.getString("user_id"),
                            rs.getString("moderator_id"), rs.getString("type"), rs.getString("reason"),
                            rs.getLong("created_at"), rs.getInt("active") == 1));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Erro ao listar infrações de {}: {}", userId, e.getMessage(), e);
        }
        return list;
    }

    /**
     * Advertências ativas dentro da janela de validade (dias).
     */
    public int countActiveWarnings(String guildId, String userId, int expiryDays) {
        long since = expiryDays > 0 ? System.currentTimeMillis() / 1000 - expiryDays * 86400L : 0;
        String sql = "SELECT COUNT(*) FROM infractions WHERE guild_id = ? AND user_id = ? AND type = 'WARN' AND active = 1 AND created_at >= ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            pstmt.setLong(3, since);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.error("Erro ao contar advertências de {}: {}", userId, e.getMessage(), e);
        }
        return 0;
    }

    /**
     * Desativa uma infração (perdão). Retorna true se existia e pertencia à guilda.
     */
    public boolean deactivate(String guildId, long id) {
        String sql = "UPDATE infractions SET active = 0 WHERE guild_id = ? AND id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setLong(2, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("Erro ao perdoar infração {}: {}", id, e.getMessage(), e);
            return false;
        }
    }
}
