package com.thalhuria.bot.config;

import net.dv8tion.jda.api.Permission;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TemplateValidationTest {

    @Test
    void projectTemplateIsValid() {
        ServerTemplate template = ServerTemplate.load("server-template.json");
        List<String> problems = template.validate();
        assertTrue(problems.isEmpty(), "server-template.json tem problemas:\n" + String.join("\n", problems));
    }

    @Test
    void embeddedTemplateIsValid() {
        ServerTemplate template = ServerTemplate.load("inexistente.json");
        assertTrue(template.validate().isEmpty(), "default-server-template.json tem problemas: " + template.validate());
    }

    @Test
    void projectTemplateUsesNewFeatures() {
        ServerTemplate template = ServerTemplate.load("server-template.json");
        assertEquals("Thaluria", template.getBranding().getName());
        assertEquals("ROLE_MEMBER", template.getOnboarding().getVerifiedRoleKey());
        assertFalse(template.selfAssignableRoles().isEmpty());
        assertTrue(template.allChannels().anyMatch(ChannelTemplate::isJoinToCreate));
        assertTrue(template.allChannels().anyMatch(c -> c.getPanel() != null && "VERIFY".equals(c.getPanel().getType())));
        assertTrue(template.allChannels().anyMatch(c -> c.getPanel() != null && "TICKET".equals(c.getPanel().getType())));
    }

    @Test
    void validationCatchesBrokenReferences() {
        ServerTemplate template = ServerTemplate.load("server-template.json");
        template.getOnboarding().setVerifiedRoleKey("ROLE_QUE_NAO_EXISTE");
        template.getCategories().get(0).getPermissionOverrides().add(
                new PermissionRule("Cargo Fantasma", List.of("PERMISSAO_INVENTADA"), List.of()));
        template.getCategories().get(0).getChannels().get(0).setTopic("Veja {channel:CHAN_FANTASMA}");

        List<String> problems = template.validate();
        assertTrue(problems.stream().anyMatch(p -> p.contains("ROLE_QUE_NAO_EXISTE")));
        assertTrue(problems.stream().anyMatch(p -> p.contains("Cargo Fantasma")));
        assertTrue(problems.stream().anyMatch(p -> p.contains("PERMISSAO_INVENTADA")));
        assertTrue(problems.stream().anyMatch(p -> p.contains("CHAN_FANTASMA")));
    }

    @Test
    void invalidJsonFailsLoudly() throws Exception {
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("template", ".json");
        java.nio.file.Files.writeString(tmp, "{ \"roles\": [ { \"keyy\": 1 } ] }");
        assertThrows(IllegalStateException.class, () -> ServerTemplate.load(tmp.toString()));
        java.nio.file.Files.deleteIfExists(tmp);
    }

    @Test
    void permissionParserAcceptsDiscordAndJdaNames() {
        assertEquals(Permission.MESSAGE_MANAGE, PermissionParser.parse("MANAGE_MESSAGES").orElseThrow());
        assertEquals(Permission.MESSAGE_MANAGE, PermissionParser.parse("message_manage").orElseThrow());
        assertEquals(Permission.MESSAGE_SEND, PermissionParser.parse("SEND_MESSAGES").orElseThrow());
        assertTrue(PermissionParser.parse("NAO_EXISTE").isEmpty());
        assertTrue(PermissionParser.parse("UNKNOWN").isEmpty());
    }
}
