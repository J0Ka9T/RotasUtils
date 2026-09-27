package net.schwarz.rotasutils.compat;

import dev.architectury.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.npc.NpcDef;
import net.schwarz.rotasutils.npc.NpcState;
import net.schwarz.rotasutils.progress.ActiveQuest;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.objective.Objective;
import net.schwarz.rotasutils.quest.objective.ObjectiveType;
import net.schwarz.rotasutils.server.NpcService;
import net.schwarz.rotasutils.server.QuestService;

import java.util.UUID;

/** Optional Easy NPC 7.12.1 entity adapter. Rotas dialogue state is not an upstream condition hook. */
public final class EasyNpcCompat {
    /** Mod ids Easy NPC has shipped under. */
    private static final String[] MOD_IDS = {"easy_npc", "easynpc", "easy_npc_bundle"};
    /** Entity-type namespaces its NPCs have shipped under, bundles included. */
    private static final String[] NAMESPACES = {"easy_npc", "easynpc", "easy_npc_bundle"};
    /** Easy NPC's own marker interface; resolved reflectively so no version is compiled against. */
    private static final String NPC_INTERFACE = "de.markusbordihn.easynpc.entity.easynpc.EasyNPC";
    /**
     * NPC data version written when Easy NPC cannot tell us its own. Newer builds moved past this
     * number, so {@link #npcDataVersion} always prefers the live entity's answer.
     */
    private static final int FALLBACK_DATA_VERSION = 3;

    private static Boolean present;
    private static Class<?> npcInterface;

    private EasyNpcCompat() {
    }

    public static boolean isPresent() {
        if (present == null) {
            boolean found = false;
            for (String modId : MOD_IDS) {
                if (Platform.isModLoaded(modId)) {
                    found = true;
                    break;
                }
            }
            present = found;
            if (found) {
                Rotasutils.LOG.info("Easy NPC detected; Rotas NPC bindings and editor passthrough available");
            }
        }
        return present;
    }

    /** True when a shipped entity-type namespace belongs to an Easy NPC build. */
    public static boolean isEasyNpcNamespace(String namespace) {
        if (namespace == null) {
            return false;
        }
        for (String candidate : NAMESPACES) {
            if (candidate.equals(namespace)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when the entity is an Easy NPC.
     *
     * <p>Asked two ways on purpose: the entity-type namespace catches every shipped build, and
     * Easy NPC's own {@code EasyNPC} marker interface catches a build whose namespace we have never
     * seen. The interface is looked up by name, so no Easy NPC version is compiled against and a
     * missing class simply falls back to the namespace answer.</p>
     */
    public static boolean isEasyNpc(Entity entity) {
        if (entity == null) {
            return false;
        }
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (key != null && isEasyNpcNamespace(key.getNamespace())) {
            return true;
        }
        if (npcInterface == null) {
            try {
                npcInterface = Class.forName(NPC_INTERFACE);
            } catch (ClassNotFoundException | LinkageError absent) {
                npcInterface = void.class;
            }
        }
        return npcInterface != void.class && npcInterface.isInstance(entity);
    }

    /**
     * The NPC data version the entity itself reports, so a newer Easy NPC is never stamped with an
     * older one. Falls back to {@link #FALLBACK_DATA_VERSION} when the accessor is unavailable.
     */
    static int npcDataVersion(Entity entity) {
        try {
            Object value = entity.getClass().getMethod("getNPCDataVersion").invoke(entity);
            if (value instanceof Integer version && version > 0) {
                return version;
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            // Older or repackaged builds: keep the long-standing default.
        }
        return FALLBACK_DATA_VERSION;
    }

    /** Leave tool interaction and its access checks to Easy NPC before advancing Rotas objectives. */
    public static boolean isEditorInteraction(Player player, Entity entity, InteractionHand hand) {
        if (!isEasyNpc(entity)) return false;
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(player.getItemInHand(hand).getItem());
        return "easy_npc_config_ui".equals(item.getNamespace())
                || "easy_npc".equals(item.getNamespace());
    }

    /** Creates and adds a persistent, passive guide. Caller must already have checked admin access. */
    public static Entity createDemoNpc(ServerLevel level, Vec3 position, UUID owner, String name) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("NPC creation off server thread");
        ResourceLocation id = new ResourceLocation("easy_npc", "humanoid");
        if (!isPresent() || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return null;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        Entity npc = type.create(level);
        if (!(npc instanceof Mob mob)) return null;
        mob.moveTo(position.x, position.y, position.z, 180, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                MobSpawnType.COMMAND, null, null);
        mob.setCustomName(Component.literal(name));
        mob.setCustomNameVisible(true);
        mob.setPersistenceRequired();
        CompoundTag saved = mob.saveWithoutId(new CompoundTag());
        saved.putInt("EasyNPCVersion", npcDataVersion(mob));
        saved.putString("VariantType", "STEVE");
        CompoundTag skin = new CompoundTag();
        skin.putString("Type", "DEFAULT");
        saved.put("SkinData", skin);
        if (owner != null) saved.putUUID("Owner", owner);
        saved.put("ObjectiveData", objectives("stationary", "passive"));
        mob.load(saved);
        return level.addFreshEntity(mob) ? mob : null;
    }

    /** Applies upstream objective NBT; patrol leaves navigation to the Rotas saved route. */
    public static boolean configureBehavior(Entity entity, String movement, String combat, UUID owner) {
        if (!isEasyNpc(entity) || !(entity.level() instanceof ServerLevel level)) return false;
        if (!level.getServer().isSameThread()) throw new IllegalStateException("NPC configuration off server thread");
        if (!java.util.Set.of("stationary", "wander", "follow", "patrol").contains(movement)
                || !java.util.Set.of("passive", "defensive", "hostile").contains(combat)) return false;
        CompoundTag saved = entity.saveWithoutId(new CompoundTag());
        if (owner != null) saved.putUUID("Owner", owner);
        if ("follow".equals(movement) && !saved.hasUUID("Owner")) return false;
        saved.put("ObjectiveData", objectives(movement, combat));
        entity.load(saved);
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
        return true;
    }

    private static CompoundTag objectives(String movement, String combat) {
        ListTag entries = new ListTag();
        addObjective(entries, "LOOK_AT_PLAYER", 9);
        addObjective(entries, "LOOK_AT_RESET", 9);
        if ("wander".equals(movement)) addObjective(entries, "RANDOM_STROLL", 11);
        if ("follow".equals(movement)) addObjective(entries, "FOLLOW_OWNER", 6);
        if (!"passive".equals(combat)) {
            addObjective(entries, "HURT_BY_TARGET", 2);
            addObjective(entries, "MELEE_ATTACK", 2);
        }
        if ("hostile".equals(combat)) addObjective(entries, "ATTACK_PLAYER_WITHOUT_OWNER", 2);
        CompoundTag objectives = new CompoundTag();
        objectives.put("ObjectiveDataSet", entries);
        return objectives;
    }

    private static void addObjective(ListTag entries, String type, int priority) {
        CompoundTag objective = new CompoundTag();
        objective.putString("Type", type);
        objective.putInt("Prio", priority);
        entries.add(objective);
    }

    /**
     * The dialogue state this entity should show for this player.
     *
     * <p>A bound NPC answers from its own configuration. Anything else is matched against the
     * quests that reference it by uuid, which is how a quest can point at an Easy NPC without
     * that NPC being configured in RotasUtils at all.</p>
     */
    public static NpcState stateFor(ServerPlayer player, Entity npc) {
        RotasData data = RotasData.get(player.server);
        NpcDef bound = NpcService.bound(data, npc);
        if (bound != null) {
            return NpcService.stateFor(player, data, bound);
        }
        return objectiveStateFor(player, data, npc.getUUID());
    }

    /** Marker string for any NPC mod that wants to render one above the head. */
    public static String markerFor(NpcState state) {
        return state.marker();
    }

    /** State from quests that name this entity in an objective, for unbound entities. */
    private static NpcState objectiveStateFor(ServerPlayer player, RotasData data, UUID npcId) {
        PlayerProgress progress = data.progress(player.getUUID());
        NpcState best = NpcState.NO_QUEST;
        for (QuestDef quest : data.quests().values()) {
            if (!quest.published() || !linksTo(quest, npcId)) {
                continue;
            }
            ActiveQuest active = progress.active(quest.id());
            if (active != null) {
                best = best.best(active.turnInReady() ? NpcState.READY_TO_TURN_IN
                        : anyObjectiveDone(quest, active) ? NpcState.OBJECTIVE_COMPLETE
                        : NpcState.QUEST_ACTIVE);
                continue;
            }
            if (progress.failedQuests().contains(quest.id())) {
                best = best.best(NpcState.QUEST_FAILED);
                continue;
            }
            if (progress.cooldownUntil(quest.id()) > QuestService.nowSeconds()) {
                best = best.best(NpcState.QUEST_ON_COOLDOWN);
                continue;
            }
            if (progress.completionCount(quest.id()) > 0 && quest.repeat() == QuestDef.Repeat.NEVER) {
                best = best.best(NpcState.QUEST_COMPLETE);
                continue;
            }
            best = best.best(QuestService.blockedReason(player, data, quest) == null
                    ? NpcState.QUEST_AVAILABLE : NpcState.REQUIREMENTS_NOT_MET);
        }
        return best;
    }

    private static boolean linksTo(QuestDef quest, UUID npcId) {
        String wanted = npcId.toString();
        for (Objective objective : quest.objectives()) {
            if (objective.type() == ObjectiveType.TALK_NPC
                    && wanted.equals(objective.params().getString("npc_uuid", ""))) {
                return true;
            }
            if (objective.type() == ObjectiveType.DELIVER_ITEM
                    && wanted.equals(objective.params().getString("target_id", ""))) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyObjectiveDone(QuestDef quest, ActiveQuest active) {
        for (int i = 0; i < quest.objectives().size(); i++) {
            if (active.isComplete(i)) {
                return true;
            }
        }
        return false;
    }
}
