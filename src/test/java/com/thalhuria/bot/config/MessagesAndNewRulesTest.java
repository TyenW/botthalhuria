package com.thalhuria.bot.config;

import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MessagesAndNewRulesTest {

    @Test
    void everyMessageKeyUsedInCodeExists() throws Exception {
        Properties props = new Properties();
        try (var in = getClass().getResourceAsStream("/messages.properties")) {
            assertNotNull(in, "messages.properties deve existir");
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        Pattern call = Pattern.compile("msg\\([^,]+, \"([a-z0-9.]+)\"");
        Pattern toggleKeys = Pattern.compile("\"(tempvoice\\.[a-z.]+)\"");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String code = Files.readString(file);
                for (Pattern p : List.of(call, toggleKeys)) {
                    Matcher m = p.matcher(code);
                    while (m.find()) {
                        if (!props.containsKey(m.group(1))) missing.add(m.group(1) + " (" + file.getFileName() + ")");
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "Chaves sem texto em messages.properties: " + missing);
    }

    @Test
    void projectTemplateHasOwnerAndAdminRoles() {
        ServerTemplate template = ServerTemplate.load("server-template.json");
        assertEquals(1, template.getRoles().stream().filter(RoleTemplate::isOwnerRole).count());
        assertTrue(template.getRoles().stream().anyMatch(RoleTemplate::isAdminRole));
        // o dono fica no topo da hierarquia
        assertTrue(template.getRoles().get(0).isOwnerRole());
    }

    @Test
    void validatesNewSections() {
        ServerTemplate template = ServerTemplate.load("server-template.json");
        template.getLeveling().getRewards().add(reward(10, "ROLE_FANTASMA"));
        ShopItem item = new ShopItem();
        item.setKey("x");
        item.setName("X");
        item.setRoleKey("ROLE_INEXISTENTE");
        template.getEconomy().getShop().add(item);
        WarnThreshold threshold = new WarnThreshold();
        threshold.setAction("EXPLODIR");
        template.getModeration().getWarnThresholds().add(threshold);
        ChannelTemplate counter = new ChannelTemplate("CHAN_CONTADOR_RUIM", "sem valor", "TEXT", "");
        counter.setCounter("MEMBERS");
        template.getCategories().get(0).getChannels().add(counter);

        List<String> problems = template.validate();
        assertTrue(problems.stream().anyMatch(p -> p.contains("ROLE_FANTASMA")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("ROLE_INEXISTENTE")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("EXPLODIR")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("{value}")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("contadores devem ser canais VOICE")), problems.toString());
    }

    private static LevelReward reward(int level, String key) {
        LevelReward r = new LevelReward();
        r.setLevel(level);
        r.setRoleKey(key);
        return r;
    }
}
