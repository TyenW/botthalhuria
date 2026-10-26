package com.thalhuria.bot.service;

import com.thalhuria.bot.config.InviteTier;
import com.thalhuria.bot.config.InvitesConfig;
import com.thalhuria.bot.database.InviteRepository;
import com.thalhuria.bot.database.InviteRepository.InviteJoin;
import com.thalhuria.bot.database.MemberStatsRepository.MemberStats;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rastreio de convites.
 *
 * O Discord não diz qual convite alguém usou. O bot guarda quantas vezes cada convite foi usado e, quando
 * alguém entra, busca a lista de novo: o convite cujo contador subiu é o usado. Convites de uso único somem
 * ao serem usados, então convites que desapareceram com o último uso também contam como candidatos.
 *
 * Anti-fraude: contas mais novas que minAccountAgeDays não contam; com rewardOnlyVerified, a recompensa só sai
 * quando o convidado se verifica; quem sai e volta mantém o convidador original e nunca rende recompensa duas vezes.
 */
public class InviteService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InviteService.class);
    private static final Duration DELETED_WINDOW = Duration.ofSeconds(20);

    /** Estado de um convite num momento. maxUses = 0 significa ilimitado. */
    public record Snapshot(String code, String inviterId, int uses, int maxUses) {}

    private record Deleted(Snapshot snapshot, OffsetDateTime at) {}

    private record Found(String inviterId, String code, boolean vanity) {}

    /** Resultado da entrada: quem convidou (pode ser null), por qual código e quantos convites essa pessoa soma. */
    public record JoinSource(String inviterId, String code, boolean vanity, boolean rejoin, boolean valid, int inviterCount) {}

    private final InviteRepository repository;
    private final ActivityService activityService;
    private final TemplateService templateService;
    private final AuditLogService auditLogService;

    private final Map<String, Map<String, Snapshot>> cache = new ConcurrentHashMap<>();
    private final Map<String, Integer> vanityUses = new ConcurrentHashMap<>();
    private final Map<String, List<Deleted>> recentlyDeleted = new ConcurrentHashMap<>();

    public InviteService(InviteRepository repository, ActivityService activityService,
                         TemplateService templateService, AuditLogService auditLogService) {
        this.repository = repository;
        this.activityService = activityService;
        this.templateService = templateService;
        this.auditLogService = auditLogService;
    }

    public InvitesConfig config(Guild guild) {
        return templateService.get(guild).getInvites();
    }

    public InviteRepository repository() {
        return repository;
    }

    // ---------------------------------------------------------------- cache de convites

    /** Carrega os convites atuais (chamado quando o bot conecta à guilda). */
    public void load(Guild guild) {
        if (!canTrack(guild)) return;
        guild.retrieveInvites().queue(
                invites -> cache.put(guild.getId(), toMap(invites)),
                err -> LOGGER.warn("Não foi possível ler os convites de {}: {}", guild.getName(), err.getMessage()));
        if (guild.getVanityCode() != null) {
            guild.retrieveVanityInvite().queue(v -> vanityUses.put(guild.getId(), v.getUses()), err -> { });
        }
    }

    public void onInviteCreate(Guild guild, Invite invite) {
        cache.computeIfAbsent(guild.getId(), id -> new ConcurrentHashMap<>()).put(invite.getCode(), snapshot(invite));
    }

    public void onInviteDelete(Guild guild, String code) {
        Map<String, Snapshot> invites = cache.get(guild.getId());
        Snapshot removed = invites != null ? invites.remove(code) : null;
        if (removed != null) {
            List<Deleted> list = recentlyDeleted.computeIfAbsent(guild.getId(), id -> Collections.synchronizedList(new ArrayList<>()));
            list.add(new Deleted(removed, OffsetDateTime.now()));
        }
    }

    // ---------------------------------------------------------------- entrada, verificação e saída

    /**
     * Descobre por qual convite o membro entrou, registra e (se não exigir verificação) recompensa quem convidou.
     * Nunca falha: em qualquer problema, devolve vazio e as boas-vindas saem normalmente.
     */
    public CompletableFuture<Optional<JoinSource>> onJoin(Member member) {
        Guild guild = member.getGuild();
        if (member.getUser().isBot() || !config(guild).isEnabled() || !canTrack(guild)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Map<String, Snapshot> before = new HashMap<>(cache.getOrDefault(guild.getId(), Map.of()));

        return guild.retrieveInvites().submit()
                .thenCompose(invites -> {
                    Map<String, Snapshot> after = toMap(invites);
                    cache.put(guild.getId(), after);
                    Optional<Snapshot> used = detect(before, after, recentDeleted(guild));
                    if (used.isPresent() || guild.getVanityCode() == null) {
                        return CompletableFuture.completedFuture(used.map(s -> new Found(s.inviterId(), s.code(), false)));
                    }
                    // Nenhum convite comum subiu: talvez tenha sido o link personalizado do servidor
                    return guild.retrieveVanityInvite().submit().thenApply(v -> {
                        Integer previous = vanityUses.put(guild.getId(), v.getUses());
                        boolean vanity = previous != null && v.getUses() > previous;
                        return vanity ? Optional.of(new Found(null, v.getCode(), true)) : Optional.<Found>empty();
                    });
                })
                .thenApply(found -> Optional.of(found
                        .map(f -> register(member, f.inviterId(), f.code(), f.vanity()))
                        .orElseGet(() -> register(member, null, null, false))))
                .exceptionally(err -> {
                    LOGGER.warn("Rastreio de convite falhou para {}: {}", member.getEffectiveName(), err.getMessage());
                    return Optional.empty();
                });
    }

    private JoinSource register(Member member, String inviterId, String code, boolean vanity) {
        Guild guild = member.getGuild();
        InvitesConfig config = config(guild);
        Optional<InviteJoin> previous = repository.get(guild.getId(), member.getId());

        if (previous.isPresent()) {
            // Voltou: mantém o convidador original e não paga de novo
            repository.setLeft(guild.getId(), member.getId(), false);
            InviteJoin p = previous.get();
            if (p.inviterId() != null) updateTiers(guild, p.inviterId());
            int count = p.inviterId() != null ? counted(guild, p.inviterId()) : 0;
            log(guild, member, p.inviterId(), p.code(), false, true, p.valid(), count);
            return new JoinSource(p.inviterId(), p.code(), false, true, p.valid(), count);
        }

        if (inviterId != null && inviterId.equals(member.getId())) inviterId = null;
        long ageDays = Duration.between(member.getUser().getTimeCreated(), OffsetDateTime.now()).toDays();
        boolean valid = ageDays >= config.getMinAccountAgeDays();
        repository.insert(guild.getId(), member.getId(), inviterId, code, valid);

        if (inviterId != null && valid && !config.isRewardOnlyVerified()) {
            reward(guild, inviterId, member);
        }
        int count = inviterId != null ? counted(guild, inviterId) : 0;
        log(guild, member, inviterId, code, vanity, false, valid, count);
        return new JoinSource(inviterId, code, vanity, false, valid, count);
    }

    /** Chamado quando o convidado passa pela verificação. */
    public void onVerify(Member member) {
        Guild guild = member.getGuild();
        InvitesConfig config = config(guild);
        if (!config.isEnabled()) return;
        Optional<InviteJoin> join = repository.get(guild.getId(), member.getId());
        if (join.isEmpty() || join.get().verified()) return;

        repository.markVerified(guild.getId(), member.getId());
        String inviterId = join.get().inviterId();
        if (inviterId == null || !join.get().valid()) return;
        if (config.isRewardOnlyVerified() && !join.get().rewarded()) {
            reward(guild, inviterId, member);
        } else {
            updateTiers(guild, inviterId);
        }
    }

    public void onLeave(Guild guild, User user) {
        InvitesConfig config = config(guild);
        if (!config.isEnabled()) return;
        Optional<InviteJoin> join = repository.get(guild.getId(), user.getId());
        if (join.isEmpty() || join.get().left()) return;
        repository.setLeft(guild.getId(), user.getId(), true);
        if (join.get().inviterId() != null && config.isRemoveTierOnLeave()) {
            updateTiers(guild, join.get().inviterId());
        }
    }

    // ---------------------------------------------------------------- recompensas

    private void reward(Guild guild, String inviterId, Member invitee) {
        Member inviter = guild.getMemberById(inviterId);
        repository.markRewarded(guild.getId(), invitee.getId());
        if (inviter == null || inviter.getUser().isBot()) return;

        int coins = config(guild).getCoinsPerInvite();
        if (coins > 0 && templateService.get(guild).getEconomy().isEnabled()) {
            activityService.modify(guild, inviterId, s -> new MemberStats(s.guildId(), s.userId(), s.xp(), s.level(),
                    s.messages(), s.voiceMinutes(), s.coins() + coins, s.lastXpAt(), s.lastDailyAt(), s.dailyStreak()));
        }
        List<Role> earned = updateTiers(guild, inviterId);
        int count = counted(guild, inviterId);

        auditLogService.logEvent(guild, "🎟️ Recompensa de convite",
                inviter.getAsMention() + " recebeu **" + coins + " " + templateService.get(guild).getEconomy().getCurrencyName()
                        + "** por trazer " + invitee.getAsMention() + " (" + count + " convite(s) válidos)"
                        + (earned.isEmpty() ? "" : "\nNovo título: " + String.join(" ", earned.stream().map(Role::getAsMention).toList())),
                AuditLogService.COLOR_PURPLE);

        inviter.getUser().openPrivateChannel()
                .flatMap(dm -> dm.sendMessage(templateService.msg(guild, "invites.reward.dm",
                        "user", invitee.getEffectiveName(), "coins", coins, "count", count, "server", guild.getName())
                        + (earned.isEmpty() ? "" : "\n" + templateService.msg(guild, "invites.reward.tier",
                        "roles", String.join(", ", earned.stream().map(Role::getName).toList())))))
                .queue(null, err -> { });
    }

    /**
     * Mantém só o cargo de recrutador do maior nível alcançado.
     *
     * @return cargos recém-concedidos
     */
    public List<Role> updateTiers(Guild guild, String inviterId) {
        Member inviter = guild.getMemberById(inviterId);
        if (inviter == null || inviter.getUser().isBot()) return List.of();
        int count = counted(guild, inviterId);

        List<InviteTier> tiers = config(guild).getTiers().stream()
                .sorted(Comparator.comparingInt(InviteTier::getInvites)).toList();
        Role target = null;
        List<Role> all = new ArrayList<>();
        for (InviteTier tier : tiers) {
            Optional<Role> role = templateService.role(guild, tier.getRoleKey()).filter(r -> guild.getSelfMember().canInteract(r));
            if (role.isEmpty()) continue;
            all.add(role.get());
            if (count >= tier.getInvites()) target = role.get();
        }
        Role keep = target;
        List<Role> add = keep != null && !inviter.getRoles().contains(keep) ? List.of(keep) : List.of();
        List<Role> remove = all.stream().filter(r -> !r.equals(keep) && inviter.getRoles().contains(r)).toList();
        if (!add.isEmpty() || !remove.isEmpty()) {
            guild.modifyMemberRoles(inviter, add, remove).reason("Cargos de recrutador (" + count + " convites)")
                    .queue(null, err -> LOGGER.warn("Falha ao ajustar cargos de recrutador: {}", err.getMessage()));
        }
        return add;
    }

    private int counted(Guild guild, String inviterId) {
        return repository.stats(guild.getId(), inviterId, config(guild).isRewardOnlyVerified()).counted();
    }

    private void log(Guild guild, Member member, String inviterId, String code, boolean vanity, boolean rejoin, boolean valid, int count) {
        String source;
        if (vanity) source = "pelo link personalizado `" + code + "`";
        else if (inviterId != null) source = "pelo convite `" + code + "` de <@" + inviterId + "> (" + count + " convite(s) válidos)";
        else source = "por um convite que não consegui identificar";
        String notes = (rejoin ? "\n↩️ Já esteve no servidor antes (não gera nova recompensa)." : "")
                + (!valid && !rejoin ? "\n⚠️ Conta criada há menos de " + config(guild).getMinAccountAgeDays() + " dia(s): não conta para recompensas." : "");
        auditLogService.logEvent(guild, "🎟️ Entrada rastreada", member.getAsMention() + " entrou " + source + notes, AuditLogService.COLOR_INFO);
    }

    // ---------------------------------------------------------------- detecção (pura, testável)

    /**
     * Descobre o convite usado comparando o antes e o depois. Só responde quando há exatamente um candidato:
     * com duas entradas simultâneas por convites diferentes, é melhor dizer "não sei" do que creditar a pessoa errada.
     */
    public static Optional<Snapshot> detect(Map<String, Snapshot> before, Map<String, Snapshot> after, Collection<Snapshot> recentlyDeleted) {
        List<Snapshot> candidates = new ArrayList<>();
        for (Snapshot now : after.values()) {
            Snapshot old = before.get(now.code());
            int previousUses = old != null ? old.uses() : 0;
            if (now.uses() > previousUses) candidates.add(now);
        }
        if (candidates.isEmpty()) {
            // Convites de uso limitado somem ao atingir o máximo
            for (Snapshot old : before.values()) {
                if (!after.containsKey(old.code()) && old.maxUses() > 0 && old.uses() + 1 >= old.maxUses()) candidates.add(old);
            }
            for (Snapshot old : recentlyDeleted) {
                if (old.maxUses() > 0 && old.uses() + 1 >= old.maxUses()
                        && candidates.stream().noneMatch(c -> c.code().equals(old.code()))) candidates.add(old);
            }
        }
        return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty();
    }

    // ---------------------------------------------------------------- utilitários

    private boolean canTrack(Guild guild) {
        return guild.getSelfMember().hasPermission(Permission.MANAGE_SERVER);
    }

    private List<Snapshot> recentDeleted(Guild guild) {
        List<Deleted> list = recentlyDeleted.get(guild.getId());
        if (list == null) return List.of();
        OffsetDateTime limit = OffsetDateTime.now().minus(DELETED_WINDOW);
        synchronized (list) {
            list.removeIf(d -> d.at().isBefore(limit));
            return list.stream().map(Deleted::snapshot).toList();
        }
    }

    private static Map<String, Snapshot> toMap(List<Invite> invites) {
        Map<String, Snapshot> map = new ConcurrentHashMap<>();
        for (Invite invite : invites) map.put(invite.getCode(), snapshot(invite));
        return map;
    }

    private static Snapshot snapshot(Invite invite) {
        String inviter = invite.getInviter() != null ? invite.getInviter().getId() : null;
        return new Snapshot(invite.getCode(), inviter, invite.getUses(), invite.getMaxUses());
    }
}
