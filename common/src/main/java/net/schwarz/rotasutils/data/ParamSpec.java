package net.schwarz.rotasutils.data;

/**
 * Declares one editable field of an objective, requirement, reward or skill effect.
 *
 * <p>Every configurable value in RotasUtils is described by a spec so the admin
 * screens can render a working editor without any type specific screen code. This
 * is what removes the need to hand edit JSON.
 */
public record ParamSpec(String key, ParamKind kind, String label, String defaultValue, boolean advanced) {

    /**
     * Field labels are shown in Thai. The lookup is keyed by the English label, so two specs with
     * the same label always read the same, and editors that match a field back to its spec by label
     * keep matching. A label without a Thai entry keeps its English text.
     */
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

    /** Marks this field as only visible in the quest creator's Advanced Mode. */
    public ParamSpec asAdvanced() {
        return new ParamSpec(key, kind, label, defaultValue, true);
    }

    public enum ParamKind {
        /** Whole number, edited with a text box plus +/- steppers. */
        INT,
        /** Decimal number. */
        DOUBLE,
        /** Toggle button. */
        BOOL,
        /** Single line of text. */
        STRING,
        /** Multi line text. */
        TEXT,
        /** Item, picked from the searchable item browser. */
        ITEM,
        /** Item tag id. */
        ITEM_TAG,
        /** Entity type, picked from the searchable entity browser. */
        ENTITY,
        /** Entity type tag id. */
        ENTITY_TAG,
        /** Loaded entity namespaces, including installed mods. */
        ENTITY_NAMESPACE,
        /** Biomes from the connected world's registry. */
        BIOME,
        /** Block, picked from the searchable block browser. */
        BLOCK,
        /** Dimension id, picked from the loaded dimension list. */
        DIMENSION,
        /** World position, picked with the world selection tool. */
        POS,
        /** Identity of an existing living entity, selected in the world. */
        NPC,
        /** Danger rank dropdown. */
        RANK,
        /** Quest id, picked from the quest browser. */
        QUEST,
        /** Skill node id, picked from the skill browser. */
        SKILL,
        /** Skill category id, picked from the category list. */
        CATEGORY,
        /** Quest board id, picked from the board browser or world selection. */
        BOARD,
        /** Authored kernel merchant. */
        MERCHANT,
        /** Mob effect id. */
        EFFECT,
        /** Attribute id. */
        ATTRIBUTE,
        /** Free text used as a command line. */
        COMMAND
    }
}
