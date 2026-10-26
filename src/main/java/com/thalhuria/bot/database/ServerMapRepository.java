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
 * Repositório responsável por salvar, buscar e limpar o mapeamento de IDs de cargos,
 * categorias e canais persistidos para cada servidor (guildId).
 */
public class ServerMapRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerMapRepository.class);
    private final DatabaseManager databaseManager;

    public ServerMapRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public record MappingEntry(
        String guildId,
        String entityType,
        String keyName,
        String discordId,
        String displayName
    ) {}

    /**
     * Salva ou atualiza um mapeamento de snowflake ID no banco de dados.
     */
    public void saveMapping(String guildId, String entityType, String keyName, String discordId, String displayName) {
        String sql = """
            INSERT INTO server_mappings (guild_id, entity_type, key_name, discord_id, display_name)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(guild_id, entity_type, key_name)
            DO UPDATE SET discord_id = excluded.discord_id,
                          display_name = excluded.display_name,
                          created_at = CURRENT_TIMESTAMP;
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, entityType.toUpperCase());
            pstmt.setString(3, keyName.toUpperCase());
            pstmt.setString(4, discordId);
            pstmt.setString(5, displayName);
            pstmt.executeUpdate();
            LOGGER.debug("Mapeamento salvo: Guild={} [{}] {} -> {}", guildId, entityType, keyName, discordId);
        } catch (SQLException e) {
            LOGGER.error("Erro ao salvar mapeamento Guild={} [{}] {}: {}", guildId, entityType, keyName, e.getMessage(), e);
        }
    }

    /**
     * Busca o Snowflake ID de um recurso mapeado.
     */
    public Optional<String> getDiscordId(String guildId, String entityType, String keyName) {
        String sql = "SELECT discord_id FROM server_mappings WHERE guild_id = ? AND entity_type = ? AND key_name = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, entityType.toUpperCase());
            pstmt.setString(3, keyName.toUpperCase());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("discord_id"));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Erro ao buscar discord_id para Guild={} [{}] {}: {}", guildId, entityType, keyName, e.getMessage(), e);
        }
        return Optional.empty();
    }

    /**
     * Busca todos os mapeamentos de uma determinada guilda.
     */
    public List<MappingEntry> getMappingsByGuild(String guildId) {
        List<MappingEntry> entries = new ArrayList<>();
        String sql = "SELECT guild_id, entity_type, key_name, discord_id, display_name FROM server_mappings WHERE guild_id = ? ORDER BY entity_type, key_name";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(new MappingEntry(
                        rs.getString("guild_id"),
                        rs.getString("entity_type"),
                        rs.getString("key_name"),
                        rs.getString("discord_id"),
                        rs.getString("display_name")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Erro ao listar mapeamentos da Guild {}: {}", guildId, e.getMessage(), e);
        }
        return entries;
    }

    /**
     * Busca mapeamentos filtrando por tipo de entidade (ROLE, CATEGORY, CHANNEL).
     */
    public List<MappingEntry> getMappingsByType(String guildId, String entityType) {
        List<MappingEntry> entries = new ArrayList<>();
        String sql = "SELECT guild_id, entity_type, key_name, discord_id, display_name FROM server_mappings WHERE guild_id = ? AND entity_type = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, entityType.toUpperCase());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(new MappingEntry(
                        rs.getString("guild_id"),
                        rs.getString("entity_type"),
                        rs.getString("key_name"),
                        rs.getString("discord_id"),
                        rs.getString("display_name")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Erro ao listar mapeamentos por tipo Guild={} [{}]", guildId, entityType, e);
        }
        return entries;
    }

    /**
     * Deleta todos os mapeamentos de uma guilda (utilizado no comando /reset-server).
     */
    public void deleteGuildMappings(String guildId) {
        String sql = "DELETE FROM server_mappings WHERE guild_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            int rows = pstmt.executeUpdate();
            LOGGER.info("Mapeamentos da Guild {} excluídos: {} registros removidos", guildId, rows);
        } catch (SQLException e) {
            LOGGER.error("Erro ao deletar mapeamentos da Guild {}: {}", guildId, e.getMessage(), e);
        }
    }

    /**
     * Remove um mapeamento específico.
     */
    public void deleteMapping(String guildId, String entityType, String keyName) {
        String sql = "DELETE FROM server_mappings WHERE guild_id = ? AND entity_type = ? AND key_name = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, entityType.toUpperCase());
            pstmt.setString(3, keyName.toUpperCase());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Erro ao remover mapeamento: {}", e.getMessage(), e);
        }
    }
}
