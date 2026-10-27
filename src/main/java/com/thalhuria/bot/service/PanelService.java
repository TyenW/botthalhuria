package com.thalhuria.bot.service;

import com.thalhuria.bot.config.ChannelTemplate;
import com.thalhuria.bot.config.PanelButton;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import com.thalhuria.bot.config.PanelTemplate;
import com.thalhuria.bot.config.RoleTemplate;
import com.thalhuria.bot.config.ServerTemplate;
import com.thalhuria.bot.database.ServerMapRepository;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Publica e mantém atualizados os painéis definidos no template (regras, verificação, menu de cargos, tickets...).
 * O ID de cada mensagem fica salvo no banco: em uma nova sincronização o bot edita a mensagem existente em vez de repostar.
 */
public class PanelService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PanelService.class);

    public static final String VERIFY_BUTTON_ID = "thal:verify";
    public static final String SELF_ROLES_MENU_ID = "thal:roles";
    public static final String TICKET_OPEN_BUTTON_ID = TicketService.OPEN_BUTTON_PREFIX;
    public static final String ROLE_BUTTON_PREFIX = "thal:role:";

    private final ServerMapRepository repository;
    private final TemplateService templateService;

    public PanelService(ServerMapRepository repository, TemplateService templateService) {
        this.repository = repository;
        this.templateService = templateService;
    }

    /**
     * Publica (ou edita) todos os painéis do template. Executa de forma bloqueante — chamar fora da thread do JDA.
     *
     * @return quantidade de painéis publicados ou atualizados
     */
    public int publishAll(Guild guild, ServerTemplate template) {
        int published = 0;
        for (ChannelTemplate chanTpl : template.allChannels().toList()) {
            if (chanTpl.getPanel() == null) continue;
            try {
                if (publish(guild, template, chanTpl)) published++;
            } catch (Exception e) {
                LOGGER.error("Falha ao publicar painel em {}: {}", chanTpl.getKey(), e.getMessage(), e);
            }
        }
        return published;
    }

    private boolean publish(Guild guild, ServerTemplate template, ChannelTemplate chanTpl) {
        Optional<String> channelId = repository.getDiscordId(guild.getId(), "CHANNEL", chanTpl.getKey());
        if (channelId.isEmpty()) return false;
        GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, channelId.get());
        if (channel == null) return false;

        PanelTemplate panel = chanTpl.getPanel();
        MessageEmbed embed = buildEmbed(guild, template, panel);
        List<LayoutComponent> components = buildComponents(guild, template, panel);

        Optional<String> messageId = repository.getDiscordId(guild.getId(), "PANEL", chanTpl.getKey());
        if (messageId.isPresent()) {
            try {
                channel.editMessageEmbedsById(messageId.get(), embed).setComponents(components).complete();
                LOGGER.debug("Painel atualizado em #{}", channel.getName());
                return true;
            } catch (Exception e) {
                // Mensagem apagada manualmente: publicar uma nova
                LOGGER.info("Painel antigo de #{} não encontrado, publicando novamente", channel.getName());
            }
        }

        var message = channel.sendMessageEmbeds(embed).setComponents(components).complete();
        repository.saveMapping(guild.getId(), "PANEL", chanTpl.getKey(), message.getId(), panel.getTitle());
        LOGGER.info("Painel publicado em #{}", channel.getName());
        return true;
    }

    private MessageEmbed buildEmbed(Guild guild, ServerTemplate template, PanelTemplate panel) {
        EmbedBuilder embed = templateService.brandedEmbed(guild).setTimestamp(null);
        if (panel.getColorHex() != null) {
            embed.setColor(TemplateService.parseColor(panel.getColorHex(), templateService.brandColor(guild)));
        }
        if (panel.getTitle() != null) embed.setTitle(templateService.render(guild, panel.getTitle()));
        if (panel.getDescription() != null) embed.setDescription(templateService.render(guild, panel.getDescription()));
        for (PanelTemplate.Field field : panel.getFields()) {
            embed.addField(templateService.render(guild, field.getName()), templateService.render(guild, field.getValue()), field.isInline());
        }

        if (panel.getImageUrl() != null && !panel.getImageUrl().isBlank()) embed.setImage(panel.getImageUrl());
        // Sem thumbnail própria, o painel leva a logo do reino
        if (panel.getThumbnailUrl() != null && !panel.getThumbnailUrl().isBlank()) embed.setThumbnail(panel.getThumbnailUrl());
        else embed.setThumbnail(templateService.logoUrl(guild));

        // Painel de cargos: listar as opções no próprio embed
        if ("SELF_ROLES".equalsIgnoreCase(panel.getType())) {
            StringBuilder list = new StringBuilder();
            for (RoleTemplate role : template.selfAssignableRoles()) {
                String mention = templateService.role(guild, role.getKey()).map(r -> r.getAsMention()).orElse(role.getName());
                list.append(role.getEmoji() != null ? role.getEmoji() + " " : "• ")
                        .append(mention);
                if (role.getDescription() != null) list.append(" — ").append(role.getDescription());
                list.append('\n');
            }
            if (!list.isEmpty()) embed.addField("Cargos disponíveis", list.toString(), false);
        }
        return embed.build();
    }

    private List<LayoutComponent> buildComponents(Guild guild, ServerTemplate template, PanelTemplate panel) {
        String type = panel.getType() == null ? "EMBED" : panel.getType().toUpperCase();
        List<LayoutComponent> rows = new ArrayList<>();

        // Botões configurados explicitamente substituem o botão padrão do tipo
        if (!panel.getButtons().isEmpty()) {
            List<Button> buttons = new ArrayList<>();
            for (PanelButton cfg : panel.getButtons()) {
                Button button = toButton(cfg);
                if (button != null) buttons.add(button);
            }
            if (!buttons.isEmpty()) rows.add(ActionRow.of(buttons));
            if (!"SELF_ROLES".equals(type)) return rows;
        }

        switch (type) {
            case "VERIFY" -> rows.add(ActionRow.of(withEmoji(
                    Button.success(VERIFY_BUTTON_ID, orDefault(panel.getButtonLabel(), "Verificar")), panel.getButtonEmoji())));
            case "TICKET" -> rows.add(ActionRow.of(withEmoji(
                    Button.secondary(TICKET_OPEN_BUTTON_ID, orDefault(panel.getButtonLabel(), "Abrir ticket")), panel.getButtonEmoji())));
            case "SELF_ROLES" -> {
                List<SelectOption> options = new ArrayList<>();
                for (RoleTemplate role : template.selfAssignableRoles()) {
                    SelectOption option = SelectOption.of(stripEmojiPrefix(role), role.getKey());
                    if (role.getDescription() != null) option = option.withDescription(truncate(role.getDescription(), 100));
                    if (role.getEmoji() != null) option = option.withEmoji(Emoji.fromFormatted(role.getEmoji()));
                    options.add(option);
                }
                if (!options.isEmpty()) {
                    rows.add(ActionRow.of(StringSelectMenu.create(SELF_ROLES_MENU_ID)
                            .setPlaceholder(orDefault(panel.getPlaceholder(), "Escolha seus cargos..."))
                            .setMinValues(0)
                            .setMaxValues(options.size())
                            .addOptions(options)
                            .build()));
                }
            }
            default -> { }
        }
        return rows;
    }

    /**
     * IDs: ticket = "thal:ticket:open[:TIPO]", verificação = "thal:verify", cargo = "thal:role:KEY"; URL vira botão de link.
     */
    private static Button toButton(PanelButton cfg) {
        String action = cfg.getAction() == null ? "TICKET" : cfg.getAction().toUpperCase();
        String label = cfg.getLabel() == null || cfg.getLabel().isBlank() ? null : cfg.getLabel();
        Emoji emoji = cfg.getEmoji() == null || cfg.getEmoji().isBlank() ? null : Emoji.fromFormatted(cfg.getEmoji());
        String id = switch (action) {
            case "URL" -> cfg.getUrl();
            case "VERIFY" -> VERIFY_BUTTON_ID;
            case "ROLE" -> ROLE_BUTTON_PREFIX + cfg.getRoleKey();
            case "TICKET" -> TICKET_OPEN_BUTTON_ID
                    + (cfg.getTicketType() != null && !cfg.getTicketType().isBlank() ? ":" + cfg.getTicketType() : "");
            default -> null;
        };
        if (id == null) return null;
        ButtonStyle style = "URL".equals(action) ? ButtonStyle.LINK : switch (cfg.getStyle() == null ? "SECONDARY" : cfg.getStyle().toUpperCase()) {
            case "PRIMARY" -> ButtonStyle.PRIMARY;
            case "SUCCESS" -> ButtonStyle.SUCCESS;
            case "DANGER" -> ButtonStyle.DANGER;
            default -> ButtonStyle.SECONDARY;
        };
        return Button.of(style, id, label, emoji);
    }

    private static Button withEmoji(Button button, String emoji) {
        return emoji == null || emoji.isBlank() ? button : button.withEmoji(Emoji.fromFormatted(emoji));
    }

    /**
     * "🎮 Games" -> "Games": o emoji já aparece como ícone da opção do menu.
     */
    private static String stripEmojiPrefix(RoleTemplate role) {
        String name = role.getName();
        if (role.getEmoji() != null && name.startsWith(role.getEmoji())) {
            name = name.substring(role.getEmoji().length()).trim();
        }
        return truncate(name.isBlank() ? role.getName() : name, 100);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
