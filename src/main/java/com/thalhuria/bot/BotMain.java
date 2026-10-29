package com.thalhuria.bot;

import com.thalhuria.bot.commands.*;
import com.thalhuria.bot.config.BotConfig;
import com.thalhuria.bot.database.DatabaseManager;
import com.thalhuria.bot.database.ServerMapRepository;
import com.thalhuria.bot.database.InfractionRepository;
import com.thalhuria.bot.database.InviteRepository;
import com.thalhuria.bot.database.MemberStatsRepository;
import com.thalhuria.bot.listeners.*;
import com.thalhuria.bot.service.*;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ponto de entrada principal do Bot Discord Thalhuria.
 * Inicializa conexão com Discord via JDA 5, banco de dados SQLite, listeners e comandos.
 */
public class BotMain {

    private static final Logger LOGGER = LoggerFactory.getLogger(BotMain.class);

    public static void main(String[] args) {
        // O cartão de boas-vindas usa Java2D; em VPS/containers não existe tela
        System.setProperty("java.awt.headless", "true");
        LOGGER.info("==================================================");
        LOGGER.info("       Iniciando Bot Thalhuria - Infraestrutura   ");
        LOGGER.info("==================================================");

        // 1. Carregar Configurações
        BotConfig config = new BotConfig();

        if (config.getToken() == null || config.getToken().isBlank()) {
            LOGGER.error("ERRO FATAL: Token do bot não fornecido.");
            LOGGER.error("Por favor, crie um arquivo .env na raiz do projeto com:");
            LOGGER.error("DISCORD_TOKEN=seu_token_aqui");
            LOGGER.error("TEST_GUILD_ID=seu_id_de_servidor_de_testes (opcional)");
            System.exit(1);
            return;
        }

        // 2. Inicializar Banco de Dados SQLite e Repositório
        LOGGER.info("Inicializando persistência SQLite em: {}", config.getDatabasePath());
        DatabaseManager databaseManager = new DatabaseManager(config.getDatabasePath());
        ServerMapRepository repository = new ServerMapRepository(databaseManager);

        // 3. Inicializar Camada de Serviços
        MemberStatsRepository statsRepository = new MemberStatsRepository(databaseManager);
        InfractionRepository infractionRepository = new InfractionRepository(databaseManager);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "thalhuria-scheduler");
            t.setDaemon(true);
            return t;
        });

        TemplateService templateService = new TemplateService(config.getTemplatePath(), config.getTemplatesDir(), repository);
        AuditLogService auditLogService = new AuditLogService(repository);
        ModLogService modLogService = new ModLogService(templateService, auditLogService);
        PanelService panelService = new PanelService(repository, templateService);
        ServerProvisioningService provisioningService = new ServerProvisioningService(repository, auditLogService, templateService, panelService);
        MemberManagementService memberManagementService = new MemberManagementService(repository);
        TicketService ticketService = new TicketService(templateService, repository, auditLogService, new TranscriptService());
        TempVoiceService tempVoiceService = new TempVoiceService(templateService, repository);
        ActivityService activityService = new ActivityService(statsRepository, templateService);
        InviteService inviteService = new InviteService(new InviteRepository(databaseManager), activityService, templateService, auditLogService);
        OnboardingService onboardingService = new OnboardingService(templateService, auditLogService, new WelcomeCardRenderer(), inviteService);
        EconomyService economyService = new EconomyService(activityService, templateService, auditLogService);
        ModerationService moderationService = new ModerationService(infractionRepository, templateService, modLogService);
        EventService eventService = new EventService(templateService, scheduler);
        StatsCounterService statsCounterService = new StatsCounterService(templateService);
        BackupService backupService = new BackupService(repository, templateService, config.getBackupsDir());
        StartupCheckService startupCheckService = new StartupCheckService(templateService, onboardingService);
        provisioningService.setCommunityServices(moderationService, statsCounterService);

        // 4. Configurar Gerenciador de Comandos Slash
        CommandManager commandManager = new CommandManager(templateService);
        RebuildServerCommand rebuildCommand = new RebuildServerCommand(provisioningService, auditLogService, templateService);
        commandManager.registerCommand(new SetupServerCommand(rebuildCommand));
        commandManager.registerCommand(rebuildCommand);
        commandManager.registerCommand(new SyncServerCommand(provisioningService, auditLogService, templateService));
        commandManager.registerCommand(new ServerStatusCommand(templateService, repository));
        commandManager.registerCommand(new ResetServerCommand());
        commandManager.registerCommand(new MemberConfigCommand(memberManagementService, auditLogService));
        commandManager.registerCommand(new LevelCommands(activityService, economyService, templateService, inviteService));
        commandManager.registerCommand(new EconomyCommands(economyService, activityService, templateService));
        commandManager.registerCommand(new ModCommand(moderationService, templateService));
        commandManager.registerCommand(new EventCommand(eventService, templateService));
        commandManager.registerCommand(new BackupCommand(backupService, templateService));
        commandManager.registerCommand(new WelcomeCommand(onboardingService, templateService));
        commandManager.registerCommand(new InviteCommand(inviteService, economyService, templateService));

        // 5. Configurar Listeners de Eventos
        ButtonInteractionListener buttonListener = new ButtonInteractionListener(provisioningService, auditLogService);
        GuildAuditListener auditListener = new GuildAuditListener(auditLogService, templateService);
        CommunityListener communityListener = new CommunityListener(templateService, onboardingService, tempVoiceService, activityService, eventService, inviteService);
        PanelInteractionListener panelListener = new PanelInteractionListener(onboardingService, ticketService, economyService);
        TempVoiceControlListener tempVoiceControlListener = new TempVoiceControlListener(tempVoiceService, templateService);
        ModLogListener modLogListener = new ModLogListener(modLogService, templateService);

        try {
            // 6. Construir e Conectar instância do JDA
            JDABuilder builder = JDABuilder.createDefault(config.getToken())
                    // Habilitar intents necessários para moderação, canais, membros, voz e eventos
                    .enableIntents(
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.GUILD_MODERATION,
                            GatewayIntent.GUILD_VOICE_STATES,
                            GatewayIntent.GUILD_EMOJIS_AND_STICKERS,
                            GatewayIntent.SCHEDULED_EVENTS,
                            GatewayIntent.GUILD_INVITES
                    )
                    .enableCache(CacheFlag.SCHEDULED_EVENTS)
                    // Política de cache de membros para comandos de gerenciamento
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .setChunkingFilter(ChunkingFilter.ALL)
                    .setActivity(Activity.watching(templateService.get().getBranding().getActivity()))
                    // Registrar listeners
                    .addEventListeners(
                            commandManager,
                            buttonListener,
                            auditListener,
                            communityListener,
                            panelListener,
                            rebuildCommand,
                            tempVoiceControlListener,
                            modLogListener,
                            new ListenerAdapter() {
                                @Override
                                public void onReady(@NotNull ReadyEvent event) {
                                    LOGGER.info("Bot conectado com sucesso como: {}", event.getJDA().getSelfUser().getName());
                                    LOGGER.info("Publicando comandos Slash na API do Discord...");
                                    commandManager.publishCommands(event.getJDA(), config.getTestGuildId());

                                    JDA ready = event.getJDA();
                                    scheduler.scheduleAtFixedRate(() -> activityService.voiceTick(ready), 1, 1, TimeUnit.MINUTES);
                                    scheduler.scheduleAtFixedRate(() -> statsCounterService.updateAll(ready), 1, 10, TimeUnit.MINUTES);
                                    // Corrige limites de voz e cargos de quem entrou com o bot desligado
                                    scheduler.execute(() -> startupCheckService.runAll(ready));
                                }
                            }
                    );
            if (config.isMessageContentIntent()) {
                // Privilegiada: precisa estar ativada no Developer Portal
                builder.enableIntents(GatewayIntent.MESSAGE_CONTENT);
                LOGGER.info("MESSAGE_CONTENT habilitada: logs mostrarão o conteúdo de mensagens apagadas/editadas");
            }
            JDA jda = builder                    .build();

            // Hook de desligamento gracioso
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.info("Desligando Bot Thalhuria de forma graciosa...");
                scheduler.shutdownNow();
                jda.shutdown();
            }));

        } catch (Exception e) {
            LOGGER.error("Falha ao iniciar conexão JDA com o Discord", e);
            System.exit(1);
        }
    }
}
