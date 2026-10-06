package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.schwarz.rotasutils.util.Nbt;

import java.util.ArrayList;
import java.util.List;

public record ZoneDungeon(boolean enabled, int maxPlayers, String keyItem, int keyCount, long entryGold,
                          int timeLimitSeconds, int cooldownSeconds, List<Wave> waves, String bossProfile,
                          long rewardGold, long rewardXp, List<String> rewardItems) {
    public static final int MAX_WAVES = 10;
    public static final int MAX_REWARD_ITEMS = 8;
    public static final int MAX_PLAYERS = 16;
    public static final ZoneDungeon NONE = new ZoneDungeon(false, 4, "", 1, 0, 900, 3600, List.of(), "", 0, 0, List.of());

    public record Wave(String profile, int count) {
        public static final int MAX_COUNT = 24;

        public Wave {
            new ContentId(profile);
            if (count < 1 || count > MAX_COUNT) {
                throw new IllegalArgumentException("A wave has 1.." + MAX_COUNT + " monsters");
            }
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("profile", profile);
            tag.putInt("count", count);
            return tag;
        }

        static Wave load(CompoundTag tag) {
            return new Wave(tag.getString("profile"), Math.max(1, tag.getInt("count")));
        }
    }

    public ZoneDungeon {
        keyItem = keyItem == null ? "" : keyItem.trim();
        bossProfile = bossProfile == null ? "" : bossProfile.trim();
        waves = List.copyOf(waves == null ? List.of() : waves);
        rewardItems = List.copyOf(rewardItems == null ? List.of() : rewardItems);
        if (maxPlayers < 1 || maxPlayers > MAX_PLAYERS) {
            throw new IllegalArgumentException("Party size must be 1.." + MAX_PLAYERS);
        }
        if (keyCount < 1 || keyCount > 64) {
            throw new IllegalArgumentException("Key count must be 1..64");
        }
        if (entryGold < 0 || rewardGold < 0 || rewardXp < 0) {
            throw new IllegalArgumentException("Gold and XP cannot be negative");
        }
        if (timeLimitSeconds < 60 || timeLimitSeconds > 7200) {
            throw new IllegalArgumentException("Time limit must be 60..7200 seconds");
        }
        if (cooldownSeconds < 0 || cooldownSeconds > 604_800) {
            throw new IllegalArgumentException("Cooldown must be 0..604800 seconds");
        }
        if (waves.size() > MAX_WAVES) {
            throw new IllegalArgumentException("A dungeon has at most " + MAX_WAVES + " waves");
        }
        if (rewardItems.size() > MAX_REWARD_ITEMS) {
            throw new IllegalArgumentException("A dungeon gives at most " + MAX_REWARD_ITEMS + " reward items");
        }
        if (!bossProfile.isEmpty()) {
            new ContentId(bossProfile);
        }
        if (enabled && waves.isEmpty() && bossProfile.isEmpty()) {
            throw new IllegalArgumentException("A dungeon needs at least one wave or a final boss");
        }
    }

    public int stages() {
        return waves.size() + (bossProfile.isEmpty() ? 0 : 1);
    }

    public ZoneDungeon withEnabled(boolean next) {
        return new ZoneDungeon(next, maxPlayers, keyItem, keyCount, entryGold, timeLimitSeconds, cooldownSeconds,
                waves, bossProfile, rewardGold, rewardXp, rewardItems);
    }

    public ZoneDungeon withWaves(List<Wave> next) {
        return new ZoneDungeon(enabled, maxPlayers, keyItem, keyCount, entryGold, timeLimitSeconds, cooldownSeconds,
                next, bossProfile, rewardGold, rewardXp, rewardItems);
    }

    public ZoneDungeon withBoss(String next) {
        return new ZoneDungeon(enabled, maxPlayers, keyItem, keyCount, entryGold, timeLimitSeconds, cooldownSeconds,
                waves, next, rewardGold, rewardXp, rewardItems);
    }

    public ZoneDungeon withKey(String item, int count) {
        return new ZoneDungeon(enabled, maxPlayers, item, count, entryGold, timeLimitSeconds, cooldownSeconds,
                waves, bossProfile, rewardGold, rewardXp, rewardItems);
    }

    public ZoneDungeon withRewardItems(List<String> next) {
        return new ZoneDungeon(enabled, maxPlayers, keyItem, keyCount, entryGold, timeLimitSeconds, cooldownSeconds,
                waves, bossProfile, rewardGold, rewardXp, next);
    }

    public ZoneDungeon withNumbers(int players, long gold, int timeLimit, int cooldown, long payGold, long payXp) {
        return new ZoneDungeon(enabled, players, keyItem, keyCount, gold, timeLimit, cooldown,
                waves, bossProfile, payGold, payXp, rewardItems);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("enabled", enabled);
        tag.putInt("max_players", maxPlayers);
        tag.putString("key_item", keyItem);
        tag.putInt("key_count", keyCount);
        tag.putLong("entry_gold", entryGold);
        tag.putInt("time_limit", timeLimitSeconds);
        tag.putInt("cooldown", cooldownSeconds);
        tag.put("waves", Nbt.saveList(waves, Wave::save));
        tag.putString("boss", bossProfile);
        tag.putLong("reward_gold", rewardGold);
        tag.putLong("reward_xp", rewardXp);
        ListTag items = new ListTag();
        rewardItems.forEach(line -> items.add(net.minecraft.nbt.StringTag.valueOf(line)));
        tag.put("reward_items", items);
        return tag;
    }

    public static ZoneDungeon load(CompoundTag tag) {
        List<String> items = new ArrayList<>();
        ListTag list = tag.getList("reward_items", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            items.add(list.getString(i));
        }
        return new ZoneDungeon(tag.getBoolean("enabled"),
                tag.contains("max_players") ? tag.getInt("max_players") : 4,
                tag.getString("key_item"), tag.contains("key_count") ? Math.max(1, tag.getInt("key_count")) : 1,
                tag.getLong("entry_gold"),
                tag.contains("time_limit") ? tag.getInt("time_limit") : 900,
                tag.contains("cooldown") ? tag.getInt("cooldown") : 3600,
                Nbt.loadList(tag, "waves", Wave::load), tag.getString("boss"),
                tag.getLong("reward_gold"), tag.getLong("reward_xp"), items);
    }
}
