package net.schwarz.rotasutils.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.schwarz.rotasutils.core.ContentRegistry;
import net.schwarz.rotasutils.core.MonsterDefinitions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Registry-backed monster and item checks shared by startup, disk reload, drafts and rollback. */
public final class MonsterContentValidator {
    private MonsterContentValidator() { }
    public static ContentRegistry.Prepared validate(ContentRegistry.Prepared prepared) {
        if (!prepared.valid()) { return prepared; }
        List<ContentRegistry.Diagnostic> issues = new ArrayList<>();
        var monsters = prepared.snapshot().monsters();
        monsters.profiles().forEach((id, value) -> {
            attributes(id.value(), value.attributes(), issues);
            selector(id.value(), value.selector(), issues);
        });
        monsters.tiers().forEach((id, value) -> attributes(id.value(), value.attributes(), issues));
        monsters.affixes().forEach((id, value) -> {
            attributes(id.value(), value.attributes(), issues);
            selector(id.value(), value.selector(), issues);
        });
        prepared.snapshot().definitions().forEach((id, definition) -> {
            if (!definition.enabled() || definition.kind() != ContentRegistry.Kind.ACTION) { return; }
            var body = definition.source().document().getAsJsonObject("body");
            if (body.has("type") && body.get("type").getAsString().equals("effect")) {
                String effect = body.get("id").getAsString();
                if (!BuiltInRegistries.MOB_EFFECT.containsKey(new ResourceLocation(effect))) {
                    issues.add(new ContentRegistry.Diagnostic(id.value(), "Unknown effect: " + effect));
                }
            }
        });
        var items = prepared.snapshot().items();
        items.profiles().forEach((id, profile) -> {
            if (!BuiltInRegistries.ITEM.containsKey(new ResourceLocation(profile.item()))) {
                issues.add(new ContentRegistry.Diagnostic(id.value(), "Unknown item: " + profile.item()));
            }
            profile.modifiers().forEach(modifier -> {
                if (!BuiltInRegistries.ATTRIBUTE.containsKey(new ResourceLocation(modifier.attribute()))) {
                    issues.add(new ContentRegistry.Diagnostic(id.value(), "Unknown item attribute: " + modifier.attribute()));
                }
            });
        });
        items.sets().forEach((id, set) -> set.bonuses().forEach(bonus -> bonus.modifiers().forEach(modifier -> {
            if (!BuiltInRegistries.ATTRIBUTE.containsKey(new ResourceLocation(modifier.attribute()))) {
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
    private static void attributes(String source, Map<String, MonsterDefinitions.Scale> attributes,
                                   List<ContentRegistry.Diagnostic> issues) {
        attributes.keySet().forEach(id -> {
            if (!BuiltInRegistries.ATTRIBUTE.containsKey(new ResourceLocation(id))) {
                issues.add(new ContentRegistry.Diagnostic(source, "Unknown monster attribute: " + id));
            }
        });
    }
    private static void selector(String source, MonsterDefinitions.Selector selector,
                                 List<ContentRegistry.Diagnostic> issues) {
        java.util.stream.Stream.concat(selector.entities().stream(), selector.excluded().stream()).distinct().forEach(id -> {
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(new ResourceLocation(id))) {
                issues.add(new ContentRegistry.Diagnostic(source, "Unknown entity: " + id));
            }
        });
    }
}
