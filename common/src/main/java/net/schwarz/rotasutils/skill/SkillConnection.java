package net.schwarz.rotasutils.skill;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.util.Nbt;

public final class SkillConnection {
    private String fromId;
    private Type type = Type.NORMAL;
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
        NORMAL("Normal prerequisite", 0xFFFFFFFF),
        REQUIRE_ALL("Require all connected", 0xFF66CCFF),
        REQUIRE_ANY("Require any connected", 0xFF88FF88),
        EXCLUSIVE("Exclusive branch", 0xFFFF5555),
        VISUAL_ONLY("Visual only", 0x66FFFFFF),
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
