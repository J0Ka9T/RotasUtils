package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.ArrayList;
import java.util.List;

public final class ConfigHistory {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private CompoundTag state = new CompoundTag();
    private final Runnable changed;

    public record Draft(long generation, CompoundTag base, CompoundTag value) {
        public Draft { base = base.copy(); value = value.copy(); }
        @Override public CompoundTag base() { return base.copy(); }
        @Override public CompoundTag value() { return value.copy(); }
    }

    public ConfigHistory(Runnable changed) { this.changed = changed; state.putInt("schema", 1); }

    private static String key(String actor, String domain) {
        if (actor == null || actor.isBlank() || actor.length() > 96 || domain == null
                || domain.isBlank() || domain.length() > 256 || actor.contains("|") || domain.contains("|")) {
            throw new IllegalArgumentException("Invalid configuration draft identity");
        }
        return actor + "|" + domain;
    }

    public Draft draft(String actor, String domain) {
        var drafts = state.getCompound("drafts"); String key = key(actor, domain);
        if (!drafts.contains(key, 10)) { return null; }
        var tag = drafts.getCompound(key);
        return new Draft(tag.getLong("generation"), tag.getCompound("base"), tag.getCompound("value"));
    }

    public void stage(String actor, String domain, CompoundTag baseline, CompoundTag value, long expected) {
        Draft previous = draft(actor, domain);
        if ((previous == null ? -1 : previous.generation()) != expected) {
            throw new IllegalStateException("Draft changed; reload its latest version before saving");
        }
        var next = state.copy(); var drafts = next.getCompound("drafts");
        if (previous == null && drafts.size() >= 64) { throw new IllegalStateException("Configuration draft limit reached"); }
        var tag = new CompoundTag(); tag.putLong("generation", Math.addExact(expected, 1));
        tag.put("base", baseline.copy()); tag.put("value", value.copy());
        drafts.put(key(actor, domain), tag); next.put("drafts", drafts); install(next);
    }

    public Draft check(String actor, String domain, CompoundTag live, long generation) {
        Draft draft = draft(actor, domain);
        if (draft == null) { throw new IllegalStateException("Save a draft first"); }
        if (draft.generation() != generation || !draft.base().equals(live)) {
            throw new IllegalStateException("Configuration changed since editing began; compare with current values before applying");
        }
        return draft;
    }

    public void discard(String actor, String domain, long generation) {
        Draft draft = draft(actor, domain);
        if (draft == null || draft.generation() != generation) { throw new IllegalStateException("Draft changed"); }
        var next = state.copy(); next.getCompound("drafts").remove(key(actor, domain)); install(next);
    }

    public void discard(String actor, String domain) {
        var next = state.copy();
        var drafts = next.getCompound("drafts");
        if (drafts.contains(key(actor, domain))) {
            drafts.remove(key(actor, domain));
            install(next);
        }
    }

    public void applied(String actor, String domain, String reason) {
        Draft draft = draft(actor, domain);
        if (draft == null) { throw new IllegalStateException("No configuration draft"); }
        var next = state.copy(); var revisions = next.getList("history", 10);
        var entry = new CompoundTag(); entry.putString("domain", domain); entry.putString("actor", actor);
        entry.putString("reason", reason); entry.putLong("time", System.currentTimeMillis());
        entry.putLong("revision", Math.addExact(next.getLong("revision"), 1));
        entry.put("before", draft.base()); entry.put("after", draft.value()); revisions.add(entry);
        var retained = next.getCompound("retained"); retained.put(domain, draft.value()); next.put("retained", retained);
        while (revisions.size() > 32) { revisions.remove(0); }
        next.putLong("revision", entry.getLong("revision")); next.put("history", revisions);
        next.getCompound("drafts").remove(key(actor, domain)); install(next);
    }

    public List<CompoundTag> revisions(String domain) {
        List<CompoundTag> result = new ArrayList<>();
        for (var tag : state.getList("history", 10)) {
            var entry = (CompoundTag) tag;
            if (entry.getString("domain").equals(domain)) { result.add(entry.copy()); }
        }
        return List.copyOf(result);
    }

    public CompoundTag save() { return state.copy(); }
    public CompoundTag retained(String domain) { return state.getCompound("retained").getCompound(domain).copy(); }
    public List<String> domains(String actor) {
        String prefix = actor + "|";
        return state.getCompound("drafts").getAllKeys().stream().filter(key -> key.startsWith(prefix)).map(key -> key.substring(prefix.length())).sorted().toList();
    }
    public static ConfigHistory load(CompoundTag tag, Runnable changed) {
        if (tag.getInt("schema") != 1) { throw new IllegalArgumentException("Unsupported configuration history schema"); }
        var history = new ConfigHistory(changed); history.validate(tag); history.state = tag.copy(); return history;
    }

    private void validate(CompoundTag next) {
        if (next.getCompound("drafts").size() > 64 || next.getList("history", 10).size() > 32
                || next.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("Configuration history exceeds its storage budget");
        }
    }
    private void install(CompoundTag next) { validate(next); state = next; changed.run(); }
}
