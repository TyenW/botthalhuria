package com.thalhuria.bot.commands;

import com.thalhuria.bot.config.ChannelTemplate;
import com.thalhuria.bot.config.ServerTemplate;
import com.thalhuria.bot.database.ServerMapRepository;
import com.thalhuria.bot.service.TemplateService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.ArrayList;
import java.util.List;

/**
 * Comando /server-status: diagnóstico do servidor frente ao template — o que existe, o que falta,
 * recursos ativos e problemas de configuração. Somente leitura.
 */
public class ServerStatusCommand implements SlashCommandHandler {

    private final TemplateService templateService;
    private final ServerMapRepository repository;

    public ServerStatusCommand(TemplateService templateService, ServerMapRepository repository) {
        this.templateService = templateService;
        this.repository = repository;
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("server-status", "Mostra a saúde do servidor em relação ao template")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .setGuildOnly(true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Este comando só pode ser executado dentro de um servidor!").setEphemeral(true).queue();
            return;
        }

        ServerTemplate template = templateService.get(guild);
        List<String> missing = new ArrayList<>();

        long rolesOk = template.getRoles().stream()
                .filter(r -> {
                    boolean ok = templateService.role(guild, r.getKey()).isPresent();
                    if (!ok) missing.add("🛡️ " + r.getName());
                    return ok;
                }).count();
        long categoriesOk = template.getCategories().stream()
                .filter(c -> {
                    boolean ok = templateService.category(guild, c.getKey()).isPresent();
                    if (!ok) missing.add("📁 " + c.getName());
                    return ok;
                }).count();
        List<ChannelTemplate> channels = template.allChannels().toList();
        long channelsOk = channels.stream()
                .filter(c -> {
                    boolean ok = templateService.channel(guild, c.getKey()).isPresent();
                    if (!ok) missing.add("💬 " + c.getName());
                    return ok;
                }).count();
        long panels = channels.stream().filter(c -> c.getPanel() != null).count();
        long panelsOk = channels.stream()
                .filter(c -> c.getPanel() != null && repository.getDiscordId(guild.getId(), "PANEL", c.getKey()).isPresent())
                .count();

        // Hierarquia: cargos do template acima do cargo do bot não podem ser gerenciados
        List<String> aboveBot = template.getRoles().stream()
                .map(r -> templateService.role(guild, r.getKey()))
                .flatMap(java.util.Optional::stream)
                .filter(r -> !guild.getSelfMember().canInteract(r))
                .map(Role::getName)
                .toList();

        List<String> problems = template.validate();
        boolean healthy = missing.isEmpty() && problems.isEmpty() && aboveBot.isEmpty() && panelsOk == panels;

        EmbedBuilder embed = templateService.brandedEmbed(guild)
                .setTitle((healthy ? "🟢" : "🟡") + " Status de " + guild.getName())
                .setDescription("Template **" + template.getTemplateName() + "** · versão `" + template.getVersion() + "`")
                .addField("🛡️ Cargos", rolesOk + "/" + template.getRoles().size(), true)
                .addField("📁 Categorias", categoriesOk + "/" + template.getCategories().size(), true)
                .addField("💬 Canais", channelsOk + "/" + channels.size(), true)
                .addField("🧩 Painéis", panelsOk + "/" + panels, true)
                .addField("🎭 Cargos de autoatribuição", String.valueOf(template.selfAssignableRoles().size()), true)
                .addField("🔊 Salas temporárias ativas", String.valueOf(repository.getMappingsByType(guild.getId(), "TEMP_VOICE").size()), true)
                .addField("⚙️ Recursos", features(template), false);

        if (!missing.isEmpty()) {
            embed.addField("❗ Faltando no servidor (rode /sync-server)", truncate(String.join("\n", missing), 1024), false);
        }
        if (!aboveBot.isEmpty()) {
            embed.addField("⛔ Cargos acima do bot", truncate(String.join(", ", aboveBot)
                    + "\nArraste o cargo do bot para o topo em Configurações → Cargos.", 1024), false);
        }
        if (!problems.isEmpty()) {
            embed.addField("📝 Problemas no template", truncate("• " + String.join("\n• ", problems), 1024), false);
        }

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private static String features(ServerTemplate template) {
        boolean tempVoice = template.allChannels().anyMatch(ChannelTemplate::isJoinToCreate);
        boolean suggestions = template.allChannels().anyMatch(c -> !c.getAutoReactions().isEmpty() || c.isAutoThread());
        boolean counters = template.allChannels().anyMatch(c -> c.getCounter() != null);
        return flag("Onboarding", template.getOnboarding().isEnabled()) + " · "
                + flag("Cartão de boas-vindas", template.getWelcomeCard().isEnabled()) + " · "
                + flag("Tickets (" + template.getTickets().getTypes().size() + " tipos)", template.getTickets().isEnabled()) + "\n"
                + flag("Níveis", template.getLeveling().isEnabled()) + " · "
                + flag("Economia (" + template.getEconomy().getShop().size() + " itens)", template.getEconomy().isEnabled()) + " · "
                + flag("Eventos", template.getEvents().isEnabled()) + "\n"
                + flag("AutoMod", template.getModeration().getAutomod().isEnabled()) + " · "
                + flag("Salas temporárias", tempVoice) + " · "
                + flag("Sugestões", suggestions) + " · "
                + flag("Contadores", counters);
    }

    private static String flag(String name, boolean on) {
        return (on ? "✅ " : "⬜ ") + name;
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
