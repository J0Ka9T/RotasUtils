package net.schwarz.rotasutils.smoke;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.common.util.FakePlayer;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.ContentPacks;
import net.schwarz.rotasutils.core.MonsterDefinitions;
import net.schwarz.rotasutils.data.RotasData;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BossSmoke {
    private static final UUID MAIN = UUID.fromString("8b3f3d21-9f4b-4f4d-9a0a-6d1c53c7f4aa");
    private static final UUID HELPER = UUID.fromString("41cf6b09-6d33-4b7e-9f6f-2b2e4a2d3c55");
    private static final String ACTOR = "smoke-boss";
    private static final String TAG = "rotas_boss_smoke";
    private static final ContentId PROFILE = new ContentId("rotas:monster/smoke_boss");
    private static final ContentId BOSS = new ContentId("rotas:boss/smoke");
    private static final List<String> DEFINITIONS = List.of(
            "{\"schema\":1,\"id\":\"rotas:action/smoke_boss_coin\",\"kind\":\"action\",\"body\":{\"type\":\"currency\",\"id\":\"rotas:boss_coin\",\"amount\":11}}",
            "{\"schema\":1,\"id\":\"rotas:reward/smoke_boss\",\"kind\":\"reward\",\"body\":{\"actions\":[\"rotas:action/smoke_boss_coin\"]}}",
            "{\"schema\":1,\"id\":\"rotas:tier/smoke_boss\",\"kind\":\"tier\",\"body\":{\"label\":\"Boss\",\"rank\":5,\"boss\":true,\"xp_multiplier\":1,\"loot_multiplier\":1,"
                    + "\"attributes\":{\"minecraft:generic.max_health\":{\"multiplier\":5}}}}",
            "{\"schema\":1,\"id\":\"rotas:boss/smoke\",\"kind\":\"boss\",\"body\":{\"label\":\"Smoke Warden\",\"arena_radius\":32,\"leash\":true,"
                    + "\"reset_seconds\":5,\"minimum_share\":0.2,\"reward\":\"rotas:reward/smoke_boss\",\"phases\":["
                    + "{\"threshold\":1,\"label\":\"Opening\"},"
                    + "{\"threshold\":0.5,\"label\":\"The Smoke Warden roars\",\"attributes\":{\"minecraft:generic.armor\":{\"add\":8}}}]}}",
            "{\"schema\":1,\"id\":\"rotas:monster/smoke_boss\",\"kind\":\"monster\",\"body\":{\"manual_only\":true,\"priority\":9,"
                    + "\"selector\":{\"entities\":[\"minecraft:zombie\"]},\"level\":{\"strategy\":\"FIXED\",\"value\":20,\"min\":1,\"max\":50},"
                    + "\"base_xp\":10,\"tiers\":{\"rotas:tier/smoke_boss\":1},\"boss\":\"rotas:boss/smoke\",\"name\":\"Smoke Warden\"}}");

    private BossSmoke() { }

    static void attach(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rotas_boss_smoke").requires(source -> source.hasPermission(4))
                .executes(context -> {
                    MinecraftServer server = context.getSource().getServer();
                    var kernel = RotasData.get(server).kernel();
                    if (kernel.content().monsters().bosses().containsKey(BOSS)) { return report(server); }
                    if (kernel.history().draft(ACTOR) != null) { kernel.history().discard(ACTOR); }
                    kernel.history().begin(ACTOR);
                    try {
                        for (String definition : DEFINITIONS) { kernel.history().put(ACTOR, ContentPacks.parse(definition)); }
                    } catch (java.io.IOException error) { throw new IllegalArgumentException(error); }
                    require(kernel.validateDraft(ACTOR, true, () -> true, result -> {
                        if (!result.valid()) {
                            net.schwarz.rotasutils.Rotasutils.LOG.error("ROTAS_BOSS_SMOKE_FAIL {}", result.issues());
                            return;
                        }
                        report(server);
                    }), "boss definition publication scheduled");
                    return 1;
                }));
    }

    private static int report(MinecraftServer server) {
        try {
            checks(server);
            net.schwarz.rotasutils.Rotasutils.LOG.info("ROTAS_BOSS_SMOKE_PASS");
            return 1;
        } catch (Exception | AssertionError error) {
            net.schwarz.rotasutils.Rotasutils.LOG.error("ROTAS_BOSS_SMOKE_FAIL", error);
            return 0;
        }
    }

    private static Zombie spawn(ServerLevel level) {
        BlockPos pos = level.getSharedSpawnPos();
        Zombie zombie = EntityType.ZOMBIE.create(level);
        zombie.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null, null);
        zombie.setPersistenceRequired();
        zombie.setNoAi(true);
        zombie.addTag(TAG);
        require(level.addFreshEntity(zombie), "boss probe added to the level");
        return zombie;
    }

    private static void checks(MinecraftServer server) {
        var data = RotasData.get(server);
        var kernel = data.kernel();
        var bosses = kernel.bosses();
        var monsters = kernel.monsters();
        ServerLevel level = server.overworld();
        java.util.List<Zombie> stale = new java.util.ArrayList<>();
        for (var entity : level.getEntities().getAll()) {
            if (entity instanceof Zombie zombie && zombie.getTags().contains(TAG)) { stale.add(zombie); }
        }
        stale.forEach(net.minecraft.world.entity.Entity::discard);

        Zombie boss = spawn(level);
        var state = monsters.assign(boss, PROFILE, null, "COMMAND");
        var definition = bosses.definition(state);
        require(definition != null && definition.id().equals(BOSS), "profile links its boss definition");
        require(Math.abs(boss.getMaxHealth() - 100.0F) < .001, "boss tier scaling: " + boss.getMaxHealth());
        require(state.runtime().contains("boss:origin"), "encounter records its arena origin");
        require(state.runtime().getInt("boss:phase") == 0, "encounter starts in the opening phase");

        var main = new FakePlayer(level, new GameProfile(MAIN, "BossSmokeMain"));
        var helper = new FakePlayer(level, new GameProfile(HELPER, "BossSmokeHelp"));
        main.moveTo(boss.getX(), boss.getY(), boss.getZ());
        helper.moveTo(boss.getX(), boss.getY(), boss.getZ());
        bosses.contribute(boss, state, main, 80);
        bosses.contribute(boss, state, helper, 20);
        var shares = net.schwarz.rotasutils.core.BossDefinitions.shares(bosses.contributions(state));
        require(Math.abs(shares.get(MAIN.toString()) - 0.8) < 1e-9, "damage share for the main contributor");
        require(Math.abs(shares.get(HELPER.toString()) - 0.2) < 1e-9, "damage share for the helper");

        double armourBefore = boss.getAttributeValue(Attributes.ARMOR);
        boss.setHealth(boss.getMaxHealth() * 0.4F);
        require(bosses.phase(boss, state, definition), "crossing a threshold advances the phase");
        require(state.runtime().getInt("boss:phase") == 1, "phase index persisted");
        double armourPhase = boss.getAttributeValue(Attributes.ARMOR);
        require(Math.abs(armourPhase - armourBefore - 8.0) < .001, "phase attributes applied: " + armourBefore + " -> " + armourPhase);
        require(!bosses.phase(boss, state, definition), "the same phase does not re-trigger");
        boss.setHealth(boss.getMaxHealth());
        require(bosses.phase(boss, state, definition), "healing returns the boss to the earlier phase");
        require(Math.abs(boss.getAttributeValue(Attributes.ARMOR) - armourBefore) < .001, "phase modifiers removed on the way back");
        boss.setHealth(boss.getMaxHealth() * 0.4F);
        bosses.phase(boss, state, definition);

        bosses.reset(boss, state, definition, new net.minecraft.world.phys.Vec3(boss.getX(), boss.getY(), boss.getZ()));
        require(bosses.contributions(state).isEmpty(), "reset clears the contribution ledger");
        require(Math.abs(boss.getHealth() - boss.getMaxHealth()) < .001, "reset restores full health");
        require(state.runtime().getInt("boss:phase") == 0, "reset returns to the opening phase");
        require(Math.abs(boss.getAttributeValue(Attributes.ARMOR) - armourBefore) < .001, "reset removes phase modifiers");

        bosses.contribute(boss, state, main, 90);
        bosses.contribute(boss, state, helper, 10);
        long mainBefore = data.progress(MAIN).rpg().currency("rotas:boss_coin");
        long helperBefore = data.progress(HELPER).rpg().currency("rotas:boss_coin");
        require(bosses.reward(boss, state) == 0, "offline contributors are skipped rather than paid blind");
        require(data.progress(MAIN).rpg().currency("rotas:boss_coin") == mainBefore, "no payout without an online player");
        require(data.progress(HELPER).rpg().currency("rotas:boss_coin") == helperBefore, "minimum share is not paid either");

        monsters.persist(boss, state);
        var reloaded = net.schwarz.rotasutils.core.MonsterState.load(
                net.schwarz.rotasutils.server.MonsterStorage.read(boss));
        require(bosses.contributions(reloaded).size() == 2, "contribution ledger persisted with the entity");
        require(reloaded.runtime().getInt("boss:phase") == 0, "phase persisted with the entity");
        require(bosses.definition(reloaded) != null, "restored state resolves its boss definition");

        for (int i = 0; i < net.schwarz.rotasutils.server.BossService.MAX_CONTRIBUTORS + 8; i++) {
            var extra = new FakePlayer(level, new GameProfile(UUID.nameUUIDFromBytes(("boss-smoke-" + i).getBytes()), "F" + i));
            bosses.contribute(boss, state, extra, 1);
        }
        require(bosses.contributions(state).size() <= net.schwarz.rotasutils.server.BossService.MAX_CONTRIBUTORS,
                "contributor budget enforced: " + bosses.contributions(state).size());

        monsters.trigger(boss, main, MonsterDefinitions.Trigger.HURT, Map.of("event.amount", "5.0"));
        require(bosses.contributions(state).containsKey(MAIN.toString()), "the shared trigger funnel records damage");
        boss.discard();
        data.setDirty();
    }

    private static void require(boolean value, String message) {
        if (!value) { throw new AssertionError(message); }
    }
}
