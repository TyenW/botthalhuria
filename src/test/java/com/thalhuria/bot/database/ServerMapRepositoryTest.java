package com.thalhuria.bot.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ServerMapRepositoryTest {

    private DatabaseManager databaseManager;
    private ServerMapRepository repository;
    private File tempDbFile;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        tempDbFile = tempDir.resolve("test_server_map.db").toFile();
        databaseManager = new DatabaseManager(tempDbFile.getAbsolutePath());
        repository = new ServerMapRepository(databaseManager);
    }

    @Test
    void testSaveAndRetrieveMapping() {
        String guildId = "123456789";
        repository.saveMapping(guildId, "ROLE", "ROLE_ADMIN", "99887766", "Admin");

        Optional<String> discordId = repository.getDiscordId(guildId, "ROLE", "ROLE_ADMIN");
        assertTrue(discordId.isPresent());
        assertEquals("99887766", discordId.get());
    }

    @Test
    void testUpdateMappingOnConflict() {
        String guildId = "123456789";
        repository.saveMapping(guildId, "CHANNEL", "CHAN_LOGS", "111111", "logs-antigo");
        repository.saveMapping(guildId, "CHANNEL", "CHAN_LOGS", "222222", "logs-do-sistema");

        Optional<String> discordId = repository.getDiscordId(guildId, "CHANNEL", "CHAN_LOGS");
        assertTrue(discordId.isPresent());
        assertEquals("222222", discordId.get());
    }

    @Test
    void testGetMappingsByType() {
        String guildId = "123456789";
        repository.saveMapping(guildId, "ROLE", "ROLE_ADMIN", "1", "Admin");
        repository.saveMapping(guildId, "ROLE", "ROLE_MEMBER", "2", "Membro");
        repository.saveMapping(guildId, "CHANNEL", "CHAN_CHAT", "3", "bate-papo");

        List<ServerMapRepository.MappingEntry> roles = repository.getMappingsByType(guildId, "ROLE");
        assertEquals(2, roles.size());

        List<ServerMapRepository.MappingEntry> channels = repository.getMappingsByType(guildId, "CHANNEL");
        assertEquals(1, channels.size());
        assertEquals("CHAN_CHAT", channels.get(0).keyName());
    }

    @Test
    void testDeleteGuildMappings() {
        String guildId = "123456789";
        repository.saveMapping(guildId, "ROLE", "ROLE_ADMIN", "1", "Admin");
        repository.saveMapping(guildId, "CATEGORY", "CAT_INFO", "2", "Informações");

        assertEquals(2, repository.getMappingsByGuild(guildId).size());

        repository.deleteGuildMappings(guildId);

        assertEquals(0, repository.getMappingsByGuild(guildId).size());
    }
}
