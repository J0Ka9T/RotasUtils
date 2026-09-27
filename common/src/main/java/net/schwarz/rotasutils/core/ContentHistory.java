package net.schwarz.rotasutils.core;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class ContentHistory {
    public static final int MAX_REVISIONS = 64;
    public static final int MAX_DRAFTS = 32;
    public static final int MAX_BYTES = 32 * 1024 * 1024;

    public record Revision(long number, long parent, long time, String actor, String reason,
                           String hash, Map<String, String> documents) {
        public Revision { documents = Map.copyOf(documents); }
    }

    public record Draft(long base, long generation, Map<String, String> documents) {
        public Draft { documents = Map.copyOf(documents); }
    }

    public record Preview(List<String> added, List<String> changed, List<String> removed) { }

    private final Runnable changed;
    private Map<String, ContentRegistry.Source> documents = Map.of();
    private List<Revision> revisions = List.of();
    private Map<String, Draft> drafts = Map.of();

    public ContentHistory(Runnable changed) { this.changed = java.util.Objects.requireNonNull(changed); }
    public long revision() { return revisions.isEmpty() ? 0 : revisions.get(revisions.size() - 1).number(); }
    public List<Revision> revisions() { return revisions; }
    public Draft draft(String actor) { return drafts.get(actor); }
    public Revision current() { return revisions.isEmpty() ? null : revisions.get(revisions.size() - 1); }

    public Revision revision(long number) {
        return revisions.stream().filter(value -> value.number() == number).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Revision is not retained: " + number));
    }

    public List<ContentRegistry.Source> sources(Map<String, String> references) {
        return new TreeMap<>(references).values().stream().map(key -> {
            ContentRegistry.Source source = documents.get(key);
            if (source == null) { throw new IllegalStateException("Missing revision document " + key); }
            return source;
        }).toList();
    }

    public Draft begin(String actor) {
        text(actor, 96);
        if (drafts.containsKey(actor)) { throw new IllegalStateException("Draft already exists; apply or discard it first"); }
        if (drafts.size() >= MAX_DRAFTS) { throw new IllegalStateException("Too many retained admin drafts"); }
        Draft draft = new Draft(revision(), 0, current() == null ? Map.of() : current().documents());
        Map<String, Draft> next = new HashMap<>(drafts); next.put(actor, draft);
        install(documents, revisions, next);
        return draft;
    }

    /** Imports a validated disk snapshot without publishing it or replacing another draft. */
    public void importDraft(String actor, ContentRegistry.Prepared prepared, long expected) {
        requireValid(prepared);
        if (expected != revision()) { throw new IllegalStateException("Live content changed during import; import again"); }
        text(actor, 96);
        if (drafts.containsKey(actor)) { throw new IllegalStateException("A draft already exists; apply or discard it before importing"); }
        if (drafts.size() >= MAX_DRAFTS) { throw new IllegalStateException("Too many retained admin drafts"); }
        Map<String, ContentRegistry.Source> pool = new HashMap<>(documents);
        Map<String, String> references = references(prepared.snapshot(), pool);
        Map<String, Draft> next = new HashMap<>(drafts);
        next.put(actor, new Draft(expected, 0, references));
        install(pool, revisions, next);
    }

    public Draft requireDraft(String actor) {
        Draft draft = drafts.get(actor);
        if (draft == null) { throw new IllegalStateException("Create a draft first"); }
        return draft;
    }

    public void put(String actor, JsonObject definition) {
        Draft draft = requireDraft(actor);
        String id = new ContentId(KernelJson.string(definition, "id")).value();
        KernelJson.integer(definition, "schema", 1, 1);
        ContentRegistry.Kind.valueOf(KernelJson.string(definition, "kind").toUpperCase(java.util.Locale.ROOT));
        Map<String, String> references = new TreeMap<>(draft.documents());
        String oldKey = references.get(id);
        ContentRegistry.Source old = oldKey == null ? null : documents.get(oldKey);
        if (old != null && !KernelJson.string(old.document(), "kind").equalsIgnoreCase(KernelJson.string(definition, "kind"))) {
            throw new IllegalArgumentException("Editing cannot change a definition's kind");
        }
        ContentRegistry.Source source = new ContentRegistry.Source("admin/" + id,
                old == null ? ContentRegistry.Layer.HOTFIX : old.layer(), definition);
        byte[] bytes = bytes(source);
        if (bytes.length > ContentPacks.MAX_FILE_BYTES) { throw new IllegalArgumentException("Definition exceeds 256 KiB"); }
        String key = key(source);
        references.put(id, key);
        Map<String, ContentRegistry.Source> pool = new HashMap<>(documents); pool.put(key, source);
        replaceDraft(actor, draft, references, pool);
    }

    public void remove(String actor, ContentId id) {
        Draft draft = requireDraft(actor);
        Map<String, String> references = new TreeMap<>(draft.documents());
        if (references.remove(id.value()) == null) { throw new IllegalArgumentException("Unknown draft definition: " + id); }
        replaceDraft(actor, draft, references, documents);
    }

    private void replaceDraft(String actor, Draft draft, Map<String, String> references, Map<String, ContentRegistry.Source> pool) {
        if (references.size() > 4096) { throw new IllegalArgumentException("Definition count exceeds 4096"); }
        Map<String, Draft> next = new HashMap<>(drafts);
        next.put(actor, new Draft(draft.base(), Math.addExact(draft.generation(), 1), references));
        install(pool, revisions, next);
    }

    public void discard(String actor) {
        requireDraft(actor);
        Map<String, Draft> next = new HashMap<>(drafts); next.remove(actor);
        install(documents, revisions, next);
    }

    public Preview preview(String actor) {
        Map<String, String> before = current() == null ? Map.of() : current().documents();
        Map<String, String> after = requireDraft(actor).documents();
        List<String> added = new ArrayList<>(), edited = new ArrayList<>(), removed = new ArrayList<>();
        new TreeMap<>(after).forEach((id, key) -> {
            if (!before.containsKey(id)) { added.add(id); }
            else if (!before.get(id).equals(key)) { edited.add(id); }
        });
        new TreeMap<>(before).keySet().forEach(id -> { if (!after.containsKey(id)) { removed.add(id); } });
        return new Preview(List.copyOf(added), List.copyOf(edited), List.copyOf(removed));
    }

    public long applyDraft(String actor, long generation, ContentRegistry.Prepared prepared, long time) {
        Draft draft = requireDraft(actor);
        if (draft.generation() != generation || draft.base() != revision()) {
            throw new IllegalStateException("Draft is stale; recreate it from the current revision");
        }
        requireValid(prepared);
        Map<String, String> references = references(prepared.snapshot(), new HashMap<>());
        if (!references.equals(draft.documents())) { throw new IllegalStateException("Validated draft changed before apply"); }
        return commit(prepared, draft.base(), actor, "draft", time, actor);
    }

    public long commit(ContentRegistry.Prepared prepared, long expected, String actor, String reason, long time) {
        return commit(prepared, expected, actor, reason, time, null);
    }

    private long commit(ContentRegistry.Prepared prepared, long expected, String actor, String reason, long time, String discard) {
        requireValid(prepared); text(actor, 96); text(reason, 160);
        if (expected != revision()) { throw new IllegalStateException("Live revision changed; validate again"); }
        Map<String, ContentRegistry.Source> pool = new HashMap<>(documents);
        Map<String, String> references = references(prepared.snapshot(), pool);
        Map<String, Draft> nextDrafts = new HashMap<>(drafts);
        if (discard != null) { nextDrafts.remove(discard); }
        List<Revision> next = new ArrayList<>(revisions);
        if (current() == null || !current().hash().equals(prepared.snapshot().hash())) {
            next.add(new Revision(Math.addExact(revision(), 1), revision(), time, actor, reason,
                    prepared.snapshot().hash(), references));
            if (next.size() > MAX_REVISIONS) { next.remove(0); }
        }
        install(pool, next, nextDrafts);
        return revision();
    }

    private static Map<String, String> references(ContentRegistry.Snapshot snapshot, Map<String, ContentRegistry.Source> pool) {
        Map<String, String> references = new TreeMap<>();
        snapshot.definitions().forEach((id, definition) -> {
            String key = key(definition.source());
            references.put(id.value(), key); pool.putIfAbsent(key, definition.source());
        });
        return references;
    }

    private void install(Map<String, ContentRegistry.Source> pool, List<Revision> next, Map<String, Draft> nextDrafts) {
        Set<String> used = new HashSet<>(); long size = 0;
        for (Revision revision : next) { used.addAll(revision.documents().values()); size += cost(revision.documents()); }
        for (Draft draft : nextDrafts.values()) { used.addAll(draft.documents().values()); size += cost(draft.documents()); }
        Map<String, ContentRegistry.Source> retained = new HashMap<>();
        for (String key : used) {
            ContentRegistry.Source source = pool.get(key);
            if (source == null) { throw new IllegalArgumentException("Missing content document: " + key); }
            retained.put(key, source); size += bytes(source).length + source.name().length() * 3L + 256;
        }
        if (size > MAX_BYTES) { throw new IllegalStateException("Content history exceeds 32 MiB; export/compact retained content first"); }
        documents = Map.copyOf(retained); revisions = List.copyOf(next); drafts = Map.copyOf(nextDrafts);
        changed.run();
    }

    private static long cost(Map<String, String> references) {
        long bytes = 512;
        for (String id : references.keySet()) { bytes += id.length() * 3L + 96; }
        return bytes;
    }

    private static void requireValid(ContentRegistry.Prepared prepared) {
        if (!prepared.valid()) { throw new IllegalArgumentException("Content must validate before apply"); }
    }

    private static void text(String value, int limit) {
        if (value == null || value.isBlank() || value.length() > limit || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid content history metadata");
        }
    }

    private static byte[] bytes(ContentRegistry.Source source) { return source.document().toString().getBytes(StandardCharsets.UTF_8); }

    private static String key(ContentRegistry.Source source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(source.name().getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0);
            digest.update(source.layer().name().getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0);
            return HexFormat.of().formatHex(digest.digest(bytes(source)));
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putInt("schema", 1);
        ListTag pool = new ListTag(), history = new ListTag(), edits = new ListTag();
        new TreeMap<>(documents).forEach((key, source) -> {
            CompoundTag entry = new CompoundTag(); entry.putString("key", key); entry.putString("name", source.name());
            entry.putString("layer", source.layer().name()); entry.putByteArray("body", bytes(source)); pool.add(entry);
        });
        for (Revision revision : revisions) {
            CompoundTag entry = new CompoundTag(); entry.putLong("number", revision.number()); entry.putLong("parent", revision.parent());
            entry.putLong("time", revision.time()); entry.putString("actor", revision.actor()); entry.putString("reason", revision.reason());
            entry.putString("hash", revision.hash()); entry.put("refs", saveReferences(revision.documents())); history.add(entry);
        }
        new TreeMap<>(drafts).forEach((actor, draft) -> {
            CompoundTag entry = new CompoundTag(); entry.putString("actor", actor); entry.putLong("base", draft.base());
            entry.putLong("generation", draft.generation()); entry.put("refs", saveReferences(draft.documents())); edits.add(entry);
        });
        tag.put("documents", pool); tag.put("revisions", history); tag.put("drafts", edits); return tag;
    }

    private static CompoundTag saveReferences(Map<String, String> references) {
        CompoundTag tag = new CompoundTag(); references.forEach(tag::putString); return tag;
    }

    public static ContentHistory load(CompoundTag tag, Runnable changed) {
        if (tag.getInt("schema") != 1) { throw new IllegalArgumentException("Unsupported content history schema"); }
        ContentHistory history = new ContentHistory(() -> { });
        Map<String, ContentRegistry.Source> pool = new HashMap<>(); long size = 0;
        for (Tag value : list(tag, "documents", 4096 * (MAX_REVISIONS + MAX_DRAFTS))) {
            CompoundTag entry = (CompoundTag) value; byte[] body = entry.getByteArray("body"); size += body.length;
            if (body.length > ContentPacks.MAX_FILE_BYTES || size > MAX_BYTES) { throw new IllegalArgumentException("Stored content exceeds byte budget"); }
            String name = entry.getString("name"); text(name, 1024);
            try {
                var source = new ContentRegistry.Source(name, ContentRegistry.Layer.valueOf(entry.getString("layer")),
                        ContentPacks.parse(new String(body, StandardCharsets.UTF_8)));
                String key = key(source);
                if (!key.equals(entry.getString("key")) || pool.put(key, source) != null) {
                    throw new IllegalArgumentException("Corrupt or duplicate stored content document");
                }
            } catch (IOException error) { throw new IllegalArgumentException("Invalid stored JSON", error); }
        }
        List<Revision> revisions = new ArrayList<>(); long last = 0;
        for (Tag value : list(tag, "revisions", MAX_REVISIONS)) {
            CompoundTag entry = (CompoundTag) value;
            long number = entry.getLong("number"), parent = entry.getLong("parent");
            if (number <= last || parent < 0 || parent >= number || (last > 0 && parent != last)) {
                throw new IllegalArgumentException("Invalid content revision chain");
            }
            String actor = entry.getString("actor"), reason = entry.getString("reason"), hash = entry.getString("hash");
            text(actor, 96); text(reason, 160);
            if (!hash.matches("[0-9a-f]{64}")) { throw new IllegalArgumentException("Invalid content hash"); }
            revisions.add(new Revision(number, parent, entry.getLong("time"), actor, reason, hash, loadReferences(entry, pool)));
            last = number;
        }
        Map<String, Draft> drafts = new HashMap<>();
        for (Tag value : list(tag, "drafts", MAX_DRAFTS)) {
            CompoundTag entry = (CompoundTag) value; String actor = entry.getString("actor"); text(actor, 96);
            long base = entry.getLong("base"), generation = entry.getLong("generation");
            if (base < 0 || base > last || generation < 0 || drafts.containsKey(actor)) {
                throw new IllegalArgumentException("Invalid stored draft");
            }
            drafts.put(actor, new Draft(base, generation, loadReferences(entry, pool)));
        }
        history.install(pool, revisions, drafts);
        ContentHistory result = new ContentHistory(changed);
        result.documents = history.documents; result.revisions = history.revisions; result.drafts = history.drafts;
        return result;
    }

    private static ListTag list(CompoundTag tag, String key, int limit) {
        if (!(tag.get(key) instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
                || list.size() > limit) { throw new IllegalArgumentException("Invalid history list: " + key); }
        return list;
    }

    private static Map<String, String> loadReferences(CompoundTag entry, Map<String, ContentRegistry.Source> pool) {
        if (!entry.contains("refs", Tag.TAG_COMPOUND)) { throw new IllegalArgumentException("Missing history references"); }
        CompoundTag refs = entry.getCompound("refs");
        if (refs.size() > 4096) { throw new IllegalArgumentException("Too many history references"); }
        Map<String, String> result = new TreeMap<>();
        for (String id : refs.getAllKeys()) {
            new ContentId(id); String key = refs.getString(id); ContentRegistry.Source source = pool.get(key);
            if (source == null || !id.equals(KernelJson.string(source.document(), "id"))) {
                throw new IllegalArgumentException("Invalid history reference " + id);
            }
            result.put(id, key);
        }
        return result;
    }
}
