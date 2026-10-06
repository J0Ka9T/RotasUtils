package net.schwarz.rotasutils.core;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

public record ResolvedZoneRules(String identityZoneId, Optional<Boolean> pvpEnabled,
        Optional<Boolean> hostileSpawningEnabled, OptionalDouble playerDamageTakenMultiplier,
        OptionalDouble playerDamageDealtMultiplier, OptionalDouble healingMultiplier,
        Optional<Boolean> keepInventory, OptionalDouble rotasXpLossPercentage,
        Optional<RespawnTarget> respawnTarget, Map<String, String> sources) {
    public static final String PVP="pvp", HOSTILE_SPAWNING="hostile_spawning", DAMAGE_TAKEN="damage_taken",
            DAMAGE_DEALT="damage_dealt", HEALING="healing", KEEP_INVENTORY="keep_inventory", XP_LOSS="xp_loss", RESPAWN="respawn";
    public ResolvedZoneRules { sources = Map.copyOf(sources); }
    public String source(String rule) { return sources.getOrDefault(rule, "global"); }
}
