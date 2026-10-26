package com.thalhuria.bot.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Registro de quem entrou por qual convite. Uma linha por membro e servidor: quem sai e volta
 * mantém o primeiro convidador, e a recompensa nunca é paga duas vezes.
 */
public class InviteRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(InviteRepository.class);
    private final DatabaseManager databaseManager;

    public record InviteJoin(String guildId, String userId, String inviterId, String code, long joinedAt,
                             boolean valid, boolean verified, boolean left, boolean rewarded) {}

    /** Contagem de convites de uma pessoa. "counted" = os que valem para recompensas e ranking. */
    public record InviterStats(int total, int counted, int left, int fake, int pending) {}

    public record RankEntry(String inviterId, int counted) {}

    public InviteRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Optional<InviteJoin> get(String guildId, String userId) {
        String sql = "SELECT * FROM invite_joins WHERE guild_id = ? AND user_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new InviteJoin(rs.getString("guild_id"), rs.getString("user_id"),
                            rs.getString("inviter_id"), rs.getString("code"), rs.getLong("joined_at"),
                            rs.getInt("valid") == 1, rs.getInt("verified") == 1,
                            rs.getInt("left_guild") == 1, rs.getInt("rewarded") == 1));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Erro ao buscar entrada de {}: {}", userId, e.getMessage(), e);
        }
        return Optional.empty();
    }

    /** Primeira entrada do membro no servidor. */
    public void insert(String guildId, String userId, String inviterId, String code, boolean valid) {
        String sql = "INSERT OR IGNORE INTO invite_joins (guild_id, user_id, inviter_id, code, joined_at, valid) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            pstmt.setString(3, inviterId);
            pstmt.setString(4, code);
            pstmt.setLong(5, System.currentTimeMillis() / 1000);
            pstmt.setInt(6, valid ? 1 : 0);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Erro ao registrar entrada de {}: {}", userId, e.getMessage(), e);
        }
    }

    public void setLeft(String guildId, String userId, boolean left) {
        update("UPDATE invite_joins SET left_guild = ? WHERE guild_id = ? AND user_id = ?", left ? 1 : 0, guildId, userId);
    }

    public void markVerified(String guildId, String userId) {
        update("UPDATE invite_joins SET verified = ? WHERE guild_id = ? AND user_id = ?", 1, guildId, userId);
    }

    public void markRewarded(String guildId, String userId) {
        update("UPDATE invite_joins SET rewarded = ? WHERE guild_id = ? AND user_id = ?", 1, guildId, userId);
    }

    public InviterStats stats(String guildId, String inviterId, boolean requireVerified) {
        String sql = """
            SELECT COUNT(*) AS total,
                   SUM(CASE WHEN valid = 1 AND left_guild = 0 AND (verified = 1 OR ? = 0) THEN 1 ELSE 0 END) AS counted,
                   SUM(CASE WHEN left_guild = 1 THEN 1 ELSE 0 END) AS left_count,
                   SUM(CASE WHEN valid = 0 THEN 1 ELSE 0 END) AS fake,
                   SUM(CASE WHEN valid = 1 AND left_guild = 0 AND verified = 0 AND ? = 1 THEN 1 ELSE 0 END) AS pending
            FROM invite_joins WHERE guild_id = ? AND inviter_id = ?
            """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, requireVerified ? 1 : 0);
            pstmt.setInt(2, requireVerified ? 1 : 0);
            pstmt.setString(3, guildId);
            pstmt.setString(4, inviterId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new InviterStats(rs.getInt("total"), rs.getInt("counted"), rs.getInt("left_count"),
                            rs.getInt("fake"), rs.getInt("pending"));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Erro ao contar convites de {}: {}", inviterId, e.getMessage(), e);
        }
        return new InviterStats(0, 0, 0, 0, 0);
    }

    public List<RankEntry> top(String guildId, boolean requireVerified, int limit) {
        String sql = """
            SELECT inviter_id, SUM(CASE WHEN valid = 1 AND left_guild = 0 AND (verified = 1 OR ? = 0) THEN 1 ELSE 0 END) AS counted
            FROM invite_joins WHERE guild_id = ? AND inviter_id IS NOT NULL
            GROUP BY inviter_id HAVING counted > 0 ORDER BY counted DESC LIMIT ?
            """;
        List<RankEntry> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, requireVerified ? 1 : 0);
            pstmt.setString(2, guildId);
            pstmt.setInt(3, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) list.add(new RankEntry(rs.getString("inviter_id"), rs.getInt("counted")));
            }
        } catch (SQLException e) {
            LOGGER.error("Erro ao montar ranking de convites: {}", e.getMessage(), e);
        }
        return list;
    }

    private void update(String sql, int value, String guildId, String userId) {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, value);
            pstmt.setString(2, guildId);
            pstmt.setString(3, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Erro ao atualizar convite de {}: {}", userId, e.getMessage(), e);
        }
    }
}
