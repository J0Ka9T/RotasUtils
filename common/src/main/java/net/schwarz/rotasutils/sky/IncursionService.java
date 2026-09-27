package net.schwarz.rotasutils.sky;

import dev.architectury.event.EventResult;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.server.GoldCoinService;
import net.schwarz.rotasutils.server.ProgressService;
import net.schwarz.rotasutils.server.SeasonService;
import net.schwarz.rotasutils.server.SpawnPlacer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A sky incursion: what an eldritch sigil unleashes. The sky opens (the omen), waves of the sky's own monsters
 * come for everyone nearby, and a champion follows the last wave. Beating it shatters the sky and pays everyone
 * who fought, the best hunter most; running out of time closes the sky with a smaller reward. A sundering sigil
 * ends it early, paying for the waves already won.
 *
 * <p>Incursions live in memory: a restart ends them, and their leftover monsters are refused when they reload.</p>
 */
public final class IncursionService {
    public static final String TAG = "rotas_incursion:";
    private static final int OMEN_SECONDS = 14;

    enum Phase { OMEN, WAVES, CHAMPION }

    private static final class Incursion {
        final int id;
        final ResourceKey<Level> dimension;
        final Vec3 center;
        final int variant;
        final SeasonRules.IncursionTheme theme;
        final ServerBossEvent bar;
        final Map<UUID, Integer> kills = new LinkedHashMap<>();
        final Map<UUID, Integer> presence = new HashMap<>();
        Phase phase = Phase.OMEN;
        int seconds;
        int wave;
        int nextWave = OMEN_SECONDS;
        UUID champion;

        Incursion(int id, ResourceKey<Level> dimension, Vec3 center, int variant, SeasonRules.IncursionTheme theme) {
            this.id = id;
            this.dimension = dimension;
            this.center = center;
            this.variant = variant;
            this.theme = theme;
            this.bar = new ServerBossEvent(Component.literal(theme.title), BossEvent.BossBarColor.PURPLE,
                    BossEvent.BossBarOverlay.NOTCHED_10);
            bar.setDarkenScreen(true);
            bar.setCreateWorldFog(true);
        }

        String tag() {
            return TAG + id;
        }
    }

    private static final Map<ResourceKey<Level>, Incursion> ACTIVE = new HashMap<>();
    private static int nextId = 1;

    private IncursionService() {
    }

    /** Drop incursions from a stopped server before another world is opened in this JVM. */
    public static void clear() {
        ACTIVE.values().forEach(incursion -> incursion.bar.removeAllPlayers());
        ACTIVE.clear();
    }

    private static SeasonRules.IncursionRules rules(MinecraftServer server) {
        return SeasonService.rules(RotasData.get(server)).incursions;
    }

    static String themeKey(int variant) {
        if (variant == EldritchSkyTransition.VARIANT_FOUR_SKIES) return "rainbow";
        return switch (EldritchSkyTransition.palette(variant)) {
            case EldritchSkyTransition.PALETTE_RED -> "red";
            case EldritchSkyTransition.PALETTE_GOLD -> "gold";
            case EldritchSkyTransition.PALETTE_VOID -> "void";
            case EldritchSkyTransition.PALETTE_RAINBOW -> "rainbow";
            default -> "blue";
        };
    }

    /** A sigil opened a sky over {@code level} at {@code center}. */
    public static void start(ServerLevel level, Vec3 center, int variant) {
        SeasonRules.IncursionRules rules = rules(level.getServer());
        if (!rules.enabled || ACTIVE.containsKey(level.dimension())) return;
        SeasonRules.IncursionTheme theme = rules.themes.get(themeKey(variant));
        if (theme == null) theme = rules.themes.values().stream().findFirst().orElse(null);
        if (theme == null) return;
        Incursion incursion = new Incursion(nextId++, level.dimension(), center, variant, theme);
        ACTIVE.put(level.dimension(), incursion);
        for (ServerPlayer player : level.players()) {
            if (!near(incursion, player, rules)) continue;
            title(player, Component.literal(theme.title).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
                    Component.literal(theme.subtitle).withStyle(ChatFormatting.GRAY));
            level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6f, 0.6f);
        }
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("⚠ " + theme.title + ": "
                + theme.subtitle).withStyle(ChatFormatting.DARK_PURPLE), false);
    }

    /** The sky was closed or sundered by hand: the incursion ends, paying for the waves already won. */
    public static void stop(ServerLevel level, boolean sundered) {
        Incursion incursion = ACTIVE.get(level.dimension());
        if (incursion == null) return;
        SeasonRules.IncursionRules rules = rules(level.getServer());
        double share = rules.waves <= 0 ? 0 : 0.5 * Math.max(0, incursion.wave - 1) / rules.waves;
        finish(level, incursion, share, sundered ? "ผนึกฟ้าแล้ว การบุกรุกสิ้นสุด" : "ฟ้าปิดลง การบุกรุกสิ้นสุด", false);
    }

    private static boolean near(Incursion incursion, ServerPlayer player, SeasonRules.IncursionRules rules) {
        return player.level().dimension().equals(incursion.dimension) && !player.isSpectator()
                && player.position().distanceToSqr(incursion.center) <= (double) rules.radius * rules.radius;
    }

    private static void title(ServerPlayer player, Component title, Component subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }

    // Tick -----------------------------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        SeasonRules.IncursionRules rules = rules(server);
        for (Incursion incursion : new ArrayList<>(ACTIVE.values())) {
            ServerLevel level = server.getLevel(incursion.dimension);
            if (level == null) {
                ACTIVE.remove(incursion.dimension);
                incursion.bar.removeAllPlayers();
                continue;
            }
            tick(level, incursion, rules);
        }
    }

    private static void tick(ServerLevel level, Incursion incursion, SeasonRules.IncursionRules rules) {
        incursion.seconds++;
        List<ServerPlayer> fighters = new ArrayList<>();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (near(incursion, player, rules)) {
                fighters.add(player);
                incursion.bar.addPlayer(player);
                incursion.presence.merge(player.getUUID(), 1, Integer::sum);
            } else {
                incursion.bar.removePlayer(player);
            }
        }
        List<Mob> alive = alive(level, incursion, rules);
        if (incursion.seconds >= rules.timeLimitMinutes * 60) {
            double share = rules.waves <= 0 ? 0 : 0.5 * Math.max(0, incursion.wave - 1) / rules.waves;
            finish(level, incursion, share, "หมดเวลา! ท้องฟ้ากลืนแผ่นดินไปครึ่งหนึ่ง แล้วค่อย ๆ ปิดลง", true);
            return;
        }
        switch (incursion.phase) {
            case OMEN -> {
                incursion.bar.setName(Component.literal(incursion.theme.title + " · ลางร้าย"));
                incursion.bar.setProgress(Math.min(1f, incursion.seconds / (float) OMEN_SECONDS));
                if (incursion.seconds >= OMEN_SECONDS) incursion.phase = Phase.WAVES;
            }
            case WAVES -> {
                boolean cleared = alive.isEmpty() && incursion.wave > 0;
                if (incursion.wave < rules.waves && (incursion.seconds >= incursion.nextWave || cleared)) {
                    incursion.wave++;
                    incursion.nextWave = incursion.seconds + rules.waveSeconds;
                    spawnWave(level, incursion, rules, fighters, alive.size());
                } else if (incursion.wave >= rules.waves && alive.size() <= 2) {
                    spawnChampion(level, incursion, fighters);
                }
                incursion.bar.setName(Component.literal(incursion.theme.title + " · คลื่นที่ " + incursion.wave + "/" + rules.waves
                        + " · เหลือ " + alive.size()));
                incursion.bar.setProgress(rules.waves <= 0 ? 1f : Math.max(0f, 1f - (incursion.wave - 1) / (float) rules.waves));
            }
            case CHAMPION -> {
                Entity champion = incursion.champion == null ? null : level.getEntity(incursion.champion);
                if (!(champion instanceof LivingEntity living) || !living.isAlive()) {
                    finish(level, incursion, 1.0, "จ้าวแห่งฟากฟ้าพ่ายแพ้! ท้องฟ้าแตกสลาย", true);
                    return;
                }
                incursion.bar.setName(Component.literal(incursion.theme.championName));
                incursion.bar.setColor(BossEvent.BossBarColor.RED);
                incursion.bar.setProgress(Math.max(0f, living.getHealth() / living.getMaxHealth()));
                // The champion does not flee the fight: past the arena it is pulled back to the centre.
                if (living.position().distanceToSqr(incursion.center) > (double) rules.radius * rules.radius) {
                    living.teleportTo(incursion.center.x, incursion.center.y, incursion.center.z);
                }
            }
        }
    }

    private static List<Mob> alive(ServerLevel level, Incursion incursion, SeasonRules.IncursionRules rules) {
        double reach = rules.radius * 1.5;
        return level.getEntitiesOfClass(Mob.class, new AABB(incursion.center, incursion.center).inflate(reach, 128, reach),
                mob -> mob.isAlive() && mob.getTags().contains(incursion.tag()) && !mob.getUUID().equals(incursion.champion));
    }

    private static void spawnWave(ServerLevel level, Incursion incursion, SeasonRules.IncursionRules rules,
                                  List<ServerPlayer> fighters, int alive) {
        if (fighters.isEmpty() || incursion.theme.mobs.length == 0) return;
        int budget = Math.min(rules.maxAlive - alive, fighters.size() * (rules.mobsPerPlayer + incursion.wave - 1));
        for (int i = 0; i < budget; i++) {
            ServerPlayer target = fighters.get(i % fighters.size());
            EntityType<?> type = SpawnPlacer.mobType(incursion.theme.mobs[level.random.nextInt(incursion.theme.mobs.length)]);
            if (type == null) continue;
            BlockPos pos = SpawnPlacer.find(level, target.blockPosition(), 8, 18, type, level.random, null);
            if (pos == null) continue;
            Mob mob = SpawnPlacer.create(level, type, pos, level.random);
            if (mob == null) continue;
            prepare(mob, incursion, 1 + rules.healthPerWave * (incursion.wave - 1));
            mob.setTarget(target);
            if (level.addFreshEntity(mob)) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getY() + 1, mob.getZ(), 30, 0.4, 0.8, 0.4, 0.1);
            }
        }
        for (ServerPlayer player : fighters) {
            player.displayClientMessage(Component.literal("คลื่นที่ " + incursion.wave + " มาแล้ว!").withStyle(ChatFormatting.RED), true);
            level.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.2f, 1.2f);
        }
    }

    private static void prepare(Mob mob, Incursion incursion, double health) {
        mob.addTag(incursion.tag());
        mob.setPersistenceRequired();
        var attribute = mob.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null && health > 1) {
            attribute.addPermanentModifier(new AttributeModifier(UUID.randomUUID(), "Rotas incursion", health - 1,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
            mob.setHealth(mob.getMaxHealth());
        }
    }

    private static void spawnChampion(ServerLevel level, Incursion incursion, List<ServerPlayer> fighters) {
        incursion.phase = Phase.CHAMPION;
        EntityType<?> type = SpawnPlacer.mobType(incursion.theme.champion);
        BlockPos center = BlockPos.containing(incursion.center);
        BlockPos pos = type == null ? null : SpawnPlacer.find(level, center, 2, 12, type, level.random, null);
        Mob champion = pos == null ? null : SpawnPlacer.create(level, type, pos, level.random);
        if (champion == null) {
            // A theme with a champion that cannot stand here still ends in victory rather than hanging.
            finish(level, incursion, 1.0, "ท้องฟ้าสงบลงเอง ผู้บุกรุกไม่อาจข้ามมาได้", true);
            return;
        }
        double scale = incursion.theme.championHealth * Math.min(4, Math.max(1, fighters.size()));
        prepare(champion, incursion, scale);
        champion.setCustomName(Component.literal(incursion.theme.championName).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        champion.setCustomNameVisible(true);
        champion.setGlowingTag(true);
        if (!level.addFreshEntity(champion)) {
            finish(level, incursion, 1.0, "ท้องฟ้าสงบลงเอง ผู้บุกรุกไม่อาจข้ามมาได้", true);
            return;
        }
        incursion.champion = champion.getUUID();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, champion.getX(), champion.getY() + 1, champion.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, champion.getX(), champion.getY() + 1, champion.getZ(), 80, 1.2, 1.5, 1.2, 0.05);
        for (ServerPlayer player : fighters) {
            title(player, Component.literal(incursion.theme.championName).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                    Component.literal("ข้ามมาจากรอยแยกแล้ว!").withStyle(ChatFormatting.GRAY));
            level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1f, 0.7f);
        }
    }

    // Ending ---------------------------------------------------------------------------------------

    /** Pays everyone who fought ({@code share} of a full reward), clears the monsters and closes the sky. */
    private static void finish(ServerLevel level, Incursion incursion, double share, String message, boolean closeSky) {
        ACTIVE.remove(incursion.dimension);
        incursion.bar.removeAllPlayers();
        SeasonRules.IncursionRules rules = rules(level.getServer());
        RotasData data = RotasData.get(level.getServer());
        for (Mob mob : alive(level, incursion, rules)) {
            level.sendParticles(ParticleTypes.PORTAL, mob.getX(), mob.getY() + 1, mob.getZ(), 20, 0.3, 0.6, 0.3, 0.5);
            mob.discard();
        }
        Entity champion = incursion.champion == null ? null : level.getEntity(incursion.champion);
        if (champion != null && champion.isAlive()) champion.discard();

        UUID top = incursion.kills.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        List<String> paid = new ArrayList<>();
        if (share > 0) {
            for (Map.Entry<UUID, Integer> entry : incursion.presence.entrySet()) {
                int kills = incursion.kills.getOrDefault(entry.getKey(), 0);
                if (kills == 0 && entry.getValue() < 60) continue;
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
                if (player == null) continue;
                double scale = share * (1 + Math.min(1, kills * rules.rewardPerKill)) * (entry.getKey().equals(top) ? rules.topBonus : 1);
                long gold = Math.round(rules.rewardGold * scale);
                long xp = Math.round(rules.rewardXp * scale);
                if (gold > 0) data.progress(player.getUUID()).rpg().currency(GoldCoinService.CURRENCY, gold);
                if (xp > 0) ProgressService.addExperience(player, data, xp, false);
                player.sendSystemMessage(Component.literal("รางวัลการบุกรุก: +" + gold + " ทอง, +" + xp + " EXP (ฆ่า " + kills + ")")
                        .withStyle(ChatFormatting.GOLD));
                RotasNetwork.syncProgress(player);
                paid.add(player.getGameProfile().getName());
            }
            data.setDirty();
        }
        String hero = top == null ? null : level.getServer().getPlayerList().getPlayer(top) == null ? null
                : level.getServer().getPlayerList().getPlayer(top).getGameProfile().getName();
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("✦ " + message
                + (hero != null && share > 0 ? " · ผู้ล่ายอดเยี่ยม: " + hero : "")).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        data.audit("Incursion " + incursion.id + " ended share=" + share + " paid=" + paid);
        if (!closeSky) return;
        var snapshot = EldritchSkySavedData.get(level).snapshot().settle(level.getGameTime());
        if (!snapshot.active()) return;
        if (share >= 1 && rules.shatterOnVictory && SkySunder.begin(level) == SkySunder.Result.STARTED) return;
        EldritchSkyService.toggle(level, snapshot.variant);
    }

    // Hooks ----------------------------------------------------------------------------------------

    public static void onKill(ServerPlayer killer, LivingEntity victim) {
        for (Incursion incursion : ACTIVE.values()) {
            if (victim.getTags().contains(incursion.tag())) {
                incursion.kills.merge(killer.getUUID(), 1, Integer::sum);
                return;
            }
        }
    }

    /** Leftover incursion monsters from an ended or pre-restart incursion are refused when they load. */
    public static EventResult onAdd(Entity entity, Level level) {
        if (level.isClientSide) return EventResult.pass();
        for (String tag : entity.getTags()) {
            if (!tag.startsWith(TAG)) continue;
            Incursion incursion = ACTIVE.get(level.dimension());
            return incursion != null && tag.equals(incursion.tag()) ? EventResult.pass() : EventResult.interruptFalse();
        }
        return EventResult.pass();
    }

    public static boolean active(ServerLevel level) {
        return ACTIVE.containsKey(level.dimension());
    }
}
