package net.schwarz.rotasutils.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.StatRules;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.progress.RpgProfile;
import net.schwarz.rotasutils.stat.CharacterStat;
import net.schwarz.rotasutils.stat.CoreStat;
import net.schwarz.rotasutils.job.JobDef;
import net.schwarz.rotasutils.job.JobSlot;
import net.schwarz.rotasutils.util.ThaiText;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Level-up stat points and the attribute bonuses they buy.
 *
 * <p>There are four fixed stats ({@link CoreStat}). A character starts with {@code startPoints}, earns
 * {@code pointsPerLevel} every level, each stat holds up to {@code maxPerStat} points, and every point is
 * worth the same. Allocations live in the player's RPG profile; bonuses are transient modifiers rebuilt on
 * login, respawn and every change.</p>
 */
public final class CharacterStatService {
    private static final String MODIFIER_PREFIX = "rotasutils.character_stat/";
    /** Stat points handed out so far; server-only because of the {@code rpg.} prefix. */
    private static final String GRANTED = "rpg.stats.granted";
    private static final String LEGACY_GRANTED = "rpg.season.statpts";
    private static final String LEGACY_RESPECS = "season.respecs";
    /** Dodge never goes past this, whatever titles and AGI add up to. */
    public static final double MAX_DODGE = 0.5;

    public record Result(boolean success, String message) {
        static Result ok(String message) {
            return new Result(true, message);
        }

        static Result no(String message) {
            return new Result(false, message);
        }
    }

    private CharacterStatService() {
    }

    public static StatRules rules(RotasData data) {
        return SeasonService.rules(data).stats;
    }

    public static int allocated(PlayerProgress progress, CoreStat stat) {
        return (int) Math.round(progress.rpg().stats().getOrDefault(stat.id(), 0.0));
    }

    private static long allocatedTotal(PlayerProgress progress) {
        long total = 0;
        for (CoreStat stat : CoreStat.ALL) {
            total += Math.max(0, allocated(progress, stat));
        }
        return total;
    }

    /**
     * Tops the character up to the points their level has earned ({@link StatRules#pointsAt}). Points already
     * handed out are remembered, so lowering a level never takes points back and nothing is granted twice.
     */
    public static int grantLevelPoints(PlayerProgress progress, RotasData data) {
        int target = rules(data).pointsAt(progress.level());
        long granted = number(progress.questVariables().get(GRANTED));
        long owed = target - granted;
        if (owed <= 0) {
            return 0;
        }
        int added = (int) Math.min(owed, Integer.MAX_VALUE - (long) progress.rpg().statPoints());
        if (added > 0) {
            progress.rpg().addStatPoints(added);
        }
        progress.questVariables().put(GRANTED, Long.toString(target));
        progress.markDirty();
        data.setDirty();
        return added;
    }

    private static long number(String value) {
        try {
            return value == null ? 0 : Math.max(0, Long.parseLong(value));
        } catch (NumberFormatException malformed) {
            return 0;
        }
    }

    /** Spends points on several stats at once, all or nothing. */
    public static Result allocate(ServerPlayer player, RotasData data, CompoundTag request) {
        if (request.size() > CoreStat.ALL.size()) {
            return Result.no(ThaiText.t("rotasutils.msg.stat.too_many"));
        }
        PlayerProgress progress = data.progress(player.getUUID());
        int cap = rules(data).maxPerStat;
        Map<CoreStat, Integer> plan = new LinkedHashMap<>();
        long total = 0;
        for (String id : request.getAllKeys()) {
            int amount = request.getInt(id);
            if (amount == 0) {
                continue;
            }
            CoreStat stat = CoreStat.byId(id);
            if (stat == null) {
                return Result.no(ThaiText.t("rotasutils.msg.stat.unavailable"));
            }
            if (amount < 0 || amount > 10_000) {
                return Result.no(ThaiText.t("rotasutils.msg.stat.invalid_points"));
            }
            if ((long) allocated(progress, stat) + amount > cap) {
                return Result.no(ThaiText.t("rotasutils.msg.stat.max_points", stat.displayName(), cap));
            }
            plan.put(stat, amount);
            total += amount;
        }
        if (plan.isEmpty()) {
            return Result.no(ThaiText.t("rotasutils.msg.stat.choose_first"));
        }
        if (total > progress.rpg().statPoints()) {
            return Result.no(ThaiText.t("rotasutils.msg.stat.only_have", progress.rpg().statPoints()));
        }
        plan.forEach((stat, amount) -> progress.rpg().allocate(stat.id(), amount));
        data.setDirty();
        apply(player, data);
        return Result.ok(ThaiText.t("rotasutils.msg.stat.spent", total));
    }

    /** Admin reset: every point comes back, free. */
    public static int reset(ServerPlayer player, RotasData data) {
        int refunded = refund(data.progress(player.getUUID()), data);
        apply(player, data);
        return refunded;
    }

    /** Replaces a player's four allocations while conserving their allocated and unspent points. */
    public static Result adminSetAllocations(ServerPlayer player, RotasData data,
                                             Map<CoreStat, Integer> requested, long expectedRevision) {
        PlayerProgress progress = data.progress(player.getUUID());
        RpgProfile profile = progress.rpg();
        if (profile.revision() != expectedRevision) {
            return Result.no(ThaiText.t("rotasutils.msg.stat.admin_stale"));
        }
        if (requested.size() != CoreStat.ALL.size()) {
            return Result.no(ThaiText.t("rotasutils.msg.stat.admin_invalid"));
        }

        int cap = rules(data).maxPerStat;
        long previousTotal = 0;
        long nextTotal = 0;
        for (CoreStat stat : CoreStat.ALL) {
            Integer value = requested.get(stat);
            if (value == null || value < 0) {
                return Result.no(ThaiText.t("rotasutils.msg.stat.admin_invalid"));
            }
            if (value > cap) {
                return Result.no(ThaiText.t("rotasutils.msg.stat.max_points", stat.displayName(), cap));
            }
            previousTotal += Math.max(0, allocated(progress, stat));
            nextTotal += value;
        }

        long nextUnspent = (long) profile.statPoints() + previousTotal - nextTotal;
        if (nextUnspent < 0) {
            return Result.no(ThaiText.t("rotasutils.msg.stat.admin_pool"));
        }
        if (nextUnspent > Integer.MAX_VALUE) {
            return Result.no(ThaiText.t("rotasutils.msg.stat.admin_overflow"));
        }

        for (CoreStat stat : CoreStat.ALL) {
            profile.stat(stat.id(), requested.get(stat));
        }
        profile.addStatPoints((int) nextUnspent - profile.statPoints());
        data.setDirty();
        apply(player, data);
        return Result.ok(ThaiText.t("rotasutils.msg.stat.admin_saved"));
    }

    public static Result adminGrantStatPoints(ServerPlayer player, RotasData data, int amount,
                                              long expectedRevision) {
        RpgProfile profile = data.progress(player.getUUID()).rpg();
        if (profile.revision() != expectedRevision) {
            return Result.no(ThaiText.t("rotasutils.msg.stat.admin_stale"));
        }
        if (amount <= 0) {
            return Result.no(ThaiText.t("rotasutils.msg.stat.admin_invalid"));
        }
        if (amount > Integer.MAX_VALUE - profile.statPoints()) {
            return Result.no(ThaiText.t("rotasutils.msg.stat.admin_overflow"));
        }
        profile.addStatPoints(amount);
        data.setDirty();
        return Result.ok(ThaiText.t("rotasutils.msg.stat.admin_points_granted", amount));
    }

    /** A player's own respec: every point comes back for the flat price in {@link StatRules#respecCost}. */
    public static Result respec(ServerPlayer player, RotasData data) {
        PlayerProgress progress = data.progress(player.getUUID());
        if (allocatedTotal(progress) <= 0) {
            return Result.no(ThaiText.t("rotasutils.msg.stat.nothing_to_reset"));
        }
        long cost = rules(data).respecCost;
        String currency = SeasonService.rules(data).currency;
        if (cost > 0) {
            if (progress.rpg().currency(currency) < cost) {
                return Result.no(ThaiText.t("rotasutils.msg.stat.respec_funds", cost, progress.rpg().currency(currency)));
            }
            progress.rpg().currency(currency, -cost);
        }
        int refunded = refund(progress, data);
        apply(player, data);
        data.audit(player.getGameProfile().getName() + " respec stats cost=" + cost + " refunded=" + refunded);
        return Result.ok(ThaiText.t("rotasutils.msg.stat.respec_done", refunded, cost));
    }

    /** Gives back every point spent on the four stats. */
    public static int refund(PlayerProgress progress, RotasData data) {
        long refunded = 0;
        for (CoreStat stat : CoreStat.ALL) {
            int points = allocated(progress, stat);
            if (points > 0) {
                refunded += points;
                progress.rpg().stat(stat.id(), 0);
            }
        }
        int granted = (int) Math.min(refunded, Integer.MAX_VALUE - (long) progress.rpg().statPoints());
        if (granted > 0) {
            progress.rpg().addStatPoints(granted);
            data.setDirty();
        }
        return granted;
    }

    /**
     * Wipes a character's level, EXP, stat points and skill trees back to a fresh level 1 character. Money,
     * items, quests, jobs and everything else stay.
     */
    public static void wipeProgression(PlayerProgress progress, RotasData data, int startLevel) {
        for (String id : new ArrayList<>(progress.rpg().stats().keySet())) {
            if (!isKernelStat(data, id)) {
                progress.rpg().stat(id, 0);
            }
        }
        progress.rpg().addStatPoints(-progress.rpg().statPoints());
        progress.questVariables().remove(GRANTED);
        progress.questVariables().remove(LEGACY_GRANTED);
        progress.questVariables().remove(LEGACY_RESPECS);
        progress.resetLevelAndSkills(startLevel);
        grantLevelPoints(progress, data);
    }

    private static boolean isKernelStat(RotasData data, String id) {
        if (data.kernel() == null) {
            return false;
        }
        return data.kernel().content().stats().keySet().stream().anyMatch(key -> key.value().equals(id));
    }

    /** Rebuilds this player's stat, title and job modifiers. */
    public static void apply(ServerPlayer player, RotasData data) {
        float healthBefore = player.getHealth();
        boolean wasFull = healthBefore >= player.getMaxHealth() - 0.01f;
        clear(player);
        PlayerProgress progress = data.progress(player.getUUID());
        StatRules rules = rules(data);
        List<Bonus> bonuses = new ArrayList<>();
        for (CoreStat stat : CoreStat.ALL) {
            int points = Math.min(allocated(progress, stat), rules.maxPerStat);
            if (points <= 0) {
                continue;
            }
            List<CharacterStat.Effect> effects = stat.effects(rules);
            for (int index = 0; index < effects.size(); index++) {
                CharacterStat.Effect effect = effects.get(index);
                bonuses.add(new Bonus(effect, effect.perPoint() * points, stat.id() + (index == 0 ? "" : "/" + index)));
            }
        }
        // A worn title contributes its own small bonus through the same pipeline, so it is cleared and
        // rebuilt with everything else and a title taken off never leaves a modifier behind.
        net.schwarz.rotasutils.title.TitleDef worn = TitleService.worn(data, progress);
        if (worn != null) {
            List<CharacterStat.Effect> titleEffects = worn.effects();
            for (int index = 0; index < titleEffects.size(); index++) {
                CharacterStat.Effect effect = titleEffects.get(index);
                bonuses.add(new Bonus(effect, effect.perPoint(), "title/" + worn.id() + "/" + index));
            }
        }
        // The whole title collection pays a little too, so every title earned counts, not just the worn one.
        List<CharacterStat.Effect> collection = TitleService.collectionEffects(data, progress);
        for (int index = 0; index < collection.size(); index++) {
            CharacterStat.Effect effect = collection.get(index);
            bonuses.add(new Bonus(effect, effect.perPoint(), "title_collection/" + index));
        }
        double defense = 0, evasion = 0, magic = 0, magicPower = 0;
        for (Bonus bonus : bonuses) {
            CharacterStat.Effect effect = bonus.effect();
            double amount = bonus.amount();
            if (Double.isFinite(effect.cap())) {
                amount = Math.max(-effect.cap(), Math.min(effect.cap(), amount));
            }
            switch (effect.attribute()) {
                case CombatStats.DEFENSE -> defense += amount;
                case CombatStats.EVASION -> evasion += amount;
                case CombatStats.MAGIC_ATTACK -> magic += amount;
                case CombatStats.MAGIC_POWER -> magicPower += amount;
                default -> addModifier(player, effect.attribute(), bonus.source(), amount, effect.operation());
            }
        }
        // Job buffs on the logical combat stats join the same totals; attribute buffs go through applyJob.
        for (JobSlot slot : JobSlot.values()) {
            JobDef job = data.job(slot == JobSlot.MAIN ? progress.mainJob() : progress.subJob());
            if (job == null || !job.enabled()) continue;
            for (var modifier : job.attributeModifiers()) {
                double amount = modifier.amount(slot, job.subPassiveCap());
                if (!Double.isFinite(amount)) continue;
                switch (modifier.attribute()) {
                    case CombatStats.DEFENSE -> defense += amount;
                    case CombatStats.EVASION -> evasion += amount;
                    case CombatStats.MAGIC_ATTACK -> magic += amount;
                    case CombatStats.MAGIC_POWER -> magicPower += amount;
                    default -> { }
                }
            }
        }
        CombatStats.set(player.getUUID(), new CombatStats.Values(Math.max(0, defense),
                Math.max(0, Math.min(MAX_DODGE, evasion)), Math.max(0, magic), Math.max(0, magicPower)));
        applyJob(player, data.job(progress.mainJob()), JobSlot.MAIN);
        applyJob(player, data.job(progress.subJob()), JobSlot.SUB);
        if (wasFull && player.getMaxHealth() > healthBefore) {
            player.setHealth(player.getMaxHealth());
        } else if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private record Bonus(CharacterStat.Effect effect, double amount, String source) {
    }

    private static void addModifier(ServerPlayer player, String attribute, String source, double amount,
                                    CharacterStat.Operation operation) {
        if (!Double.isFinite(amount) || amount == 0) {
            return;
        }
        ResourceLocation attributeId = ResourceLocation.tryParse(attribute);
        if (attributeId == null || !BuiltInRegistries.ATTRIBUTE.containsKey(attributeId)) {
            return;
        }
        AttributeInstance instance = player.getAttribute(BuiltInRegistries.ATTRIBUTE.get(attributeId));
        if (instance == null) {
            return;
        }
        UUID modifierId = UUID.nameUUIDFromBytes((MODIFIER_PREFIX + source).getBytes(StandardCharsets.UTF_8));
        instance.removeModifier(modifierId);
        instance.addTransientModifier(new AttributeModifier(modifierId, MODIFIER_PREFIX + source, amount, operation.vanilla()));
    }

    private static void applyJob(ServerPlayer player, JobDef job, JobSlot slot) {
        if (job == null || !job.enabled()) return;
        for (int index = 0; index < job.attributeModifiers().size() && index < 64; index++) {
            var modifier = job.attributeModifiers().get(index);
            ResourceLocation attributeId = ResourceLocation.tryParse(modifier.attribute());
            if (attributeId == null || !BuiltInRegistries.ATTRIBUTE.containsKey(attributeId)) continue;
            AttributeInstance instance = player.getAttribute(BuiltInRegistries.ATTRIBUTE.get(attributeId));
            if (instance == null) continue;
            double amount = modifier.amount(slot, job.subPassiveCap());
            if (!Double.isFinite(amount) || amount == 0) continue;
            String source = "job/" + slot.name().toLowerCase(java.util.Locale.ROOT) + "/" + job.id() + "/" + index;
            UUID id = UUID.nameUUIDFromBytes((MODIFIER_PREFIX + source).getBytes(StandardCharsets.UTF_8));
            instance.addTransientModifier(new AttributeModifier(id, MODIFIER_PREFIX + source, amount, modifier.operation().vanilla()));
        }
    }

    /**
     * Restores the health a player logged out with. Vanilla clamps saved health to the maximum
     * before transient stat modifiers exist, so a Vitality build would otherwise lose health on
     * every login.
     */
    public static void restoreHealth(ServerPlayer player, PlayerProgress progress) {
        float saved = progress.lastHealth();
        if (saved > 0 && player.isAlive()) {
            player.setHealth(Math.min(player.getMaxHealth(), saved));
        }
    }

    /**
     * The attack damage multiplier this mod's stats and jobs add to the player (0.8 = +80%), read back from the
     * transient modifiers {@link #apply} installed. PvP uses it to count only part of a stacked attack build.
     */
    public static double statAttackBonus(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (instance == null) {
            return 0;
        }
        double bonus = 0;
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (modifier.getName().startsWith(MODIFIER_PREFIX)
                    && modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_BASE) {
                bonus += modifier.getAmount();
            }
        }
        return Math.max(0, bonus);
    }

    /** Minecraft attributes and the logical combat values both count as known stat targets. */
    public static boolean attributeExists(String id) {
        if (CombatStats.logical(id)) {
            return true;
        }
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location != null && BuiltInRegistries.ATTRIBUTE.containsKey(location);
    }

    private static void clear(ServerPlayer player) {
        for (Attribute attribute : BuiltInRegistries.ATTRIBUTE) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            for (AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) {
                if (modifier.getName().startsWith(MODIFIER_PREFIX)) {
                    instance.removeModifier(modifier.getId());
                }
            }
        }
    }
}
