package com.thalhuria.bot.service;

import com.thalhuria.bot.config.OnboardingConfig;
import com.thalhuria.bot.config.RoleTemplate;
import com.thalhuria.bot.config.ServerTemplate;
import com.thalhuria.bot.config.WelcomeCardConfig;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Jornada do novo membro: cargo automático na entrada, mensagem de boas-vindas,
 * verificação por botão e autoatribuição de cargos pelo menu.
 */
public class OnboardingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnboardingService.class);

    private final TemplateService templateService;
    private final AuditLogService auditLogService;
    private final WelcomeCardRenderer cardRenderer;
    private final InviteService inviteService;

    public record RoleChange(List<Role> added, List<Role> removed) {}

    public OnboardingService(TemplateService templateService, AuditLogService auditLogService,
                             WelcomeCardRenderer cardRenderer, InviteService inviteService) {
        this.templateService = templateService;
        this.auditLogService = auditLogService;
        this.cardRenderer = cardRenderer;
        this.inviteService = inviteService;
    }

    public void handleJoin(Member member) {
        handleJoin(member, Optional.empty());
    }

    /**
     * @param source por qual convite o membro entrou (do {@link InviteService}); vazio se não rastreado
     */
    public void handleJoin(Member member, Optional<InviteService.JoinSource> source) {
        ServerTemplate template = templateService.get(member.getGuild());
        OnboardingConfig config = template.getOnboarding();
        if (!config.isEnabled()) return;
        Guild guild = member.getGuild();

        String roleKey = member.getUser().isBot() ? config.getBotRoleKey() : config.getAutoRoleKey();
        templateService.role(guild, roleKey).ifPresent(role -> {
            if (guild.getSelfMember().canInteract(role)) {
                guild.addRoleToMember(member, role).reason("Cargo automático de entrada").queue(
                        ok -> LOGGER.debug("Cargo automático {} aplicado em {}", role.getName(), member.getEffectiveName()),
                        err -> LOGGER.warn("Falha ao aplicar cargo automático em {}: {}", member.getEffectiveName(), err.getMessage()));
            } else {
                LOGGER.warn("O cargo {} está acima do cargo do bot — mova o cargo do bot para o topo da hierarquia.", role.getName());
            }
        });

        if (member.getUser().isBot()) return;
        sendWelcome(member, config, config.isPingOnWelcome(), source.orElse(null));
        sendWelcomeDm(member, config);
    }

    /**
     * Mensagem privada de boas-vindas com o passo a passo e um botão de link para o canal de verificação.
     */
    private void sendWelcomeDm(Member member, OnboardingConfig config) {
        if (!config.isDmWelcome() || config.getDmMessage() == null || config.getDmMessage().isBlank()) return;
        Guild guild = member.getGuild();
        Map<String, String> vars = userVars(member);
        EmbedBuilder embed = templateService.brandedEmbed(guild)
                .setAuthor(guild.getName(), null, templateService.logoUrl(guild))
                .setTitle(templateService.render(guild, config.getDmTitle(), vars))
                .setDescription(templateService.render(guild, config.getDmMessage(), vars));
        embed.setThumbnail(templateService.logoUrl(guild));

        var verify = templateService.channel(guild, config.getDmButtonChannelKey())
                .or(() -> templateService.channel(guild, config.getWelcomeChannelKey()));
        member.getUser().openPrivateChannel()
                .flatMap(dm -> {
                    var action = dm.sendMessageEmbeds(embed.build());
                    if (verify.isPresent()) {
                        action = action.setActionRow(net.dv8tion.jda.api.interactions.components.buttons.Button.link(
                                verify.get().getJumpUrl(), templateService.msg(guild, "welcome.dm.button")));
                    }
                    return action;
                })
                .queue(null, err -> LOGGER.debug("DM de boas-vindas fechada para {}", member.getEffectiveName()));
    }

    /**
     * Reenvia as boas-vindas de um membro (comando /boas-vindas). Sempre marca o membro.
     *
     * @return mensagem para responder a quem executou
     */
    public CompletableFuture<String> resendWelcome(Member member, boolean includeDm) {
        Guild guild = member.getGuild();
        OnboardingConfig config = templateService.get(guild).getOnboarding();
        if (includeDm) sendWelcomeDm(member, config);
        InviteService.JoinSource source = inviteService == null ? null : inviteService.repository()
                .get(guild.getId(), member.getId())
                .map(j -> new InviteService.JoinSource(j.inviterId(), j.code(), false, false, j.valid(), 0))
                .orElse(null);
        return sendWelcome(member, config, true, source).thenApply(sent -> sent == null
                ? templateService.msg(guild, "welcome.resend.no.channel")
                : templateService.msg(guild, "welcome.resend.ok", "user", member.getAsMention(), "link", sent.getJumpUrl())
                  + (includeDm ? "\n" + templateService.msg(guild, "welcome.resend.dm") : ""));
    }

    /**
     * @return a mensagem enviada, ou null se o canal de boas-vindas não existe / o bot não pode escrever nele
     */
    private CompletableFuture<Message> sendWelcome(Member member, OnboardingConfig config, boolean ping, InviteService.JoinSource source) {
        Guild guild = member.getGuild();
        Optional<GuildMessageChannel> channel = templateService.channel(guild, config.getWelcomeChannelKey())
                .filter(GuildMessageChannel.class::isInstance)
                .map(GuildMessageChannel.class::cast);
        if (channel.isEmpty() || !channel.get().canTalk()) return CompletableFuture.completedFuture(null);

        Map<String, String> vars = new java.util.HashMap<>(userVars(member));
        vars.put("inviter", inviterText(guild, source));
        EmbedBuilder embed = templateService.brandedEmbed(guild)
                .setTitle(templateService.render(guild, config.getWelcomeTitle(), vars))
                .setDescription(templateService.render(guild, config.getWelcomeMessage(), vars))
                .setThumbnail(member.getUser().getEffectiveAvatarUrl());
        if (source != null && inviteService != null && inviteService.config(guild).isShowInWelcome()
                && (source.inviterId() != null || source.vanity())) {
            embed.addField(templateService.msg(guild, "invites.welcome.field"), vars.get("inviter"), true);
        }
        if (config.getWelcomeImageUrl() != null && !config.getWelcomeImageUrl().isBlank()) {
            embed.setImage(config.getWelcomeImageUrl());
        }

        WelcomeCardConfig card = templateService.get(guild).getWelcomeCard();
        if (!card.isEnabled()) {
            return send(channel.get(), embed, null, member, ping);
        }

        // O cartão baixa o avatar e desenha a imagem: fora da thread de eventos
        return CompletableFuture.supplyAsync(() -> {
            try {
                return cardRenderer.render(card, templateService.brandColor(guild),
                        member.getUser().getEffectiveAvatarUrl(),
                        member.getEffectiveName(),
                        templateService.render(guild, card.getTitle(), vars),
                        templateService.render(guild, card.getSubtitle(), vars),
                        templateService.get(guild).getBranding().getFooter(),
                        member.getIdLong());
            } catch (Exception e) {
                LOGGER.warn("Falha ao gerar cartão de boas-vindas: {}", e.getMessage());
                return null;
            }
        }).thenCompose(png -> {
            if (png != null) embed.setThumbnail(null).setImage("attachment://boas-vindas.png");
            return send(channel.get(), embed, png, member, ping);
        });
    }

    private CompletableFuture<Message> send(GuildMessageChannel channel, EmbedBuilder embed, byte[] png, Member member, boolean ping) {
        var action = channel.sendMessageEmbeds(embed.build());
        if (png != null) action = action.addFiles(FileUpload.fromData(png, "boas-vindas.png"));
        if (ping) {
            action = action.setContent(member.getAsMention())
                    .setAllowedMentions(java.util.EnumSet.of(Message.MentionType.USER));
        }
        return action.submit().exceptionally(err -> {
            LOGGER.warn("Falha ao enviar boas-vindas: {}", err.getMessage());
            return null;
        });
    }

    /**
     * Botão ROLE de painel: adiciona o cargo se o membro não tem, remove se já tem.
     */
    public String toggleRole(Member member, String roleKey) {
        Guild guild = member.getGuild();
        Optional<Role> role = templateService.role(guild, roleKey);
        if (role.isEmpty() || !guild.getSelfMember().canInteract(role.get())) {
            return templateService.msg(guild, "role.toggle.unavailable");
        }
        if (member.getRoles().contains(role.get())) {
            guild.removeRoleFromMember(member, role.get()).reason("Botão de painel").complete();
            return templateService.msg(guild, "role.toggle.removed", "role", role.get().getAsMention());
        }
        guild.addRoleToMember(member, role.get()).reason("Botão de painel").complete();
        return templateService.msg(guild, "role.toggle.added", "role", role.get().getAsMention());
    }

    /**
     * Executado pelo botão do painel VERIFY. Bloqueante: chamar fora da thread de eventos do JDA.
     *
     * @return mensagem para responder ao membro (efêmera)
     */
    public String verify(Member member) {
        Guild guild = member.getGuild();
        OnboardingConfig config = templateService.get(guild).getOnboarding();
        Optional<Role> verified = templateService.role(guild, config.getVerifiedRoleKey());
        if (verified.isEmpty()) {
            return "⚠️ O cargo de verificação não está configurado. Avise a staff para rodar `/sync-server`.";
        }
        if (member.getRoles().contains(verified.get())) {
            return "Você já está verificado(a)! 😉";
        }

        List<Role> remove = new ArrayList<>();
        if (config.isRemoveAutoRoleOnVerify()) {
            templateService.role(guild, config.getAutoRoleKey())
                    .filter(member.getRoles()::contains)
                    .ifPresent(remove::add);
        }
        guild.modifyMemberRoles(member, List.of(verified.get()), remove).reason("Verificação pelo painel").complete();
        if (inviteService != null) {
            try {
                inviteService.onVerify(member);
            } catch (Exception e) {
                LOGGER.warn("Falha ao processar convite na verificação de {}: {}", member.getEffectiveName(), e.getMessage());
            }
        }
        auditLogService.logEvent(guild, "✅ Membro Verificado",
                member.getAsMention() + " (`" + member.getId() + "`) concluiu a verificação.", AuditLogService.COLOR_MEMBER);

        return templateService.render(guild, config.getVerifiedMessage(), userVars(member));
    }

    /**
     * Aplica a seleção do menu de cargos: adiciona os escolhidos e remove os demais cargos autoatribuíveis.
     */
    public RoleChange applySelfRoles(Member member, List<String> selectedKeys) {
        Guild guild = member.getGuild();
        List<Role> add = new ArrayList<>();
        List<Role> remove = new ArrayList<>();

        for (RoleTemplate roleTpl : templateService.get(guild).selfAssignableRoles()) {
            Optional<Role> role = templateService.role(guild, roleTpl.getKey());
            if (role.isEmpty() || !guild.getSelfMember().canInteract(role.get())) continue;
            boolean selected = selectedKeys.stream().anyMatch(k -> k.equalsIgnoreCase(roleTpl.getKey()));
            boolean has = member.getRoles().contains(role.get());
            if (selected && !has) add.add(role.get());
            if (!selected && has) remove.add(role.get());
        }

        if (!add.isEmpty() || !remove.isEmpty()) {
            guild.modifyMemberRoles(member, add, remove).reason("Menu de cargos").complete();
        }
        return new RoleChange(add, remove);
    }

    private String inviterText(Guild guild, InviteService.JoinSource source) {
        if (source == null) return templateService.msg(guild, "invites.welcome.unknown");
        if (source.vanity()) return templateService.msg(guild, "invites.welcome.vanity");
        if (source.inviterId() == null) return templateService.msg(guild, "invites.welcome.unknown");
        return source.inviterCount() > 0
                ? templateService.msg(guild, "invites.welcome.by.count", "inviter", "<@" + source.inviterId() + ">", "count", source.inviterCount())
                : templateService.msg(guild, "invites.welcome.by", "inviter", "<@" + source.inviterId() + ">");
    }

    private static Map<String, String> userVars(Member member) {
        return Map.of(
                "user", member.getAsMention(),
                "username", member.getEffectiveName(),
                "count", String.valueOf(member.getGuild().getMemberCount())
        );
    }
}
