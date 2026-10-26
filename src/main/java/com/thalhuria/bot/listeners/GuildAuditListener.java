package com.thalhuria.bot.listeners;

import com.thalhuria.bot.config.ServerTemplate;
import com.thalhuria.bot.service.AuditLogService;
import com.thalhuria.bot.service.TemplateService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.role.RoleCreateEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Listener de auditoria automática em tempo real.
 * Escuta eventos do servidor Discord e encaminha embeds formatados para o canal #logs-do-sistema.
 */
public class GuildAuditListener extends ListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildAuditListener.class);
    private final AuditLogService auditLogService;
    private final TemplateService templateService;

    public GuildAuditListener(AuditLogService auditLogService, TemplateService templateService) {
        this.auditLogService = auditLogService;
        this.templateService = templateService;
    }

    /**
     * Cargos que os membros trocam sozinhos (menu de cargos, recompensas de nível, loja) não precisam de auditoria:
     * conforme o servidor cresce, eles enchem o canal de logs.
     */
    private boolean onlyCosmetic(Guild guild, List<Role> roles) {
        ServerTemplate template = templateService.get(guild);
        Set<String> keys = new HashSet<>();
        template.selfAssignableRoles().forEach(r -> keys.add(r.getKey()));
        template.getLeveling().getRewards().forEach(r -> keys.add(r.getRoleKey()));
        template.getEconomy().getShop().forEach(i -> keys.add(i.getRoleKey()));
        template.getInvites().getTiers().forEach(t -> keys.add(t.getRoleKey()));
        Set<String> ids = new HashSet<>();
        for (String key : keys) templateService.role(guild, key).ifPresent(r -> ids.add(r.getId()));
        return roles.stream().allMatch(r -> ids.contains(r.getId()));
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        LOGGER.info("Membro entrou: {} na guilda {}", event.getUser().getName(), event.getGuild().getName());
        auditLogService.logMemberJoin(event.getMember());
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        LOGGER.info("Membro saiu: {} da guilda {}", event.getUser().getName(), event.getGuild().getName());
        auditLogService.logMemberLeave(event.getGuild(), event.getUser());
    }

    @Override
    public void onGuildMemberRoleAdd(@NotNull GuildMemberRoleAddEvent event) {
        if (onlyCosmetic(event.getGuild(), event.getRoles())) return;
        auditLogService.findLogChannel(event.getGuild()).ifPresent(channel -> {
            String roleNames = event.getRoles().stream()
                    .map(Role::getAsMention)
                    .collect(Collectors.joining(", "));

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🛡️ Cargo(s) Adicionado(s) a Membro")
                    .setColor(new Color(0x3498DB))
                    .setThumbnail(event.getUser().getEffectiveAvatarUrl())
                    .addField("Membro", event.getMember().getAsMention() + " (`" + event.getMember().getId() + "`)", false)
                    .addField("Cargo(s) Atribuído(s)", roleNames, false)
                    .setFooter("Auditoria de Permissões", event.getGuild().getJDA().getSelfUser().getEffectiveAvatarUrl())
                    .setTimestamp(Instant.now());

            channel.sendMessageEmbeds(embed.build()).queue();
        });
    }

    @Override
    public void onGuildMemberRoleRemove(@NotNull GuildMemberRoleRemoveEvent event) {
        if (onlyCosmetic(event.getGuild(), event.getRoles())) return;
        auditLogService.findLogChannel(event.getGuild()).ifPresent(channel -> {
            String roleNames = event.getRoles().stream()
                    .map(Role::getName)
                    .collect(Collectors.joining(", "));

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🛡️ Cargo(s) Removido(s) de Membro")
                    .setColor(new Color(0xE67E22))
                    .setThumbnail(event.getUser().getEffectiveAvatarUrl())
                    .addField("Membro", event.getMember().getAsMention() + " (`" + event.getMember().getId() + "`)", false)
                    .addField("Cargo(s) Removido(s)", roleNames, false)
                    .setFooter("Auditoria de Permissões", event.getGuild().getJDA().getSelfUser().getEffectiveAvatarUrl())
                    .setTimestamp(Instant.now());

            channel.sendMessageEmbeds(embed.build()).queue();
        });
    }

    @Override
    public void onChannelCreate(@NotNull ChannelCreateEvent event) {
        if (auditLogService.isSuppressed(event.getGuild())) return;
        String categoryName = null;
        if (event.getChannel() instanceof net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel catChan && catChan.getParentCategory() != null) {
            categoryName = catChan.getParentCategory().getName();
        }
        auditLogService.logChannelLifecycle(
                event.getGuild(),
                event.getChannel().getName(),
                event.getChannel().getType().name(),
                event.getChannel().getId(),
                categoryName,
                "CRIADO",
                null
        );
    }

    @Override
    public void onChannelDelete(@NotNull ChannelDeleteEvent event) {
        if (auditLogService.isSuppressed(event.getGuild())) return;
        String categoryName = null;
        if (event.getChannel() instanceof net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel catChan && catChan.getParentCategory() != null) {
            categoryName = catChan.getParentCategory().getName();
        }
        auditLogService.logChannelLifecycle(
                event.getGuild(),
                event.getChannel().getName(),
                event.getChannel().getType().name(),
                event.getChannel().getId(),
                categoryName,
                "EXCLUÍDO",
                null
        );
    }

    @Override
    public void onRoleCreate(@NotNull RoleCreateEvent event) {
        if (auditLogService.isSuppressed(event.getGuild())) return;
        auditLogService.logRoleLifecycle(event.getGuild(), event.getRole(), "CRIADO");
    }

    @Override
    public void onRoleDelete(@NotNull RoleDeleteEvent event) {
        if (auditLogService.isSuppressed(event.getGuild())) return;
        auditLogService.logRoleLifecycle(event.getGuild(), event.getRole(), "EXCLUÍDO");
    }
}
