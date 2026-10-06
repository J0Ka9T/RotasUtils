package net.schwarz.rotasutils.data;

public record ParamSpec(String key, ParamKind kind, String label, String defaultValue, boolean advanced) {
    public ParamSpec {
        if (label != null) {
            String key0 = "rotasutils.param." + label.toLowerCase(java.util.Locale.ROOT)
                    .replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
            if (net.schwarz.rotasutils.util.ThaiText.has(key0)) {
                label = net.schwarz.rotasutils.util.ThaiText.t(key0);
            }
        }
    }

    public ParamSpec(String key, ParamKind kind, String label, String defaultValue) {
        this(key, kind, label, defaultValue, false);
    }

    public ParamSpec asAdvanced() {
        return new ParamSpec(key, kind, label, defaultValue, true);
    }

    public enum ParamKind {
        INT,
        DOUBLE,
        BOOL,
        STRING,
        TEXT,
        ITEM,
        ITEM_TAG,
        ENTITY,
        ENTITY_TAG,
        ENTITY_NAMESPACE,
        BIOME,
        BLOCK,
        DIMENSION,
        POS,
        NPC,
        RANK,
        QUEST,
        SKILL,
        CATEGORY,
        BOARD,
        MERCHANT,
        EFFECT,
        ATTRIBUTE,
        COMMAND
    }
}
