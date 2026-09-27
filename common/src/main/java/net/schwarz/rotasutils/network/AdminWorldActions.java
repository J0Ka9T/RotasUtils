package net.schwarz.rotasutils.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.core.CardIndex;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.item.CardItem;
import net.schwarz.rotasutils.mine.MiningSite;
import net.schwarz.rotasutils.nemesis.Nemesis;
import net.schwarz.rotasutils.registry.RotasRegistry;
import net.schwarz.rotasutils.server.DailyService;
import net.schwarz.rotasutils.server.LootService;
import net.schwarz.rotasutils.server.MiningService;
import net.schwarz.rotasutils.server.NemesisService;
import net.schwarz.rotasutils.server.SeasonService;

import java.util.List;
import java.util.Set;

/**
 * Admin screens for the systems that were command-only: mining sites, nemeses, and per-player tools
 * (give a card, reset daily missions). Each action does exactly what its {@code /rotas} command does,
 * re-checked on the server; the caller has already checked admin permission. Every mining or nemesis
 * change re-sends the screen state, so the screen never shows stale data.
 */
public final class AdminWorldActions {
    public static final Set<String> ACTIONS = Set.of(
            "mine_admin_open", "mine_create", "mine_delete", "mine_save", "mine_add_look", "mine_unadd_look",
            "mine_scan", "mine_refill",
            "nemesis_admin_open", "nemesis_remove", "nemesis_summon",
            "player_tool_card", "player_tool_daily_reset");

    private AdminWorldActions() {
    }

    public static void handle(ServerPlayer player, RotasData data, String action, CompoundTag payload) {
        try {
            switch (action) {
                case "mine_admin_open" -> openMines(player, data, payload.getString("select"));
                case "mine_create" -> createMine(player, data, payload.getString("id").trim());
                case "mine_delete" -> {
                    String id = payload.getString("id");
                    boolean done = data.removeMiningSite(id);
                    audit(player, data, done, "deleted mining site " + id);
                    openMines(player, data, "");
                }
                case "mine_save" -> saveMine(player, data, payload);
                case "mine_add_look", "mine_unadd_look" -> lookNode(player, data, payload.getString("id"),
                        action.equals("mine_add_look"));
                case "mine_scan" -> scanMine(player, data, payload);
                case "mine_refill" -> {
                    MiningSite site = data.miningSites().get(payload.getString("id"));
                    ServerLevel level = site == null ? null : level(player, site.dimension());
                    if (site == null || level == null) {
                        RotasNetwork.feedback(player, false, "Unknown site or dimension");
                        return;
                    }
                    int restored = MiningService.refill(level, site);
                    data.setDirty();
                    RotasNetwork.feedback(player, true, "Refilled " + restored + " blocks");
                    openMines(player, data, site.id());
                }
                case "nemesis_admin_open" -> openNemeses(player, data);
                case "nemesis_remove" -> {
                    int id = payload.getInt("id");
                    boolean done = NemesisService.remove(player.server, data, id);
                    audit(player, data, done, "removed nemesis #" + id);
                    RotasNetwork.feedback(player, done, done ? "Removed nemesis #" + id : "Unknown nemesis #" + id);
                    openNemeses(player, data);
                }
                case "nemesis_summon" -> {
                    Nemesis nemesis = data.nemesis(payload.getInt("id"));
                    if (nemesis == null || NemesisService.body(player.server, nemesis) != null) {
                        RotasNetwork.feedback(player, false, nemesis == null ? "Unknown nemesis" : "It is already in the world");
                        return;
                    }
                    var mob = NemesisService.summon(player.serverLevel(), player.blockPosition(), nemesis, data,
                            SeasonService.rules(data).nemesis);
                    RotasNetwork.feedback(player, mob != null, mob != null ? "Summoned " + nemesis.displayName() : "No room here");
                    openNemeses(player, data);
                }
                case "player_tool_card" -> giveCard(player, payload);
                case "player_tool_daily_reset" -> {
                    ServerPlayer target = target(player, payload.getString("player"));
                    if (target == null) return;
                    DailyService.reset(data.progress(target.getUUID()));
                    data.setDirty();
                    RotasNetwork.syncProgress(target);
                    audit(player, data, true, "reset daily missions of " + target.getGameProfile().getName());
                    RotasNetwork.feedback(player, true, "Daily missions reset: " + target.getGameProfile().getName());
                }
                default -> {
                }
            }
        } catch (RuntimeException failure) {
            RotasNetwork.feedback(player, false, "Failed: " + failure.getMessage());
        }
    }

    // ---- Mining sites ---------------------------------------------------------------------------

    private static void openMines(ServerPlayer player, RotasData data, String select) {
        CompoundTag state = new CompoundTag();
        ListTag sites = new ListTag();
        long now = System.currentTimeMillis() / 1000L;
        for (MiningSite site : data.miningSites().values()) {
            CompoundTag row = new CompoundTag();
            row.putString("id", site.id());
            row.putString("name", site.name());
            row.putString("dimension", site.dimension());
            row.putInt("nodes", site.nodes().size());
            row.putInt("depleted", site.depletedCount(now));
            row.putInt("respawn", site.respawnSeconds());
            row.putLong("gold_min", site.goldMin());
            row.putLong("gold_max", site.goldMax());
            row.putLong("xp", site.xp());
            row.putInt("limit", site.dailyLimit());
            row.putString("depleted_block", site.depletedBlock());
            ListTag loot = new ListTag();
            site.loot().forEach(line -> loot.add(StringTag.valueOf(line)));
            row.put("loot", loot);
            sites.add(row);
        }
        state.put("sites", sites);
        state.putString("select", select);
        RotasNetwork.openScreen(player, "mine_admin", state);
    }

    private static void createMine(ServerPlayer player, RotasData data, String id) {
        if (!id.matches("[a-z0-9_]{1,32}") || data.miningSites().containsKey(id)) {
            RotasNetwork.feedback(player, false, data.miningSites().containsKey(id)
                    ? "Site already exists: " + id : "Id must be 1-32 of a-z 0-9 _");
            return;
        }
        data.putMiningSite(new MiningSite(id, player.level().dimension().location().toString()));
        audit(player, data, true, "created mining site " + id + " in " + player.level().dimension().location());
        openMines(player, data, id);
    }

    private static void saveMine(ServerPlayer player, RotasData data, CompoundTag payload) {
        MiningSite site = data.miningSites().get(payload.getString("id"));
        if (site == null) {
            RotasNetwork.feedback(player, false, "Unknown site");
            return;
        }
        String depleted = payload.getString("depleted_block").trim();
        if (!depleted.isEmpty() && !BuiltInRegistries.BLOCK.containsKey(ResourceLocation.tryParse(depleted))) {
            RotasNetwork.feedback(player, false, "Unknown block: " + depleted);
            return;
        }
        site.setName(payload.getString("name"));
        site.setRespawnSeconds(payload.getInt("respawn"));
        site.setGold(payload.getLong("gold_min"), payload.getLong("gold_max"));
        site.setXp(payload.getLong("xp"));
        site.setDailyLimit(payload.getInt("limit"));
        site.setDepletedBlock(depleted);
        site.loot().clear();
        int rejected = 0;
        for (Tag line : payload.getList("loot", Tag.TAG_STRING)) {
            String text = line.getAsString().trim();
            if (!text.isEmpty() && !site.addLoot(text)) rejected++;
        }
        data.setDirty();
        audit(player, data, true, "saved mining site " + site.id());
        RotasNetwork.feedback(player, rejected == 0, rejected == 0 ? "Saved " + site.name()
                : "Saved, but " + rejected + " loot line(s) were rejected (format: minecraft:diamond 1-3 @0.5)");
        openMines(player, data, site.id());
    }

    private static void lookNode(ServerPlayer player, RotasData data, String id, boolean add) {
        MiningSite site = data.miningSites().get(id);
        BlockPos pos = lookedAt(player);
        if (site == null || pos == null) {
            RotasNetwork.feedback(player, false, site == null ? "Unknown site" : "Look at a block within 6 blocks first");
            return;
        }
        boolean done;
        if (add) {
            String block = String.valueOf(BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(pos).getBlock()));
            done = site.addNode(pos.asLong(), block);
            RotasNetwork.feedback(player, done, done ? "Added " + block + " (" + site.nodes().size() + " blocks)"
                    : "Not added: already in, air, or site full (" + MiningSite.MAX_NODES + ")");
        } else {
            done = site.removeNode(pos.asLong());
            RotasNetwork.feedback(player, done, done ? "Removed (" + site.nodes().size() + " blocks)" : "That block is not in the site");
        }
        if (done) data.setDirty();
        openMines(player, data, id);
    }

    private static void scanMine(ServerPlayer player, RotasData data, CompoundTag payload) {
        MiningSite site = data.miningSites().get(payload.getString("id"));
        String block = payload.getString("block").trim();
        int radius = Math.max(1, Math.min(16, payload.getInt("radius")));
        if (site == null || !BuiltInRegistries.BLOCK.containsKey(ResourceLocation.tryParse(block))) {
            RotasNetwork.feedback(player, false, site == null ? "Unknown site" : "Unknown block: " + block);
            return;
        }
        int added = MiningService.scan(player.serverLevel(), site, player.blockPosition(), radius, block);
        data.setDirty();
        RotasNetwork.feedback(player, added > 0, "Added " + added + " × " + block + " within " + radius + " blocks of you");
        openMines(player, data, site.id());
    }

    private static BlockPos lookedAt(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(6.0));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }

    private static ServerLevel level(ServerPlayer player, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        return id == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    // ---- Nemeses --------------------------------------------------------------------------------

    private static void openNemeses(ServerPlayer player, RotasData data) {
        CompoundTag state = new CompoundTag();
        ListTag rows = new ListTag();
        for (Nemesis nemesis : data.nemeses().values()) {
            CompoundTag row = new CompoundTag();
            row.putInt("id", nemesis.id());
            row.putString("name", nemesis.displayName());
            row.putString("type", nemesis.entityType());
            row.putInt("level", nemesis.level());
            row.putInt("kills", nemesis.kills());
            row.putBoolean("loaded", NemesisService.body(player.server, nemesis) != null);
            String hunted = "";
            for (Nemesis.Victim victim : nemesis.victims()) {
                if (victim.id().equals(nemesis.target())) hunted = victim.name();
            }
            row.putString("hunting", hunted);
            rows.add(row);
        }
        state.put("nemeses", rows);
        RotasNetwork.openScreen(player, "nemesis_admin", state);
    }

    // ---- Player tools ---------------------------------------------------------------------------

    private static void giveCard(ServerPlayer player, CompoundTag payload) {
        ServerPlayer target = target(player, payload.getString("player"));
        String id = payload.getString("card");
        if (target == null) return;
        if (CardIndex.get(id) == null) {
            RotasNetwork.feedback(player, false, "Unknown card: " + id);
            return;
        }
        LootService.deliver(target, List.of(CardItem.of(RotasRegistry.CARD.get(), id, 1)));
        RotasData data = RotasData.get(player.server);
        audit(player, data, true, "gave card " + id + " to " + target.getGameProfile().getName());
        RotasNetwork.feedback(player, true, "Gave " + CardIndex.name(id) + " to " + target.getGameProfile().getName());
    }

    /** An online player by name, or the admin themselves when the name is empty. */
    private static ServerPlayer target(ServerPlayer player, String name) {
        if (name == null || name.isBlank()) return player;
        ServerPlayer target = player.server.getPlayerList().getPlayerByName(name.trim());
        if (target == null) RotasNetwork.feedback(player, false, "Player not online: " + name);
        return target;
    }

    private static void audit(ServerPlayer player, RotasData data, boolean done, String what) {
        if (done) data.audit(player.getGameProfile().getName() + " " + what);
    }
}
