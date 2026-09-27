package net.schwarz.rotasutils.smoke;

import com.mojang.authlib.GameProfile;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.schwarz.rotasutils.core.*;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.LevelConfig;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.server.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod("rotasutils_smoke")
public final class ProgressionSmoke {
    private static final UUID ID = UUID.fromString("f2381220-5f6c-46c8-b0e3-818fa7136779");

    public ProgressionSmoke() {
        NpcSmoke.register();
        MinecraftForge.EVENT_BUS.addListener(this::commands);
    }

    private void commands(RegisterCommandsEvent event) {
        MonsterSmoke.attach(event.getDispatcher());
        ItemSmoke.attach(event.getDispatcher());
        BossSmoke.attach(event.getDispatcher());
        QuestEconomySmoke.attach(event.getDispatcher());
        ConsoleSmoke.attach(event.getDispatcher());
        UsabilitySmoke.attach(event.getDispatcher());
        event.getDispatcher().register(Commands.literal("rotas_admin_async_smoke").requires(source -> source.hasPermission(4))
                .executes(context -> {
                    var server = context.getSource().getServer(); var kernel = RotasData.get(server).kernel();
                    long revision = kernel.revision(); String actor = "smoke-permission";
                    if (kernel.history().draft(actor) != null) { kernel.history().discard(actor); }
                    kernel.history().begin(actor);
                    try {
                        kernel.history().put(actor, ContentPacks.parse("{\"schema\":1,\"id\":\"rotas:stat/permission_smoke\",\"kind\":\"stat\",\"body\":{\"base\":1,\"per_level\":0,\"min\":0,\"max\":100}}"));
                    } catch (java.io.IOException error) { throw new IllegalArgumentException(error); }
                    require(kernel.validateDraft(actor, true, () -> false, result -> {
                        require(!result.valid() && kernel.revision() == revision, "revoked permission cannot publish");
                        require(kernel.history().draft(actor) != null, "rejected apply retains draft");
                        kernel.history().discard(actor);
                        net.schwarz.rotasutils.Rotasutils.LOG.info("ROTAS_ADMIN_PERMISSION_RECHECK_PASS");
                    }), "async admin operation scheduled");
                    return 1;
                }));
        event.getDispatcher().register(Commands.literal("rotas_smoke").requires(source -> source.hasPermission(4))
                .executes(context -> {
                    try {
                        check(context.getSource().getServer());
                        context.getSource().sendSuccess(() -> Component.literal("ROTAS_PHASE2_SMOKE_PASS"), false);
                        return 1;
                    } catch (Exception | AssertionError error) {
                        net.schwarz.rotasutils.Rotasutils.LOG.error("ROTAS_PHASE2_SMOKE_FAIL", error);
                        return 0;
                    }
                }));
    }

    private void check(net.minecraft.server.MinecraftServer server) throws Exception {
        var data = RotasData.get(server);
        var profile = new GameProfile(ID, "RpgSmoke");
        var player = new FakePlayer(server.overworld(), profile);
        var progress = data.progress(ID);
        boolean repeat = progress.claimedRewards().contains("kernel|rotas:reward/smoke|once");
        if (repeat) {
            require(progress.rpg().currency("rotas:smoke_coin") == 25, "wallet persisted across restart");
            require(progress.reputation("rotas:smoke_faction") == -4, "reputation persisted across restart");
        }
        var actions = new ActionEngine(Map.of());
        var reward = new RewardEngine.Reward(new ContentId("rotas:reward/smoke"), ConditionEngine.ALWAYS,
                List.of(tx -> tx.currency("rotas:smoke_coin", 25), tx -> tx.reputation("rotas:smoke_faction", -4)));
        var engine = new RewardEngine(actions);
        var result = engine.grant(reward, "once", new KernelPlayerContext(player, Map.of()));
        require(result == (repeat ? RewardEngine.Result.ALREADY_CLAIMED : RewardEngine.Result.GRANTED), "receipt lifecycle");
        require(engine.grant(reward, "once", new KernelPlayerContext(player, Map.of())) == RewardEngine.Result.ALREADY_CLAIMED, "duplicate grant");

        var originalConfig = data.levelConfig().save();
        var testConfig = new LevelConfig(); testConfig.curve().setBaseXp(100); testConfig.curve().setGrowth(1);
        testConfig.curve().setMaxLevel(10); testConfig.setStatPointsPerLevel(2);
        data.setLevelConfig(testConfig);
        try {
            progress.setLevel(1); progress.setXp(0);
            int beforePoints = progress.rpg().statPoints();
            require(ProgressService.addExperience(player, data, 350, false) == 3, "multi-level XP callbacks");
            require(progress.level() == 4 && progress.xp() == 50, "XP remainder");
            require(progress.rpg().statPoints() == beforePoints + 6, "level stat point grant");
        } finally { data.setLevelConfig(LevelConfig.load(originalConfig)); }

        var registry = new ContentRegistry(new ConditionEngine(Map.of()), actions);
        var source = ContentPacks.parse("{\"schema\":1,\"id\":\"rotas:stat/smoke\",\"kind\":\"stat\",\"body\":{\"base\":2,\"per_level\":1,\"min\":0,\"max\":100,\"attribute\":\"minecraft:generic.max_health\"}}");
        var snapshot = StatsService.validate(registry.prepare(List.of(new ContentRegistry.Source("smoke", ContentRegistry.Layer.CORE, source))));
        require(snapshot.valid(), "stat definition validated");
        var stats = new StatsService();
        progress.rpg().stat("rotas:stat/smoke", 3);
        double healthBefore = player.getAttributeValue(Attributes.MAX_HEALTH);
        var first = stats.refresh(player, data, snapshot.snapshot());
        require(Math.abs(player.getMaxHealth() - healthBefore - 8) < .001, "attribute stat application");
        require(stats.refresh(player, data, snapshot.snapshot()) == first, "stat cache reused");
        progress.rpg().stat("rotas:stat/smoke", 4);
        require(stats.refresh(player, data, snapshot.snapshot()) != first, "stat mutation invalidates cache");
        stats.clear(); require(Math.abs(player.getMaxHealth() - healthBefore) < .001, "owned modifiers removed");

        var replacement = new FakePlayer(server.overworld(), profile);
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement, player, true));
        require(data.progress(replacement.getUUID()) == progress, "death clone retains UUID-owned profile");
        require(data.progress(replacement.getUUID()).rpg().currency("rotas:smoke_coin") == 25, "clone wallet retained");
        var nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
        var changedDimension = new FakePlayer(nether, profile);
        require(RotasData.get(changedDimension.server).progress(ID) == progress, "dimension-independent store");

        var dispatcher = server.getCommands().getDispatcher();
        var low = server.createCommandSourceStack().withEntity(player).withPermission(0);
        boolean denied = false;
        try { dispatcher.execute("rotas stats points_per_level 5", low); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied = true; }
        require(denied, "non-OP administration denied");
        denied = false;
        try { dispatcher.execute("rotas reload", low); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied = true; }
        require(denied, "non-OP reload denied");
        for (String command : List.of("rotas admin", "rotas admin draft create", "rotas admin rollback 1", "rotas admin history")) {
            denied = false;
            try { dispatcher.execute(command, low); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied = true; }
            require(denied, "non-OP admin operation denied: " + command);
        }
        require(net.schwarz.rotasutils.server.RotasPermissions.allowed(server.createCommandSourceStack(),
                net.schwarz.rotasutils.server.RotasPermissions.Capability.APPLY), "console may apply content");
        var saved = data.save(new CompoundTag());
        require(RotasData.load(saved).peek(ID).rpg().currency("rotas:smoke_coin") == 25, "actual SavedData roundtrip");
        data.setDirty();
    }

    private static void require(boolean value, String message) {
        if (!value) { throw new AssertionError(message); }
    }
}
