package com.thalhuria.bot.service;

import com.thalhuria.bot.database.DatabaseManager;
import com.thalhuria.bot.database.InviteRepository;
import com.thalhuria.bot.service.InviteService.Snapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InviteTrackingTest {

    @TempDir
    Path tmp;

    private static Snapshot inv(String code, String inviter, int uses, int max) {
        return new Snapshot(code, inviter, uses, max);
    }

    @Test
    void detectsInviteWhoseUsesIncreased() {
        Map<String, Snapshot> before = Map.of("abc", inv("abc", "ana", 3, 0), "xyz", inv("xyz", "bia", 7, 0));
        Map<String, Snapshot> after = Map.of("abc", inv("abc", "ana", 4, 0), "xyz", inv("xyz", "bia", 7, 0));
        Optional<Snapshot> used = InviteService.detect(before, after, List.of());
        assertEquals("ana", used.orElseThrow().inviterId());
    }

    @Test
    void detectsInviteCreatedAfterCacheLoad() {
        // Convite novo que ainda não estava no cache e já tem 1 uso
        Optional<Snapshot> used = InviteService.detect(Map.of(), Map.of("new", inv("new", "caio", 1, 0)), List.of());
        assertEquals("caio", used.orElseThrow().inviterId());
    }

    @Test
    void detectsSingleUseInviteThatDisappeared() {
        Map<String, Snapshot> before = Map.of("once", inv("once", "duda", 0, 1), "perm", inv("perm", "ana", 2, 0));
        Map<String, Snapshot> after = Map.of("perm", inv("perm", "ana", 2, 0));
        assertEquals("duda", InviteService.detect(before, after, List.of()).orElseThrow().inviterId());

        // ...ou que já foi removido do cache pelo evento de exclusão
        Map<String, Snapshot> withoutIt = Map.of("perm", inv("perm", "ana", 2, 0));
        assertEquals("duda", InviteService.detect(withoutIt, withoutIt, List.of(inv("once", "duda", 0, 1))).orElseThrow().inviterId());
    }

    @Test
    void refusesToGuessWhenAmbiguous() {
        Map<String, Snapshot> before = Map.of("a", inv("a", "ana", 1, 0), "b", inv("b", "bia", 1, 0));
        Map<String, Snapshot> after = Map.of("a", inv("a", "ana", 2, 0), "b", inv("b", "bia", 2, 0));
        assertTrue(InviteService.detect(before, after, List.of()).isEmpty());
        assertTrue(InviteService.detect(before, before, List.of()).isEmpty(), "nada mudou = desconhecido");
    }

    @Test
    void repositoryCountsOnlyValidVerifiedAndPresentInvites() {
        InviteRepository repo = new InviteRepository(new DatabaseManager(tmp.resolve("inv.db").toString()));
        repo.insert("g", "u1", "ana", "abc", true);
        repo.insert("g", "u2", "ana", "abc", true);
        repo.insert("g", "u3", "ana", "abc", false);   // conta nova
        repo.insert("g", "u4", "ana", "abc", true);
        repo.insert("g", "u5", "bia", "xyz", true);
        repo.markVerified("g", "u1");
        repo.markVerified("g", "u2");
        repo.markVerified("g", "u4");
        repo.setLeft("g", "u4", true);                 // saiu
        repo.markVerified("g", "u5");

        var verifiedOnly = repo.stats("g", "ana", true);
        assertEquals(4, verifiedOnly.total());
        assertEquals(2, verifiedOnly.counted());
        assertEquals(1, verifiedOnly.left());
        assertEquals(1, verifiedOnly.fake());
        assertEquals(0, verifiedOnly.pending());

        repo.insert("g", "u6", "ana", "abc", true);    // ainda não verificou
        assertEquals(1, repo.stats("g", "ana", true).pending());
        assertEquals(3, repo.stats("g", "ana", false).counted(), "sem exigir verificação, o pendente conta");

        // Reinserir quem já entrou não sobrescreve o convidador original
        repo.insert("g", "u1", "bia", "xyz", true);
        assertEquals("ana", repo.get("g", "u1").orElseThrow().inviterId());

        var top = repo.top("g", true, 10);
        assertEquals("ana", top.get(0).inviterId());
        assertEquals(2, top.get(0).counted());
        assertEquals("bia", top.get(1).inviterId());
    }
}
