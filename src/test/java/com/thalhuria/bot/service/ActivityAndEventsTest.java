package com.thalhuria.bot.service;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ActivityAndEventsTest {

    @Test
    void levelFormulaMatchesMee6Curve() {
        assertEquals(100, ActivityService.xpToNext(0));
        assertEquals(155, ActivityService.xpToNext(1));
        assertEquals(255, ActivityService.totalXpForLevel(2));
        assertEquals(0, ActivityService.levelForXp(0));
        assertEquals(0, ActivityService.levelForXp(99));
        assertEquals(1, ActivityService.levelForXp(100));
        assertEquals(1, ActivityService.levelForXp(254));
        assertEquals(2, ActivityService.levelForXp(255));
        for (int level = 0; level < 60; level++) {
            assertEquals(level, ActivityService.levelForXp(ActivityService.totalXpForLevel(level)), "nível " + level);
        }
    }

    @Test
    void progressBar() {
        assertEquals("▱▱▱▱▱▱▱▱▱▱", ActivityService.progressBar(0, 100, 10));
        assertEquals("▰▰▰▰▰▱▱▱▱▱", ActivityService.progressBar(50, 100, 10));
        assertEquals("▰▰▰▰▰▰▰▰▰▰", ActivityService.progressBar(100, 100, 10));
    }

    @Test
    void parsesBrazilianDates() {
        ZoneId zone = ZoneId.of("America/Sao_Paulo");
        Optional<OffsetDateTime> full = EventService.parseDate("25/12/2030 20:00", zone);
        assertTrue(full.isPresent());
        assertEquals(2030, full.get().getYear());
        assertEquals(20, full.get().getHour());
        assertEquals(-3 * 3600, full.get().getOffset().getTotalSeconds());

        Optional<OffsetDateTime> shortForm = EventService.parseDate("5/1 9:30", zone);
        assertTrue(shortForm.isPresent());
        assertEquals(5, shortForm.get().getDayOfMonth());
        assertEquals(1, shortForm.get().getMonthValue());
        assertEquals(9, shortForm.get().getHour());

        assertTrue(EventService.parseDate("amanhã às 8", zone).isEmpty());
        assertTrue(EventService.parseDate("32/13/2030 25:00", zone).isEmpty());
    }
}
