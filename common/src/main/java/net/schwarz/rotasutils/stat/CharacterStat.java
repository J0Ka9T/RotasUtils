package net.schwarz.rotasutils.stat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.schwarz.rotasutils.util.Nbt;

import java.util.Locale;

/**
 * Shared attribute bonus types used by the four {@link CoreStat}s, titles, cards, equipment and jobs.
 *
 * <p>Character stats themselves are fixed in {@link CoreStat}; this class only holds the building blocks.</p>
 */
public final class CharacterStat {
    public enum Operation {
        ADD(AttributeModifier.Operation.ADDITION),
        MULTIPLY_BASE(AttributeModifier.Operation.MULTIPLY_BASE),
        MULTIPLY_TOTAL(AttributeModifier.Operation.MULTIPLY_TOTAL);

        private final AttributeModifier.Operation vanilla;

        Operation(AttributeModifier.Operation vanilla) {
            this.vanilla = vanilla;
        }

        public AttributeModifier.Operation vanilla() {
            return vanilla;
        }
    }

    private CharacterStat() {
    }

    /**
     * One bonus. {@code attribute} is a Minecraft attribute id or a logical combat value
     * ({@code rotas:defense}, {@code rotas:evasion}, {@code rotas:magic_power}); {@code cap} bounds the total bonus.
     */
    public record Effect(String attribute, double perPoint, Operation operation, boolean percent, String label, double cap) {
        public Effect {
            attribute = attribute == null ? "" : attribute.trim();
            perPoint = Double.isFinite(perPoint) ? Math.max(-1000, Math.min(1000, perPoint)) : 0;
            operation = operation == null ? Operation.ADD : operation;
            label = label == null ? "" : label;
            cap = Double.isNaN(cap) || cap <= 0 ? Double.POSITIVE_INFINITY : cap;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("attribute", attribute);
            tag.putDouble("per_point", perPoint);
            tag.putString("operation", operation.name());
            tag.putBoolean("percent", percent);
            tag.putString("label", label);
            if (Double.isFinite(cap)) tag.putDouble("cap", cap);
            return tag;
        }

        public static Effect load(CompoundTag tag) {
            return new Effect(tag.getString("attribute"), tag.getDouble("per_point"),
                    Nbt.readEnum(tag, "operation", Operation.class, Operation.ADD), tag.getBoolean("percent"),
                    tag.getString("label"), tag.contains("cap") ? tag.getDouble("cap") : Double.POSITIVE_INFINITY);
        }

        public String describe(double value) {
            double shown = percent ? value * 100.0 : value;
            String number = shown == Math.rint(shown) ? String.valueOf((long) shown)
                    : String.format(Locale.ROOT, "%.2f", shown).replaceAll("0+$", "").replaceAll("\\.$", "");
            return (shown >= 0 ? "+" : "") + number + (percent ? "%" : "") + (label.isBlank() ? "" : " " + label);
        }
    }
}
