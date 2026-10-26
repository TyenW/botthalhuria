package com.thalhuria.bot.database;

import com.thalhuria.bot.database.MemberStatsRepository.MemberStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StatsAndInfractionsTest {

    @TempDir
    Path tmp;

    private MemberStatsRepository stats;
    private InfractionRepository infractions;

    @BeforeEach
    void setUp() {
        DatabaseManager db = new DatabaseManager(tmp.resolve("test.db").toString());
        stats = new MemberStatsRepository(db);
        infractions = new InfractionRepository(db);
    }

    @Test
    void unknownMemberStartsEmpty() {
        MemberStats s = stats.get("g", "u");
        assertEquals(0, s.xp());
        assertEquals(0, s.coins());
    }

    @Test
    void savesAndRanks() {
        stats.save(new MemberStats("g", "a", 500, 3, 10, 5, 100, 0, 0, 0));
        stats.save(new MemberStats("g", "b", 900, 4, 20, 0, 50, 0, 0, 0));
        stats.save(new MemberStats("g", "c", 100, 1, 1, 0, 999, 0, 0, 0));
        stats.save(new MemberStats("outra", "z", 99999, 50, 0, 0, 0, 0, 0, 0));

        assertEquals(500, stats.get("g", "a").xp());
        List<MemberStats> byXp = stats.top("g", false, 10);
        assertEquals(List.of("b", "a", "c"), byXp.stream().map(MemberStats::userId).toList());
        List<MemberStats> byCoins = stats.top("g", true, 1);
        assertEquals("c", byCoins.get(0).userId());
        assertEquals(2, stats.rankOf("g", 500));

        // upsert
        stats.save(new MemberStats("g", "a", 1000, 5, 11, 5, 100, 0, 0, 0));
        assertEquals(1, stats.rankOf("g", 1000));
    }

    @Test
    void warningsCountAndPardon() {
        long w1 = infractions.add("g", "u", "mod", "WARN", "spam");
        infractions.add("g", "u", "mod", "WARN", "flood");
        infractions.add("g", "u", "mod", "TIMEOUT", "10 min");
        infractions.add("g", "outro", "mod", "WARN", "x");

        assertEquals(2, infractions.countActiveWarnings("g", "u", 30));
        assertEquals(3, infractions.list("g", "u").size());

        assertTrue(infractions.deactivate("g", w1));
        assertFalse(infractions.deactivate("outra-guild", w1), "não pode perdoar infração de outra guilda");
        assertEquals(1, infractions.countActiveWarnings("g", "u", 30));
    }
}
