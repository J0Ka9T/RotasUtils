package net.schwarz.rotasutils.server;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.stat.CharacterStat;
import net.schwarz.rotasutils.title.TitleCounters;
import net.schwarz.rotasutils.title.TitleDef;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Titles (ฉายา): earning them, wearing them, and keeping a unique one unique.
 *
 * <p>Nothing here is bought. A title is the record of something a player did, so every check reads a
 * tally that the act itself wrote - a kill, a level, a quest, a refine. A title marked unique is
 * claimed exactly once on the server thread, so the first player to meet its condition keeps it and
 * everyone who gets there afterwards is told they were too late.</p>
 */
public final class TitleService {
    private TitleService() {
    }

    /** Installs the starter titles once per world; a later deletion is respected. */
    public static void seedDefaults(RotasData data) {
        if (!data.titlesSeeded()) {
            if (data.titles().isEmpty()) {
                for (TitleDef title : defaults()) {
                    data.putTitle(title);
                }
            }
            data.markTitlesSeeded();
        }
        seedBatch(data, "nemesis_v1", nemesisTitles());
        seedBatch(data, "expansion_v2", expansionTitles());
    }

    private static CharacterStat.Effect percent(String attribute, double amount, String label) {
        return new CharacterStat.Effect(attribute, amount, CharacterStat.Operation.MULTIPLY_BASE, true, label, 0);
    }

    private static CharacterStat.Effect flat(String attribute, double amount, String label) {
        return new CharacterStat.Effect(attribute, amount, CharacterStat.Operation.ADD, false, label, 0);
    }

    /** Titles for bounties, breeding, the bestiary, travel, trade, stubbornness and collecting titles. */
    public static List<TitleDef> expansionTitles() {
        List<TitleDef> titles = new ArrayList<>();
        TitleDef bountyHunter = title("rotas:bounty_hunter", "นักล่าค่าหัว", "ส่งงานค่าหัว 10 งาน", 0xFFD9A441,
                TitleDef.Condition.BOUNTY, "", 10, false);
        bountyHunter.addEffect(percent("minecraft:generic.attack_damage", 0.02, "พลังโจมตี"));
        titles.add(bountyHunter);
        TitleDef manhunter = title("rotas:dread_marshal", "นายพรานผู้น่าสะพรึง", "ส่งงานค่าหัว 100 งาน", 0xFFB23A48,
                TitleDef.Condition.BOUNTY, "", 100, false);
        manhunter.setRarity(TitleDef.Rarity.EPIC);
        manhunter.addEffect(percent("minecraft:generic.attack_damage", 0.04, "พลังโจมตี"));
        manhunter.addEffect(flat(CombatStats.DEFENSE, 2, "พลังป้องกัน"));
        titles.add(manhunter);

        TitleDef breeder = title("rotas:horse_breeder", "นักเพาะพันธุ์ม้า", "ผสมพันธุ์ม้า 5 ตัว", 0xFF86C05C,
                TitleDef.Condition.BREED, "", 5, false);
        breeder.addEffect(percent("minecraft:generic.movement_speed", 0.02, "ความเร็ว"));
        titles.add(breeder);
        TitleDef bloodline = title("rotas:bloodline_master", "เจ้าแห่งสายเลือด", "ผสมพันธุ์ม้า 50 ตัว", 0xFFE0AC4C,
                TitleDef.Condition.BREED, "", 50, false);
        bloodline.setRarity(TitleDef.Rarity.EPIC);
        bloodline.addEffect(percent("minecraft:generic.movement_speed", 0.04, "ความเร็ว"));
        titles.add(bloodline);

        TitleDef scholar = title("rotas:monster_scholar", "นักปราชญ์อสูร", "บันทึกมอนสเตอร์ 30 ชนิดในสมุด", 0xFF5AA9E6,
                TitleDef.Condition.BESTIARY, "", 30, false);
        scholar.addEffect(flat(CombatStats.MAGIC_ATTACK, 2, "พลังเวท"));
        titles.add(scholar);
        TitleDef encyclopedia = title("rotas:living_bestiary", "สารานุกรมมีชีวิต", "บันทึกมอนสเตอร์ 100 ชนิด", 0xFFB07CE8,
                TitleDef.Condition.BESTIARY, "", 100, false);
        encyclopedia.setRarity(TitleDef.Rarity.EPIC);
        encyclopedia.addEffect(flat(CombatStats.MAGIC_ATTACK, 5, "พลังเวท"));
        titles.add(encyclopedia);

        TitleDef wayfarer = title("rotas:wayfarer", "นักเดินทางไกล", "ค้นพบหินวาร์ป 10 แห่ง", 0xFF7FD1C7,
                TitleDef.Condition.WAYSTONE, "", 10, false);
        wayfarer.addEffect(percent("minecraft:generic.movement_speed", 0.03, "ความเร็ว"));
        titles.add(wayfarer);
        titles.add(title("rotas:first_cartographer", "ผู้วาดแผนที่โลก", "คนแรกของเซิร์ฟเวอร์ที่ค้นพบหินวาร์ป 50 แห่ง",
                0xFFE0AC4C, TitleDef.Condition.WAYSTONE, "", 50, true));

        TitleDef merchant = title("rotas:merchant_prince", "เจ้าชายพ่อค้า", "หาเงินจากนักสะสมและโรงประมูล 100,000", 0xFFE3A857,
                TitleDef.Condition.TRADE, "", 100_000, false);
        merchant.setRarity(TitleDef.Rarity.EPIC);
        merchant.addEffect(flat("minecraft:generic.luck", 1, "โชค"));
        titles.add(merchant);

        TitleDef undying = title("rotas:undying", "ผู้ไม่ยอมแพ้", "ล้มแล้วลุก 100 ครั้ง", 0xFF9FB4C7,
                TitleDef.Condition.DEATH, "", 100, false);
        undying.addEffect(flat("minecraft:generic.max_health", 2, "พลังชีวิต"));
        titles.add(undying);
        TitleDef hidden = title("rotas:deaths_old_friend", "สหายเก่าของยมทูต", "ตาย 1000 ครั้ง ยมทูตจำชื่อท่านได้แล้ว", 0xFF3F3F46,
                TitleDef.Condition.DEATH, "", 1000, false);
        hidden.setHidden(true);
        hidden.setRarity(TitleDef.Rarity.EPIC);
        hidden.addEffect(flat("minecraft:generic.max_health", 4, "พลังชีวิต"));
        titles.add(hidden);

        TitleDef collector = title("rotas:title_collector", "นักสะสมฉายา", "มีฉายา 10 ฉายา", 0xFFC88CFF,
                TitleDef.Condition.TITLE_COUNT, "", 10, false);
        collector.setRarity(TitleDef.Rarity.RARE);
        titles.add(collector);
        TitleDef thousandNames = title("rotas:thousand_names", "ผู้มีพันนาม", "มีฉายา 25 ฉายา", 0xFFE0AC4C,
                TitleDef.Condition.TITLE_COUNT, "", 25, false);
        thousandNames.setRarity(TitleDef.Rarity.LEGENDARY);
        thousandNames.addEffect(percent("minecraft:generic.max_health", 0.03, "พลังชีวิต"));
        thousandNames.addEffect(percent("minecraft:generic.attack_damage", 0.03, "พลังโจมตี"));
        titles.add(thousandNames);
        return titles;
    }

    /**
     * Installs titles added after a world was first seeded, once per batch. A world that already has the
     * batch keeps whatever an administrator did to it since, deletions included.
     */
    private static void seedBatch(RotasData data, String batch, List<TitleDef> titles) {
        if (data.titleBatchSeeded(batch)) {
            return;
        }
        int order = data.titles().values().stream().mapToInt(TitleDef::order).max().orElse(-1) + 1;
        for (TitleDef title : titles) {
            if (data.titles().containsKey(title.id()) || data.titles().size() >= TitleDef.MAX_TITLES) {
                continue;
            }
            title.setOrder(order++);
            data.putTitle(title);
        }
        data.markTitleBatchSeeded(batch);
    }

    /** Titles earned by ending nemeses; the unique one is the server's own history. */
    public static List<TitleDef> nemesisTitles() {
        List<TitleDef> titles = new ArrayList<>();
        titles.add(title("rotas:avenger", "ผู้ล้างแค้น", "ปราบศัตรูคู่แค้น 1 ตัว", 0xFFE2695C,
                TitleDef.Condition.NEMESIS, "", 1, false));
        TitleDef hunter = title("rotas:nemesis_hunter", "นักล่าศัตรูคู่แค้น", "ปราบศัตรูคู่แค้น 10 ตัว", 0xFFB23A48,
                TitleDef.Condition.NEMESIS, "", 10, false);
        hunter.addEffect(new CharacterStat.Effect("minecraft:generic.attack_damage", 0.02,
                CharacterStat.Operation.MULTIPLY_BASE, true, "พลังโจมตี", 0));
        titles.add(hunter);
        titles.add(title("rotas:nightmare_of_nemeses", "ฝันร้ายของเหล่าอสูร",
                "คนแรกของเซิร์ฟเวอร์ที่ปราบศัตรูคู่แค้นครบ 25 ตัว", 0xFFE0AC4C, TitleDef.Condition.NEMESIS, "", 25, true));
        return titles;
    }

    /** Titles in display order. */
    public static List<TitleDef> sorted(RotasData data) {
        List<TitleDef> titles = new ArrayList<>(data.titles().values());
        titles.sort(Comparator.comparingInt(TitleDef::order).thenComparing(TitleDef::id));
        return titles;
    }

    // Tallies ------------------------------------------------------------------------------------

    /**
     * One kill. Only the entity types some title asks about are tallied, so the counters cannot grow
     * with the entity list of a large modpack.
     */
    public static void onKill(ServerPlayer player, RotasData data, String entityId, boolean boss) {
        PlayerProgress progress = data.progress(player.getUUID());
        var variables = progress.questVariables();
        boolean touched = false;
        if (watches(data, TitleDef.Condition.KILL_ANY, "")) {
            TitleCounters.add(variables, TitleCounters.KILL_ANY, 1);
            touched = true;
        }
        if (boss && watches(data, TitleDef.Condition.KILL_BOSS, "")) {
            TitleCounters.add(variables, TitleCounters.KILL_BOSS, 1);
            touched = true;
        }
        String id = entityId == null ? "" : entityId.toLowerCase(Locale.ROOT);
        if (!id.isBlank() && watches(data, TitleDef.Condition.KILL_ENTITY, id)) {
            TitleCounters.add(variables, TitleCounters.killKey(id), 1);
            touched = true;
        }
        if (touched) {
            progress.markDirty();
            data.setDirty();
        }
        check(player, data);
    }

    /** Records the best refine level this player has ever reached. */
    public static void onRefine(ServerPlayer player, RotasData data, int level) {
        PlayerProgress progress = data.progress(player.getUUID());
        long best = TitleCounters.raise(progress.questVariables(), TitleCounters.REFINE_BEST, level);
        if (best == level) {
            progress.markDirty();
            data.setDirty();
        }
        check(player, data);
    }

    /** Anything else that can earn a title: a level, a quest, a fat wallet. */
    public static void onProgress(ServerPlayer player, RotasData data) {
        check(player, data);
    }

    /** True when at least one enabled title watches this condition (and entity, when it names one). */
    private static boolean watches(RotasData data, TitleDef.Condition condition, String target) {
        for (TitleDef title : data.titles().values()) {
            if (!title.enabled() || title.condition() != condition) {
                continue;
            }
            if (condition != TitleDef.Condition.KILL_ENTITY || title.target().equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    // Earning ------------------------------------------------------------------------------------

    /** Awards every title this player now qualifies for. Returns how many were earned. */
    public static int check(ServerPlayer player, RotasData data) {
        if (!mayEarnAutomatically(player, data)) {
            return 0;
        }
        PlayerProgress progress = data.progress(player.getUUID());
        int earned = 0;
        // Earning a title can itself earn another (a title count, a level from the reward EXP), so settle it.
        for (int pass = 0; pass < 4; pass++) {
            int before = earned;
            for (TitleDef title : sorted(data)) {
                if (!title.enabled() || progress.hasTitle(title.id())
                        || title.condition() == TitleDef.Condition.MANUAL) {
                    continue;
                }
                if (!title.met(progressOf(data, progress, title))) {
                    continue;
                }
                if (blocked(progress, title.id()) && SeasonService.rules(data).titles.revokeBlocksReEarn) {
                    continue;
                }
                if (award(player, data, title, true)) {
                    earned++;
                }
            }
            if (earned == before) break;
        }
        return earned;
    }

    /** Staff and creative players are skipped unless the season's title rules say otherwise. */
    public static boolean mayEarnAutomatically(ServerPlayer player, RotasData data) {
        var rules = SeasonService.rules(data).titles;
        if (!rules.staffEarnTitles && player.hasPermissions(2)) {
            return false;
        }
        return rules.creativeEarnTitles || !(player.isCreative() || player.isSpectator());
    }

    private static final String BLOCK_PREFIX = "title.revoked.";

    /** True when an admin revoked this title from this player, so the checker leaves it alone. */
    public static boolean blocked(PlayerProgress progress, String id) {
        return "true".equals(progress.questVariables().get(BLOCK_PREFIX + id));
    }

    public static void setBlocked(PlayerProgress progress, String id, boolean blocked) {
        if (blocked) progress.questVariables().put(BLOCK_PREFIX + id, "true");
        else progress.questVariables().remove(BLOCK_PREFIX + id);
    }

    /**
     * Takes a title from any player, online or not, by UUID; frees a unique title and, when asked,
     * stops the checker from handing it straight back. Returns false when they did not hold it.
     */
    public static boolean revokeById(net.minecraft.server.MinecraftServer server, RotasData data,
                                     java.util.UUID playerId, String id, boolean block) {
        PlayerProgress progress = data.progress(playerId);
        if (block) setBlocked(progress, id, true);
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            boolean done = revoke(online, data, id);
            data.setDirty();
            return done;
        }
        boolean held = progress.removeTitle(id);
        TitleDef title = data.title(id);
        if (title != null && title.unique() && playerId.equals(data.uniqueTitleOwner(id))) {
            data.releaseUniqueTitle(id);
            held = true;
        }
        data.setDirty();
        return held;
    }

    /** What this player has done towards one title. */
    public static long progressOf(RotasData data, PlayerProgress progress, TitleDef title) {
        return switch (title.condition()) {
            case LEVEL -> progress.level();
            case KILL_ENTITY, KILL_ANY, KILL_BOSS, REFINE, NEMESIS ->
                    TitleCounters.read(progress.questVariables(), title.counterKey());
            case QUEST -> progress.completedQuests().getOrDefault(title.target(), 0);
            case QUEST_COUNT -> progress.completedQuests().values().stream().mapToLong(Integer::longValue).sum();
            case GOLD -> progress.rpg().currency(SeasonService.rules(data).currency);
            case BOUNTY, BREED, DEATH, TRADE -> TitleCounters.read(progress.questVariables(), title.counterKey());
            case BESTIARY -> progress.bestiary().size();
            case WAYSTONE -> progress.waystones().size();
            case TITLE_COUNT -> progress.titles().size();
            case MANUAL -> 0;
        };
    }

    // Collection ---------------------------------------------------------------------------------

    /** Collection points of every title this player holds. */
    public static int collectionPoints(RotasData data, PlayerProgress progress) {
        int[] points = SeasonService.rules(data).titles.rarityPoints;
        int total = 0;
        for (String id : progress.titles()) {
            TitleDef title = data.title(id);
            if (title != null && title.enabled()) total += points[title.rarity().ordinal()];
        }
        return total;
    }

    /** The collection bonuses this player has reached; they apply whichever title is worn. */
    public static List<CharacterStat.Effect> collectionEffects(RotasData data, PlayerProgress progress) {
        int points = collectionPoints(data, progress);
        List<CharacterStat.Effect> effects = new ArrayList<>();
        for (var tier : SeasonService.rules(data).titles.collection) {
            if (tier.points > points || tier.attribute.isBlank() || tier.amount == 0) continue;
            effects.add(new CharacterStat.Effect(tier.attribute, tier.amount,
                    CharacterStat.Operation.valueOf(tier.operation), tier.percent, tier.label, 0));
        }
        return effects;
    }

    /** Once a second: a faint glow around everyone wearing a legendary title. */
    public static void tickAura(net.minecraft.server.MinecraftServer server, RotasData data) {
        if (!SeasonService.rules(data).titles.legendaryAura) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || player.isInvisible()) continue;
            PlayerProgress progress = data.peek(player.getUUID());
            TitleDef title = progress == null ? null : worn(data, progress);
            if (title == null || title.rarity() != TitleDef.Rarity.LEGENDARY) continue;
            int rgb = title.color() & 0xFFFFFF;
            var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(
                    (rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f), 0.8f);
            // Three motes orbiting at waist height, a third of a turn apart, stepping round once every few seconds.
            double turn = (player.serverLevel().getGameTime() % 100) / 100.0 * Math.PI * 2;
            for (int i = 0; i < 3; i++) {
                double angle = turn + i * Math.PI * 2 / 3;
                player.serverLevel().sendParticles(dust, player.getX() + Math.cos(angle) * 0.7,
                        player.getY() + 0.9 + 0.25 * Math.sin(turn * 2 + i), player.getZ() + Math.sin(angle) * 0.7, 1, 0, 0, 0, 0);
            }
        }
    }

    /** Counts something a title may watch and checks titles right away when the player is online. */
    public static void count(net.minecraft.server.MinecraftServer server, RotasData data, java.util.UUID player,
                             String key, long amount) {
        PlayerProgress progress = data.progress(player);
        TitleCounters.add(progress.questVariables(), key, amount);
        progress.markDirty();
        data.setDirty();
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) check(online, data);
    }

    /**
     * Hands a title to a player. A unique title is claimed here: the second player to arrive is told
     * somebody already holds it and earns nothing.
     */
    public static boolean award(ServerPlayer player, RotasData data, TitleDef title, boolean announce) {
        PlayerProgress progress = data.progress(player.getUUID());
        if (progress.hasTitle(title.id())) {
            return false;
        }
        if (title.unique() && !data.claimUniqueTitle(title.id(), player.getUUID())) {
            return false;
        }
        if (!progress.addTitle(title.id())) {
            return false;
        }
        if (progress.activeTitle().isBlank()) {
            // A player's first title is worn at once; after that they choose.
            progress.setActiveTitle(title.id());
        }
        data.setDirty();
        data.audit(player.getGameProfile().getName() + " earned title " + title.id());
        // A title pays once, by rarity, so every one is worth chasing, worn or not.
        var rules = SeasonService.rules(data).titles;
        int rarity = title.rarity().ordinal();
        long gold = rules.rarityGold[rarity];
        long xp = rules.rarityXp[rarity];
        if (gold > 0) progress.rpg().currency(GoldCoinService.CURRENCY, gold);
        if (announce) {
            player.sendSystemMessage(ThaiText.c("rotasutils.msg.title.earned", title.name())
                    .withStyle(style -> style.withColor(title.color() & 0xFFFFFF)));
            player.sendSystemMessage(ThaiText.c("rotasutils.msg.title.reward", gold, xp, collectionPoints(data, progress))
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.title.earned", title.name()));
            // A spiral in the title's own colour; rarer titles climb higher and denser.
            Fx.spiral(player, Fx.dust(title.color() & 0xFFFFFF, 1.1f), 16 + rarity * 6, 1.0, 1.6 + rarity * 0.35);
            if (rarity >= 3) Fx.fountain(player, net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, 20 + rarity * 8);
            player.level().playSound(null, player.blockPosition(), rarity >= 3
                    ? net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.1f);
            if (title.unique() || rarity >= 3) {
                NpcSocial.rumor("ว่ากันว่า " + player.getGameProfile().getName() + " ได้รับฉายา \"" + title.name() + "\"");
            }
            if (title.unique()) {
                player.server.getPlayerList().broadcastSystemMessage(ThaiText.c("rotasutils.msg.title.unique_claimed",
                        player.getGameProfile().getName(), title.name()), false);
            } else if (rarity >= rules.announceFromRarity) {
                player.server.getPlayerList().broadcastSystemMessage(ThaiText.c("rotasutils.msg.title.rare_earned",
                        player.getGameProfile().getName(), title.name())
                        .withStyle(style -> style.withColor(title.color() & 0xFFFFFF)), false);
            }
        }
        if (xp > 0) ProgressService.addExperience(player, data, xp, true);
        if (title.unique()) {
            RotasNetwork.syncContent(player.server);
        }
        EventService.fire(player, data, net.schwarz.rotasutils.event.EventType.TITLE_EARNED, title.id());
        CharacterStatService.apply(player, data);
        RotasNetwork.syncProgress(player);
        return true;
    }

    /** Takes a title away. A unique one goes back on the shelf for the next player to claim. */
    public static boolean revoke(ServerPlayer player, RotasData data, String id) {
        PlayerProgress progress = data.progress(player.getUUID());
        if (!progress.removeTitle(id)) {
            return false;
        }
        TitleDef title = data.title(id);
        if (title != null && title.unique()) {
            data.releaseUniqueTitle(id);
            RotasNetwork.syncContent(player.server);
        }
        data.setDirty();
        data.audit(player.getGameProfile().getName() + " lost title " + id);
        CharacterStatService.apply(player, data);
        RotasNetwork.syncProgress(player);
        return true;
    }

    /** Wears one of the player's own titles, or takes the current one off when {@code id} is empty. */
    public static boolean wear(ServerPlayer player, RotasData data, String id) {
        PlayerProgress progress = data.progress(player.getUUID());
        if (!progress.setActiveTitle(id)) {
            return false;
        }
        data.setDirty();
        CharacterStatService.apply(player, data);
        RotasNetwork.syncProgress(player);
        return true;
    }

    // Display ------------------------------------------------------------------------------------

    /** The worn title of a player, or null when they wear none. */
    public static TitleDef worn(RotasData data, PlayerProgress progress) {
        String id = progress.activeTitle();
        if (id == null || id.isBlank()) {
            return null;
        }
        TitleDef title = data.title(id);
        return title != null && title.enabled() ? title : null;
    }

    /** The effects the worn title contributes, for the stat modifier pass. */
    public static List<CharacterStat.Effect> wornEffects(RotasData data, PlayerProgress progress) {
        TitleDef title = worn(data, progress);
        return title == null ? List.of() : List.copyOf(title.effects());
    }

    /** {@code "[ผู้ล่ามังกร] Name"}, for chat and the name plate. */
    public static Component decorate(RotasData data, PlayerProgress progress, Component name) {
        TitleDef title = worn(data, progress);
        if (title == null) {
            return name;
        }
        return Component.literal("[" + title.name() + "] ")
                .withStyle(style -> style.withColor(title.color() & 0xFFFFFF))
                .append(name);
    }

    // Defaults -----------------------------------------------------------------------------------

    /**
     * The titles a new world starts with. They are the milestones this mod can see for itself, and the
     * three unique ones are the server's own history: the first to 100, the first +10, the first to
     * clear a thousand bosses.
     */
    public static List<TitleDef> defaults() {
        List<TitleDef> titles = new ArrayList<>();
        titles.add(title("rotas:novice", "มือใหม่หัดเดิน", "ถึงระดับ 5", 0xFFB6A17C,
                TitleDef.Condition.LEVEL, "", 5, false));
        titles.add(title("rotas:adventurer", "นักผจญภัย", "ถึงระดับ 20", 0xFF86C05C,
                TitleDef.Condition.LEVEL, "", 20, false));
        TitleDef veteran = title("rotas:veteran", "ผู้ช่ำชอง", "ถึงระดับ 50", 0xFF5AA9E6,
                TitleDef.Condition.LEVEL, "", 50, false);
        veteran.addEffect(new CharacterStat.Effect("minecraft:generic.max_health", 0.02,
                CharacterStat.Operation.MULTIPLY_BASE, true, "พลังชีวิต", 0));
        titles.add(veteran);
        TitleDef legend = title("rotas:legend", "ตำนานที่ยังมีลมหายใจ", "ถึงระดับสูงสุด", 0xFFB07CE8,
                TitleDef.Condition.LEVEL, "", 100, false);
        legend.addEffect(new CharacterStat.Effect("minecraft:generic.attack_damage", 0.03,
                CharacterStat.Operation.MULTIPLY_BASE, true, "พลังโจมตี", 0));
        titles.add(legend);
        titles.add(title("rotas:first_century", "ปฐมบทแห่งตำนาน", "คนแรกของเซิร์ฟเวอร์ที่ถึงระดับ 100", 0xFFE0AC4C,
                TitleDef.Condition.LEVEL, "", 100, true));

        titles.add(title("rotas:monster_hunter", "นักล่าอสูร", "ล่ามอนสเตอร์ 1000 ตัว", 0xFFE2695C,
                TitleDef.Condition.KILL_ANY, "", 1000, false));
        TitleDef exterminator = title("rotas:exterminator", "เพชฌฆาตแห่งพงไพร", "ล่ามอนสเตอร์ 10000 ตัว", 0xFFE2695C,
                TitleDef.Condition.KILL_ANY, "", 10000, false);
        exterminator.addEffect(new CharacterStat.Effect("minecraft:generic.attack_damage", 1.0,
                CharacterStat.Operation.ADD, false, "พลังโจมตี", 0));
        titles.add(exterminator);
        TitleDef bossSlayer = title("rotas:boss_slayer", "ผู้ปราบเจ้าถิ่น", "ล้มบอส 50 ตัว", 0xFFE0AC4C,
                TitleDef.Condition.KILL_BOSS, "", 50, false);
        bossSlayer.addEffect(new CharacterStat.Effect(CombatStats.DEFENSE, 2.0,
                CharacterStat.Operation.ADD, false, "พลังป้องกัน", 0));
        titles.add(bossSlayer);
        titles.add(title("rotas:dragon_slayer", "ผู้ล่ามังกร", "สังหารเอนเดอร์ดราก้อน", 0xFFB07CE8,
                TitleDef.Condition.KILL_ENTITY, "minecraft:ender_dragon", 1, false));
        titles.add(title("rotas:wither_bane", "ผู้สยบวิเธอร์", "สังหารวิเธอร์", 0xFF3F3F46,
                TitleDef.Condition.KILL_ENTITY, "minecraft:wither", 1, false));

        titles.add(title("rotas:board_regular", "ขาประจำกระดานภารกิจ", "ทำภารกิจครบ 100 ครั้ง", 0xFF9FB4C7,
                TitleDef.Condition.QUEST_COUNT, "", 100, false));
        TitleDef smith = title("rotas:blacksmith", "ช่างตีเหล็กฝีมือดี", "ตีบวกไอเทมถึง +7", 0xFF7FD1C7,
                TitleDef.Condition.REFINE, "", 7, false);
        smith.addEffect(new CharacterStat.Effect(CombatStats.DEFENSE, 1.0,
                CharacterStat.Operation.ADD, false, "พลังป้องกัน", 0));
        titles.add(smith);
        TitleDef grandmaster = title("rotas:grandmaster_smith", "จอมปราชญ์แห่งค้อน",
                "คนแรกของเซิร์ฟเวอร์ที่ตีบวกถึง +10", 0xFFE0AC4C, TitleDef.Condition.REFINE, "", 10, true);
        grandmaster.addEffect(new CharacterStat.Effect("minecraft:generic.attack_damage", 0.05,
                CharacterStat.Operation.MULTIPLY_BASE, true, "พลังโจมตี", 0));
        titles.add(grandmaster);
        titles.add(title("rotas:millionaire", "เศรษฐีเมืองใหม่", "มีทองครบ 1,000,000", 0xFFE0AC4C,
                TitleDef.Condition.GOLD, "", 1_000_000, false));

        for (int i = 0; i < titles.size(); i++) {
            titles.get(i).setOrder(i);
        }
        return titles;
    }

    private static TitleDef title(String id, String name, String description, int color,
                                  TitleDef.Condition condition, String target, long amount, boolean unique) {
        TitleDef title = new TitleDef(id, name);
        title.setDescription(description);
        title.setColor(color);
        title.setCondition(condition);
        title.setTarget(target);
        title.setAmount(amount);
        title.setUnique(unique);
        return title;
    }

    /** Entity types any title is currently counting, for diagnostics and tests. */
    public static Set<String> watchedEntities(RotasData data) {
        Set<String> watched = new HashSet<>();
        for (TitleDef title : data.titles().values()) {
            if (title.enabled() && title.condition() == TitleDef.Condition.KILL_ENTITY && !title.target().isBlank()) {
                watched.add(title.target().toLowerCase(Locale.ROOT));
            }
        }
        return watched;
    }
}
