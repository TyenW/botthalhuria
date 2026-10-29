package com.thalhuria.bot.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PresenceRepositoryTest {

    @TempDir
    Path tmp;

    @Test
    void lastSeenIsEmptyUntilFirstTouchAndKeepsTheLatest() {
        PresenceRepository presence = new PresenceRepository(new DatabaseManager(tmp.resolve("p.db").toString()));
        assertEquals(Optional.empty(), presence.lastSeen("g"));

        presence.touch("g", 1_000L);
        presence.touch("g", 2_000L);
        presence.touch("outra", 5_000L);

        assertEquals(Optional.of(2_000L), presence.lastSeen("g"));
        assertEquals(Optional.of(5_000L), presence.lastSeen("outra"));
    }
}
