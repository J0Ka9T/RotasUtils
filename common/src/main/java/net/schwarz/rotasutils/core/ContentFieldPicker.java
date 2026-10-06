package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.data.ParamSpec.ParamKind;
import java.util.List;

public final class ContentFieldPicker {
    private ContentFieldPicker() {}

    public static ParamKind kind(List<String> path) {
        if (path.isEmpty()) return null;
        String field = path.get(path.size() - 1);
        if (field.matches("[0-9]+") && path.size() > 1) field = path.get(path.size() - 2);
        return switch (field) {
            case "entity", "entity_type", "npc_type", "entities", "exclude_entities", "event.entity_type" -> ParamKind.ENTITY;
            case "entity_tags", "entity_tag" -> ParamKind.ENTITY_TAG;
            case "namespaces" -> ParamKind.ENTITY_NAMESPACE;
            case "biomes", "biome", "event.biome" -> ParamKind.BIOME;
            case "dimension", "dimensions", "event.dimension" -> ParamKind.DIMENSION;
            case "item", "event.item" -> ParamKind.ITEM;
            case "item_tag" -> ParamKind.ITEM_TAG;
            case "block", "event.block" -> ParamKind.BLOCK;
            case "attribute" -> ParamKind.ATTRIBUTE;
            case "effect" -> ParamKind.EFFECT;
            default -> null;
        };
    }
}
