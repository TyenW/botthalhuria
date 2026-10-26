package com.thalhuria.bot.config;

import net.dv8tion.jda.api.Permission;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Converte nomes de permissões do template (aceitando tanto os nomes do JDA quanto os da API do Discord)
 * para o enum {@link Permission}. Usado pelo provisionamento e pela validação do template.
 */
public final class PermissionParser {

    // Sinônimos da API do Discord -> Enum do JDA
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("MANAGE_MESSAGES", "MESSAGE_MANAGE"),
            Map.entry("MANAGE_CHANNELS", "MANAGE_CHANNEL"),
            Map.entry("MUTE_MEMBERS", "VOICE_MUTE_OTHERS"),
            Map.entry("DEAFEN_MEMBERS", "VOICE_DEAF_OTHERS"),
            Map.entry("MOVE_MEMBERS", "VOICE_MOVE_OTHERS"),
            Map.entry("EMBED_LINKS", "MESSAGE_EMBED_LINKS"),
            Map.entry("ATTACH_FILES", "MESSAGE_ATTACH_FILES"),
            Map.entry("ADD_REACTIONS", "MESSAGE_ADD_REACTION"),
            Map.entry("READ_MESSAGE_HISTORY", "MESSAGE_HISTORY"),
            Map.entry("SEND_MESSAGES", "MESSAGE_SEND"),
            Map.entry("SEND_MESSAGES_IN_THREADS", "MESSAGE_SEND_IN_THREADS"),
            Map.entry("USE_EXTERNAL_EMOJIS", "MESSAGE_EXT_EMOJI"),
            Map.entry("USE_EXTERNAL_STICKERS", "MESSAGE_EXT_STICKER"),
            Map.entry("MENTION_EVERYONE", "MESSAGE_MENTION_EVERYONE"),
            Map.entry("CHANGE_NICKNAME", "NICKNAME_CHANGE"),
            Map.entry("MANAGE_NICKNAMES", "NICKNAME_MANAGE"),
            Map.entry("CONNECT", "VOICE_CONNECT"),
            Map.entry("SPEAK", "VOICE_SPEAK"),
            Map.entry("STREAM", "VOICE_STREAM"),
            Map.entry("USE_VAD", "VOICE_USE_VAD"),
            Map.entry("MANAGE_GUILD", "MANAGE_SERVER"),
            Map.entry("SEND_POLLS", "MESSAGE_SEND_POLLS")
    );

    private PermissionParser() {}

    public static Optional<Permission> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String normalized = raw.trim().toUpperCase();
        normalized = ALIASES.getOrDefault(normalized, normalized);
        try {
            Permission permission = Permission.valueOf(normalized);
            return permission == Permission.UNKNOWN ? Optional.empty() : Optional.of(permission);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Converte a lista ignorando entradas inválidas (a validação do template reporta essas entradas).
     */
    public static Set<Permission> parseAll(List<String> raws) {
        Set<Permission> set = EnumSet.noneOf(Permission.class);
        if (raws == null) return set;
        for (String raw : raws) {
            parse(raw).ifPresent(set::add);
        }
        return set;
    }
}
