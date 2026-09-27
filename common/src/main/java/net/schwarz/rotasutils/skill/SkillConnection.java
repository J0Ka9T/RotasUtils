package net.schwarz.rotasutils.skill;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.util.Nbt;

/** A directed link from a prerequisite node to this node. */
public final class SkillConnection {
    private String fromId;
    private Type type = Type.NORMAL;
    /** Minimum rank the prerequisite must hold for a {@link Type#NORMAL} link. */
    private int requiredRank = 1;

    public SkillConnection(String fromId) {
        this.fromId = fromId;
    }

    public String fromId() {
        return fromId;
    }

    public void setFromId(String fromId) {
        this.fromId = fromId;
    }

    public Type type() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public int requiredRank() {
        return Math.max(1, requiredRank);
    }

    public void setRequiredRank(int requiredRank) {
        this.requiredRank = requiredRank;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("from", fromId);
        tag.putString("type", type.name());
        tag.putInt("rank", requiredRank);
        return tag;
    }

    public static SkillConnection load(CompoundTag tag) {
        SkillConnection connection = new SkillConnection(tag.getString("from"));
        connection.type = Nbt.readEnum(tag, "type", Type.class, Type.NORMAL);
        connection.requiredRank = Math.max(1, tag.getInt("rank"));
        return connection;
    }

    public enum Type {
        /** Prerequisite must be unlocked. Drawn as a solid line. */
        NORMAL("Normal prerequisite", 0xFFFFFFFF),
        /** Every NORMAL/ALL link into the node must be satisfied. Drawn as a double line. */
        REQUIRE_ALL("Require all connected", 0xFF66CCFF),
        /** Any one of the ANY links is enough. Drawn as a dashed line. */
        REQUIRE_ANY("Require any connected", 0xFF88FF88),
        /** Taking this node locks the source node's branch. Drawn as a red dotted line. */
        EXCLUSIVE("Exclusive branch", 0xFFFF5555),
        /** Cosmetic only, imposes no requirement. Drawn faint. */
        VISUAL_ONLY("Visual only", 0x66FFFFFF),
        /** Hidden from players until the source node is unlocked. */
        HIDDEN("Hidden connection", 0xFFAA88FF);

        public static final Type[] VALUES = values();

        private final String display;
        private final int color;

        Type(String display, int color) {
            this.display = display;
            this.color = color;
        }

        public String display() {
            return net.schwarz.rotasutils.util.ThaiText.label("connection_type", this, display);
        }

        public int color() {
            return color;
        }

        public boolean gating() {
            return this != VISUAL_ONLY;
        }
    }
}
