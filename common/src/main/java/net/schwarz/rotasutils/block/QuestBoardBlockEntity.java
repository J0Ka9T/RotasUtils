package net.schwarz.rotasutils.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.schwarz.rotasutils.board.BoardStyle;
import net.schwarz.rotasutils.registry.RotasRegistry;
import net.schwarz.rotasutils.util.Nbt;

/**
 * Placed billboard.
 *
 * <p>The block entity only stores the board id plus the handful of fields the
 * renderer needs. All authoritative board configuration lives in
 * {@link net.schwarz.rotasutils.data.RotasData}, so a board keeps working after a
 * chunk reload and cannot be edited by touching the block's NBT client side.
 */
public class QuestBoardBlockEntity extends BlockEntity {
    private String boardId = "";
    // Render-only mirror of the board config, pushed by the server on change.
    private String displayName = net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.board.default_name");
    private BoardStyle style = BoardStyle.WOODEN_VILLAGE;
    private int questCount;
    private int screenColor = 0xFF3BE8FF;
    private boolean emergency;
    private boolean glow;
    private boolean particles;

    public QuestBoardBlockEntity(BlockPos pos, BlockState state) {
        super(RotasRegistry.QUEST_BOARD_BLOCK_ENTITY.get(), pos, state);
    }

    public String boardId() {
        return boardId;
    }

    public void setBoardId(String boardId) {
        this.boardId = boardId == null ? "" : boardId;
        setChanged();
        sync();
    }

    public String displayName() {
        return displayName;
    }

    public BoardStyle style() {
        return style;
    }

    public int questCount() {
        return questCount;
    }

    public int screenColor() {
        return screenColor;
    }

    public boolean emergency() {
        return emergency;
    }

    public boolean glow() {
        return glow;
    }

    public boolean particles() {
        return particles;
    }

    /** Refreshes the render mirror from the authoritative board config. */
    public void refreshDisplay(String name, BoardStyle style, int questCount, int screenColor,
                               boolean emergency, boolean glow, boolean particles) {
        this.displayName = name;
        this.style = style;
        this.questCount = questCount;
        this.screenColor = screenColor;
        this.emergency = emergency;
        this.glow = glow;
        this.particles = particles;
        setChanged();
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("board_id", boardId);
        tag.putString("display_name", displayName);
        tag.putString("style", style.name());
        tag.putInt("quest_count", questCount);
        tag.putInt("screen_color", screenColor);
        tag.putBoolean("emergency", emergency);
        tag.putBoolean("glow", glow);
        tag.putBoolean("particles", particles);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        boardId = tag.getString("board_id");
        displayName = tag.contains("display_name") ? tag.getString("display_name") : "Quest Board";
        style = Nbt.readEnum(tag, "style", BoardStyle.class, BoardStyle.WOODEN_VILLAGE);
        questCount = tag.getInt("quest_count");
        screenColor = tag.contains("screen_color") ? tag.getInt("screen_color") : 0xFF3BE8FF;
        emergency = tag.getBoolean("emergency");
        glow = tag.getBoolean("glow");
        particles = tag.getBoolean("particles");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
