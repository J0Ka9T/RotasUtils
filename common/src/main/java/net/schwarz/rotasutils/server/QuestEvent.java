package net.schwarz.rotasutils.server;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.quest.objective.EventKind;

import java.util.UUID;

/**
 * One gameplay event, already narrowed to the objective kind that could consume it.
 *
 * <p>Built at the call site of a game event and passed straight to
 * {@link ObjectiveEngine#handle}; nothing polls for these.
 */
public final class QuestEvent {
    private final EventKind kind;
    private int amount = 1;

    private ResourceLocation entityType;
    private String entityName = "";
    private UUID entityUuid;
    private boolean boss;
    private boolean questSpawned;
    private ItemStack stack = ItemStack.EMPTY;
    private ResourceLocation blockId;
    private BlockPos pos;
    private String dimension = "";
    private String biome = "";
    private ResourceLocation weapon;
    private String damageType = "";
    private boolean projectile;
    private boolean petKill;
    private boolean partyKill;
    private ServerPlayer victim;
    private String customId = "";
    private String dialogueId = "";
    private String choiceId = "";
    private boolean dialogueComplete;

    private String targetBoardId = "";

    public boolean dialogueComplete() { return dialogueComplete; }
    public QuestEvent dialogueComplete(boolean complete) { dialogueComplete = complete; return this; }

    public QuestEvent(EventKind kind) {
        this.kind = kind;
    }

    public EventKind kind() {
        return kind;
    }

    public int amount() {
        return amount;
    }

    public QuestEvent amount(int amount) {
        this.amount = Math.max(1, amount);
        return this;
    }

    public ResourceLocation entityType() {
        return entityType;
    }

    public QuestEvent entityType(ResourceLocation entityType) {
        this.entityType = entityType;
        return this;
    }

    public String entityName() {
        return entityName;
    }

    public QuestEvent entityName(String entityName) {
        this.entityName = entityName == null ? "" : entityName;
        return this;
    }

    public UUID entityUuid() {
        return entityUuid;
    }

    public QuestEvent entityUuid(UUID entityUuid) {
        this.entityUuid = entityUuid;
        return this;
    }

    public boolean boss() {
        return boss;
    }

    public QuestEvent boss(boolean boss) {
        this.boss = boss;
        return this;
    }

    public boolean questSpawned() {
        return questSpawned;
    }

    public QuestEvent questSpawned(boolean questSpawned) {
        this.questSpawned = questSpawned;
        return this;
    }

    public ItemStack stack() {
        return stack;
    }

    public QuestEvent stack(ItemStack stack) {
        this.stack = stack == null ? ItemStack.EMPTY : stack;
        return this;
    }

    public ResourceLocation blockId() {
        return blockId;
    }

    public QuestEvent blockId(ResourceLocation blockId) {
        this.blockId = blockId;
        return this;
    }

    public BlockPos pos() {
        return pos;
    }

    public QuestEvent pos(BlockPos pos) {
        this.pos = pos;
        return this;
    }

    public String dimension() {
        return dimension;
    }

    public QuestEvent dimension(String dimension) {
        this.dimension = dimension == null ? "" : dimension;
        return this;
    }

    public String biome() {
        return biome;
    }

    public QuestEvent biome(String biome) {
        this.biome = biome == null ? "" : biome;
        return this;
    }

    public ResourceLocation weapon() {
        return weapon;
    }

    public QuestEvent weapon(ResourceLocation weapon) {
        this.weapon = weapon;
        return this;
    }

    public String damageType() {
        return damageType;
    }

    public QuestEvent damageType(String damageType) {
        this.damageType = damageType == null ? "" : damageType;
        return this;
    }

    public boolean projectile() {
        return projectile;
    }

    public QuestEvent projectile(boolean projectile) {
        this.projectile = projectile;
        return this;
    }

    public boolean petKill() {
        return petKill;
    }

    public QuestEvent petKill(boolean petKill) {
        this.petKill = petKill;
        return this;
    }

    public boolean partyKill() {
        return partyKill;
    }

    public QuestEvent partyKill(boolean partyKill) {
        this.partyKill = partyKill;
        return this;
    }

    public ServerPlayer victim() {
        return victim;
    }

    public QuestEvent victim(ServerPlayer victim) {
        this.victim = victim;
        return this;
    }

    public String customId() {
        return customId;
    }

    public QuestEvent customId(String customId) {
        this.customId = customId == null ? "" : customId;
        return this;
    }

    public String dialogueId() {
        return dialogueId;
    }

    public QuestEvent dialogueId(String dialogueId) {
        this.dialogueId = dialogueId == null ? "" : dialogueId;
        return this;
    }

    public String choiceId() {
        return choiceId;
    }

    public QuestEvent choiceId(String choiceId) {
        this.choiceId = choiceId == null ? "" : choiceId;
        return this;
    }

    public String targetBoardId() {
        return targetBoardId;
    }

    public QuestEvent targetBoardId(String targetBoardId) {
        this.targetBoardId = targetBoardId == null ? "" : targetBoardId;
        return this;
    }
}
