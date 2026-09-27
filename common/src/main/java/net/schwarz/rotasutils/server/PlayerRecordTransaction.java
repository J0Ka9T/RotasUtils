package net.schwarz.rotasutils.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.KernelContext;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.progress.PlayerProgress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlayerRecordTransaction implements KernelContext.Transaction {
    private final RotasData data;
    private final PlayerProgress player;
    private final Map<String, String> originalVariables;
    private final Set<String> originalUnlocks;
    private final Set<String> originalClaims;
    private final Map<String, String> variables;
    private final Set<String> unlocks;
    private final Set<String> claims;
    private final net.minecraft.nbt.CompoundTag originalRpg;
    private final net.schwarz.rotasutils.progress.RpgProfile rpg;
    private final Thread owner = Thread.currentThread();
    /** Staged stacks are delivered only after the record write succeeds. */
    private final List<ItemStack> items = new ArrayList<>();
    private final ServerPlayer recipient;
    private String seed;
    private int rolls;
    private boolean closed;
    private boolean committed;

    public PlayerRecordTransaction(RotasData data, PlayerProgress player) {
        this(data, player, null);
    }

    public PlayerRecordTransaction(RotasData data, PlayerProgress player, ServerPlayer recipient) {
        this.data = data;
        this.player = player;
        originalVariables = Map.copyOf(player.questVariables());
        originalUnlocks = Set.copyOf(player.unlockedQuests());
        originalClaims = Set.copyOf(player.claimedRewards());
        variables = new HashMap<>(originalVariables);
        unlocks = new HashSet<>(originalUnlocks);
        claims = new HashSet<>(originalClaims);
        originalRpg = player.rpg().save();
        rpg = net.schwarz.rotasutils.progress.RpgProfile.load(originalRpg, () -> { });
        this.recipient = recipient;
        data.beginKernelTransaction(player.playerId());
    }

    @Override
    public String variable(String key) {
        checkOpen();
        return variables.get(key);
    }

    @Override
    public void variable(String key, String value) {
        checkOpen();
        if (key == null || !key.matches("rpg\\.[a-z0-9_.-]{1,120}") || value == null || value.length() > 1024) {
            throw new IllegalArgumentException("Invalid kernel variable");
        }
        if (!variables.containsKey(key) && variables.size() >= 4096) {
            throw new IllegalStateException("Player variable budget exceeded");
        }
        variables.put(key, value);
    }

    @Override
    public void unlock(String id) {
        checkOpen();
        if (data.quest(id) == null) new net.schwarz.rotasutils.core.ContentId(id);
        if (!unlocks.contains(id) && unlocks.size() >= 4096) {
            throw new IllegalStateException("Player unlock budget exceeded");
        }
        unlocks.add(id);
    }

    @Override
    public boolean claimed(String receipt) {
        checkOpen();
        return claims.contains(receipt);
    }

    @Override
    public void currency(String id, long amount) { checkOpen(); rpg.currency(id, amount); }

    @Override
    public void reputation(String id, long amount) {
        checkOpen();
        if (!rpg.hasReputation(id)) { rpg.reputation(id, player.reputation(id)); }
        rpg.reputation(id, amount);
    }

    @Override
    public void statPoints(int amount) { checkOpen(); rpg.addStatPoints(amount); }

    @Override
    public void seed(String occurrence) {
        checkOpen();
        if (occurrence == null || occurrence.length() > 400) { throw new IllegalArgumentException("Invalid transaction seed"); }
        seed = occurrence;
    }

    @Override
    public void item(String id, int count) {
        checkOpen();
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                new net.minecraft.resources.ResourceLocation(new ContentId(id).value()));
        if (item == net.minecraft.world.item.Items.AIR) { throw new IllegalArgumentException("Unknown item: " + id); }
        if (count < 1 || count > 64) { throw new IllegalArgumentException("Item count must be 1..64"); }
        stage(List.of(new ItemStack(item, Math.min(count, item.getMaxStackSize()))));
    }

    @Override
    public void profileItem(String profile, int level, int count) {
        checkOpen();
        stage(List.of(ItemFactory.create(catalog(), new ContentId(profile), level, count, LootService.random(seed() + "|item|" + rolls++))));
    }

    @Override
    public void loot(String table, int level, double multiplier) {
        checkOpen();
        var context = recipient == null ? null : new KernelPlayerContext(recipient, Map.of());
        stage(LootService.roll(catalog(), new ContentId(table), seed() + "|" + rolls++, level, multiplier, context));
    }

    private void stage(List<ItemStack> stacks) {
        if (recipient == null) { throw new IllegalStateException("Item grants require an online player transaction"); }
        if (items.size() + stacks.size() > LootService.MAX_STACKS) {
            throw new IllegalStateException("Transaction item budget exceeded");
        }
        items.addAll(stacks);
    }

    private String seed() {
        // Without a reward receipt the caller gets a stable per-player seed rather than a random one.
        return seed != null ? seed : "player|" + player.playerId();
    }

    private net.schwarz.rotasutils.core.ItemCatalog catalog() {
        if (data.kernel() == null) { throw new IllegalStateException("RPG kernel is not ready"); }
        return data.kernel().content().items();
    }

    @Override
    public void claim(String receipt) {
        checkOpen();
        if (receipt == null || !receipt.startsWith("kernel|") || receipt.length() > 400) {
            throw new IllegalArgumentException("Invalid kernel receipt");
        }
        if (!claims.contains(receipt) && claims.size() >= 65536) {
            throw new IllegalStateException("Reward receipt budget exceeded; admin retention review required");
        }
        claims.add(receipt);
    }

    @Override
    public void commit() {
        checkOpen();
        if (data.peek(player.playerId()) != player || !player.questVariables().equals(originalVariables)
                || !player.unlockedQuests().equals(originalUnlocks) || !player.claimedRewards().equals(originalClaims)
                || !player.rpg().save().equals(originalRpg)) {
            throw new IllegalStateException("Player changed while reward was staged");
        }
        if (!items.isEmpty() && !LootService.canDeliver(recipient, items)) {
            throw new IllegalStateException("Inventory and reward mailbox are full; collect pending rewards first");
        }
        if (!rpg.save().equals(originalRpg)) { player.replaceRpg(rpg.save()); }
        player.questVariables().clear();
        player.questVariables().putAll(variables);
        player.unlockedQuests().clear();
        player.unlockedQuests().addAll(unlocks);
        player.claimedRewards().clear();
        player.claimedRewards().addAll(claims);
        player.markDirty();
        data.setDirty();
        committed = true;
        if (!items.isEmpty()) { LootService.deliver(recipient, List.copyOf(items)); }
    }

    public boolean committed() { return committed; }

    private void checkOpen() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Transaction requires its owning server thread");
        }
        if (closed || committed) {
            throw new IllegalStateException("Transaction is already closed or committed");
        }
    }

    @Override
    public void close() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Transaction requires its owning server thread");
        }
        if (!closed) {
            closed = true;
            data.endKernelTransaction(player.playerId());
        }
    }
}
