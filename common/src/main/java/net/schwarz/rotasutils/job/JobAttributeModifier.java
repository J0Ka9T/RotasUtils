package net.schwarz.rotasutils.job;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.stat.CharacterStat;

public record JobAttributeModifier(String label, String attribute, double amount, CharacterStat.Operation operation) {
    public JobAttributeModifier {
        label = label == null ? "" : label.trim();
        attribute = attribute == null ? "" : attribute.trim();
        if (label.isEmpty() || attribute.isEmpty()) throw new IllegalArgumentException("Job modifier needs a label and attribute");
        if (!Double.isFinite(amount) || Math.abs(amount) > 1000) throw new IllegalArgumentException("Job modifier amount is invalid");
        operation = operation == null ? CharacterStat.Operation.ADD : operation;
    }

    public double amount(JobSlot slot, double subJobCap) {
        double scale = slot == JobSlot.SUB ? Math.max(0, Math.min(1, subJobCap)) : 1;
        return amount * scale;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("label", label);
        tag.putString("attribute", attribute);
        tag.putDouble("amount", amount);
        tag.putString("operation", operation.name());
        return tag;
    }

    public static JobAttributeModifier load(CompoundTag tag) {
        CharacterStat.Operation operation;
        try { operation = CharacterStat.Operation.valueOf(tag.getString("operation")); }
        catch (IllegalArgumentException ignored) { operation = CharacterStat.Operation.ADD; }
        return new JobAttributeModifier(tag.getString("label"), tag.getString("attribute"), tag.getDouble("amount"), operation);
    }
}
