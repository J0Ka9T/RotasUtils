package net.schwarz.rotasutils.server.horse;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.core.HorseBreeding;
import net.schwarz.rotasutils.core.HorseGacha;
import net.schwarz.rotasutils.core.HorseTrait;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every player's stable, kept in its own save file so horse snapshots (a SWEM horse with tack and saddlebags
 * is several kilobytes) never bloat the progress record that syncs to clients.
 *
 * <p>Each horse has exactly one record and one live generation. A summoned horse carries a scoreboard tag
 * naming its record and generation; any copy with an older generation is refused when its chunk loads, which
 * is what makes summon, store, death and trade immune to duplication.</p>
 */
public final class StableData extends SavedData {
    public static final String FILE_ID = Rotasutils.MOD_ID + "_stables";
    public static final int MAX_HORSES_PER_PLAYER = 1000;

    public enum Origin { GACHA, BRED, WILD, ADMIN }

    public static final class Horse {
        public String id;
        public UUID owner;
        public String name = "";
        public Origin origin = Origin.GACHA;
        /** Birth rarity from the draw; null for bred, wild and admin horses. */
        public HorseGacha.Rarity rarity;
        public int[] startLevels = {1, 1, 1, 1};
        public String coat = "";
        public boolean secretCoat;
        public boolean rareCoat;
        public CompoundTag snapshot = new CompoundTag();
        /** The entity currently in the world, or null while the horse is in the stable. */
        public UUID active;
        public int generation;
        /** Market price; 0 when not listed. */
        public long listedPrice;
        public long recoverUntil;
        public long created;
        public long potionDay;
        public int potionUses;
        /** Pedigree: parent record ids and names (names survive a parent's release or sale). */
        public String sire = "", dam = "", sireName = "", damName = "";
        /** 0 for founders (draw, wild, admin); a foal is one more than its older parent. */
        public int lineage;
        public List<HorseTrait> traits = new ArrayList<>();
        /** Breedings left; -1 until the rules first assign it. */
        public int breedsLeft = -1;
        public long breedReadyAt;
        /** Epoch seconds a foal is born; until then it waits in its slot, unknown. 0 = born. */
        public long bornAt;
        /** Fee other players pay to breed with this horse; 0 = not offered at stud. */
        public long studFee;
        /** Legacy draw horses get their traits rolled once, on first sight. */
        public boolean traitsRolled;

        public boolean unborn(long nowSeconds) {
            return bornAt > nowSeconds;
        }

        public HorseBreeding.Parent parent() {
            return new HorseBreeding.Parent(id, levels(), lineage, traits, sire, dam, secretCoat, rareCoat);
        }

        public int[] levels() {
            return SwemCompat.levels(snapshot);
        }

        /** Levels trained since the horse joined the stable, for the leaderboard. */
        public int trainedLevels() {
            int[] now = levels();
            int trained = 0;
            for (int i = 0; i < 4; i++) {
                trained += Math.max(0, now[i] - startLevels[i]);
            }
            return trained;
        }

        public String tag() {
            return "rotas_horse:" + id + ":" + generation;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", id);
            tag.putUUID("owner", owner);
            tag.putString("name", name);
            tag.putString("origin", origin.name());
            if (rarity != null) tag.putString("rarity", rarity.name());
            tag.putIntArray("start", startLevels);
            tag.putString("coat", coat);
            tag.putBoolean("secret", secretCoat);
            tag.putBoolean("rare", rareCoat);
            tag.put("snapshot", snapshot);
            if (active != null) tag.putUUID("active", active);
            tag.putInt("generation", generation);
            tag.putLong("price", listedPrice);
            tag.putLong("recover", recoverUntil);
            tag.putLong("created", created);
            tag.putLong("potion_day", potionDay);
            tag.putInt("potion_uses", potionUses);
            tag.putString("sire", sire);
            tag.putString("dam", dam);
            tag.putString("sire_name", sireName);
            tag.putString("dam_name", damName);
            tag.putInt("lineage", lineage);
            ListTag traitTag = new ListTag();
            traits.forEach(trait -> traitTag.add(StringTag.valueOf(trait.name())));
            tag.put("traits", traitTag);
            tag.putInt("breeds_left", breedsLeft);
            tag.putLong("breed_ready", breedReadyAt);
            tag.putLong("born", bornAt);
            tag.putLong("stud_fee", studFee);
            tag.putBoolean("traits_rolled", traitsRolled);
            return tag;
        }

        static Horse load(CompoundTag tag) {
            Horse horse = new Horse();
            horse.id = tag.getString("id");
            horse.owner = tag.getUUID("owner");
            horse.name = tag.getString("name");
            try { horse.origin = Origin.valueOf(tag.getString("origin")); } catch (IllegalArgumentException ignored) { horse.origin = Origin.ADMIN; }
            if (tag.contains("rarity")) {
                try { horse.rarity = HorseGacha.Rarity.valueOf(tag.getString("rarity")); } catch (IllegalArgumentException ignored) { horse.rarity = null; }
            }
            int[] start = tag.getIntArray("start");
            horse.startLevels = start.length == 4 ? start : new int[]{1, 1, 1, 1};
            horse.coat = tag.getString("coat");
            horse.secretCoat = tag.getBoolean("secret");
            horse.rareCoat = tag.getBoolean("rare");
            horse.snapshot = tag.getCompound("snapshot");
            horse.active = tag.hasUUID("active") ? tag.getUUID("active") : null;
            horse.generation = tag.getInt("generation");
            horse.listedPrice = Math.max(0, tag.getLong("price"));
            horse.recoverUntil = tag.getLong("recover");
            horse.created = tag.getLong("created");
            horse.potionDay = tag.getLong("potion_day");
            horse.potionUses = tag.getInt("potion_uses");
            horse.sire = tag.getString("sire");
            horse.dam = tag.getString("dam");
            horse.sireName = tag.getString("sire_name");
            horse.damName = tag.getString("dam_name");
            horse.lineage = Math.max(0, tag.getInt("lineage"));
            ListTag traitTag = tag.getList("traits", Tag.TAG_STRING);
            List<String> names = new ArrayList<>();
            for (int i = 0; i < traitTag.size(); i++) names.add(traitTag.getString(i));
            horse.traits = new ArrayList<>(HorseTrait.parse(names));
            horse.breedsLeft = tag.contains("breeds_left") ? tag.getInt("breeds_left") : -1;
            horse.breedReadyAt = tag.getLong("breed_ready");
            horse.bornAt = tag.getLong("born");
            horse.studFee = Math.max(0, tag.getLong("stud_fee"));
            horse.traitsRolled = tag.getBoolean("traits_rolled");
            return horse;
        }
    }

    public static final class Stable {
        public int sinceRare, sinceEpic, sinceLegendary;
        public int purchasedSlots;
        public long salesDay;
        public int salesToday;
        public long lastSummonMillis;
        public int totalPulls;

        HorseGacha.Pity pity() {
            return new HorseGacha.Pity(sinceRare, sinceEpic, sinceLegendary);
        }

        void pity(HorseGacha.Pity pity) {
            sinceRare = pity.sinceRare();
            sinceEpic = pity.sinceEpic();
            sinceLegendary = pity.sinceLegendary();
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putIntArray("pity", new int[]{sinceRare, sinceEpic, sinceLegendary});
            tag.putInt("slots", purchasedSlots);
            tag.putLong("sales_day", salesDay);
            tag.putInt("sales", salesToday);
            tag.putInt("pulls", totalPulls);
            return tag;
        }

        static Stable load(CompoundTag tag) {
            Stable stable = new Stable();
            int[] pity = tag.getIntArray("pity");
            if (pity.length == 3) {
                stable.sinceRare = pity[0];
                stable.sinceEpic = pity[1];
                stable.sinceLegendary = pity[2];
            }
            stable.purchasedSlots = Math.max(0, tag.getInt("slots"));
            stable.salesDay = tag.getLong("sales_day");
            stable.salesToday = tag.getInt("sales");
            stable.totalPulls = tag.getInt("pulls");
            return stable;
        }
    }

    private final Map<String, Horse> horses = new LinkedHashMap<>();
    private final Map<UUID, Stable> stables = new LinkedHashMap<>();

    public static StableData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(StableData::load, StableData::new, FILE_ID);
    }

    public Horse horse(String id) {
        return id == null ? null : horses.get(id);
    }

    public Collection<Horse> all() {
        return horses.values();
    }

    public List<Horse> owned(UUID owner) {
        List<Horse> result = new ArrayList<>();
        for (Horse horse : horses.values()) {
            if (horse.owner.equals(owner)) result.add(horse);
        }
        return result;
    }

    public List<Horse> studs() {
        List<Horse> result = new ArrayList<>();
        for (Horse horse : horses.values()) {
            if (horse.studFee > 0) result.add(horse);
        }
        return result;
    }

    public List<Horse> listed() {
        List<Horse> result = new ArrayList<>();
        for (Horse horse : horses.values()) {
            if (horse.listedPrice > 0) result.add(horse);
        }
        return result;
    }

    public Horse byActive(UUID entity) {
        for (Horse horse : horses.values()) {
            if (entity.equals(horse.active)) return horse;
        }
        return null;
    }

    public Stable stable(UUID owner) {
        return stables.computeIfAbsent(owner, key -> new Stable());
    }

    public void put(Horse horse) {
        horses.put(horse.id, horse);
        setDirty();
    }

    public void remove(String id) {
        horses.remove(id);
        setDirty();
    }

    public String newId() {
        String id;
        do {
            id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        } while (horses.containsKey(id));
        return id;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        horses.values().forEach(horse -> list.add(horse.save()));
        tag.put("horses", list);
        CompoundTag stableTag = new CompoundTag();
        stables.forEach((owner, stable) -> stableTag.put(owner.toString(), stable.save()));
        tag.put("stables", stableTag);
        return tag;
    }

    public static StableData load(CompoundTag tag) {
        StableData data = new StableData();
        ListTag list = tag.getList("horses", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                Horse horse = Horse.load(list.getCompound(i));
                if (!horse.id.isEmpty()) data.horses.put(horse.id, horse);
            } catch (RuntimeException malformed) {
                Rotasutils.LOG.error("Skipping a malformed stable horse: {}", malformed.toString());
            }
        }
        CompoundTag stableTag = tag.getCompound("stables");
        for (String key : stableTag.getAllKeys()) {
            try {
                data.stables.put(UUID.fromString(key), Stable.load(stableTag.getCompound(key)));
            } catch (IllegalArgumentException malformed) {
                Rotasutils.LOG.error("Skipping a malformed stable owner: {}", key);
            }
        }
        return data;
    }
}
