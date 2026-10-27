package com.thalhuria.bot.service;

import com.thalhuria.bot.config.Palette;
import com.thalhuria.bot.config.TicketConfig;
import com.thalhuria.bot.config.TicketType;
import com.thalhuria.bot.database.ServerMapRepository;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tickets de atendimento: cada membro pode ter um ticket aberto por vez, num canal privado visível
 * apenas para ele, a staff do tipo de ticket e o bot. O dono e o tipo ficam no tópico do canal
 * ("ticket:ID:TIPO"), então nada precisa ser persistido. Ao fechar, a conversa vira um arquivo HTML.
 */
public class TicketService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TicketService.class);
    public static final String OPEN_BUTTON_PREFIX = "thal:ticket:open";
    public static final String CLOSE_BUTTON_ID = "thal:ticket:close";
    private static final String TOPIC_PREFIX = "ticket:";

    private static final EnumSet<Permission> PARTICIPANT_PERMS = EnumSet.of(
            Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY,
            Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_ADD_REACTION);

    private final TemplateService templateService;
    private final ServerMapRepository repository;
    private final AuditLogService auditLogService;
    private final TranscriptService transcriptService;

    public TicketService(TemplateService templateService, ServerMapRepository repository,
                         AuditLogService auditLogService, TranscriptService transcriptService) {
        this.templateService = templateService;
        this.repository = repository;
        this.auditLogService = auditLogService;
        this.transcriptService = transcriptService;
    }

    public String open(Member member) {
        return open(member, null);
    }

    /**
     * Abre (ou reaproveita) o ticket do membro. Bloqueante: chamar fora da thread de eventos do JDA.
     *
     * @param typeKey tipo de ticket (tickets.types[].key) ou null para o ticket padrão
     * @return mensagem efêmera para o membro
     */
    public String open(Member member, String typeKey) {
        Guild guild = member.getGuild();
        TicketConfig config = templateService.get(guild).getTickets();
        if (!config.isEnabled()) return templateService.msg(guild, "ticket.disabled");

        Category category = resolveCategory(guild, config);
        if (category == null) return templateService.msg(guild, "ticket.no.category");

        Optional<TextChannel> existing = findTicketOf(category, member.getId());
        if (existing.isPresent()) {
            return templateService.msg(guild, "ticket.already", "channel", existing.get().getAsMention());
        }

        Optional<TicketType> type = findType(config, typeKey);
        String prefix = type.map(TicketType::getChannelPrefix).filter(p -> p != null && !p.isBlank()).orElse(config.getChannelPrefix());
        String label = type.map(TicketType::getLabel).orElse("Ticket");
        String name = prefix + sanitize(member.getUser().getName());

        var action = category.createTextChannel(name.length() > 100 ? name.substring(0, 100) : name)
                .setTopic(TOPIC_PREFIX + member.getId() + ":" + type.map(TicketType::getKey).orElse("") + " • " + label + " de " + member.getEffectiveName())
                .addRolePermissionOverride(guild.getPublicRole().getIdLong(), EnumSet.noneOf(Permission.class), EnumSet.of(Permission.VIEW_CHANNEL))
                .addMemberPermissionOverride(member.getIdLong(), PARTICIPANT_PERMS, EnumSet.noneOf(Permission.class))
                .addMemberPermissionOverride(guild.getSelfMember().getIdLong(),
                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MANAGE_CHANNEL,
                                Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_HISTORY, Permission.MESSAGE_ATTACH_FILES),
                        EnumSet.noneOf(Permission.class));

        List<Role> staff = staffRoles(guild, config, type.orElse(null));
        for (Role role : staff) {
            action = action.addRolePermissionOverride(role.getIdLong(), EnumSet.copyOf(PARTICIPANT_PERMS), EnumSet.noneOf(Permission.class));
        }
        TextChannel channel = action.complete();

        Map<String, String> vars = Map.of("user", member.getAsMention(), "username", member.getEffectiveName(), "type", label);
        String message = type.map(TicketType::getOpenMessage).filter(m -> m != null && !m.isBlank()).orElse(config.getOpenMessage());
        EmbedBuilder embed = templateService.brandedEmbed(guild)
                .setTitle(type.map(t -> (t.getEmoji() != null ? t.getEmoji() + " " : "") + t.getLabel())
                        .orElse(templateService.render(guild, config.getOpenTitle(), vars)))
                .setDescription(templateService.render(guild, message, vars))
                .setThumbnail(member.getUser().getEffectiveAvatarUrl());

        String pings = member.getAsMention() + staff.stream().map(r -> " " + r.getAsMention()).reduce("", String::concat);
        channel.sendMessage(pings)
                .setEmbeds(embed.build())
                .setActionRow(Button.danger(CLOSE_BUTTON_ID, templateService.msg(guild, "ticket.close.button")).withEmoji(Emoji.fromUnicode("🔒")))
                .queue();

        auditLogService.logEvent(guild, "🎫 Ticket Aberto",
                member.getAsMention() + " abriu " + channel.getAsMention() + " (" + label + ")", AuditLogService.COLOR_INFO);
        LOGGER.info("Ticket #{} ({}) aberto por {}", channel.getName(), label, member.getEffectiveName());
        return templateService.msg(guild, "ticket.created", "channel", channel.getAsMention());
    }

    /**
     * Fecha o ticket se quem clicou for o dono, staff ou administrador. A transcrição e a exclusão
     * do canal rodam em segundo plano.
     *
     * @return mensagem de resposta, ou vazio se o canal não é um ticket
     */
    public Optional<String> close(Member member, TextChannel channel) {
        String owner = ownerOf(channel);
        if (owner == null) return Optional.empty();
        Guild guild = member.getGuild();

        TicketConfig config = templateService.get(guild).getTickets();
        Optional<TicketType> type = findType(config, typeOf(channel));
        boolean isStaff = member.hasPermission(Permission.ADMINISTRATOR)
                || staffRoles(guild, config, type.orElse(null)).stream().anyMatch(member.getRoles()::contains);
        if (!isStaff && !member.getId().equals(owner)) {
            return Optional.of(templateService.msg(guild, "ticket.close.denied"));
        }

        int delay = Math.max(0, config.getCloseDelaySeconds());
        CompletableFuture.runAsync(() -> {
            byte[] transcript = null;
            if (config.isTranscripts()) {
                try {
                    transcript = transcriptService.build(channel);
                } catch (Exception e) {
                    LOGGER.warn("Falha ao gerar transcrição de #{}: {}", channel.getName(), e.getMessage());
                }
            }
            deliverTranscript(guild, channel, owner, member, transcript, config.isDmTranscript());
            channel.delete().reason("Ticket fechado por " + member.getUser().getName()).queueAfter(delay, TimeUnit.SECONDS);
        });
        return Optional.of(templateService.msg(guild, "ticket.closing", "seconds", delay));
    }

    private void deliverTranscript(Guild guild, TextChannel channel, String ownerId, Member closer, byte[] transcript, boolean dm) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔒 Ticket Fechado")
                .setColor(Palette.RUBY)
                .addField("Ticket", "#" + channel.getName(), true)
                .addField("Aberto por", "<@" + ownerId + ">", true)
                .addField("Fechado por", closer.getAsMention(), true)
                .setTimestamp(java.time.Instant.now());

        String fileName = "ticket-" + sanitize(channel.getName()) + ".html";
        auditLogService.findLogChannel(guild).ifPresent(log -> {
            var action = log.sendMessageEmbeds(embed.build());
            if (transcript != null) action = action.addFiles(FileUpload.fromData(transcript, fileName));
            action.queue(null, err -> LOGGER.warn("Falha ao enviar transcrição: {}", err.getMessage()));
        });

        if (dm && transcript != null) {
            guild.getJDA().retrieveUserById(ownerId)
                    .flatMap(User::openPrivateChannel)
                    .flatMap(pc -> pc.sendMessage(templateService.msg(guild, "ticket.transcript.dm", "server", guild.getName()))
                            .addFiles(FileUpload.fromData(transcript, fileName)))
                    .queue(null, err -> { });
        }
    }

    public List<TicketType> types(Guild guild) {
        return new ArrayList<>(templateService.get(guild).getTickets().getTypes());
    }

    private Category resolveCategory(Guild guild, TicketConfig config) {
        Optional<Category> configured = templateService.category(guild, config.getCategoryKey());
        if (configured.isPresent()) return configured.get();
        return repository.getDiscordId(guild.getId(), "METADATA", "INDIVIDUAL_ROOMS_CATEGORY")
                .map(guild::getCategoryById)
                .orElse(null);
    }

    private List<Role> staffRoles(Guild guild, TicketConfig config, TicketType type) {
        List<String> keys = type != null && !type.getStaffRoleKeys().isEmpty() ? type.getStaffRoleKeys() : config.getStaffRoleKeys();
        return keys.stream()
                .map(key -> templateService.role(guild, key))
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<TicketType> findType(TicketConfig config, String key) {
        if (key == null || key.isBlank()) return Optional.empty();
        return config.getTypes().stream().filter(t -> key.equalsIgnoreCase(t.getKey())).findFirst();
    }

    private static Optional<TextChannel> findTicketOf(Category category, String memberId) {
        return category.getTextChannels().stream()
                .filter(ch -> memberId.equals(ownerOf(ch)))
                .findFirst();
    }

    /** Tópico: "ticket:<dono>:<tipo> • ..." (o tipo pode estar vazio). Também aceita o formato antigo "ticket:<dono> • ...". */
    private static String[] topicParts(TextChannel channel) {
        String topic = channel.getTopic();
        if (topic == null || !topic.startsWith(TOPIC_PREFIX)) return null;
        String head = topic.substring(TOPIC_PREFIX.length());
        int space = head.indexOf(' ');
        if (space > 0) head = head.substring(0, space);
        return head.split(":", 2);
    }

    private static String ownerOf(TextChannel channel) {
        String[] parts = topicParts(channel);
        return parts == null ? null : parts[0];
    }

    private static String typeOf(TextChannel channel) {
        String[] parts = topicParts(channel);
        return parts == null || parts.length < 2 ? null : parts[1];
    }

    private static String sanitize(String username) {
        String clean = username.toLowerCase().replaceAll("[^a-z0-9-]", "");
        return clean.isBlank() ? "membro" : clean;
    }
}
