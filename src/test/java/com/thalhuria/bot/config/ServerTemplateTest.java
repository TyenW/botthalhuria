package com.thalhuria.bot.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServerTemplateTest {

    @Test
    void testDefaultTemplateStructure() {
        ServerTemplate template = ServerTemplate.createDefault();
        assertNotNull(template);
        assertNotNull(template.getRoles());
        assertFalse(template.getRoles().isEmpty());

        // Verificar cargos essenciais
        List<String> roleNames = template.getRoles().stream().map(RoleTemplate::getName).toList();
        assertTrue(roleNames.contains("Admin"), "Deve conter o cargo Admin");
        assertTrue(roleNames.contains("Moderador"), "Deve conter o cargo Moderador");
        assertTrue(roleNames.contains("Membro"), "Deve conter o cargo Membro");
        assertTrue(roleNames.contains("Visitante"), "Deve conter o cargo Visitante");
        assertTrue(roleNames.contains("Bot"), "Deve conter o cargo Bot");

        // Verificar categorias essenciais
        List<String> catNames = template.getCategories().stream().map(CategoryTemplate::getName).toList();
        assertTrue(catNames.stream().anyMatch(n -> n.contains("Informações")), "Deve conter categoria Informações");
        assertTrue(catNames.stream().anyMatch(n -> n.contains("Geral")), "Deve conter categoria Geral");
        assertTrue(catNames.stream().anyMatch(n -> n.contains("Voz")), "Deve conter categoria Voz");
        assertTrue(catNames.stream().anyMatch(n -> n.contains("Restrita") || n.contains("Admin")), "Deve conter categoria Restrita");
        assertTrue(catNames.stream().anyMatch(n -> n.contains("Logs")), "Deve conter categoria Logs");
        assertTrue(catNames.stream().anyMatch(n -> n.contains("Salas Individuais")), "Deve conter categoria Salas Individuais");

        // Verificar canal de logs do sistema
        boolean hasLogChannel = template.getCategories().stream()
                .flatMap(cat -> cat.getChannels().stream())
                .anyMatch(ChannelTemplate::isSystemLogChannel);
        assertTrue(hasLogChannel, "Deve haver ao menos um canal marcado como isSystemLogChannel");
    }

    @Test
    void testLoadTemplateFromResource() {
        ServerTemplate template = ServerTemplate.load("inexistente.json");
        assertNotNull(template);
        assertFalse(template.getRoles().isEmpty());
    }
}
