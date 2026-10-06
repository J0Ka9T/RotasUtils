package net.schwarz.rotasutils.server;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.core.ConditionEngine;
import net.schwarz.rotasutils.core.KernelContext;
import net.schwarz.rotasutils.data.ParamSpec;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.requirement.Requirement;
import net.schwarz.rotasutils.quest.requirement.RequirementType;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class KernelPlayerContext implements KernelContext {
    private final ServerPlayer player;
    private final RotasData data;
    private final Map<String, String> facts;

    public KernelPlayerContext(ServerPlayer player, Map<String, String> facts) {
        if (!player.server.isSameThread()) {
            throw new IllegalStateException("RPG context must be created on the server thread");
        }
        this.player = player;
        this.data = RotasData.get(player.server);
        this.facts = Map.copyOf(facts);
    }

    @Override
    public double number(String name) {
        checkThread();
        PlayerProgress progress = data.progress(player.getUUID());
        return switch (name) {
            case "player.level" -> progress.level();
            case "player.xp" -> progress.xp();
            case "player.total_xp" -> progress.totalXp();
            case "player.skill_points" -> progress.skillPoints();
            case "player.stat_points" -> progress.rpg().statPoints();
            case "player.rank" -> progress.highestClearance().ordinal();
            default -> {
                if (name.startsWith("currency.")) { yield progress.rpg().currency(name.substring(9)); }
                if (name.startsWith("reputation.")) { yield progress.reputation(name.substring(11)); }
                if (name.startsWith("mastery.")) { yield progress.rpg().masteryXp(name.substring(8)); }
                if (name.startsWith("stat.") && data.kernel() != null) {
                    Double value = data.kernel().stats(player).get(name.substring(5));
                    if (value == null) { throw new IllegalArgumentException("Unknown stat: " + name); }
                    yield value;
                }
                String value = text(name);
                double parsed = Double.parseDouble(value);
                if (!Double.isFinite(parsed)) {
                    throw new IllegalArgumentException("Non-finite fact: " + name);
                }
                yield parsed;
            }
        };
    }

    @Override
    public String text(String name) {
        checkThread();
        if (name.equals("player.rank_name")) {
            return data.progress(player.getUUID()).highestClearance().name();
        }
        if (name.equals("player.dimension")) {
            return player.level().dimension().location().toString();
        }
        if (name.equals("player.biome")) {
            return player.level().getBiome(player.blockPosition()).unwrapKey()
                    .orElseThrow(() -> new IllegalArgumentException("Biome has no registry key")).location().toString();
        }
        String value = name.startsWith("rpg.") ? data.progress(player.getUUID()).questVariables().get(name) : facts.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Unavailable fact: " + name);
        }
        return value;
    }

    @Override
    public boolean requirement(String type, Map<String, String> parameters) {
        checkThread();
        Requirement requirement = requirementDefinition(type, parameters);
        return RequirementChecker.check(player, data, requirement).pass();
    }

    @Override
    public Transaction begin() {
        if (!player.server.isSameThread() || player.hasDisconnected()) {
            throw new IllegalStateException("Reward requires an online player on the server thread");
        }
        return new PlayerRecordTransaction(data, data.progress(player.getUUID()), player);
    }

    private void checkThread() {
        if (!player.server.isSameThread()) {
            throw new IllegalStateException("RPG facts require the server thread");
        }
    }

    public static Map<String, Function<JsonObject, ConditionEngine.Condition>> requirementAdapters() {
        Map<String, Function<JsonObject, ConditionEngine.Condition>> adapters = new HashMap<>();
        for (String name : Set.of("MIN_LEVEL", "MAX_LEVEL", "HAS_ITEM", "DIMENSION", "PERMISSION", "PRESTIGE", "RANK_CLEARANCE")) {
            adapters.put(name, json -> {
                Map<String, String> params = new HashMap<>();
                json.getAsJsonObject("params").entrySet().forEach(entry -> params.put(entry.getKey(), entry.getValue().getAsString()));
                requirementDefinition(name, params);
                Map<String, String> immutable = Map.copyOf(params);
                return context -> context.requirement(name, immutable);
            });
        }
        return Map.copyOf(adapters);
    }

    private static Requirement requirementDefinition(String name, Map<String, String> parameters) {
        RequirementType type = RequirementType.valueOf(name);
        Map<String, ParamSpec> specs = new HashMap<>();
        type.specs().forEach(spec -> specs.put(spec.key(), spec));
        CompoundTag tag = new CompoundTag();
        for (var entry : parameters.entrySet()) {
            ParamSpec spec = specs.get(entry.getKey());
            if (spec == null) {
                throw new IllegalArgumentException("Unknown " + name + " parameter: " + entry.getKey());
            }
            String value = entry.getValue();
            switch (spec.kind()) {
                case INT -> {
                    int parsed = Integer.parseInt(value);
                    if (parsed < 0 || parsed > 1_000_000 || (spec.key().equals("op_level") && parsed > 4)) {
                        throw new IllegalArgumentException("Requirement integer out of range: " + spec.key());
                    }
                    tag.putInt(spec.key(), parsed);
                }
                case BOOL -> {
                    if (!value.equals("true") && !value.equals("false")) {
                        throw new IllegalArgumentException("Invalid boolean: " + spec.key());
                    }
                    if (spec.key().equals("consume") && value.equals("true")) {
                        throw new IllegalArgumentException("Conditions cannot consume inventory");
                    }
                    tag.putBoolean(spec.key(), Boolean.parseBoolean(value));
                }
                case ITEM, DIMENSION -> {
                    ResourceLocation id = ResourceLocation.tryParse(value);
                    if (id == null || (spec.kind() == ParamSpec.ParamKind.ITEM
                            && (!BuiltInRegistries.ITEM.containsKey(id) || id.equals(new ResourceLocation("minecraft:air"))))) {
                        throw new IllegalArgumentException("Unknown/invalid requirement ID: " + value);
                    }
                    tag.putString(spec.key(), value);
                }
                default -> {
                    if (spec.key().equals("permission") && !value.isEmpty()) {
                        throw new IllegalArgumentException("Named permissions require a registered permission adapter; use op_level");
                    }
                    if (spec.kind() == ParamSpec.ParamKind.RANK
                            && net.schwarz.rotasutils.quest.DangerRank.byName(value, null) == null) {
                        throw new IllegalArgumentException("Unknown rank (use F, E, D, C, B, A, S, SS or SSS): " + value);
                    }
                    tag.putString(spec.key(), value);
                }
            }
        }
        return new Requirement(type, tag);
    }
}
