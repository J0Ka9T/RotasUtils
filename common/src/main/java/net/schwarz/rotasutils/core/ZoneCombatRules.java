package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;

/** Local combat/death overrides. Every absent override inherits through the matching zone stack. */
public record ZoneCombatRules(RuleBool pvpEnabled, RuleBool hostileSpawningEnabled,
                              RuleDouble playerDamageTakenMultiplier, RuleDouble playerDamageDealtMultiplier,
                              RuleDouble healingMultiplier, RuleBool keepInventory,
                              RuleDouble rotasXpLossPercentage, RespawnTarget respawnTarget) {
    public ZoneCombatRules {
        pvpEnabled = safe(pvpEnabled); hostileSpawningEnabled = safe(hostileSpawningEnabled);
        playerDamageTakenMultiplier = safe(playerDamageTakenMultiplier); playerDamageDealtMultiplier = safe(playerDamageDealtMultiplier);
        healingMultiplier = safe(healingMultiplier); keepInventory = safe(keepInventory); rotasXpLossPercentage = safe(rotasXpLossPercentage);
        bound(playerDamageTakenMultiplier, 100, "damage taken"); bound(playerDamageDealtMultiplier, 100, "damage dealt");
        bound(healingMultiplier, 100, "healing"); bound(rotasXpLossPercentage, 100, "XP loss");
    }
    public static ZoneCombatRules inherit() {
        return new ZoneCombatRules(null, null, null, null, null, null, null, null);
    }
    private static RuleBool safe(RuleBool value) { return value == null ? RuleBool.inherit() : value; }
    private static RuleDouble safe(RuleDouble value) { return value == null ? RuleDouble.inherit() : value; }
    private static void bound(RuleDouble rule, double max, String name) {
        if (rule.overridden() && rule.value() > max) throw new IllegalArgumentException("Zone " + name + " rule exceeds " + max);
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        bool(tag, "pvp", pvpEnabled); bool(tag, "hostile_spawning", hostileSpawningEnabled);
        number(tag, "damage_taken", playerDamageTakenMultiplier); number(tag, "damage_dealt", playerDamageDealtMultiplier);
        number(tag, "healing", healingMultiplier); bool(tag, "keep_inventory", keepInventory); number(tag, "xp_loss", rotasXpLossPercentage);
        if (respawnTarget != null) tag.put("respawn", respawnTarget.save()); return tag;
    }
    public static ZoneCombatRules load(CompoundTag tag) {
        return new ZoneCombatRules(bool(tag,"pvp"), bool(tag,"hostile_spawning"), number(tag,"damage_taken"), number(tag,"damage_dealt"),
                number(tag,"healing"), bool(tag,"keep_inventory"), number(tag,"xp_loss"), tag.contains("respawn") ? RespawnTarget.load(tag.getCompound("respawn")) : null);
    }
    private static void bool(CompoundTag tag, String key, RuleBool rule) { if (rule.overridden()) tag.putBoolean(key, rule.value()); }
    private static RuleBool bool(CompoundTag tag, String key) { return tag.contains(key) ? RuleBool.of(tag.getBoolean(key)) : RuleBool.inherit(); }
    private static void number(CompoundTag tag, String key, RuleDouble rule) { if (rule.overridden()) tag.putDouble(key, rule.value()); }
    private static RuleDouble number(CompoundTag tag, String key) { return tag.contains(key) ? RuleDouble.of(tag.getDouble(key)) : RuleDouble.inherit(); }
}
