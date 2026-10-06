package net.schwarz.rotasutils.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.schwarz.rotasutils.core.ContentRegistry;
import net.schwarz.rotasutils.core.MonsterDefinitions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MonsterContentValidator {
    private MonsterContentValidator() { }
    public static ContentRegistry.Prepared validate(ContentRegistry.Prepared prepared) {
        if (!prepared.valid()) { return prepared; }
        List<ContentRegistry.Diagnostic> issues = new ArrayList<>();
        var monsters = prepared.snapshot().monsters();
        monsters.profiles().forEach((id, value) -> {
            attributes(id.value(), value.attributes(), issues);
            selector(id.value(), value.selector(), issues);
            if (value.tiers().isEmpty()) {
                issues.add(new ContentRegistry.Diagnostic(id.value(), "Monster profile has no tiers"));
            }
            value.tiers().forEach((tier, weight) -> {
                if (!monsters.tiers().containsKey(tier)) {
                    issues.add(new ContentRegistry.Diagnostic(id.value(), "Unknown tier: " + tier.value()));
                }
                if (weight == null || weight < 1) {
                    issues.add(new ContentRegistry.Diagnostic(id.value(), "Tier weight must be positive: " + tier.value()));
                }
            });
            value.affixes().forEach(affix -> {
                if (!monsters.affixes().containsKey(affix)) {
                    issues.add(new ContentRegistry.Diagnostic(id.value(), "Unknown affix: " + affix.value()));
                }
            });
            if (value.boss() != null && !monsters.bosses().containsKey(value.boss())) {
                issues.add(new ContentRegistry.Diagnostic(id.value(), "Unknown boss: " + value.boss().value()));
            }
            if (value.level() != null && value.level().min() > value.level().max()) {
                issues.add(new ContentRegistry.Diagnostic(id.value(), "Level min is above max"));
            }
        });
        monsters.tiers().forEach((id, value) -> attributes(id.value(), value.attributes(), issues));
        monsters.affixes().forEach((id, value) -> {
            attributes(id.value(), value.attributes(), issues);
            selector(id.value(), value.selector(), issues);
        });
        prepared.snapshot().definitions().forEach((id, definition) -> {
            if (!definition.enabled() || definition.kind() != ContentRegistry.Kind.ACTION) { return; }
            var body = definition.source().document().getAsJsonObject("body");
            if (body != null && body.has("type") && body.get("type").getAsString().equals("effect")) {
                String effect = body.has("id") ? body.get("id").getAsString() : "";
                if (!known(BuiltInRegistries.MOB_EFFECT, effect)) {
                    issues.add(new ContentRegistry.Diagnostic(id.value(), "Unknown effect: " + effect));
                }
            }
        });
        var items = prepared.snapshot().items();
        items.profiles().forEach((id, profile) -> {
            if (!known(BuiltInRegistries.ITEM, profile.item())) {
                issues.add(new ContentRegistry.Diagnostic(id.value(), "Unknown item: " + profile.item()));
            }
            profile.modifiers().forEach(modifier -> {
                if (!known(BuiltInRegistries.ATTRIBUTE, modifier.attribute())) {
                    issues.add(new ContentRegistry.Diagnostic(id.value(), "Unknown item attribute: " + modifier.attribute()));
                }
            });
        });
        items.sets().forEach((id, set) -> set.bonuses().forEach(bonus -> bonus.modifiers().forEach(modifier -> {
            if (!known(BuiltInRegistries.ATTRIBUTE, modifier.attribute())) {
                issues.add(new ContentRegistry.Diagnostic(id.value(), "Unknown set attribute: " + modifier.attribute()));
            }
        })));
        items.loot().forEach((id, table) -> {
            List<String> problems = new ArrayList<>();
            LootService.checkBuildable(items, table, problems);
            problems.forEach(problem -> issues.add(new ContentRegistry.Diagnostic(id.value(), problem)));
        });
        return issues.isEmpty() ? prepared : new ContentRegistry.Prepared(null, issues);
    }
    private static boolean known(net.minecraft.core.Registry<?> registry, String id) {
        ResourceLocation parsed = id == null ? null : ResourceLocation.tryParse(id);
        return parsed != null && registry.containsKey(parsed);
    }
    private static void attributes(String source, Map<String, MonsterDefinitions.Scale> attributes,
                                   List<ContentRegistry.Diagnostic> issues) {
        attributes.keySet().forEach(id -> {
            if (!known(BuiltInRegistries.ATTRIBUTE, id)) {
                issues.add(new ContentRegistry.Diagnostic(source, "Unknown monster attribute: " + id));
            }
        });
    }
    private static void selector(String source, MonsterDefinitions.Selector selector,
                                 List<ContentRegistry.Diagnostic> issues) {
        java.util.stream.Stream.concat(selector.entities().stream(), selector.excluded().stream()).distinct().forEach(id -> {
            if (!known(BuiltInRegistries.ENTITY_TYPE, id)) {
                issues.add(new ContentRegistry.Diagnostic(source, "Unknown entity: " + id));
            }
        });
    }
}
