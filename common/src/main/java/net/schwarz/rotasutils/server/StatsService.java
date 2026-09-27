package net.schwarz.rotasutils.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.ContentRegistry;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.progress.RpgProfile;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StatsService {
    private record Cache(ServerPlayer entity, RpgProfile profile, long revision, int level, String hash,
                         Map<String, Double> values, Map<Attribute, List<UUID>> applied) { }
    private final Map<UUID, Cache> caches = new HashMap<>();

    public Map<String, Double> refresh(ServerPlayer player, RotasData data, ContentRegistry.Snapshot content) {
        if (!player.server.isSameThread()) { throw new IllegalStateException("Stats require server thread"); }
        var progress = data.progress(player.getUUID());
        Cache previous = caches.get(player.getUUID());
        if (previous != null && previous.entity() == player && previous.profile() == progress.rpg()
                && previous.revision() == progress.rpg().revision() && previous.level() == progress.level()
                && previous.hash().equals(content.hash())) {
            return previous.values();
        }
        Map<String, Double> values = new LinkedHashMap<>();
        Map<Attribute, List<AttributeModifier>> pending = new HashMap<>();
        content.stats().forEach((id, definition) -> {
            double value = definition.calculate(progress.level(), progress.rpg().stats().getOrDefault(id.value(), 0.0), List.of());
            values.put(id.value(), value);
            if (!definition.attribute().isEmpty()) {
                Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(new ResourceLocation(definition.attribute()));
                if (attribute == null) { throw new IllegalArgumentException("Unknown stat attribute " + definition.attribute()); }
                var operation = switch (definition.operation()) {
                    case ADD -> AttributeModifier.Operation.ADDITION;
                    case MULTIPLY_BASE -> AttributeModifier.Operation.MULTIPLY_BASE;
                    case MULTIPLY_TOTAL -> AttributeModifier.Operation.MULTIPLY_TOTAL;
                };
                UUID modifierId = UUID.nameUUIDFromBytes(("rotasutils.rpg.stat/" + id).getBytes(StandardCharsets.UTF_8));
                pending.computeIfAbsent(attribute, key -> new java.util.ArrayList<>()).add(new AttributeModifier(
                        modifierId, "rotasutils.rpg.stat/" + id, value * definition.attributeScale(), operation));
            }
        });
        remove(previous);
        Map<Attribute, List<UUID>> applied = new HashMap<>();
        pending.forEach((attribute, modifiers) -> {
            var instance = player.getAttribute(attribute);
            if (instance != null) {
                for (AttributeModifier modifier : modifiers) {
                    instance.removeModifier(modifier.getId());
                    instance.addTransientModifier(modifier);
                }
                applied.put(attribute, modifiers.stream().map(AttributeModifier::getId).toList());
            }
        });
        if (player.getHealth() > player.getMaxHealth()) { player.setHealth(player.getMaxHealth()); }
        Map<String, Double> result = Map.copyOf(values);
        caches.put(player.getUUID(), new Cache(player, progress.rpg(), progress.rpg().revision(), progress.level(),
                content.hash(), result, applied));
        return result;
    }

    public void forget(UUID id) { remove(caches.remove(id)); }
    public void clear() { caches.values().forEach(StatsService::remove); caches.clear(); }

    private static void remove(Cache cache) {
        if (cache != null) {
            cache.applied().forEach((attribute, ids) -> {
                var instance = cache.entity().getAttribute(attribute);
                if (instance != null) { ids.forEach(instance::removeModifier); }
            });
        }
    }

    public static ContentRegistry.Prepared validate(ContentRegistry.Prepared prepared) {
        if (!prepared.valid()) { return prepared; }
        var issues = new java.util.ArrayList<ContentRegistry.Diagnostic>();
        prepared.snapshot().stats().forEach((id, stat) -> {
            if (!stat.attribute().isEmpty() && !BuiltInRegistries.ATTRIBUTE.containsKey(new ResourceLocation(stat.attribute()))) {
                issues.add(new ContentRegistry.Diagnostic(id.value(), "Unknown attribute: " + stat.attribute()));
            }
        });
        return issues.isEmpty() ? MonsterContentValidator.validate(prepared) : new ContentRegistry.Prepared(null, issues);
    }
}
