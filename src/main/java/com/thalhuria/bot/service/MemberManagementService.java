package com.thalhuria.bot.service;

import com.thalhuria.bot.database.ServerMapRepository;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Serviço responsável por configurar novos membros, atribuir o cargo principal
 * e provisionar salas de texto privadas exclusivas na categoria de Salas Individuais.
 */
public class MemberManagementService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemberManagementService.class);
    private final ServerMapRepository repository;

    public record MemberConfigResult(
            boolean roleAssigned,
            Role role,
            TextChannel privateChannel,
            String details
    ) {}

    public MemberManagementService(ServerMapRepository repository) {
        this.repository = repository;
    }

    /**
     * Atribui o cargo principal de membro e, opcionalmente, cria uma sala privada na categoria designada.
     */
    public CompletableFuture<MemberConfigResult> configureMember(Guild guild, Member targetMember, boolean createPrivateRoom) {
        return CompletableFuture.supplyAsync(() -> {
            boolean roleAssigned = false;
            Role memberRole = resolveMemberRole(guild);

            // 1. Atribuir cargo principal ao membro selecionado
            if (memberRole != null) {
                if (!targetMember.getRoles().contains(memberRole)) {
                    try {
                        guild.addRoleToMember(targetMember, memberRole).complete();
                        roleAssigned = true;
                        LOGGER.info("Cargo {} atribuído com sucesso ao membro {}", memberRole.getName(), targetMember.getEffectiveName());
                    } catch (Exception e) {
                        LOGGER.error("Falha ao atribuir cargo {} ao membro {}: {}", memberRole.getName(), targetMember.getEffectiveName(), e.getMessage());
                    }
                } else {
                    roleAssigned = true;
                    LOGGER.debug("Membro {} já possui o cargo {}", targetMember.getEffectiveName(), memberRole.getName());
                }
            } else {
                LOGGER.warn("Cargo de membro principal não encontrado na guilda {}", guild.getName());
            }

            TextChannel privateChannel = null;

            // 2. Criação opcional de sala privada exclusiva
            if (createPrivateRoom) {
                try {
                    Category category = resolveOrPrepareRoomsCategory(guild);
                    String sanitizedName = "sala-" + targetMember.getUser().getName().toLowerCase().replaceAll("[^a-z0-9-]", "");
                    if (sanitizedName.length() > 32) {
                        sanitizedName = sanitizedName.substring(0, 32);
                    }

                    // Verificar se já existe canal com este nome na categoria
                    for (var ch : category.getTextChannels()) {
                        if (ch.getName().equalsIgnoreCase(sanitizedName)) {
                            privateChannel = ch;
                            break;
                        }
                    }

                    Role adminRole = resolveAdminRole(guild);

                    if (privateChannel == null) {
                        // Criar novo canal exclusivo com permissões restritas
                        var action = category.createTextChannel(sanitizedName)
                                .setTopic("Sala de atendimento privada exclusiva de " + targetMember.getEffectiveName())
                                // Travar para @everyone
                                .addRolePermissionOverride(guild.getPublicRole().getIdLong(),
                                        EnumSet.noneOf(Permission.class),
                                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND))
                                // Liberar acesso total para o membro selecionado
                                .addMemberPermissionOverride(targetMember.getIdLong(),
                                        EnumSet.of(
                                                Permission.VIEW_CHANNEL,
                                                Permission.MESSAGE_SEND,
                                                Permission.MESSAGE_HISTORY,
                                                Permission.MESSAGE_ATTACH_FILES,
                                                Permission.MESSAGE_EMBED_LINKS,
                                                Permission.MESSAGE_ADD_REACTION
                                        ),
                                        EnumSet.noneOf(Permission.class))
                                // Garantir acesso ao Bot
                                .addMemberPermissionOverride(guild.getSelfMember().getIdLong(),
                                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MANAGE_CHANNEL),
                                        EnumSet.noneOf(Permission.class));

                        // Se houver cargo de Admin, permitir acesso administrativo
                        if (adminRole != null) {
                            action.addRolePermissionOverride(adminRole.getIdLong(),
                                    EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY, Permission.MESSAGE_MANAGE),
                                    EnumSet.noneOf(Permission.class));
                        }

                        privateChannel = action.complete();
                        LOGGER.info("Sala privada #{} criada para o membro {}", sanitizedName, targetMember.getEffectiveName());

                        // Enviar mensagem de boas-vindas inicial na sala privada
                        sendWelcomeEmbedInPrivateRoom(privateChannel, targetMember, adminRole);
                    }
                } catch (Exception e) {
                    LOGGER.error("Erro ao criar sala privada para {}: {}", targetMember.getEffectiveName(), e.getMessage(), e);
                }
            }

            String details = String.format("Membro: %s | Cargo Atribuído: %s | Sala Privada: %s",
                    targetMember.getAsMention(),
                    memberRole != null ? memberRole.getName() : "Não definido",
                    privateChannel != null ? "#" + privateChannel.getName() : "Não solicitada");

            return new MemberConfigResult(roleAssigned, memberRole, privateChannel, details);
        });
    }

    private void sendWelcomeEmbedInPrivateRoom(TextChannel channel, Member targetMember, Role adminRole) {
        EmbedBuilder welcome = new EmbedBuilder()
                .setTitle("🚪 Bem-vindo(a) à sua Sala Privada!")
                .setColor(new Color(0x3498DB))
                .setThumbnail(targetMember.getUser().getEffectiveAvatarUrl())
                .setDescription("Olá " + targetMember.getAsMention() + "!\n" +
                        "Este é o seu canal de texto privado e seguro. Aqui você pode tirar dúvidas, " +
                        "receber orientações individuais ou interagir diretamente com a equipe administrativa.")
                .addField("🔒 Privacidade", "Apenas você e os administradores possuem visibilidade deste canal.", false)
                .setFooter("Sistema de Atendimento Privado • Bot Thaluria", channel.getGuild().getJDA().getSelfUser().getEffectiveAvatarUrl())
                .setTimestamp(Instant.now());

        String ping = targetMember.getAsMention() + (adminRole != null ? " " + adminRole.getAsMention() : "");
        channel.sendMessage(ping).setEmbeds(welcome.build()).queue();
    }

    private Role resolveMemberRole(Guild guild) {
        Optional<String> idOpt = repository.getDiscordId(guild.getId(), "METADATA", "MEMBER_ROLE");
        if (idOpt.isPresent()) {
            Role role = guild.getRoleById(idOpt.get());
            if (role != null) return role;
        }
        List<Role> roles = guild.getRolesByName("Membro", true);
        return roles.isEmpty() ? null : roles.get(0);
    }

    private Role resolveAdminRole(Guild guild) {
        Optional<String> idOpt = repository.getDiscordId(guild.getId(), "METADATA", "ADMIN_ROLE");
        if (idOpt.isPresent()) {
            Role role = guild.getRoleById(idOpt.get());
            if (role != null) return role;
        }
        List<Role> roles = guild.getRolesByName("Admin", true);
        return roles.isEmpty() ? null : roles.get(0);
    }

    private Category resolveOrPrepareRoomsCategory(Guild guild) {
        Optional<String> catIdOpt = repository.getDiscordId(guild.getId(), "METADATA", "INDIVIDUAL_ROOMS_CATEGORY");
        if (catIdOpt.isPresent()) {
            Category cat = guild.getCategoryById(catIdOpt.get());
            if (cat != null) return cat;
        }

        List<Category> byName = guild.getCategoriesByName("👤 Salas Individuais", true);
        if (!byName.isEmpty()) {
            return byName.get(0);
        }

        // Criar a categoria caso ela ainda não exista
        Category newCat = guild.createCategory("👤 Salas Individuais")
                .addRolePermissionOverride(guild.getPublicRole().getIdLong(),
                        EnumSet.noneOf(Permission.class),
                        EnumSet.of(Permission.VIEW_CHANNEL))
                .complete();

        repository.saveMapping(guild.getId(), "METADATA", "INDIVIDUAL_ROOMS_CATEGORY", newCat.getId(), newCat.getName());
        return newCat;
    }
}
