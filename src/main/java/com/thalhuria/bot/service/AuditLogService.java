package com.thalhuria.bot.service;

import com.thalhuria.bot.database.ServerMapRepository;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serviço responsável por gerar e despachar logs de auditoria formatados com EmbedBuilder
 * para o canal de logs do sistema (#logs-do-sistema) mapeado para a guilda.
 */
public class AuditLogService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditLogService.class);
    private final ServerMapRepository repository;

    // Cores temáticas para diferentes categorias de logs
    public static final Color COLOR_SETUP = new Color(0x2ECC71); // Verde Esmeralda
    public static final Color COLOR_WARNING = new Color(0xF39C12); // Laranja / Âmbar
    public static final Color COLOR_DANGER = new Color(0xE74C3C); // Vermelho
    public static final Color COLOR_INFO = new Color(0x3498DB); // Azul Claro
    public static final Color COLOR_PURPLE = new Color(0x9B59B6); // Roxo
    public static final Color COLOR_MEMBER = new Color(0x1ABC9C); // Turquesa

    // Guildas em provisionamento/reset: eventos de criação/exclusão gerados pelo próprio bot não viram log individual
    private final Set<String> suppressedGuilds = ConcurrentHashMap.newKeySet();

    public AuditLogService(ServerMapRepository repository) {
        this.repository = repository;
    }

    public void suppress(Guild guild) {
        suppressedGuilds.add(guild.getId());
    }

    public void release(Guild guild) {
        suppressedGuilds.remove(guild.getId());
    }

    public boolean isSuppressed(Guild guild) {
        return suppressedGuilds.contains(guild.getId());
    }

    /**
     * Log genérico para os recursos de comunidade (verificação, tickets, etc.).
     */
    public void logEvent(Guild guild, String title, String description, Color color) {
        findLogChannel(guild).ifPresent(channel -> {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(title)
                    .setColor(color)
                    .setDescription(description)
                    .setFooter("Auditoria", guild.getJDA().getSelfUser().getEffectiveAvatarUrl())
                    .setTimestamp(Instant.now());
            channel.sendMessageEmbeds(embed.build()).queue(
                    null,
                    error -> LOGGER.warn("Não foi possível enviar log para {}: {}", channel.getName(), error.getMessage())
            );
        });
    }

    /**
     * Localiza o canal de logs do sistema: primeiro tenta pelo mapeamento persistido no banco;
     * se não encontrar, tenta pelo canal com nome "logs-do-sistema".
     */
    public Optional<TextChannel> findLogChannel(Guild guild) {
        // 1. Tentar via banco de dados (canal marcado como systemLogChannel no template)
        Optional<String> channelIdOpt = repository.getDiscordId(guild.getId(), "METADATA", "LOG_CHANNEL")
                .or(() -> repository.getDiscordId(guild.getId(), "CHANNEL", "CHAN_LOGS"));
        if (channelIdOpt.isPresent()) {
            TextChannel channel = guild.getTextChannelById(channelIdOpt.get());
            if (channel != null && channel.canTalk()) {
                return Optional.of(channel);
            }
        }

        // 2. Fallback buscando por nome (aceita nomes estilizados como "📊・logs-do-sistema")
        for (TextChannel textChannel : guild.getTextChannels()) {
            if (textChannel.getName().toLowerCase().contains("logs-do-sistema") && textChannel.canTalk()) {
                return Optional.of(textChannel);
            }
        }

        return Optional.empty();
    }

    /**
     * Registra o provisionamento ou sincronização do servidor (/setup-server e /sync-server).
     */
    public void logSetupExecution(Guild guild, Member admin, String mode, int rolesCount, int categoriesCount, int channelsCount) {
        findLogChannel(guild).ifPresent(channel -> {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("⚙️ Auditoria de Infraestrutura: " + mode)
                    .setColor(COLOR_SETUP)
                    .setThumbnail(guild.getIconUrl())
                    .setDescription("O provisionamento de infraestrutura automatizada foi concluído com sucesso.")
                    .addField("Executor", admin != null ? admin.getAsMention() + " (`" + admin.getId() + "`)" : "Sistema Automático", true)
                    .addField("Modo", mode, true)
                    .addField("Servidor", guild.getName(), true)
                    .addField("Cargos Processados", String.valueOf(rolesCount), true)
                    .addField("Categorias Processadas", String.valueOf(categoriesCount), true)
                    .addField("Canais Processados", String.valueOf(channelsCount), true)
                    .setFooter("Bot Thalhuria • Sistema de Auditoria", guild.getJDA().getSelfUser().getEffectiveAvatarUrl())
                    .setTimestamp(Instant.now());

            channel.sendMessageEmbeds(embed.build()).queue(
                    success -> LOGGER.debug("Log de provisionamento enviado para {}", channel.getName()),
                    error -> LOGGER.warn("Não foi possível enviar log para {}: {}", channel.getName(), error.getMessage())
            );
        });
    }

    /**
     * Registra o reset do servidor (/reset-server).
     */
    public void logServerReset(Guild guild, Member admin) {
        findLogChannel(guild).ifPresent(channel -> {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🚨 Auditoria de Limpeza: Servidor Resetado")
                    .setColor(COLOR_DANGER)
                    .setDescription("Todos os canais, categorias e cargos gerenciados pelo Bot foram removidos.")
                    .addField("Administrador Responsável", admin.getAsMention() + " (`" + admin.getId() + "`)", false)
                    .setFooter("Bot Thalhuria • Ambiente Resetado", guild.getJDA().getSelfUser().getEffectiveAvatarUrl())
                    .setTimestamp(Instant.now());

            channel.sendMessageEmbeds(embed.build()).queue();
        });
    }

    /**
     * Registra entrada de novos membros no servidor.
     */
    public void logMemberJoin(Member member) {
        Guild guild = member.getGuild();
        findLogChannel(guild).ifPresent(channel -> {
            User user = member.getUser();
            String created = user.getTimeCreated().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("📥 Novo Membro Conectado")
                    .setColor(COLOR_MEMBER)
                    .setThumbnail(user.getEffectiveAvatarUrl())
                    .addField("Usuário", member.getAsMention() + " (" + user.getName() + ")", false)
                    .addField("ID do Usuário", user.getId(), true)
                    .addField("Conta Criada em", created, true)
                    .addField("Total de Membros", String.valueOf(guild.getMemberCount()), true)
                    .setFooter("Auditoria de Membros", guild.getJDA().getSelfUser().getEffectiveAvatarUrl())
                    .setTimestamp(Instant.now());

            channel.sendMessageEmbeds(embed.build()).queue();
        });
    }

    /**
     * Registra saída/remoção de membros do servidor.
     */
    public void logMemberLeave(Guild guild, User user) {
        findLogChannel(guild).ifPresent(channel -> {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("📤 Membro Saiu do Servidor")
                    .setColor(COLOR_WARNING)
                    .setThumbnail(user.getEffectiveAvatarUrl())
                    .addField("Usuário", user.getName() + " (`" + user.getId() + "`)", false)
                    .addField("Total Restante", String.valueOf(guild.getMemberCount()), true)
                    .setFooter("Auditoria de Membros", guild.getJDA().getSelfUser().getEffectiveAvatarUrl())
                    .setTimestamp(Instant.now());

            channel.sendMessageEmbeds(embed.build()).queue();
        });
    }

    /**
     * Registra atribuição de cargo e criação de sala privada pelo comando /membro configurar.
     */
    public void logMemberConfiguration(Guild guild, Member admin, Member target, Role assignedRole, TextChannel privateChannel) {
        findLogChannel(guild).ifPresent(channel -> {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("👤 Gerenciamento de Membro: Configuração Concluída")
                    .setColor(COLOR_PURPLE)
                    .setThumbnail(target.getUser().getEffectiveAvatarUrl())
                    .addField("Administrador", admin.getAsMention(), true)
                    .addField("Membro Alvo", target.getAsMention() + " (`" + target.getId() + "`)", true)
                    .addField("Cargo Atribuído", assignedRole != null ? assignedRole.getAsMention() : "Nenhum", true);

            if (privateChannel != null) {
                embed.addField("Sala Privada Criada", privateChannel.getAsMention() + " (`" + privateChannel.getId() + "`)", false);
            } else {
                embed.addField("Sala Privada", "Não solicitada", false);
            }

            embed.setFooter("Auditoria de Gerenciamento", guild.getJDA().getSelfUser().getEffectiveAvatarUrl())
                 .setTimestamp(Instant.now());

            channel.sendMessageEmbeds(embed.build()).queue();
        });
    }

    /**
     * Registra ciclo de vida de canais (Criação ou Exclusão).
     */
    public void logChannelLifecycle(Guild guild, String channelName, String channelType, String channelId, String categoryName, String action, String executorText) {
        findLogChannel(guild).ifPresent(logChan -> {
            // Evitar loop infinito se o canal modificado for o próprio canal de logs
            if (channelId.equals(logChan.getId()) && "CRIADO".equalsIgnoreCase(action)) {
                return;
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("📁 Canal " + action)
                    .setColor("CRIADO".equalsIgnoreCase(action) ? COLOR_SETUP : COLOR_DANGER)
                    .addField("Nome do Canal", "#" + channelName, true)
                    .addField("Tipo", channelType, true)
                    .addField("ID", channelId, true);

            if (categoryName != null && !categoryName.isBlank()) {
                embed.addField("Categoria", categoryName, true);
            }
            if (executorText != null && !executorText.isBlank()) {
                embed.addField("Ação Realizada Por", executorText, true);
            }

            embed.setFooter("Auditoria de Canais", guild.getJDA().getSelfUser().getEffectiveAvatarUrl())
                 .setTimestamp(Instant.now());

            logChan.sendMessageEmbeds(embed.build()).queue();
        });
    }

    /**
     * Registra ciclo de vida de cargos (Criação ou Exclusão).
     */
    public void logRoleLifecycle(Guild guild, Role role, String action) {
        findLogChannel(guild).ifPresent(logChan -> {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🛡️ Cargo " + action)
                    .setColor("CRIADO".equalsIgnoreCase(action) ? COLOR_INFO : COLOR_DANGER)
                    .addField("Nome do Cargo", role.getName(), true)
                    .addField("ID", role.getId(), true)
                    .setFooter("Auditoria de Cargos", guild.getJDA().getSelfUser().getEffectiveAvatarUrl())
                    .setTimestamp(Instant.now());

            logChan.sendMessageEmbeds(embed.build()).queue();
        });
    }
}
