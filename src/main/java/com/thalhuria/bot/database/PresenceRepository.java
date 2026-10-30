package com.thalhuria.bot.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Batimento do bot por servidor: a última vez que ele esteve online. Ao religar, quem entrou depois
 * desse momento entrou com o bot desligado e passa pela recuperação de entrada.
 */
public class PresenceRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(PresenceRepository.class);
    private final DatabaseManager databaseManager;

    public PresenceRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /** @return epoch em milissegundos, ou vazio se o bot nunca registrou presença neste servidor */
    public Optional<Long> lastSeen(String guildId) {
        String sql = "SELECT last_seen FROM bot_presence WHERE guild_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return Optional.of(rs.getLong("last_seen"));
            }
        } catch (SQLException e) {
            LOGGER.error("Erro ao ler presença do servidor {}: {}", guildId, e.getMessage(), e);
        }
        return Optional.empty();
    }

    public void touch(String guildId, long epochMillis) {
        String sql = "INSERT INTO bot_presence (guild_id, last_seen) VALUES (?, ?) "
                + "ON CONFLICT(guild_id) DO UPDATE SET last_seen = excluded.last_seen";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setLong(2, epochMillis);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Erro ao gravar presença do servidor {}: {}", guildId, e.getMessage(), e);
        }
    }
}
