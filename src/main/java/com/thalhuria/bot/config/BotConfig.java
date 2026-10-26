package com.thalhuria.bot.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BotConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(BotConfig.class);

    private final String token;
    private final String templatePath;
    private final String databasePath;
    private final String testGuildId;
    private final String templatesDir;
    private final String backupsDir;
    private final boolean messageContentIntent;

    public BotConfig() {
        Dotenv dotenv = null;
        try {
            dotenv = Dotenv.configure().ignoreIfMissing().load();
        } catch (Exception e) {
            LOGGER.debug(".env não encontrado, utilizando variáveis de ambiente do sistema.");
        }

        this.token = getEnvOrDotenv("DISCORD_TOKEN", dotenv, "");
        this.templatePath = getEnvOrDotenv("TEMPLATE_PATH", dotenv, "server-template.json");
        this.databasePath = getEnvOrDotenv("DATABASE_PATH", dotenv, "data/server_map.db");
        this.testGuildId = getEnvOrDotenv("TEST_GUILD_ID", dotenv, "");
        this.templatesDir = getEnvOrDotenv("TEMPLATES_DIR", dotenv, "templates");
        this.backupsDir = getEnvOrDotenv("BACKUPS_DIR", dotenv, "backups");
        // Intent privilegiada: só ligue depois de ativar "MESSAGE CONTENT INTENT" no Developer Portal, senão o login falha
        this.messageContentIntent = Boolean.parseBoolean(getEnvOrDotenv("MESSAGE_CONTENT_INTENT", dotenv, "false"));

        if (this.token.isBlank()) {
            LOGGER.warn("AVISO: DISCORD_TOKEN não foi configurado no .env nem nas variáveis de ambiente!");
        }
    }

    private static String getEnvOrDotenv(String key, Dotenv dotenv, String defaultValue) {
        String val = System.getenv(key);
        if (val != null && !val.isBlank()) {
            return val.trim();
        }
        if (dotenv != null) {
            val = dotenv.get(key);
            if (val != null && !val.isBlank()) {
                return val.trim();
            }
        }
        return defaultValue;
    }

    public String getToken() {
        return token;
    }

    public String getTemplatePath() {
        return templatePath;
    }

    public String getDatabasePath() {
        return databasePath;
    }

    public String getTestGuildId() {
        return testGuildId;
    }

    public String getTemplatesDir() {
        return templatesDir;
    }

    public String getBackupsDir() {
        return backupsDir;
    }

    public boolean isMessageContentIntent() {
        return messageContentIntent;
    }
}
