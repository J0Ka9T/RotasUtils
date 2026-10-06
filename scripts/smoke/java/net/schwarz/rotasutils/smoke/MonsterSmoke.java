package net.schwarz.rotasutils.smoke;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.common.util.FakePlayer;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.ContentPacks;
import net.schwarz.rotasutils.core.MonsterState;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.server.MonsterService;
import net.schwarz.rotasutils.server.MonsterStorage;
import java.util.List;
import java.util.UUID;

public final class MonsterSmoke {
    private static final UUID ID = UUID.fromString("6d0f6c1c-7cf0-4d4c-9a9d-1b1f1f4bd9a1");
    private static final String ACTOR = "smoke-monster";
    private static final String PERSISTENT_TAG = "rotas_monster_smoke_persistent";
    private static final ContentId PROFILE = new ContentId("rotas:monster/smoke_zombie");
    private static final String EXPECTED_NAME = "Smoke Elite Zombie Lv7";
    private static final List<String> DEFINITIONS = List.of(
            "{\"schema\":1,\"id\":\"rotas:action/smoke_monster_coin\",\"kind\":\"action\",\"body\":{\"type\":\"currency\",\"id\":\"rotas:smoke_coin\",\"amount\":7}}",
            "{\"schema\":1,\"id\":\"rotas:reward/smoke_monster\",\"kind\":\"reward\",\"body\":{\"actions\":[\"rotas:action/smoke_monster_coin\"]}}",
            "{\"schema\":1,\"id\":\"rotas:tier/smoke_elite\",\"kind\":\"tier\",\"body\":{\"label\":\"Elite\",\"rank\":2,\"affix_count\":1,\"xp_multiplier\":2,\"loot_multiplier\":2,"
                    + "\"attributes\":{\"minecraft:generic.max_health\":{\"multiplier\":2}}}}",
            "{\"schema\":1,\"id\":\"rotas:affix/smoke_tough\",\"kind\":\"affix\",\"body\":{\"weight\":1,\"attributes\":{\"minecraft:generic.armor\":{\"add\":4}}}}",
            "{\"schema\":1,\"id\":\"rotas:monster/smoke_zombie\",\"kind\":\"monster\",\"body\":{\"priority\":5,\"manual_only\":true,"
                    + "\"selector\":{\"entities\":[\"minecraft:zombie\"]},\"level\":{\"strategy\":\"FIXED\",\"min\":1,\"max\":50,\"value\":7},"
                    + "\"base_xp\":100,\"xp_per_level\":10,\"tiers\":{\"rotas:tier/smoke_elite\":1},\"affixes\":[\"rotas:affix/smoke_tough\"],"
                    + "\"name\":\"Smoke {tier} {name} Lv{level}\",\"reward\":\"rotas:reward/smoke_monster\"}}");

    private MonsterSmoke() { }

    static void attach(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rotas_monster_smoke").requires(source -> source.hasPermission(4))
                .executes(context -> {
                    MinecraftServer server = context.getSource().getServer();
                    var kernel = RotasData.get(server).kernel();
                    if (kernel.content().monsters().profiles().containsKey(PROFILE)) {
                        return report(server, () -> checks(server));
                    }
                    if (kernel.history().draft(ACTOR) != null) { kernel.history().discard(ACTOR); }
                    kernel.history().begin(ACTOR);
                    try {
                        for (String definition : DEFINITIONS) { kernel.history().put(ACTOR, ContentPacks.parse(definition)); }
                    } catch (java.io.IOException error) { throw new IllegalArgumentException(error); }
                    require(kernel.validateDraft(ACTOR, true, () -> true, result -> {
                        if (!result.valid()) {
                            net.schwarz.rotasutils.Rotasutils.LOG.error("ROTAS_MONSTER_SMOKE_FAIL {}", result.issues());
                            return;
                        }
                        report(server, () -> checks(server));
                    }), "monster definition publication scheduled");
                    return 1;
                }));
        dispatcher.register(Commands.literal("rotas_monster_restart_smoke").requires(source -> source.hasPermission(4))
                .executes(context -> {
                    MinecraftServer server = context.getSource().getServer();
                    try {
                        restartChecks(server);
                        context.getSource().sendSuccess(() -> Component.literal("ROTAS_MONSTER_RESTART_PASS"), false);
                        return 1;
                    } catch (Exception | AssertionError error) {
                        net.schwarz.rotasutils.Rotasutils.LOG.error("ROTAS_MONSTER_RESTART_FAIL", error);
                        return 0;
                    }
                }));
    }

    private static int report(MinecraftServer server, Runnable body) {
        try {
            body.run();
            net.schwarz.rotasutils.Rotasutils.LOG.info("ROTAS_MONSTER_SMOKE_PASS");
            return 1;
        } catch (Exception | AssertionError error) {
            net.schwarz.rotasutils.Rotasutils.LOG.error("ROTAS_MONSTER_SMOKE_FAIL", error);
            return 0;
        }
    }

    private static Zombie spawn(ServerLevel level, boolean persistent) {
        BlockPos pos = level.getSharedSpawnPos();
        Zombie zombie = EntityType.ZOMBIE.create(level);
        zombie.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null, null);
        zombie.setPersistenceRequired();
        zombie.setNoAi(true);
        zombie.setInvulnerable(persistent);
        if (persistent) { zombie.addTag(PERSISTENT_TAG); }
        require(level.addFreshEntity(zombie), "smoke zombie added to the level");
        return zombie;
    }

    private static void checks(MinecraftServer server) {
        var data = RotasData.get(server);
        var service = data.kernel().monsters();
        ServerLevel level = server.overworld();
        java.util.List<Zombie> stale = new java.util.ArrayList<>();
        for (var existing : level.getEntities().getAll()) {
            if (existing instanceof Zombie old && old.getTags().contains(PERSISTENT_TAG)) { stale.add(old); }
        }
        stale.forEach(net.minecraft.world.entity.Entity::discard);

        Zombie zombie = spawn(level, false);
        float vanillaHealth = zombie.getMaxHealth();
        double vanillaArmor = zombie.getAttributeValue(Attributes.ARMOR);
        MonsterState state = service.assign(zombie, PROFILE, null, "COMMAND");
        require(state.level() == 7, "fixed level rule applied");
        require(state.tier().equals(new ContentId("rotas:tier/smoke_elite")), "weighted tier chosen");
        require(state.affixes().equals(List.of(new ContentId("rotas:affix/smoke_tough"))), "affix rolled within tier budget");
        require(state.xp() == 320, "tier multiplied XP: " + state.xp());
        require(state.lootMultiplier() == 2.0 && !state.boss(), "tier loot multiplier and boss flag");
        require(Math.abs(zombie.getMaxHealth() - vanillaHealth * 2) < .001, "scaled max health: " + zombie.getMaxHealth());
        require(Math.abs(zombie.getHealth() - zombie.getMaxHealth()) < .001, "health ratio preserved while scaling");
        require(Math.abs(zombie.getAttributeValue(Attributes.ARMOR) - (vanillaArmor + 4)) < .001, "affix armor addition");
        require(zombie.hasCustomName() && zombie.getCustomName().getString().equals(EXPECTED_NAME),
                "name template: " + (zombie.hasCustomName() ? zombie.getCustomName().getString() : "<none>"));
        require(service.peek(zombie) == state, "assigned state is served from the live index");

        MonsterState persisted = MonsterState.load(MonsterStorage.read(zombie));
        require(persisted.level() == state.level() && persisted.xp() == state.xp()
                && persisted.affixes().equals(state.affixes()), "state written to entity storage");

        boolean rejected = false;
        try { service.assign(zombie, PROFILE, null, "COMMAND"); }
        catch (IllegalStateException expected) { rejected = true; }
        require(rejected, "reassignment without an explicit clear is rejected");
        rejected = false;
        try { service.assign(spawn(level, false), PROFILE, 99, "COMMAND"); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "level override outside profile bounds is rejected");

        service.forget(zombie);
        require(service.peek(zombie) == null, "forget drops the runtime index entry");
        service.join(zombie, "COMMAND", true);
        MonsterState restored = service.peek(zombie);
        require(restored != null && restored.level() == 7 && restored.xp() == 320, "restored monster from storage");
        require(Math.abs(zombie.getMaxHealth() - vanillaHealth * 2) < .001, "restored scaling stays idempotent");

        service.clear(zombie);
        require(service.peek(zombie) == null, "cleared monster leaves the index");
        require(Math.abs(zombie.getMaxHealth() - vanillaHealth) < .001, "cleared monster loses owned modifiers");
        require(Math.abs(zombie.getAttributeValue(Attributes.ARMOR) - vanillaArmor) < .001, "cleared affix modifiers removed");
        require(!zombie.hasCustomName(), "cleared monster loses the generated name");
        require(MonsterStorage.read(zombie).isEmpty(), "cleared monster loses persisted state");
        zombie.discard();

        var player = new FakePlayer(level, new GameProfile(ID, "MonsterSmoke"));
        var progress = data.progress(ID);
        long xpBefore = progress.totalXp();
        long coinsBefore = progress.rpg().currency("rotas:smoke_coin");
        Zombie victim = spawn(level, false);
        MonsterState kill = service.assign(victim, PROFILE, null, "COMMAND");
        victim.setInvulnerable(false);
        require(victim.hurt(level.damageSources().playerAttack(player), 10000.0F), "player damage applied to monster");
        require(!victim.isAlive(), "monster died from the player hit");
        require(progress.totalXp() > xpBefore, "kill granted monster XP: " + xpBefore + " -> " + progress.totalXp());
        require(progress.rpg().currency("rotas:smoke_coin") == coinsBefore + 7, "profile reward granted once");
        long xpAfterKill = progress.totalXp();
        net.schwarz.rotasutils.server.ProgressService.awardFromSourceOnce(player, data,
                net.schwarz.rotasutils.level.XpSource.MOB_KILL, "minecraft:zombie", kill.xp(), victim.getUUID());
        require(progress.totalXp() == xpAfterKill, "per-entity receipt blocks duplicate monster XP");
        require(!service.confirmDeath(victim, player), "a confirmed death cannot be rewarded twice");
        victim.discard();

        Zombie persistent = spawn(level, true);
        MonsterState marker = service.assign(persistent, PROFILE, null, "COMMAND");
        require(marker.level() == 7, "persistent probe assigned");
        data.setDirty();
    }

    private static void restartChecks(MinecraftServer server) {
        var service = RotasData.get(server).kernel().monsters();
        ServerLevel level = server.overworld();
        Zombie probe = null;
        for (var entity : level.getEntities().getAll()) {
            if (entity instanceof Zombie zombie && zombie.getTags().contains(PERSISTENT_TAG)) { probe = zombie; }
        }
        require(probe != null, "persistent monster probe survived the restart");
        if (service.peek(probe) == null) { service.join(probe, "LOADED", true); }
        MonsterState state = service.peek(probe);
        require(state != null, "monster state restored from entity storage after restart");
        require(state.level() == 7 && state.xp() == 320, "restored level and XP");
        require(state.tier().equals(new ContentId("rotas:tier/smoke_elite")), "restored tier");
        require(state.affixes().equals(List.of(new ContentId("rotas:affix/smoke_tough"))), "restored affixes");
        require(Math.abs(probe.getMaxHealth() - 40.0F) < .001, "restored scaling reapplied: " + probe.getMaxHealth());
        require(probe.hasCustomName() && probe.getCustomName().getString().equals(EXPECTED_NAME), "restored generated name");
        require(!RotasData.get(server).kernel().content().monsters().profiles().containsKey(PROFILE)
                || service.peek(probe) != null, "live monsters survive content removal");
    }

    private static void require(boolean value, String message) {
        if (!value) { throw new AssertionError(message); }
    }
}
