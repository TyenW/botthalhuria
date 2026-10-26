package com.thalhuria.bot.service;

import com.thalhuria.bot.config.ChannelTemplate;
import com.thalhuria.bot.database.ServerMapRepository;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Optional;

/**
 * "Entre para criar": ao entrar num canal de voz com joinToCreate=true, o membro ganha uma sala própria
 * que é apagada automaticamente quando fica vazia. No chat da sala, um painel de botões permite ao dono
 * trancar, ocultar, renomear, limitar e transferir a sala.
 *
 * As salas criadas ficam no banco (tipo TEMP_VOICE, key = ID do canal, discordId = ID do dono) para
 * serem limpas mesmo se o bot reiniciar.
 */
public class TempVoiceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TempVoiceService.class);
    private static final String TYPE = "TEMP_VOICE";

    public static final String BTN_LOCK = "thal:tv:lock";
    public static final String BTN_HIDE = "thal:tv:hide";
    public static final String BTN_RENAME = "thal:tv:rename";
    public static final String BTN_LIMIT = "thal:tv:limit";
    public static final String BTN_TRANSFER = "thal:tv:transfer";

    private final TemplateService templateService;
    private final ServerMapRepository repository;

    public TempVoiceService(TemplateService templateService, ServerMapRepository repository) {
        this.templateService = templateService;
        this.repository = repository;
    }

    public void handleJoin(Member member, AudioChannel joined) {
        Guild guild = member.getGuild();
        Optional<ChannelTemplate> hub = templateService.channelTemplateFor(guild, joined.getId())
                .filter(ChannelTemplate::isJoinToCreate);
        if (hub.isEmpty() || !(joined instanceof VoiceChannel hubChannel)) return;

        // Já tem uma sala? Leva o membro de volta para ela em vez de criar outra
        Optional<VoiceChannel> owned = findOwnedBy(guild, member.getId());
        if (owned.isPresent()) {
            guild.moveVoiceMember(member, owned.get()).queue();
            return;
        }

        Category parent = hubChannel.getParentCategory();
        String name = hub.get().getTempChannelName().replace("{user}", member.getEffectiveName());
        if (name.length() > 100) name = name.substring(0, 100);

        var action = parent != null ? parent.createVoiceChannel(name) : guild.createVoiceChannel(name);
        action.setUserlimit(hub.get().getTempUserLimit())
                .addMemberPermissionOverride(member.getIdLong(), ownerPerms(), EnumSet.noneOf(Permission.class))
                .addMemberPermissionOverride(guild.getSelfMember().getIdLong(),
                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT, Permission.MANAGE_CHANNEL,
                                Permission.VOICE_MOVE_OTHERS, Permission.MESSAGE_SEND, Permission.MANAGE_PERMISSIONS),
                        EnumSet.noneOf(Permission.class))
                .reason("Sala temporária de " + member.getUser().getName())
                .queue(channel -> {
                    repository.saveMapping(guild.getId(), TYPE, channel.getId(), member.getId(), channel.getName());
                    guild.moveVoiceMember(member, channel).queue(null, err -> {
                        // Membro saiu antes de ser movido: não deixar a sala órfã
                        deleteIfEmpty(channel);
                    });
                    sendControls(channel, member);
                    LOGGER.info("Sala temporária '{}' criada para {}", channel.getName(), member.getEffectiveName());
                }, err -> LOGGER.warn("Falha ao criar sala temporária para {}", member.getEffectiveName(), err));
    }

    public void handleLeave(AudioChannel left) {
        if (left instanceof VoiceChannel vc && isTemp(vc)) {
            deleteIfEmpty(vc);
        }
    }

    /**
     * Remove salas temporárias vazias ou já inexistentes (chamado quando o bot conecta).
     */
    public void cleanup(Guild guild) {
        for (var entry : repository.getMappingsByType(guild.getId(), TYPE)) {
            VoiceChannel channel = guild.getVoiceChannelById(entry.keyName());
            if (channel == null) {
                repository.deleteMapping(guild.getId(), TYPE, entry.keyName());
            } else {
                deleteIfEmpty(channel);
            }
        }
    }

    // ---------------------------------------------------------------- painel de controle

    private void sendControls(VoiceChannel channel, Member owner) {
        Guild guild = channel.getGuild();
        EmbedBuilder embed = templateService.brandedEmbed(guild)
                .setTitle(templateService.msg(guild, "tempvoice.panel.title"))
                .setDescription(templateService.msg(guild, "tempvoice.panel.description", "user", owner.getAsMention()));
        channel.sendMessageEmbeds(embed.build())
                .setComponents(
                        ActionRow.of(
                                Button.secondary(BTN_LOCK, "Trancar/Destrancar").withEmoji(Emoji.fromUnicode("🔒")),
                                Button.secondary(BTN_HIDE, "Ocultar/Mostrar").withEmoji(Emoji.fromUnicode("👁️")),
                                Button.secondary(BTN_RENAME, "Renomear").withEmoji(Emoji.fromUnicode("✏️"))),
                        ActionRow.of(
                                Button.secondary(BTN_LIMIT, "Limite").withEmoji(Emoji.fromUnicode("👥")),
                                Button.primary(BTN_TRANSFER, "Transferir dono").withEmoji(Emoji.fromUnicode("👑"))))
                .queue(null, err -> LOGGER.debug("Não foi possível enviar o painel da sala: {}", err.getMessage()));
    }

    public Optional<String> ownerOf(VoiceChannel channel) {
        return repository.getDiscordId(channel.getGuild().getId(), TYPE, channel.getId());
    }

    public boolean isOwner(VoiceChannel channel, Member member) {
        return ownerOf(channel).map(member.getId()::equals).orElse(false);
    }

    /**
     * Tranca (CONNECT) ou oculta (VIEW_CHANNEL) a sala para todos os cargos que têm acesso pela categoria.
     * Chamar de novo desfaz. O dono e o bot mantêm o acesso pelos overrides de membro.
     *
     * @return true se a sala ficou restrita
     */
    public boolean toggle(VoiceChannel channel, Permission permission) {
        Category category = channel.getParentCategory();
        boolean restricted = isRestricted(channel, permission);
        var manager = channel.getManager();
        long bit = permission.getRawValue();

        for (PermissionOverride o : channel.getRolePermissionOverrides()) {
            long allow = o.getAllowedRaw();
            long deny = o.getDeniedRaw();
            if (restricted) {
                // Restaurar o que a categoria define para esse cargo
                PermissionOverride original = category != null ? category.getPermissionOverride(o.getRole()) : null;
                long origAllow = original != null ? original.getAllowedRaw() : 0;
                long origDeny = original != null ? original.getDeniedRaw() : 0;
                allow = (allow & ~bit) | (origAllow & bit);
                deny = (deny & ~bit) | (origDeny & bit);
            } else {
                allow &= ~bit;
                deny |= bit;
            }
            manager.putRolePermissionOverride(o.getIdLong(), allow, deny);
        }
        if (channel.getPermissionOverride(channel.getGuild().getPublicRole()) == null && !restricted) {
            manager.putRolePermissionOverride(channel.getGuild().getPublicRole().getIdLong(), 0, bit);
        }
        manager.complete();
        return !restricted;
    }

    private boolean isRestricted(VoiceChannel channel, Permission permission) {
        Category category = channel.getParentCategory();
        for (PermissionOverride o : channel.getRolePermissionOverrides()) {
            PermissionOverride original = category != null ? category.getPermissionOverride(o.getRole()) : null;
            boolean categoryAllows = original != null && original.getAllowed().contains(permission);
            if (categoryAllows && o.getDenied().contains(permission)) return true;
        }
        return false;
    }

    public void rename(VoiceChannel channel, String name) {
        channel.getManager().setName(name.length() > 100 ? name.substring(0, 100) : name).complete();
    }

    public void setLimit(VoiceChannel channel, int limit) {
        channel.getManager().setUserLimit(Math.max(0, Math.min(99, limit))).complete();
    }

    public void transfer(VoiceChannel channel, Member oldOwner, Member newOwner) {
        Guild guild = channel.getGuild();
        channel.getManager()
                .removePermissionOverride(oldOwner.getIdLong())
                .putMemberPermissionOverride(newOwner.getIdLong(), ownerPerms(), EnumSet.noneOf(Permission.class))
                .complete();
        repository.saveMapping(guild.getId(), TYPE, channel.getId(), newOwner.getId(), channel.getName());
    }

    // ---------------------------------------------------------------- interno

    private static EnumSet<Permission> ownerPerms() {
        return EnumSet.of(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT, Permission.VOICE_SPEAK,
                Permission.VOICE_STREAM, Permission.MANAGE_CHANNEL, Permission.VOICE_MOVE_OTHERS,
                Permission.VOICE_MUTE_OTHERS, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY);
    }

    private void deleteIfEmpty(VoiceChannel channel) {
        if (!channel.getMembers().isEmpty()) return;
        Guild guild = channel.getGuild();
        channel.delete().reason("Sala temporária vazia").queue(
                ok -> repository.deleteMapping(guild.getId(), TYPE, channel.getId()),
                err -> LOGGER.warn("Falha ao apagar sala temporária {}: {}", channel.getName(), err.getMessage()));
    }

    private boolean isTemp(VoiceChannel channel) {
        return repository.getDiscordId(channel.getGuild().getId(), TYPE, channel.getId()).isPresent();
    }

    private Optional<VoiceChannel> findOwnedBy(Guild guild, String memberId) {
        return repository.getMappingsByType(guild.getId(), TYPE).stream()
                .filter(e -> e.discordId().equals(memberId))
                .map(e -> guild.getVoiceChannelById(e.keyName()))
                .filter(ch -> ch != null)
                .findFirst();
    }
}
