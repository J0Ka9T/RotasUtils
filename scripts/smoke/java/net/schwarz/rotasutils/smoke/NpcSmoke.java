package net.schwarz.rotasutils.smoke;

import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.util.FakePlayer;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.npc.NpcDef;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.server.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exercises live conversation services on an isolated dedicated server. */
final class NpcSmoke {
    private static final net.minecraftforge.registries.DeferredRegister<EntityType<?>> TYPES =
            net.minecraftforge.registries.DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES, "rotasutils_smoke");
    private static final net.minecraftforge.registries.RegistryObject<EntityType<net.minecraft.world.entity.monster.Zombie>> MOB =
            TYPES.register("quest_mob", () -> EntityType.Builder.<net.minecraft.world.entity.monster.Zombie>of(net.minecraft.world.entity.monster.Zombie::new,
                    net.minecraft.world.entity.MobCategory.MONSTER).sized(0.6f, 1.95f).build("rotasutils_smoke:quest_mob"));

    static void register() {
        var bus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        TYPES.register(bus);
        bus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent event) ->
                event.put(MOB.get(), net.minecraft.world.entity.monster.Zombie.createAttributes().build()));
    }
    static void run(MinecraftServer server) throws Exception {
        var data = RotasData.get(server);
        var operator = new AtomicBoolean(true);
        var player = new FakePlayer(server.overworld(), new GameProfile(UUID.randomUUID(), "NpcReviewSmoke")) {
            @Override public boolean hasPermissions(int level) { return operator.get(); }
        };
        player.setPos(12, 220, 12);
        var entity = EntityType.VILLAGER.create(server.overworld());
        require(entity != null, "villager created"); entity.setPos(12, 220, 14);
        server.overworld().addFreshEntity(entity);
        NpcDef npc = new NpcDef("npc_review_smoke");
        List<String> quests = new ArrayList<>();
        try {
            npc.setInteractionJson("""
                {"nodes":[{"id":"hello","lines":["Hello"],"choices":[
                 {"id":"pay","text":"Reward","next":"bye","action":{"type":"reward","repeat":"once",
                  "rewards":[{"type":"currency","id":"rotas:npc_smoke","amount":7}]}}]},
                 {"id":"bye","lines":["Goodbye"],"choices":[]}]}
                """);
            data.putNpc(npc);
            NpcConversations.preview(player, data, npc);
            var request = request(player.getUUID(), "choice:pay");
            NpcConversations.respond(player, data, request);
            require(data.progress(player.getUUID()).rpg().currency("rotas:npc_smoke") == 0, "preview cannot pay rewards");
            require(field(session(player.getUUID()), "node").equals("bye"), "preview follows unsaved branches without a binding");

            NpcService.bind(data, npc, entity);
            require(NpcService.bound(data, entity) == npc, "vanilla entity binding resolves");
            require(NpcConversations.blockedReason(player, npc) == null, "nearby live binding allowed");
            NpcConversations.open(player, data, npc);
            request = request(player.getUUID(), "choice:pay");
            NpcConversations.respond(player, data, request);
            require(data.progress(player.getUUID()).rpg().currency("rotas:npc_smoke") == 7, "live choice pays once");
            NpcConversations.respond(player, data, request);
            require(data.progress(player.getUUID()).rpg().currency("rotas:npc_smoke") == 7, "replayed nonce cannot pay twice");
            NpcConversations.open(player, data, npc);
            request = request(player.getUUID(), "choice:pay");
            npc.setName("Changed while talking");
            NpcConversations.respond(player, data, request);
            require(session(player.getUUID()) == null, "any changed NPC settings invalidate the session");

            for (int i = 0; i < 72; i++) {
                var quest = new QuestDef("npc_review_smoke_" + i); quest.setPublished(true);
                quest.setHidden(i == 0); data.putQuest(quest); quests.add(quest.id()); npc.questIds().add(quest.id());
            }
            NpcConversations.open(player, data, npc);
            Map<?, ?> options = (Map<?, ?>) field(session(player.getUUID()), "options");
            require(!options.containsKey("quest:npc_review_smoke_0"), "hidden quests never enter offered options");
            require(options.size() <= 62 && options.containsKey("next_page"), "response transport stays bounded and paged");
            NpcConversations.respond(player, data, request(player.getUUID(), "next_page"));
            options = (Map<?, ?>) field(session(player.getUUID()), "options");
            require(options.containsKey("quest:npc_review_smoke_71"), "last quest is reachable on the next page");
            try (var transaction = new PlayerRecordTransaction(data, data.progress(player.getUUID()), player)) {
                transaction.unlock("npc_review_smoke_0"); transaction.commit();
            }
            require(data.progress(player.getUUID()).unlockedQuests().contains("npc_review_smoke_0"), "legacy quest IDs can be unlocked transactionally");
            var invalidNpc = NpcDef.load(npc.save());
            invalidNpc.setInteractionJson("{\"gifts\":[{\"id\":\"invalid\",\"items\":[\"missing_mod:gift\"]}]}");
            require(Validation.validateNpc(data, invalidNpc).stream().anyMatch(issue -> issue.severity() == Validation.Severity.ERROR), "missing modded gift is rejected before applying");

            WorldPicker.begin(player, WorldPicker.Kind.NPC_BIND, "test", "bind", npc.id());
            operator.set(false);
            require(!WorldPicker.resolveEntity(player, entity) && !WorldPicker.isPending(player), "permission revocation cancels pending binding");
            operator.set(true); NpcConversations.preview(player, data, npc);
            request = request(player.getUUID(), "choice:pay"); operator.set(false);
            NpcConversations.respond(player, data, request);
            require(session(player.getUUID()) == null, "preview permission rechecked on response");

            var modded = MOB.getId();
            require(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(modded), "modded entity appears in the vanilla registry");
            var moddedEntity = MOB.get().create(server.overworld());
            require(moddedEntity != null, "modded entity can be created");
            try {
                NpcService.bind(data, npc, moddedEntity);
                require(NpcService.bound(data, moddedEntity) == npc && npc.entityType().equals(modded.toString()), "modded entity can be bound as an NPC");
            } finally { moddedEntity.discard(); }
            var objective = new net.schwarz.rotasutils.quest.objective.Objective(net.schwarz.rotasutils.quest.objective.ObjectiveType.INTERACT_ENTITY);
            objective.params().put("entity", modded.toString());
            require(ObjectiveEngine.matches(player, data, objective, new QuestEvent(net.schwarz.rotasutils.quest.objective.EventKind.ENTITY_INTERACT)
                    .entityType(modded)) == null, "modded registry selection matches objective events");
            var seen = new AtomicBoolean();
            try (var listener = data.kernel().events().subscribe(new net.schwarz.rotasutils.core.ContentId("rotas:item_used"), event -> {
                require(event.context().text("event.item").equals("minecraft:apple"), "kernel receives actual item selector");
                require(event.context().text("event.dimension").equals("minecraft:overworld"), "kernel receives dimension selector");
                seen.set(true);
            })) {
                RpgKernel.objectiveEvent(player, new QuestEvent(net.schwarz.rotasutils.quest.objective.EventKind.USE_ITEM)
                        .stack(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE))
                        .dimension("minecraft:overworld"));
                require(seen.get(), "real gameplay event reaches the kernel quest bridge");
            }
            net.schwarz.rotasutils.Rotasutils.LOG.info("ROTAS_NPC_REVIEW_SMOKE_PASS");
        } finally {
            NpcConversations.close(player.getUUID()); WorldPicker.clear(player);
            data.removeNpc(npc.id()); quests.forEach(data::removeQuest); entity.discard();
        }
    }
    private static Object session(UUID player) throws Exception {
        var sessions = NpcConversations.class.getDeclaredField("SESSIONS"); sessions.setAccessible(true);
        return ((Map<?, ?>) sessions.get(null)).get(player);
    }
    private static Object field(Object record, String name) throws Exception {
        var accessor = record.getClass().getDeclaredMethod(name); accessor.setAccessible(true); return accessor.invoke(record);
    }
    private static CompoundTag request(UUID player, String response) throws Exception {
        CompoundTag request = new CompoundTag(); request.putUUID("nonce", (UUID) field(session(player), "nonce"));
        request.putString("response", response); return request;
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
