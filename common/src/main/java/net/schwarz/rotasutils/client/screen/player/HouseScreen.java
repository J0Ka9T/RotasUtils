package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;

import java.util.UUID;

/**
 * The player's house (บ้าน): what it costs, when rent is next taken, and who may build in it.
 *
 * <p>Opened with {@code /rotas house} or from the main menu, on the house the player stands in or
 * else their own. Every button is one server action; the server re-checks it and reopens this screen
 * on the result. Giving a house back asks for a second click, since nothing is refunded.</p>
 */
@Environment(EnvType.CLIENT)
public class HouseScreen extends RotasScreen {
    private static final int MAX_LISTED = 5;

    private final CompoundTag state;
    private boolean confirmLeave;

    public HouseScreen(CompoundTag payload) {
        super(L.t("rotasutils.house.player.title"), null);
        this.state = payload == null ? new CompoundTag() : payload;
    }

    private boolean found() {
        return state.getBoolean("found");
    }

    private String status() {
        return state.getString("status");
    }

    private boolean owner() {
        return state.getBoolean("owner");
    }

    private void act(String action) {
        act(action, null);
    }

    private void act(String action, UUID player) {
        CompoundTag payload = new CompoundTag();
        payload.putString("house", state.getString("id"));
        if (player != null) {
            payload.putUUID("player", player);
        }
        send(action, payload);
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 460);
        guiHeight = Ui.fill(height, 320);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        int contentX = guiLeft + 12;
        int contentWidth = guiWidth - 24;
        int barY = guiTop + guiHeight - 34;
        int sideX = guiLeft + guiWidth / 2 + 6;
        int sideWidth = guiWidth / 2 - 30;

        addRenderableWidget(Ui.boardButton(L.c("rotasutils.house.player.close"), button -> onClose())
                .bounds(contentX, barY, 90, 24).build());

        // Switch between the houses this player owns or belongs to.
        ListTag mine = state.getList("mine", Tag.TAG_COMPOUND);
        int switchY = guiTop + guiHeight - 64;
        int switchX = contentX;
        for (int i = 0; i < Math.min(4, mine.size()); i++) {
            CompoundTag entry = mine.getCompound(i);
            if (entry.getString("id").equals(state.getString("id"))) {
                continue;
            }
            String id = entry.getString("id");
            var button = Ui.boardButton(Ui.text(Ui.truncate(entry.getString("name"), 90)), press -> {
                CompoundTag payload = new CompoundTag();
                payload.putString("house", id);
                send("open_house", payload);
            }).bounds(switchX, switchY, 100, 20).build();
            addRenderableWidget(button);
            switchX += 104;
        }
        if (!found()) {
            return;
        }
        if (state.getBoolean("admin")) {
            addRenderableWidget(Ui.boardButton(L.c("rotasutils.house.player.admin"), press -> minecraft.setScreen(
                    new net.schwarz.rotasutils.client.screen.admin.HouseQuickSettingsScreen(this, state.getString("id"),
                            state.getString("name"), state.getCompound("settings"))))
                    .bounds(guiLeft + guiWidth - 12 - 130, guiTop + 16, 118, 18).build());
        }

        long gold = state.getLong("gold");
        int x = contentX + contentWidth;
        String status = status();
        if ("AVAILABLE".equals(status)) {
            var rent = Ui.boardPrimaryButton(Ui.text(L.t("rotasutils.house.player.rent", state.getLong("deposit"))),
                    press -> act("house_rent")).bounds(x - 170, barY, 170, 24).build();
            rent.active = state.getBoolean("enabled") && gold >= state.getLong("deposit");
            addRenderableWidget(rent);
        } else if (owner()) {
            x -= 130;
            var leave = Ui.boardButton(Ui.text(L.t(confirmLeave ? "rotasutils.house.player.release_confirm"
                    : "rotasutils.house.player.release")), press -> {
                if (confirmLeave) {
                    act("house_leave");
                } else {
                    confirmLeave = true;
                    rebuildWidgets();
                }
            }).bounds(x, barY, 126, 24).build();
            addRenderableWidget(leave);
            if (!"BOUGHT_OUT".equals(status)) {
                x -= 154;
                var buyout = Ui.boardButton(Ui.text(L.t("rotasutils.house.player.buyout", state.getLong("buyout"))),
                        press -> act("house_buyout")).bounds(x, barY, 150, 24).build();
                buyout.active = gold >= state.getLong("buyout");
                addRenderableWidget(buyout);
            }
            if ("OVERDUE".equals(status)) {
                x -= 134;
                var pay = Ui.boardPrimaryButton(Ui.text(L.t("rotasutils.house.player.pay", state.getLong("overdue_charge"))),
                        press -> act("house_pay")).bounds(x, barY, 130, 24).build();
                pay.active = gold >= state.getLong("overdue_charge");
                addRenderableWidget(pay);
            }
        } else if (state.getBoolean("member")) {
            addRenderableWidget(Ui.boardButton(L.c("rotasutils.house.player.leave"), press -> act("house_leave"))
                    .bounds(x - 120, barY, 120, 24).build());
        }

        if (!owner()) {
            return;
        }
        // Members: remove the listed ones, add players standing nearby, buy another slot.
        ListTag members = state.getList("members", Tag.TAG_COMPOUND);
        int y = guiTop + 70;
        for (int i = 0; i < Math.min(MAX_LISTED, members.size()); i++) {
            UUID id = members.getCompound(i).getUUID("uuid");
            addRenderableWidget(Ui.boardButton(Ui.text("x"), press -> act("house_remove_member", id))
                    .bounds(sideX + sideWidth - 18, y - 4, 18, 16).build());
            y += 16;
        }
        ListTag nearby = state.getList("nearby", Tag.TAG_COMPOUND);
        boolean room = members.size() < state.getInt("member_limit");
        int addY = guiTop + 70 + MAX_LISTED * 16 + 18;
        for (int i = 0; i < Math.min(3, nearby.size()); i++) {
            CompoundTag entry = nearby.getCompound(i);
            UUID id = entry.getUUID("uuid");
            var add = Ui.boardButton(Ui.text("+ " + Ui.truncate(entry.getString("name"), sideWidth - 20)),
                    press -> act("house_add_member", id)).bounds(sideX, addY + i * 20, sideWidth, 18).build();
            add.active = room;
            addRenderableWidget(add);
        }
        if (state.getInt("slots_bought") < state.getInt("slots_max")) {
            var slot = Ui.boardButton(Ui.text(L.t("rotasutils.house.player.buy_slot", state.getLong("slot_price"))),
                    press -> act("house_buy_slot")).bounds(sideX, addY + 3 * 20 + 4, sideWidth, 18).build();
            slot.active = gold >= state.getLong("slot_price");
            addRenderableWidget(slot);
        }
    }

    @Override
    protected void renderBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, Ui.BOARD_SCRIM_TOP, Ui.BOARD_SCRIM_BOTTOM);
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.woodFrame(graphics, guiLeft, guiTop, guiWidth, guiHeight);
        Ui.parchment(graphics, guiLeft + 12, guiTop + 12, guiWidth - 24, guiHeight - 84, false);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int contentX = guiLeft + 12;
        int contentWidth = guiWidth - 24;
        int centerX = guiLeft + guiWidth / 2;
        if (!found()) {
            Ui.scaledCentered(graphics, L.t("rotasutils.house.player.title"), centerX, guiTop + 22, 1.3f, Ui.INK);
            Ui.wrapped(graphics, L.t("rotasutils.house.player.none"), contentX + 12, guiTop + 54, contentWidth - 24, Ui.INK_SOFT);
            return;
        }
        // Leave room on both sides for the admin button in the top-right corner.
        int titleRoom = state.getBoolean("admin") ? contentWidth - 300 : contentWidth - 40;
        Ui.scaledCentered(graphics, Ui.truncate(state.getString("name"), Math.max(60, (int) (titleRoom / 1.3f))),
                centerX, guiTop + 22, 1.3f, Ui.INK);

        int leftWidth = guiWidth / 2 - 24;
        int y = guiTop + 50;
        String status = status();
        int statusColor = switch (status) {
            case "AVAILABLE" -> Ui.INK_GOOD;
            case "OVERDUE" -> Ui.INK_BAD;
            default -> Ui.INK;
        };
        y = row(graphics, contentX, leftWidth, y, L.t("rotasutils.house.player.status"),
                L.t("rotasutils.house.player.status." + status.toLowerCase(java.util.Locale.ROOT)), statusColor);
        if (!state.getString("owner_name").isEmpty()) {
            y = row(graphics, contentX, leftWidth, y, L.t("rotasutils.house.player.owner"), state.getString("owner_name"), Ui.INK);
        }
        y = row(graphics, contentX, leftWidth, y, L.t("rotasutils.house.player.size"),
                state.getString("size") + "  @ " + state.getString("where"), Ui.INK_SOFT);
        y = row(graphics, contentX, leftWidth, y, L.t("rotasutils.house.player.deposit"),
                String.valueOf(state.getLong("deposit")), Ui.INK);
        y = row(graphics, contentX, leftWidth, y, L.t("rotasutils.house.player.rent_row"),
                L.t("rotasutils.house.player.rent_every", state.getLong("maintenance"), state.getString("interval")), Ui.INK);
        if ("ACTIVE".equals(status) && !state.getString("next_payment").isEmpty()) {
            y = row(graphics, contentX, leftWidth, y, L.t("rotasutils.house.player.next_payment"),
                    state.getString("next_payment"), Ui.INK);
        }
        if ("OVERDUE".equals(status)) {
            y = row(graphics, contentX, leftWidth, y, L.t("rotasutils.house.player.grace_left"),
                    state.getString("grace_left"), Ui.INK_BAD);
        }
        y = row(graphics, contentX, leftWidth, y, L.t("rotasutils.house.player.gold"),
                String.valueOf(state.getLong("gold")), Ui.INK_SOFT);

        String hint = switch (status) {
            case "AVAILABLE" -> L.t("rotasutils.house.player.hint.available");
            case "OVERDUE" -> owner() ? L.t("rotasutils.house.player.hint.overdue") : "";
            case "BOUGHT_OUT" -> owner() ? L.t("rotasutils.house.player.hint.bought_out") : "";
            default -> owner() ? L.t("rotasutils.house.player.hint.active") : "";
        };
        if (confirmLeave) {
            hint = L.t("rotasutils.house.player.hint.release");
        }
        if (!hint.isEmpty()) {
            Ui.wrapped(graphics, hint, contentX + 12, y + 8, leftWidth - 12,
                    confirmLeave || "OVERDUE".equals(status) ? Ui.INK_BAD : Ui.INK_SOFT);
        }

        if (!owner() && !state.getBoolean("member")) {
            return;
        }
        int sideX = guiLeft + guiWidth / 2 + 6;
        int sideWidth = guiWidth / 2 - 30;
        ListTag members = state.getList("members", Tag.TAG_COMPOUND);
        Ui.label(graphics, L.t("rotasutils.house.player.members", members.size(), state.getInt("member_limit")),
                sideX, guiTop + 52, Ui.INK);
        int my = guiTop + 70;
        for (int i = 0; i < Math.min(MAX_LISTED, members.size()); i++) {
            Ui.label(graphics, Ui.truncate(members.getCompound(i).getString("name"), sideWidth - 24), sideX, my, Ui.INK_SOFT);
            my += 16;
        }
        if (members.size() > MAX_LISTED) {
            Ui.label(graphics, L.t("rotasutils.house.player.more", members.size() - MAX_LISTED), sideX, my, Ui.INK_FADE);
        }
        if (members.isEmpty()) {
            Ui.label(graphics, L.t("rotasutils.house.player.no_members"), sideX, my, Ui.INK_FADE);
        }
        if (owner()) {
            int addY = guiTop + 70 + MAX_LISTED * 16 + 6;
            Ui.label(graphics, state.getList("nearby", Tag.TAG_COMPOUND).isEmpty()
                    ? L.t("rotasutils.house.player.nobody_near") : L.t("rotasutils.house.player.add_near"),
                    sideX, addY, Ui.INK_SOFT);
        }
    }

    private int row(GuiGraphics graphics, int x, int width, int y, String label, String value, int color) {
        Ui.label(graphics, label, x + 12, y, Ui.INK_SOFT);
        Ui.labelRight(graphics, Ui.truncate(value, width / 2 + 20), x + width, y, color);
        return y + 13;
    }
}
