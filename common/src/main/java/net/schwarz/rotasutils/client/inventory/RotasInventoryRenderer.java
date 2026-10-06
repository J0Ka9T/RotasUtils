package net.schwarz.rotasutils.client.inventory;

import com.mojang.blaze3d.systems.RenderSystem;
import net.schwarz.rotasutils.client.screen.PixelUi;
import com.schwarz.lenlorui.ui.UiCanvas;
import com.schwarz.lenlorui.ui.UiColor;
import com.schwarz.lenlorui.ui.UiSpacing;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.compat.CuriosClientCompat;
import net.schwarz.rotasutils.client.compat.SpellPointsClientCompat;
import net.schwarz.rotasutils.client.screen.RotasTheme;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.progress.ActiveQuest;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.reward.Reward;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class RotasInventoryRenderer {
    public enum ActionType {
        NONE,
        SELECT_TAB,
        OPEN_SKILLS,
        OPEN_PARTY,
        OPEN_STATS,
        WITHDRAW_COINS,
        SELECT_QUEST,
        OPEN_QUEST,
        OPEN_TITLES
    }

    public record Hit(ActionType type, CharacterHubTab tab, String value) {
        public static final Hit NONE = new Hit(ActionType.NONE, null, "");
    }

    private record Rect(int x, int y, int width, int height) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + width && my >= y && my < y + height;
        }
    }

    private record CurioVisual(CuriosClientCompat.Entry entry, Rect rect) {
    }

    private record HoverTarget(ItemStack stack, String subtitle, String hint) {
    }

    private record DescriptionLine(String text, int color) {
    }

    private static final ResourceLocation TEX_DISC = new ResourceLocation("rotasutils", "textures/gui/hub/disc.png");
    private static final ResourceLocation TEX_SOCKET = new ResourceLocation("rotasutils", "textures/gui/hub/socket.png");
    private static final ResourceLocation TEX_CARD = new ResourceLocation("rotasutils", "textures/gui/hub/card.png");
    private static final ResourceLocation TEX_SLOT = new ResourceLocation("rotasutils", "textures/gui/hub/slot.png");
    private static final ResourceLocation TEX_TAB = new ResourceLocation("rotasutils", "textures/gui/hub/tab.png");
    private static final int DISC_TEX = 512;
    private static final float DISC_FILL_RATIO = 490f / 512f;
    private static final int SOCKET_TEX = 96;
    private static final int CARD_TEX_W = 730;
    private static final int CARD_TEX_H = 626;
    private static final int CARD_SLICE = 12;
    private static final int SLOT_TEX = 64;
    private static final int TAB_TEX_W = 68;
    private static final int TAB_TEX_H = 64;
    private static final Map<ResourceLocation, AbstractTexture> SMOOTHED = new HashMap<>();

    private static final int HEALTH = 0xFFD8756E;
    private static final int FOOD = 0xFFE0A960;
    private static final int SPELL = 0xFF8FA9C4;
    private static final int BAR_TRACK = 0xFFF8EDD9;

    private static final int INK_LINE = 0xFF997956;
    private static final int CONTROL_FILL = 0xFFF8EBD7;
    private static final int LINE = 0x99D0B594;
    private static final int SLOT_EMPTY_FILL = 0xEAF1E6D1;
    private static final int SLOT_EMPTY_BORDER = 0xFFD8C2A2;
    private static final int SLOT_HOVER_FILL = 0xFFF8EDD9;
    private static final int SLOT_HOVER_BORDER = 0xFFC5A175;
    private static final int SLOT_OCCUPIED_FILL = 0xFFF1E6D1;
    private static final int CARD_KEYLINE = 0x40B08D57;
    private static final int TOOLTIP_FILL = 0xFFFBF4E6;

    private static final int GEAR_ICON_IDLE = 0x80AA8D68;
    private static final int GEAR_ICON_HOVER = 0xFF51351F;
    private static final int GEAR_ICON_ACTIVE = 0xFFC5A175;
    private static final int WORLD_TEXT = 0xFFFFFFFF;
    private static final int WORLD_TEXT_MUTED = 0xFFF1E6D1;
    private static final int WORLD_OUTLINE = 0xB0654A30;

    public static final int TAB_W = 34;
    public static final int TAB_H = 32;
    public static final int TAB_OVERHANG = 17;
    public static final int TAB_STRIP_H = 18;
    public static final int TAB_GAP = 2;
    private static final int TAB_DISABLED_TEXT = 0xFF9B8062;
    private static final int TAB_ACTIVE_FILL = 0xFFD6C09A;
    private static final int HOVER_FADE_MS = 140;

    public static final int PANE_GUTTER = 8;
    public static final int STATUS_FULL_H = 136;
    public static final int STATUS_COMPACT_H = 76;
    public static final int STATS_CHIP_H = 14;
    public static final int WALLET_BUTTONS_W = 74;
    public static final int WALLET_BALANCE_W = 58;
    public static final int LEVEL_BADGE_H = 18;
    public static final int NAME_GAP = 6;
    public static final int NAME_BLOCK_H = 58;
    public static final int NAME_BLOCK_COMPACT_H = 32;
    public static final int CURIO_RESERVE = 30;
    private static final int CURIO_SIZE = 18;
    private static final int CURIO_RING_STEP = CURIO_SIZE + 4;
    private static final int EXP_BAR_W = 120;
    private static final int EXP_FILL = 0xFF7FB069;
    private static final int[] WITHDRAW_AMOUNTS = {10, 100, 1000};
    private static final int[] WITHDRAW_WIDTHS = {20, 24, 24};

    private static final int SOCKET_HEAD = 0;
    private static final int SOCKET_CHEST = 1;
    private static final int SOCKET_LEGS = 2;
    private static final int SOCKET_FEET = 3;
    private static final int SOCKET_OFFHAND = 4;

    private static final Map<String, ResourceLocation> CURIO_ICONS = Map.of(
            "back", new ResourceLocation("curios", "textures/slot/empty_back_slot.png"),
            "belt", new ResourceLocation("curios", "textures/slot/empty_belt_slot.png"),
            "body", new ResourceLocation("curios", "textures/slot/empty_body_slot.png"),
            "bracelet", new ResourceLocation("curios", "textures/slot/empty_bracelet_slot.png"),
            "charm", new ResourceLocation("curios", "textures/slot/empty_charm_slot.png"),
            "curio", new ResourceLocation("curios", "textures/slot/empty_curio_slot.png"),
            "hands", new ResourceLocation("curios", "textures/slot/empty_hands_slot.png"),
            "head", new ResourceLocation("curios", "textures/slot/empty_head_slot.png"),
            "necklace", new ResourceLocation("curios", "textures/slot/empty_necklace_slot.png"),
            "ring", new ResourceLocation("curios", "textures/slot/empty_ring_slot.png"));

    private static CharacterHubTab activeTab = CharacterHubTab.BAG;
    private static int partyScroll;
    private static int questScroll;
    private static Layout cachedLayout;
    private static ItemStack coinStack;

    private static String hoverKey = "";
    private static long hoverStartMillis;

    private RotasInventoryRenderer() {
    }

    public static void resetScrolls() {
        partyScroll = 0;
        questScroll = 0;
    }

    public static int targetWidth(int screenWidth) {
        int margin = screenWidth < 540 ? UiSpacing.SMALL : UiSpacing.SECTION;
        int available = Math.max(1, screenWidth - margin * 2);
        int preferred = screenWidth >= 780 ? screenWidth - 190 : screenWidth - 56;
        return Math.min(640, Math.max(1, Math.min(available, preferred)));
    }

    public static int targetHeight(int screenHeight) {
        int margin = screenHeight < 360 ? UiSpacing.SMALL : UiSpacing.SECTION;
        int available = Math.max(1, screenHeight - margin * 2);
        int preferred = screenHeight >= 460 ? screenHeight - 116 : screenHeight - 32;
        return Math.min(360, Math.max(1, Math.min(available, preferred)));
    }

    public static Layout layout(int width, int height) {
        return layout(width, height, 0);
    }

    public static Layout layout(int width, int height, int curioCount) {
        width = Math.max(1, width);
        height = Math.max(1, height);

        int minCardW = 9 * 18 + 8 * 2 + 2 * 8;
        int cardW = Math.max(1, Math.min(width - PANE_GUTTER - 1,
                Math.max(Math.max(minCardW, width * 55 / 100), Math.min(300, width - 148))));
        int rightX = width - cardW;
        int leftPane = Math.max(1, rightX - PANE_GUTTER);
        int tabRow = TAB_W * 5 + TAB_GAP * 4;
        boolean hangingTabs = cardW - 12 - tabRow >= 130
                && height - TAB_OVERHANG >= 24 + STATUS_FULL_H + 10 + 8 + 8 + 4 * 18;
        int tabY = 0;
        int tabW = hangingTabs ? TAB_W : Math.max(1, (cardW - 16 - TAB_GAP * 4) / 5);
        int tabH = hangingTabs ? TAB_H : TAB_STRIP_H;
        int tabX;
        int panelY = hangingTabs ? TAB_OVERHANG : TAB_STRIP_H + 2;
        int panelH = Math.max(1, height - panelY);
        int contentShift = hangingTabs ? tabH - TAB_OVERHANG + 4 : 0;

        int gap = cardW >= 300 ? 4 : 2;
        int pad = 12;
        int hotbarGap = 8;
        int byWidth = (cardW - pad * 2 - gap * 8) / 9;
        boolean compact = (panelH - pad * 2 - STATUS_FULL_H - 10 - gap * 2 - hotbarGap) / 4 < 18;
        if (compact) {
            pad = 8;
            hotbarGap = 4;
        }
        int status = compact ? STATUS_COMPACT_H : STATUS_FULL_H;
        int byHeight = (panelH - pad * 2 - status - (compact ? 6 : 10) - gap * 2 - hotbarGap) / 4;
        int slotBox = Math.max(16, Math.min(34, Math.min(byWidth, byHeight)));
        int spacing = slotBox + gap;
        int gridW = slotBox * 9 + gap * 8;
        int side = Math.max(pad, (cardW - gridW) / 2);
        int needed = status + (compact ? 6 : 10) + slotBox * 4 + gap * 2 + hotbarGap;
        int vPad = Math.max(pad, Math.min(side, (panelH - needed) / 2));
        if (!compact) {
            int available = panelH;
            panelH = Math.min(available, needed + vPad * 2);
            panelY += (available - panelH) / 2;
            tabY = panelY - (hangingTabs ? TAB_OVERHANG : TAB_STRIP_H + 2);
        }
        pad = side;
        int gridX = rightX + side;
        int hotbarY = panelY + panelH - vPad - slotBox;
        int gridY = hotbarY - hotbarGap - (slotBox * 3 + gap * 2);
        int statusY = panelY + vPad;
        int cardRight = gridX + gridW;
        tabX = hangingTabs ? cardRight - tabRow : rightX + 8;

        int craftBox = slotBox;
        int craftX = gridX + spacing * 5;
        int resultX = gridX + spacing * 8;
        int craftY = compact ? statusY + 18 : hangingTabs ? tabY + tabH + 8 : statusY;
        int resultY = craftY + spacing / 2;
        int walletY = compact ? statusY : statusY + STATUS_FULL_H - 14;

        int barX = gridX;
        int barW = Math.max(24, compact
                ? craftX - 8 - barX
                : Math.min(craftX - 14 - barX, cardW * 55 / 100));
        int statsW = compact ? barW
                : Math.max(24, Math.min(barW, cardRight - WALLET_BUTTONS_W - WALLET_BALANCE_W - 8 - barX));
        int healthY = statusY + (compact ? 18 : 24);
        int spellY = healthY + (compact ? 19 : 23);
        int chipW = Math.min(barW, 112);
        int chipX = barX + barW - chipW;
        int chipY = statusY + 70;
        int statsY = compact ? spellY + 19 : statusY + 92;

        int below = NAME_GAP + (compact ? NAME_BLOCK_COMPACT_H : NAME_BLOCK_H);
        int badge = compact ? 0 : LEVEL_BADGE_H;
        int diameter = Math.max(48, Math.min(236,
                Math.min(height - badge - below - 8, leftPane - CURIO_RESERVE - 4)));
        while (diameter > 48 && diameter + socketReach(diameter) + curioReserve(diameter, curioCount) > leftPane) {
            diameter -= 2;
        }
        int curioReserve = curioReserve(diameter, curioCount);
        int radius = diameter / 2;
        int socketSize = socketSize(diameter);
        float orbit = socketOrbit(diameter, socketSize);
        float step = Math.max(22.5f, (float) Math.toDegrees((socketSize + 2) / orbit));
        int reach = socketReach(diameter);
        int rise = (int) Math.ceil(Math.sin(Math.toRadians(step * 2)) * orbit + socketSize / 2f - radius);
        int above = Math.max(badge, rise);
        int slack = Math.max(0, leftPane - (diameter + reach + curioReserve));
        int portraitCenterX = reach + radius + slack / 2;
        int blockTop = Math.max(0, (height - above - diameter - below) / 2);
        int portraitCenterY = blockTop + above + radius;
        int nameY = portraitCenterY - radius + diameter + NAME_GAP;

        int[] socketX = new int[5];
        int[] socketY = new int[5];
        for (int i = 0; i < 5; i++) {
            double angle = Math.toRadians(180f + step * 2 - i * step);
            socketX[i] = portraitCenterX + (int) Math.round(Math.cos(angle) * orbit);
            socketY[i] = portraitCenterY + (int) Math.round(Math.sin(angle) * orbit);
        }

        return new Layout(width, height, curioCount, compact, pad,
                leftPane, rightX, cardW, tabX, tabY, tabW, tabH, panelY, panelH, contentShift,
                nameY, portraitCenterX, portraitCenterY, diameter, socketSize,
                socketX[SOCKET_HEAD], socketY[SOCKET_HEAD], socketX[SOCKET_CHEST], socketY[SOCKET_CHEST],
                socketX[SOCKET_LEGS], socketY[SOCKET_LEGS], socketX[SOCKET_FEET], socketY[SOCKET_FEET],
                socketX[SOCKET_OFFHAND], socketY[SOCKET_OFFHAND],
                statusY, barX, barW, statsW, healthY, spellY, chipX, chipY, chipW, statsY,
                craftX, craftY, craftBox, resultX, resultY, walletY,
                gridX, gridY, hotbarY, slotBox, spacing);
    }

    private static int curioReserve(int diameter, int count) {
        return CURIO_RESERVE + Math.max(0, curioRings(diameter, count) - 1) * CURIO_RING_STEP;
    }

    private static float curioBaseOrbit(int diameter) {
        return diameter / 2f / DISC_FILL_RATIO + CURIO_SIZE / 2f + 3;
    }

    private static int curioRingCapacity(int diameter, int ring) {
        float orbit = curioBaseOrbit(diameter) + ring * CURIO_RING_STEP;
        float step = (float) Math.toDegrees((CURIO_SIZE + 3) / orbit);
        return Math.max(1, (int) (150f / step) + 1);
    }

    public static int curioRings(int diameter, int count) {
        int rings = 0;
        for (int placed = 0; placed < count; rings++) {
            placed += curioRingCapacity(diameter, rings);
        }
        return rings;
    }

    private static int socketSize(int diameter) {
        return diameter < 80 ? 18 : Math.max(22, Math.min(40, Math.round(diameter * 0.2f)));
    }

    private static float socketOrbit(int diameter, int socketSize) {
        return Math.max(diameter / 2f * 1.327f, diameter / 2f + socketSize / 2f + 4);
    }

    private static int socketReach(int diameter) {
        int size = socketSize(diameter);
        return (int) Math.ceil(socketOrbit(diameter, size) + size / 2f - diameter / 2f) + 2;
    }

    private static Layout cachedLayout(int width, int height) {
        LocalPlayer player = Minecraft.getInstance().player;
        int curios = player == null ? 0 : CharacterHubModel.capture(player).curios().size();
        Layout l = cachedLayout;
        if (l == null || l.width() != Math.max(1, width) || l.height() != Math.max(1, height)
                || l.curioCount() != curios) {
            l = layout(width, height, curios);
            cachedLayout = l;
        }
        return l;
    }

    public static Layout currentLayout(int width, int height) {
        return cachedLayout(width, height);
    }

    public static int slotHitSize(Layout l, int menuIndex) {
        if ((menuIndex >= 5 && menuIndex <= 8) || menuIndex == 45) return l.socketSize();
        if (menuIndex >= 0 && menuIndex <= 4) return l.craftBox();
        return l.slotBox();
    }

    public static int slotHitSize(int imageWidth, int imageHeight, int menuIndex) {
        return slotHitSize(cachedLayout(imageWidth, imageHeight), menuIndex);
    }

    public static int itemInset(int box) {
        return (box - 16) / 2;
    }

    public static void renderBackground(GuiGraphics graphics, int left, int top,
                                        int width, int height, int mouseX, int mouseY,
                                        CharacterHubTab tab, String selectedQuestId) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) return;

        if (activeTab != tab) {
            if (tab == CharacterHubTab.PARTY) partyScroll = 0;
            if (tab == CharacterHubTab.QUEST) questScroll = 0;
        }
        activeTab = tab;
        Layout l = cachedLayout(width, height);
        CharacterHubModel.Snapshot model = CharacterHubModel.capture(player);

        renderCharacter(graphics, player, model, left, top, mouseX, mouseY, l);
        renderIdentity(graphics, model, left, top, l, mouseX, mouseY);

        switch (tab) {
            case BAG -> renderBag(graphics, player, model, left, top, l, mouseX, mouseY);
            case JOB -> renderSkillPath(graphics, model, model.jobPath(), model.jobName(), false, left, top, l, mouseX, mouseY);
            case SUB -> renderSkillPath(graphics, model, model.subPath(), model.subName(), true, left, top, l, mouseX, mouseY);
            case PARTY -> renderParty(graphics, model, left, top, l);
            case QUEST -> renderQuests(graphics, model, selectedQuestId, left, top, l, mouseX, mouseY,
                    questScroll);
        }
        renderTabs(graphics, model, tab, left, top, l, mouseX, mouseY);
    }

    public static void renderOverlay(GuiGraphics graphics, int left, int top, int width, int height,
                                     int mouseX, int mouseY) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (!player.inventoryMenu.getCarried().isEmpty()) return;
        CharacterHubModel.Snapshot model = CharacterHubModel.capture(player);
        Layout l = cachedLayout(width, height);
        Font font = Minecraft.getInstance().font;
        int hubRight = left + width - 8;
        int hubBottom = top + height - 8;

        HoverTarget target = hoveredVanillaSlot(player, l, left, top, mouseX, mouseY);
        if (target != null) {
            inspectionPanel(graphics, font, mouseX, mouseY, hubRight, hubBottom,
                    target.stack().getHoverName().getString(), rarityColor(target.stack()),
                    target.subtitle(), descriptionLines(target.stack(), player), target.hint());
            return;
        }

        if (titleRect(left, top, l).contains(mouseX, mouseY)) {
            net.schwarz.rotasutils.title.TitleDef worn = ClientState.title(ClientState.progress().activeTitle());
            inspectionPanel(graphics, font, mouseX, mouseY, hubRight, hubBottom,
                    worn == null ? L.t("rotasutils.title.none") : worn.name(),
                    worn == null ? RotasTheme.TEXT : readableOnCard(0xFF000000 | worn.color()),
                    L.t("rotasutils.title.screen"), List.of(), L.t("rotasutils.hub.title_hint"));
            return;
        }

        String gearLabel = emptyGearLabel(player, l, left, top, mouseX, mouseY);
        if (gearLabel != null) {
            inspectionPanel(graphics, font, mouseX, mouseY, hubRight, hubBottom,
                    gearLabel, RotasTheme.TEXT, L.t("rotasutils.hub.slot.equipment"),
                    List.of(), L.t("rotasutils.hub.slot.drag_here"));
            return;
        }

        for (CurioVisual visual : curioVisuals(model, left, top, l)) {
            if (!visual.rect().contains(mouseX, mouseY)) continue;
            ItemStack stack = visual.entry().stack();
            if (!stack.isEmpty()) {
                inspectionPanel(graphics, font, mouseX, mouseY, hubRight, hubBottom,
                        stack.getHoverName().getString(), rarityColor(stack),
                        visual.entry().displayName().toUpperCase(Locale.ROOT) + L.t("rotasutils.hub.slot.accessory_tag"),
                        List.of(), L.t("rotasutils.hub.slot.drag_swap"));
            } else {
                inspectionPanel(graphics, font, mouseX, mouseY, hubRight, hubBottom,
                        visual.entry().displayName(), RotasTheme.TEXT, L.t("rotasutils.hub.slot.accessory"),
                        List.of(), L.t("rotasutils.hub.slot.drag_here"));
            }
            return;
        }
    }

    private static HoverTarget hoveredVanillaSlot(LocalPlayer player, Layout l, int left, int top,
                                                 double mouseX, double mouseY) {
        InventoryMenu menu = player.inventoryMenu;
        int count = Math.min(menu.slots.size(), 46);
        for (int index = 0; index < count; index++) {
            Slot slot = menu.getSlot(index);
            if (slot.x < -1000) continue;
            int size = slotHitSize(l, index);
            Rect rect = new Rect(left + slot.x + 8 - size / 2, top + slot.y + 8 - size / 2, size, size);
            if (!rect.contains(mouseX, mouseY)) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) return null;
            String swap = L.t("rotasutils.hub.slot.drag_swap");
            return switch (index) {
                case 5 -> new HoverTarget(stack, L.t("rotasutils.hub.tag.headgear"), swap);
                case 6 -> new HoverTarget(stack, L.t("rotasutils.hub.tag.body"), swap);
                case 7 -> new HoverTarget(stack, L.t("rotasutils.hub.tag.legs"), swap);
                case 8 -> new HoverTarget(stack, L.t("rotasutils.hub.tag.feet"), swap);
                case 45 -> new HoverTarget(stack, L.t("rotasutils.hub.tag.offhand"), swap);
                case 0 -> new HoverTarget(stack, L.t("rotasutils.hub.slot.craft_result"),
                        L.t("rotasutils.hub.slot.click_craft"));
                case 1, 2, 3, 4 -> new HoverTarget(stack, L.t("rotasutils.hub.slot.crafting"),
                        L.t("rotasutils.hub.slot.q_drop"));
                default -> {
                    boolean equipable = LivingEntity.getEquipmentSlotForItem(stack) != EquipmentSlot.MAINHAND;
                    yield new HoverTarget(stack, itemCategory(stack), equipable
                            ? L.t("rotasutils.hub.slot.drop_equip") : L.t("rotasutils.hub.slot.q_drop"));
                }
            };
        }
        return null;
    }

    private static List<DescriptionLine> descriptionLines(ItemStack stack, LocalPlayer player) {
        List<DescriptionLine> lines = new ArrayList<>();
        List<Component> tooltip = stack.getTooltipLines(player, TooltipFlag.NORMAL);
        for (int i = 1; i < tooltip.size(); i++) {
            Component part = tooltip.get(i);
            String text = part.getString();
            if (text.isBlank()) {
                lines.add(new DescriptionLine("", RotasTheme.TEXT_MUTED));
                continue;
            }
            var style = part.getStyle().getColor();
            int color = style == null ? RotasTheme.TEXT_MUTED : 0xFF000000 | style.getValue();
            lines.add(new DescriptionLine(text, readableOnCard(color)));
        }
        return lines;
    }

    private static int readableOnCard(int color) {
        return UiColor.isLight(color) ? UiColor.mix(color, RotasTheme.TEXT, 0.6f) : color;
    }

    private static List<String> indentAwareWrap(String text, int width) {
        int indent = 0;
        while (indent < text.length() && text.charAt(indent) == ' ') {
            indent++;
        }
        String pad = text.substring(0, indent);
        List<String> out = new ArrayList<>();
        for (String piece : Ui.wrap(text.substring(indent), Math.max(20, width - indent))) {
            out.add(piece.isEmpty() ? piece : pad + piece);
        }
        return out;
    }

    private static void shadowLabel(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color, false);
    }

    private static String itemCategory(ItemStack stack) {
        return switch (LivingEntity.getEquipmentSlotForItem(stack)) {
            case HEAD -> L.t("rotasutils.hub.tag.headgear");
            case CHEST -> L.t("rotasutils.hub.tag.body");
            case LEGS -> L.t("rotasutils.hub.tag.legs");
            case FEET -> L.t("rotasutils.hub.tag.feet");
            case OFFHAND -> L.t("rotasutils.hub.tag.offhand");
            default -> stack.isEdible() ? L.t("rotasutils.hub.tag.food")
                    : stack.getItem() instanceof BlockItem ? L.t("rotasutils.hub.tag.block")
                    : stack.isDamageableItem() ? L.t("rotasutils.hub.tag.tool")
                    : L.t("rotasutils.hub.tag.material");
        };
    }

    private static int rarityColor(ItemStack stack) {
        return switch (stack.getRarity()) {
            case UNCOMMON -> 0xFF6E8C3A;
            case RARE -> 0xFF3E7C99;
            case EPIC -> 0xFF8B5FA8;
            default -> RotasTheme.TEXT;
        };
    }

    private static void inspectionPanel(GuiGraphics graphics, Font font, int mouseX, int mouseY,
                                        int hubRight, int hubBottom, String title, int titleColor,
                                        String subtitle, List<DescriptionLine> lines, String hint) {
        int width = 200;
        int bodyWidth = width - 26;
        List<DescriptionLine> body = new ArrayList<>();
        for (String piece : indentAwareWrap(subtitle, bodyWidth)) {
            body.add(new DescriptionLine(piece, RotasTheme.TEXT_MUTED));
        }
        for (DescriptionLine line : lines) {
            if (line.text().isEmpty()) {
                body.add(line);
                continue;
            }
            for (String piece : indentAwareWrap(line.text(), bodyWidth)) {
                body.add(new DescriptionLine(piece, line.color()));
            }
        }
        List<String> hintLines = Ui.wrap(hint, bodyWidth);
        int hintHeight = hintLines.isEmpty() ? 0 : hintLines.size() * 10 + 6;
        int maxBody = Math.max(1, (hubBottom - 6 - 22 - hintHeight) / 10);
        int hidden = 0;
        if (body.size() > maxBody) {
            hidden = body.size() - (maxBody - 1);
            body = new ArrayList<>(body.subList(0, Math.max(0, maxBody - 1)));
        }
        int height = 22 + body.size() * 10 + (hidden > 0 ? 10 : 0) + hintHeight + 6;
        int x = mouseX + 14;
        if (x + width > hubRight) x = mouseX - 14 - width;
        x = Math.max(4, Math.min(x, hubRight - width));
        int y = mouseY - 12;
        if (y + height > hubBottom) y = hubBottom - height;
        if (y < 6) y = 6;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        try (UiCanvas ui = UiCanvas.begin(graphics, RotasTheme.MAIN)) {
            ui.shadow(x, y, width, height, 4, UiColor.multiplyAlpha(RotasTheme.MAIN.shadow(), 0.6f));
            ui.borderedRoundedRect(x, y, width, height, 4, INK_LINE, TOOLTIP_FILL);
            ui.rect(x + 3, y + 3, width - 6, 1, CARD_KEYLINE);
            ui.rect(x + 7, y + 7, 2, 10, RotasTheme.ACCENT);
        }
        shadowLabel(graphics, font, Ui.truncate(title, width - 22), x + 13, y + 7, titleColor);
        int lineY = y + 20;
        for (DescriptionLine line : body) {
            if (!line.text().isEmpty()) {
                shadowLabel(graphics, font, line.text(), x + 13, lineY, line.color());
            }
            lineY += 10;
        }
        if (hidden > 0) {
            shadowLabel(graphics, font, L.t("rotasutils.hub.more_lines", hidden), x + 13, lineY,
                    RotasTheme.TEXT_FAINT);
            lineY += 10;
        }
        for (String line : hintLines) {
            shadowLabel(graphics, font, line, x + 13, lineY + 4, RotasTheme.TEXT_FAINT);
            lineY += 10;
        }
        graphics.pose().popPose();
    }

    public static CuriosClientCompat.Entry curioAt(int left, int top, int width, int height,
                                                    double mouseX, double mouseY) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;
        CharacterHubModel.Snapshot model = CharacterHubModel.capture(player);
        for (CurioVisual visual : curioVisuals(model, left, top, cachedLayout(width, height))) {
            if (visual.rect().contains(mouseX, mouseY)) return visual.entry();
        }
        return null;
    }

    public static Hit hitTest(int left, int top, int width, int height,
                              CharacterHubTab currentTab, String selectedQuestId,
                              double mouseX, double mouseY) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return Hit.NONE;
        Layout l = cachedLayout(width, height);
        CharacterHubModel.Snapshot model = CharacterHubModel.capture(player);

        List<CharacterHubTab> tabs = List.of(CharacterHubTab.values());
        List<Rect> tabRects = tabRects(left, top, l);
        for (int i = 0; i < tabs.size(); i++) {
            if (tabRects.get(i).contains(mouseX, mouseY)) {
                CharacterHubTab candidate = tabs.get(i);
                return CharacterHubModel.tabAvailable(candidate, model)
                        ? new Hit(ActionType.SELECT_TAB, candidate, "") : Hit.NONE;
            }
        }

        if (titleRect(left, top, l).contains(mouseX, mouseY)) {
            return new Hit(ActionType.OPEN_TITLES, currentTab, "");
        }

        int panelX = left + l.rightX();
        int panelY = top + l.panelY() + l.contentShift();
        int panelW = l.rightW();
        int panelH = l.panelH() - l.contentShift();

        if (currentTab == CharacterHubTab.BAG) {
            if (statsButtonVisible(l)
                    && statsChipRect(left, top, l, ClientState.progress().rpg().statPoints()).contains(mouseX, mouseY)) {
                return new Hit(ActionType.OPEN_STATS, currentTab, "");
            }
            Rect[] withdrawals = withdrawalRects(left, top, l);
            for (int i = 0; i < withdrawals.length; i++) {
                if (withdrawals[i].contains(mouseX, mouseY)) {
                    return new Hit(ActionType.WITHDRAW_COINS, currentTab, Integer.toString(WITHDRAW_AMOUNTS[i]));
                }
            }
        }
        Rect action = actionButtonRect(left, top, l, currentTab);
        if ((currentTab == CharacterHubTab.JOB || currentTab == CharacterHubTab.SUB)
                && action.contains(mouseX, mouseY)) {
            return new Hit(ActionType.OPEN_SKILLS, currentTab, "");
        }
        if (currentTab == CharacterHubTab.PARTY && action.contains(mouseX, mouseY)) {
            return new Hit(ActionType.OPEN_PARTY, currentTab, "");
        }
        if (currentTab == CharacterHubTab.QUEST) {
            List<CharacterHubModel.QuestEntry> quests = model.quests();
            int listX = panelX + 14;
            int listY = panelY + 48;
            int listW = Math.min(142, Math.max(112, panelW * 34 / 100));
            int rowH = 27;
            int visibleRows = Math.max(1, Math.min(quests.size(), (panelH - 74) / rowH));
            int first = Math.min(questScroll, Math.max(0, quests.size() - visibleRows));
            for (int i = 0; i < visibleRows; i++) {
                int index = first + i;
                if (index < quests.size()
                        && new Rect(listX, listY + i * rowH, listW, rowH - 3).contains(mouseX, mouseY)) {
                    return new Hit(ActionType.SELECT_QUEST, currentTab, quests.get(index).id());
                }
            }
            String selected = resolveQuestId(model, selectedQuestId);
            if (!selected.isBlank() && action.contains(mouseX, mouseY)) {
                return new Hit(ActionType.OPEN_QUEST, currentTab, selected);
            }
        }
        return Hit.NONE;
    }

    public static boolean scrollList(int left, int top, int width, int height,
                                     double mouseX, double mouseY, double delta) {
        return scrollList(left, top, width, height, activeTab, mouseX, mouseY, delta);
    }

    public static boolean scrollList(int left, int top, int width, int height,
                                     CharacterHubTab tab, double mouseX, double mouseY, double delta) {
        if (delta == 0 || tab == CharacterHubTab.BAG || tab == CharacterHubTab.JOB
                || tab == CharacterHubTab.SUB) return false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        Layout l = cachedLayout(width, height);
        CharacterHubModel.Snapshot model = CharacterHubModel.capture(player);
        int panelX = left + l.rightX();
        int panelY = top + l.panelY() + l.contentShift();
        int panelW = l.rightW();
        int panelH = l.panelH() - l.contentShift();
        int listX = panelX + 14;
        int listY = panelY + 48;
        int listW = tab == CharacterHubTab.QUEST
                ? Math.min(142, Math.max(112, panelW * 34 / 100)) : panelW - 28;
        int listH = Math.max(1, panelH - 82);
        if (!new Rect(listX, listY, listW, listH).contains(mouseX, mouseY)) return false;
        int rowHeight = tab == CharacterHubTab.QUEST ? 27 : 34;
        int count = tab == CharacterHubTab.QUEST ? model.quests().size() : model.party().size();
        int visible = Math.max(1, listH / rowHeight);
        int max = Math.max(0, count - visible);
        int amount = Math.max(1, (int) Math.ceil(Math.abs(delta)));
        int change = (int) Math.signum(delta) * amount;
        if (tab == CharacterHubTab.QUEST) {
            questScroll = Math.max(0, Math.min(max, questScroll - change));
        } else {
            partyScroll = Math.max(0, Math.min(max, partyScroll - change));
        }
        return count > visible;
    }

    public static boolean showsMainInventory(CharacterHubTab tab) {
        return tab == CharacterHubTab.BAG;
    }

    public static boolean showsCrafting(CharacterHubTab tab) {
        return tab == CharacterHubTab.BAG;
    }

    public static boolean showsHotbar(CharacterHubTab tab) {
        return tab == CharacterHubTab.BAG || tab == CharacterHubTab.JOB || tab == CharacterHubTab.SUB;
    }

    public static void renderLabels(GuiGraphics graphics) {
    }

    private static void renderIdentity(GuiGraphics graphics, CharacterHubModel.Snapshot model,
                                       int left, int top, Layout l, int mouseX, int mouseY) {
        String job = model.jobName().isBlank() ? L.t("rotasutils.hub.unassigned") : model.jobName();
        String level = String.format(Locale.ROOT, "%02d", model.level());
        int center = left + l.portraitCenterX();
        int y = top + l.nameY();
        int maxWidth = Math.max(40, l.leftPane() - 8);
        Rect titleRect = titleRect(left, top, l);
        boolean titleHovered = titleRect.contains(mouseX, mouseY);
        if (l.compact()) {
            outlinedText(graphics, model.playerName(), center, y, 1, WORLD_TEXT, maxWidth);
            renderTitleLine(graphics, titleRect, center, maxWidth, titleHovered);
            outlinedText(graphics, job + "  " + L.t("rotasutils.hub.level_short", level), center, y + 21, 1,
                    WORLD_TEXT_MUTED, maxWidth);
            return;
        }
        int discTop = top + l.portraitCenterY() - l.portraitDiameter() / 2;
        outlinedText(graphics, "LV.", center, discTop - LEVEL_BADGE_H, 1, WORLD_TEXT, maxWidth);
        outlinedText(graphics, level, center, discTop - 9, 2, WORLD_TEXT, maxWidth);

        String name = model.playerName();
        outlinedText(graphics, name, center, y, Ui.scaledWidth(name, 2f) <= maxWidth ? 2 : 1, WORLD_TEXT, maxWidth);
        renderTitleLine(graphics, titleRect, center, maxWidth, titleHovered);
        outlinedText(graphics, job, center, y + 30, 1, WORLD_TEXT, maxWidth);
        String levelTag = "(" + L.t("rotasutils.hub.level_short", level) + ")";
        String sub = model.subName() == null || model.subName().isBlank() ? levelTag : model.subName() + " " + levelTag;
        outlinedText(graphics, sub, center, y + 40, 1, WORLD_TEXT_MUTED, maxWidth);
        renderExpBar(graphics, model, center, y + 52, Math.min(EXP_BAR_W, maxWidth));
    }

    private static void renderTitleLine(GuiGraphics graphics, Rect rect, int center, int maxWidth, boolean hovered) {
        net.schwarz.rotasutils.title.TitleDef worn = ClientState.title(ClientState.progress().activeTitle());
        String text = worn == null ? L.t("rotasutils.hub.title_pick") : "\u00AB " + worn.name() + " \u00BB";
        int color = worn == null || (worn.color() & 0xFFFFFF) == 0 ? WORLD_TEXT_MUTED : 0xFF000000 | worn.color();
        if (hovered) color = UiColor.mix(color, WORLD_TEXT, 0.45f);
        outlinedText(graphics, text, center, rect.y() + 1, 1, color, maxWidth);
        if (hovered) {
            int w = Math.min(maxWidth, Ui.textWidth(text));
            graphics.fill(center - w / 2, rect.y() + rect.height() - 1, center + w / 2, rect.y() + rect.height(), color);
        }
    }

    private static void renderExpBar(GuiGraphics graphics, CharacterHubModel.Snapshot model, int center, int y, int width) {
        boolean maxed = model.xpForNext() <= 0 || model.xpForNext() == Long.MAX_VALUE;
        float progress = model.levelProgress();
        String label = maxed ? L.t("rotasutils.hub.exp_max")
                : L.t("rotasutils.hub.exp_short", String.format(Locale.ROOT, "%.1f", progress * 100f));
        Font font = Minecraft.getInstance().font;
        int labelW = font.width(label) + 6;
        int barW = Math.max(24, width - labelW);
        int x = center - (barW + labelW) / 2;
        try (UiCanvas ui = UiCanvas.begin(graphics, RotasTheme.MAIN)) {
            ui.roundedRect(x, y, barW, 5, 2, WORLD_OUTLINE);
            ui.roundedRect(x + 1, y + 1, barW - 2, 3, 1, BAR_TRACK);
            int filled = Math.round((barW - 2) * progress);
            if (filled >= 2) ui.roundedRect(x + 1, y + 1, filled, 3, 1, EXP_FILL);
        }
        graphics.drawString(font, label, x + barW + 6, y - 2, WORLD_TEXT_MUTED, false);
    }

    private static Rect titleRect(int left, int top, Layout l) {
        int center = left + l.portraitCenterX();
        int w = Math.max(40, Math.min(l.leftPane() - 8, 160));
        int y = top + l.nameY() + (l.compact() ? 10 : 18);
        return new Rect(center - w / 2, y, w, 11);
    }

    private static void outlinedText(GuiGraphics graphics, String text, int centerX, int y, int scale, int color,
                                     int maxWidth) {
        Font font = Minecraft.getInstance().font;
        Component component = Ui.readableComponent(Component.literal(Ui.truncate(text, Math.max(10, maxWidth / scale))));
        graphics.pose().pushPose();
        graphics.pose().translate(Math.round(centerX - font.width(component) * scale / 2f), y, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, component, -1, 0, WORLD_OUTLINE, false);
        graphics.drawString(font, component, 1, 0, WORLD_OUTLINE, false);
        graphics.drawString(font, component, 0, -1, WORLD_OUTLINE, false);
        graphics.drawString(font, component, 0, 1, WORLD_OUTLINE, false);
        graphics.drawString(font, component, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static void renderCharacter(GuiGraphics graphics, LocalPlayer player, CharacterHubModel.Snapshot model,
                                        int left, int top, int mouseX, int mouseY, Layout l) {
        float cx = left + l.portraitCenterX();
        float cy = top + l.portraitCenterY();
        int d = l.portraitDiameter();
        float r = d / 2f;
        List<CurioVisual> curios = curioVisuals(model, left, top, l);

        ItemStack carried = player.inventoryMenu.getCarried();
        EquipmentSlot carriedTarget = carried.isEmpty() ? null : LivingEntity.getEquipmentSlotForItem(carried);

        int discSize = Math.round(d / DISC_FILL_RATIO);
        texture(graphics, TEX_DISC, Math.round(cx - discSize / 2f), Math.round(cy - discSize / 2f),
                discSize, discSize, DISC_TEX, DISC_TEX);
        for (int socket = SOCKET_HEAD; socket <= SOCKET_OFFHAND; socket++) {
            Rect rect = socketRect(l, left, top, socket);
            texture(graphics, TEX_SOCKET, rect.x(), rect.y(), rect.width(), rect.height(), SOCKET_TEX, SOCKET_TEX);
        }
        for (CurioVisual visual : curios) {
            Rect rect = visual.rect();
            texture(graphics, TEX_SOCKET, rect.x(), rect.y(), rect.width(), rect.height(), SOCKET_TEX, SOCKET_TEX);
        }

        try (UiCanvas ui = UiCanvas.begin(graphics, RotasTheme.MAIN)) {
            socketState(ui, l, left, top, SOCKET_HEAD, EquipmentSlot.HEAD, mouseX, mouseY,
                    carriedTarget == EquipmentSlot.HEAD, !player.getInventory().armor.get(3).isEmpty());
            socketState(ui, l, left, top, SOCKET_CHEST, EquipmentSlot.CHEST, mouseX, mouseY,
                    carriedTarget == EquipmentSlot.CHEST, !player.getInventory().armor.get(2).isEmpty());
            socketState(ui, l, left, top, SOCKET_LEGS, EquipmentSlot.LEGS, mouseX, mouseY,
                    carriedTarget == EquipmentSlot.LEGS, !player.getInventory().armor.get(1).isEmpty());
            socketState(ui, l, left, top, SOCKET_FEET, EquipmentSlot.FEET, mouseX, mouseY,
                    carriedTarget == EquipmentSlot.FEET, !player.getInventory().armor.get(0).isEmpty());
            socketState(ui, l, left, top, SOCKET_OFFHAND, EquipmentSlot.OFFHAND, mouseX, mouseY,
                    carriedTarget == EquipmentSlot.OFFHAND, !player.getInventory().offhand.get(0).isEmpty());
            for (CurioVisual visual : curios) {
                Rect rect = visual.rect();
                if (!rect.contains(mouseX, mouseY)) continue;
                float half = rect.width() / 2f;
                ui.ring(rect.x() + half, rect.y() + half, half - 1.5f, half, 20, SLOT_HOVER_BORDER);
            }
        }

        int scale = Math.max(18, Math.round(r * 0.62f));
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                Math.round(cx), Math.round(cy + r * 0.62f), scale, 0, 0, player);

        for (CurioVisual visual : curios) {
            Rect rect = visual.rect();
            ItemStack stack = visual.entry().stack();
            if (!stack.isEmpty()) {
                graphics.renderItem(player, stack,
                        rect.x() + (rect.width() - 16) / 2,
                        rect.y() + (rect.height() - 16) / 2,
                        120 + visual.entry().slotIndex());
            } else {
                graphics.blit(curioIcon(visual.entry().slotType()),
                        rect.x() + (rect.width() - 16) / 2,
                        rect.y() + (rect.height() - 16) / 2,
                        0, 0, 16, 16, 16, 16);
            }
        }
    }

    private static void renderTabs(GuiGraphics graphics, CharacterHubModel.Snapshot model, CharacterHubTab current,
                                   int left, int top, Layout l, int mouseX, int mouseY) {
        List<Rect> rects = tabRects(left, top, l);
        CharacterHubTab[] tabs = CharacterHubTab.values();
        boolean hanging = l.tabH() > TAB_STRIP_H;
        for (int i = 0; i < tabs.length; i++) {
            Rect r = rects.get(i);
            boolean available = CharacterHubModel.tabAvailable(tabs[i], model);
            boolean selected = tabs[i] == current;
            boolean hovered = available && r.contains(mouseX, mouseY);
            if (selected) {
                RenderSystem.setShaderColor(0.90f, 0.84f, 0.74f, 1f);
            } else if (hovered) {
                RenderSystem.setShaderColor(0.97f, 0.95f, 0.91f, 1f);
            } else {
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }
            texture(graphics, TEX_TAB, r.x(), r.y() + tabDrop(selected, hanging), r.width(), r.height(),
                    TAB_TEX_W, TAB_TEX_H);
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        for (int i = 0; i < tabs.length; i++) {
            Rect r = rects.get(i);
            boolean available = CharacterHubModel.tabAvailable(tabs[i], model);
            boolean selected = tabs[i] == current;
            int color = !available ? TAB_DISABLED_TEXT : selected ? RotasTheme.TEXT
                    : UiColor.mix(RotasTheme.TEXT_MUTED, RotasTheme.TEXT, 0.5f);
            List<String> lines = hanging
                    ? tabLines(tabs[i].label(), r.width() - 2)
                    : List.of(Ui.truncate(tabs[i].label(), r.width() - 4));
            int centerY = r.y() + tabDrop(selected, hanging) + r.height() / 2;
            int textY = centerY - lines.size() * 9 / 2 + 1;
            for (String line : lines) {
                Ui.labelCentered(graphics, line, r.x() + r.width() / 2, textY, color);
                textY += 9;
            }
        }
    }

    private static int tabDrop(boolean selected, boolean hanging) {
        return selected && hanging ? 2 : 0;
    }

    private static List<String> tabLines(String label, int maxWidth) {
        if (Ui.textWidth(label) <= maxWidth) return List.of(label);
        int space = label.indexOf(' ');
        if (space > 0) {
            return List.of(Ui.truncate(label.substring(0, space), maxWidth),
                    Ui.truncate(label.substring(space + 1).trim(), maxWidth));
        }
        int cut = label.length() - 1;
        while (cut > 1 && (Ui.textWidth(label.substring(0, cut)) > maxWidth || isCombiningMark(label.charAt(cut)))) {
            cut--;
        }
        return List.of(label.substring(0, cut), Ui.truncate(label.substring(cut), maxWidth));
    }

    private static boolean isCombiningMark(char c) {
        return c == 'ั' || (c >= 'ิ' && c <= 'ฺ') || (c >= '็' && c <= '๎');
    }

    private static void renderBag(GuiGraphics graphics, LocalPlayer player, CharacterHubModel.Snapshot model,
                                  int left, int top, Layout l, int mouseX, int mouseY) {
        int x = left + l.rightX();
        int y = top + l.panelY();
        int w = l.rightW();
        int h = l.panelH();
        mainPanel(graphics, x, y, w, h);

        boolean compact = l.compact();
        int statusY = top + l.statusY();
        String statusTitle = L.t("rotasutils.hub.status");
        if (compact) {
            Ui.label(graphics, statusTitle, x + l.pad(), statusY + 3, RotasTheme.TEXT);
        } else {
            Ui.scaledLabel(graphics, statusTitle, x + l.pad(), statusY, 2.0f, RotasTheme.TEXT);
            int rankX = x + l.pad() + Math.round(Ui.scaledWidth(statusTitle, 2.0f)) + 6;
            int limit = l.tabH() > TAB_STRIP_H ? left + l.tabX() - 6 : left + l.craftX() - 8;
            if (limit - rankX >= 24) {
                Ui.label(graphics, Ui.truncate(L.t("rotasutils.hub.rank_tag", model.clearance()), limit - rankX),
                        rankX, statusY + 7, RotasTheme.TEXT);
            }
        }

        float health = Math.max(0f, player.getHealth());
        float maxHealth = Math.max(1f, player.getMaxHealth());
        SpellPointsClientCompat.Reading mana = SpellPointsClientCompat.read(player);
        int food = player.getFoodData().getFoodLevel();
        int barX = left + l.barX();
        int barW = l.barW();
        int healthY = top + l.healthY();
        int spellY = top + l.spellY();
        int barOffset = compact ? 9 : 10;
        int secondColor = mana != null ? SPELL : FOOD;
        try (UiCanvas ui = UiCanvas.begin(graphics, RotasTheme.MAIN)) {
            vitalBar(ui, barX, healthY + barOffset, barW, health / maxHealth, HEALTH);
            vitalBar(ui, barX, spellY + barOffset, barW,
                    mana != null ? mana.progress() : food / 20f, secondColor);
            heartGlyph(ui, barX, healthY + 1, HEALTH);
            ui.diamond(barX + 2.5f, spellY + 3.5f, 2.5f, 3.5f, secondColor);
        }
        Font font = Minecraft.getInstance().font;
        String healthValue = Math.round(health) + " / " + Math.round(maxHealth);
        String secondValue = mana != null ? Math.round(mana.current()) + " / " + Math.round(mana.max()) : food + " / 20";
        String secondLabel = mana != null ? L.t("rotasutils.hub.spellpoint") : L.t("rotasutils.inventory.hunger");
        vitalLabel(graphics, font, L.t("rotasutils.hub.healthpoint"), healthValue, barX, healthY, barW);
        vitalLabel(graphics, font, secondLabel, secondValue, barX, spellY, barW);

        double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double speed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        double move = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        int movePercent = (int) Math.round(move / 0.1 * 100);
        String[] labels = {L.t("rotasutils.hub.stat.damage"), L.t("rotasutils.hub.stat.attack_speed"),
                L.t("rotasutils.hub.stat.armor"), L.t("rotasutils.hub.stat.movement")};
        String[] values = {trim(damage), trim(speed), Integer.toString(player.getArmorValue()), movePercent + "%"};
        int statsY = top + l.statsY();
        if (compact) {
            int columnW = (barW - 8) / 2;
            for (int i = 0; i < 4; i++) {
                statRow(graphics, font, barX + (i % 2) * (columnW + 8), statsY + (i / 2) * 10, columnW,
                        labels[i], values[i]);
            }
        } else {
            if (statsButtonVisible(l)) {
                int points = ClientState.progress().rpg().statPoints();
                Rect chip = statsChipRect(left, top, l, points);
                drawStatsButton(graphics, chip, points, chip.contains(mouseX, mouseY));
            }
            for (int i = 0; i < 4; i++) {
                statRow(graphics, font, barX, statsY + i * 11, l.statsW(), labels[i], values[i]);
            }
        }

        renderCraft(graphics, left, top, l, mouseX, mouseY);
        renderWallet(graphics, left, top, l, mouseX, mouseY);

        int box = l.slotBox();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                cellTexture(graphics, left + l.gridX() + col * l.spacing(), top + l.gridY() + row * l.spacing(), box);
            }
        }
        for (int col = 0; col < 9; col++) {
            cellTexture(graphics, left + l.gridX() + col * l.spacing(), top + l.hotbarY(), box);
        }
        try (UiCanvas ui = UiCanvas.begin(graphics, RotasTheme.MAIN)) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int sx = left + l.gridX() + col * l.spacing();
                    int sy = top + l.gridY() + row * l.spacing();
                    if (Ui.inside(mouseX, mouseY, sx, sy, box, box)) cellHover(ui, sx, sy, box, "slot" + (9 + row * 9 + col));
                }
            }
            hotbarStates(ui, player, left, top, l, mouseX, mouseY);
        }
    }

    private static void renderSkillPath(GuiGraphics graphics, CharacterHubModel.Snapshot model,
                                        CharacterHubModel.SkillPath path, String profileName, boolean sub,
                                        int left, int top, Layout l, int mouseX, int mouseY) {
        int x = left + l.rightX();
        int y = top + l.panelY();
        int w = l.rightW();
        int h = l.panelH();
        mainPanel(graphics, x, y, w, h);
        y += l.contentShift();
        h -= l.contentShift();

        String title = sub ? L.t("rotasutils.hub.subclass_title") : L.t("rotasutils.hub.job");
        Ui.scaledLabel(graphics, title, x + 14, y + 11, 1.05f, RotasTheme.TEXT);
        String name = profileName == null || profileName.isBlank()
                ? path == null ? L.t("rotasutils.hub.not_assigned") : path.displayName() : profileName;
        Ui.scaledLabel(graphics, name, x + 14, y + 31, 1.28f, RotasTheme.TEXT);

        if (path == null) {
            renderLoadoutHotbar(graphics, Minecraft.getInstance().player, left, top, l, mouseX, mouseY);
            if (!ClientState.pufferfishSkills()) {
                Ui.label(graphics, sub ? L.t("rotasutils.hub.race_no_trees")
                                : model.jobName().isBlank() ? L.t("rotasutils.hub.job_choose_trees")
                                : L.t("rotasutils.hub.job_no_trees"),
                        x + 14, y + 55, RotasTheme.TEXT_MUTED);
                return;
            }
            Ui.label(graphics, model.puffishAvailable()
                            ? L.t("rotasutils.hub.puffish_no_category")
                            : L.t("rotasutils.hub.puffish_unavailable"),
                    x + 14, y + 55, RotasTheme.TEXT_MUTED);
            Ui.label(graphics, L.t("rotasutils.hub.assign_category_hint"),
                    x + 14, y + 70, RotasTheme.TEXT_FAINT);
            return;
        }

        Ui.labelRight(graphics, path.categoryId(), x + w - 14, y + 14, RotasTheme.TEXT_FAINT);
        int pointsX = x + w - 94;
        Ui.labelRight(graphics, L.t("rotasutils.hub.skill_points"), pointsX, y + 39, RotasTheme.TEXT_FAINT);
        Ui.scaledLabel(graphics, Integer.toString(path.pointsLeft()), pointsX + 9, y + 30, 2.0f, RotasTheme.TEXT);

        int progressW = Math.max(60, Math.min(w - 28, w - 138));
        Ui.label(graphics, L.t("rotasutils.hub.skills_unlocked", path.skillsUnlocked(), path.skillsTotal()),
                x + 14, y + 62, RotasTheme.TEXT_MUTED);
        Ui.hudBar(graphics, x + 14, y + 77, progressW, 6, path.skillProgress(), RotasTheme.ACCENT, RotasTheme.TRACK);
        Ui.labelRight(graphics, L.t("rotasutils.hub.points_spent_total", path.pointsSpent(), path.pointsTotal()),
                x + 14 + progressW, y + 88, RotasTheme.TEXT_FAINT);

        Rect action = actionButtonRect(left, top, l, sub ? CharacterHubTab.SUB : CharacterHubTab.JOB);
        if (path.skillsTotal() == 0) {
            Ui.labelCentered(graphics, L.t("rotasutils.hub.no_skills"), x + w / 2,
                    (y + 112 + action.y()) / 2 - 4, RotasTheme.TEXT_MUTED);
        }

        actionButton(graphics, action, L.t("rotasutils.hub.open_skills"), action.contains(mouseX, mouseY));
        renderLoadoutHotbar(graphics, Minecraft.getInstance().player, left, top, l, mouseX, mouseY);
    }

    private static void renderParty(GuiGraphics graphics, CharacterHubModel.Snapshot model,
                                    int left, int top, Layout l) {
        int x = left + l.rightX();
        int y = top + l.panelY();
        int w = l.rightW();
        int h = l.panelH();
        mainPanel(graphics, x, y, w, h);
        y += l.contentShift();
        h -= l.contentShift();
        Ui.scaledLabel(graphics, L.t("rotasutils.hub.party"), x + 14, y + 11, 1.05f, RotasTheme.TEXT);
        Ui.labelRight(graphics, model.party().isEmpty() ? L.t("rotasutils.hub.solo")
                        : L.t("rotasutils.hub.members", model.party().size()),
                x + w - 14, y + 13, RotasTheme.ACCENT_STRONG);

        if (model.party().isEmpty()) {
            Ui.labelCentered(graphics, L.t("rotasutils.hub.no_party"), x + w / 2, y + h / 2 - 16,
                    RotasTheme.TEXT_MUTED);
            Ui.labelCentered(graphics, L.t("rotasutils.hub.party_hint"),
                    x + w / 2, y + h / 2 + 2, RotasTheme.TEXT_FAINT);
        } else {
            int rowY = y + 43;
            int rowHeight = 34;
            int visible = Math.max(1, (h - 82) / rowHeight);
            int first = Math.min(partyScroll, Math.max(0, model.party().size() - visible));
            int end = Math.min(model.party().size(), first + visible);
            for (int memberIndex = first; memberIndex < end; memberIndex++) {
                ClientState.PartyMember member = model.party().get(memberIndex);
                PixelUi.frame(graphics, x + 14, rowY, w - 28, 29, 3,
                        member.nearby() ? RotasTheme.ACCENT : INK_LINE,
                        member.nearby() ? SLOT_HOVER_FILL : SLOT_EMPTY_FILL);
                Ui.label(graphics, (member.leader() ? "◆ " : "") + member.name(), x + 24, rowY + 10,
                        member.online() ? RotasTheme.TEXT : RotasTheme.TEXT_FAINT);
                Ui.labelRight(graphics, member.nearby()
                                ? L.t("rotasutils.hub.party_level_nearby", member.level())
                                : L.t("rotasutils.chip.level", member.level()),
                        x + w - 24, rowY + 10, member.nearby() ? RotasTheme.ACCENT_STRONG : RotasTheme.TEXT_MUTED);
                rowY += rowHeight;
            }
            if (model.party().size() > visible) {
                Ui.labelRight(graphics, L.t("rotasutils.hub.scroll"), x + w - 14, y + h - 50, RotasTheme.TEXT_FAINT);
            }
        }
        Rect action = actionButtonRect(left, top, l, CharacterHubTab.PARTY);
        actionButton(graphics, action, L.t("rotasutils.hub.manage_party"), false);
    }

    private static void renderQuests(GuiGraphics graphics, CharacterHubModel.Snapshot model, String selectedQuestId,
                                     int left, int top, Layout l, int mouseX, int mouseY, int scroll) {
        int x = left + l.rightX();
        int y = top + l.panelY();
        int w = l.rightW();
        int h = l.panelH();
        mainPanel(graphics, x, y, w, h);
        y += l.contentShift();
        h -= l.contentShift();
        Ui.scaledLabel(graphics, L.t("rotasutils.hub.quest"), x + 14, y + 11, 1.05f, RotasTheme.TEXT);
        Ui.labelRight(graphics, L.t("rotasutils.hub.active", model.quests().size()), x + w - 14, y + 13, RotasTheme.ACCENT_STRONG);

        if (model.quests().isEmpty()) {
            Ui.labelCentered(graphics, L.t("rotasutils.hub.no_quests"),
                    x + w / 2, y + h / 2 - 10, RotasTheme.TEXT_MUTED);
            Ui.labelCentered(graphics, L.t("rotasutils.hub.quest_board_hint"),
                    x + w / 2, y + h / 2 + 8, RotasTheme.TEXT_FAINT);
            return;
        }

        int listX = x + 14;
        int listY = y + 48;
        int listW = Math.min(142, Math.max(112, w * 34 / 100));
        int rowH = 27;
        int visibleRows = Math.max(1, Math.min(model.quests().size(), (h - 74) / rowH));
        String selectedId = resolveQuestId(model, selectedQuestId);
        int first = Math.min(scroll, Math.max(0, model.quests().size() - visibleRows));
        for (int i = 0; i < visibleRows; i++) {
            CharacterHubModel.QuestEntry quest = model.quests().get(first + i);
            boolean selected = quest.id().equals(selectedId);
            boolean hovered = new Rect(listX, listY + i * rowH, listW, rowH - 3).contains(mouseX, mouseY);
            PixelUi.frame(graphics, listX, listY + i * rowH, listW, rowH - 3, 3,
                    selected ? RotasTheme.ACCENT : INK_LINE,
                    selected ? TAB_ACTIVE_FILL : hovered ? SLOT_HOVER_FILL : SLOT_OCCUPIED_FILL);
            String marker = quest.active().turnInReady() ? "✓ " : quest.definition().mainQuest() ? "! " : "";
            Ui.label(graphics, Ui.truncate(marker + quest.definition().name(), listW - 16),
                    listX + 8, listY + i * rowH + 8,
                    quest.active().turnInReady() ? RotasTheme.GOOD : RotasTheme.TEXT);
        }
        if (model.quests().size() > visibleRows) {
            Ui.labelRight(graphics, L.t("rotasutils.hub.scroll"), listX + listW, listY - 12, RotasTheme.TEXT_FAINT);
            Ui.labelCentered(graphics, (first + visibleRows) + " / " + model.quests().size(),
                    listX + listW / 2, listY + visibleRows * rowH + 2, RotasTheme.TEXT_FAINT);
        }

        int detailX = listX + listW + 18;
        int detailW = x + w - 14 - detailX;
        graphics.fill(detailX - 10, y + 43, detailX - 9, y + h - 15, LINE);
        CharacterHubModel.QuestEntry selected = findQuest(model, selectedId);
        if (selected == null) return;
        QuestDef quest = selected.definition();
        ActiveQuest active = selected.active();
        Ui.scaledLabel(graphics, quest.name(), detailX, y + 48, 1.18f, RotasTheme.TEXT);
        Ui.labelRight(graphics, L.t("rotasutils.inventory.rank", quest.rank().display()),
                detailX + detailW, y + 51, quest.rank().argb());
        Ui.label(graphics, Ui.truncate(quest.shortDescription().isBlank() ? quest.description() : quest.shortDescription(), detailW),
                detailX, y + 69, RotasTheme.TEXT_MUTED);

        int objectiveY = y + 94;
        Ui.label(graphics, L.t("rotasutils.hub.objectives"), detailX, objectiveY, RotasTheme.TEXT_FAINT);
        int objectiveCount = Math.min(quest.objectives().size(), 4);
        for (int i = 0; i < objectiveCount; i++) {
            boolean complete = active.isComplete(i);
            int progress = active.progress(i);
            String label = (complete ? "✓ " : "• ") + quest.objectives().get(i).type().display();
            if (!complete && progress > 0) label += "  " + progress;
            Ui.label(graphics, Ui.truncate(label, detailW), detailX + 4, objectiveY + 17 + i * 14,
                    complete ? RotasTheme.GOOD : RotasTheme.TEXT);
        }

        int rewardY = Math.min(y + h - 66, objectiveY + 83);
        Ui.label(graphics, L.t("rotasutils.hub.rewards"), detailX, rewardY, RotasTheme.TEXT_FAINT);
        int rewardX = detailX;
        int shown = Math.min(quest.rewards().size(), Math.max(1, detailW / 46));
        for (int i = 0; i < shown; i++) {
            Reward reward = quest.rewards().get(i);
            PixelUi.frame(graphics, rewardX, rewardY + 14, 40, 24, 3, INK_LINE, CONTROL_FILL);
            Ui.labelCentered(graphics, Ui.truncate(reward.type().display(), 34), rewardX + 20, rewardY + 22,
                    RotasTheme.TEXT_MUTED);
            rewardX += 46;
        }
        Rect action = actionButtonRect(left, top, l, CharacterHubTab.QUEST);
        actionButton(graphics, action, L.t("rotasutils.hub.details"), action.contains(mouseX, mouseY));
    }

    private static void renderCraft(GuiGraphics graphics, int left, int top, Layout l, int mouseX, int mouseY) {
        int x = left + l.craftX();
        int y = top + l.craftY();
        int box = l.craftBox();
        int pitch = l.spacing();
        int rx = left + l.resultX();
        int ry = top + l.resultY();
        LocalPlayer player = Minecraft.getInstance().player;
        boolean hasResult = player != null
                && player.inventoryMenu.getSlot(InventoryMenu.RESULT_SLOT).hasItem();
        boolean dragging = player != null && !player.inventoryMenu.getCarried().isEmpty();
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                cellTexture(graphics, x + col * pitch, y + row * pitch, box);
            }
        }
        cellTexture(graphics, rx, ry, box);
        try (UiCanvas ui = UiCanvas.begin(graphics, RotasTheme.MAIN)) {
            for (int row = 0; row < 2; row++) {
                for (int col = 0; col < 2; col++) {
                    int sx = x + col * pitch;
                    int sy = y + row * pitch;
                    if (!dragging && Ui.inside(mouseX, mouseY, sx, sy, box, box)) {
                        cellHover(ui, sx, sy, box, "craft" + (row * 2 + col));
                    }
                }
            }
            if (hasResult) {
                outline(ui, rx, ry, box, box, RotasTheme.ACCENT);
                outline(ui, rx + 1, ry + 1, box - 2, box - 2, UiColor.multiplyAlpha(RotasTheme.ACCENT, 0.6f));
            } else if (!dragging && Ui.inside(mouseX, mouseY, rx, ry, box, box)) {
                cellHover(ui, rx, ry, box, "craft4");
            }
            float sparkleX = x + pitch * 2 + box / 2f;
            float sparkleY = ry + box / 2f;
            int color = hasResult ? RotasTheme.ACCENT_STRONG : INK_LINE;
            ui.star(sparkleX, sparkleY, 7f, 1.2f, 4, 0f, color);
        }
    }

    private static void renderLoadoutHotbar(GuiGraphics graphics, LocalPlayer player, int left, int top, Layout l,
                                            int mouseX, int mouseY) {
        if (player == null) return;
        for (int col = 0; col < 9; col++) {
            cellTexture(graphics, left + l.gridX() + col * l.spacing(), top + l.hotbarY(), l.slotBox());
        }
        try (UiCanvas ui = UiCanvas.begin(graphics, RotasTheme.MAIN)) {
            hotbarStates(ui, player, left, top, l, mouseX, mouseY);
        }
        Ui.label(graphics, L.t("rotasutils.hub.loadout"), left + l.gridX(), top + l.hotbarY() - 11, RotasTheme.TEXT_FAINT);
    }

    private static void hotbarStates(UiCanvas ui, LocalPlayer player, int left, int top, Layout l,
                                     int mouseX, int mouseY) {
        int box = l.slotBox();
        for (int col = 0; col < 9; col++) {
            int sx = left + l.gridX() + col * l.spacing();
            int sy = top + l.hotbarY();
            if (player.getInventory().selected == col) {
                outline(ui, sx, sy, box, box, RotasTheme.ACCENT);
                outline(ui, sx + 1, sy + 1, box - 2, box - 2, RotasTheme.ACCENT);
                ui.rect(sx + Math.max(4, (box - 8) / 2), sy + box - 4, 8, 2, RotasTheme.ACCENT_STRONG);
            } else if (Ui.inside(mouseX, mouseY, sx, sy, box, box)) {
                cellHover(ui, sx, sy, box, "slot" + (36 + col));
            }
        }
    }

    private static void cellTexture(GuiGraphics graphics, int x, int y, int size) {
        texture(graphics, TEX_SLOT, x, y, size, size, SLOT_TEX, SLOT_TEX);
    }

    private static void cellHover(UiCanvas ui, int x, int y, int size, String key) {
        float fade = hoverFade(key);
        ui.roundedRect(x + 1, y + 1, size - 2, size - 2, 2, UiColor.multiplyAlpha(0xFFFFFFFF, 0.35f * fade));
        outline(ui, x, y, size, size, UiColor.multiplyAlpha(SLOT_HOVER_BORDER, 0.6f + 0.4f * fade));
    }

    private static void outline(UiCanvas ui, int x, int y, int w, int h, int color) {
        if (w < 3 || h < 3) return;
        ui.rect(x + 1, y, w - 2, 1, color)
                .rect(x + 1, y + h - 1, w - 2, 1, color)
                .rect(x, y + 1, 1, h - 2, color)
                .rect(x + w - 1, y + 1, 1, h - 2, color);
    }

    private static float hoverFade(String key) {
        long now = Util.getMillis();
        if (!key.equals(hoverKey)) {
            hoverKey = key;
            hoverStartMillis = now;
        }
        return Math.min(1f, (now - hoverStartMillis) / (float) HOVER_FADE_MS);
    }

    private static List<Rect> tabRects(int left, int top, Layout l) {
        List<Rect> out = new ArrayList<>();
        for (int i = 0; i < CharacterHubTab.values().length; i++) {
            out.add(new Rect(left + l.tabX() + i * (l.tabW() + TAB_GAP), top + l.tabY(), l.tabW(), l.tabH()));
        }
        return out;
    }

    public static int[][] tabBounds(int left, int top, Layout l) {
        List<Rect> rects = tabRects(left, top, l);
        int[][] out = new int[rects.size()][];
        for (int i = 0; i < rects.size(); i++) {
            Rect r = rects.get(i);
            out[i] = new int[]{r.x(), r.y(), r.width(), r.height()};
        }
        return out;
    }

    private static Rect socketRect(Layout l, int left, int top, int socket) {
        int s = l.socketSize();
        int cx = switch (socket) {
            case SOCKET_HEAD -> l.headX();
            case SOCKET_CHEST -> l.chestX();
            case SOCKET_LEGS -> l.legsX();
            case SOCKET_FEET -> l.feetX();
            default -> l.offhandX();
        };
        int cy = switch (socket) {
            case SOCKET_HEAD -> l.headY();
            case SOCKET_CHEST -> l.chestY();
            case SOCKET_LEGS -> l.legsY();
            case SOCKET_FEET -> l.feetY();
            default -> l.offhandY();
        };
        return new Rect(left + cx - s / 2, top + cy - s / 2, s, s);
    }

    private static String emptyGearLabel(LocalPlayer player, Layout l, int left, int top, double mouseX, double mouseY) {
        if (socketRect(l, left, top, SOCKET_HEAD).contains(mouseX, mouseY))
            return player.getInventory().armor.get(3).isEmpty() ? L.t("rotasutils.hub.slot.helmet") : null;
        if (socketRect(l, left, top, SOCKET_CHEST).contains(mouseX, mouseY))
            return player.getInventory().armor.get(2).isEmpty() ? L.t("rotasutils.hub.slot.chestplate") : null;
        if (socketRect(l, left, top, SOCKET_LEGS).contains(mouseX, mouseY))
            return player.getInventory().armor.get(1).isEmpty() ? L.t("rotasutils.hub.slot.leggings") : null;
        if (socketRect(l, left, top, SOCKET_FEET).contains(mouseX, mouseY))
            return player.getInventory().armor.get(0).isEmpty() ? L.t("rotasutils.hub.slot.boots") : null;
        if (socketRect(l, left, top, SOCKET_OFFHAND).contains(mouseX, mouseY))
            return player.getInventory().offhand.get(0).isEmpty() ? L.t("rotasutils.hub.slot.offhand_empty") : null;
        return null;
    }

    private static List<CurioVisual> curioVisuals(CharacterHubModel.Snapshot model, int left, int top, Layout l) {
        List<CuriosClientCompat.Entry> entries = model.curios();
        if (entries.isEmpty()) return List.of();
        int size = CURIO_SIZE;
        float cx = l.portraitCenterX();
        float cy = l.portraitCenterY();
        float baseOrbit = curioBaseOrbit(l.portraitDiameter());
        List<CurioVisual> out = new ArrayList<>();
        int placed = 0;
        for (int ring = 0; placed < entries.size(); ring++) {
            float orbit = baseOrbit + ring * CURIO_RING_STEP;
            float step = (float) Math.toDegrees((size + 3) / orbit);
            int capacity = curioRingCapacity(l.portraitDiameter(), ring);
            int inRing = Math.min(capacity, entries.size() - placed);
            for (int i = 0; i < inRing; i++) {
                double angle = Math.toRadians((i - (inRing - 1) / 2f) * step);
                int x = Math.round(cx + (float) Math.cos(angle) * orbit - size / 2f);
                int y = Math.round(cy + (float) Math.sin(angle) * orbit - size / 2f);
                out.add(new CurioVisual(entries.get(placed + i), new Rect(left + x, top + y, size, size)));
            }
            placed += inRing;
        }
        return out;
    }

    private static ResourceLocation curioIcon(String slotType) {
        String safeType = slotType == null ? "curio" : slotType.toLowerCase(Locale.ROOT);
        return CURIO_ICONS.getOrDefault(safeType, CURIO_ICONS.get("curio"));
    }

    private static void mainPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        try (UiCanvas ui = UiCanvas.begin(graphics, RotasTheme.MAIN)) {
            ui.shadow(x, y, w, h, 4, UiColor.multiplyAlpha(RotasTheme.MAIN.shadow(), 0.5f));
        }
        nineSlice(graphics, TEX_CARD, CARD_TEX_W, CARD_TEX_H, CARD_SLICE, x, y, w, h);
    }

    private static void nineSlice(GuiGraphics graphics, ResourceLocation tex, int texW, int texH, int slice,
                                  int x, int y, int w, int h) {
        int s = Math.min(slice / 2, Math.min(w, h) / 2);
        int midW = w - s * 2;
        int midH = h - s * 2;
        int texMidW = texW - slice * 2;
        int texMidH = texH - slice * 2;
        smooth(tex);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(tex, x, y, s, s, 0, 0, slice, slice, texW, texH);
        graphics.blit(tex, x + w - s, y, s, s, texW - slice, 0, slice, slice, texW, texH);
        graphics.blit(tex, x, y + h - s, s, s, 0, texH - slice, slice, slice, texW, texH);
        graphics.blit(tex, x + w - s, y + h - s, s, s, texW - slice, texH - slice, slice, slice, texW, texH);
        if (midW > 0) {
            graphics.blit(tex, x + s, y, midW, s, slice, 0, texMidW, slice, texW, texH);
            graphics.blit(tex, x + s, y + h - s, midW, s, slice, texH - slice, texMidW, slice, texW, texH);
        }
        if (midH > 0) {
            graphics.blit(tex, x, y + s, s, midH, 0, slice, slice, texMidH, texW, texH);
            graphics.blit(tex, x + w - s, y + s, s, midH, texW - slice, slice, slice, texMidH, texW, texH);
        }
        if (midW > 0 && midH > 0) {
            graphics.blit(tex, x + s, y + s, midW, midH, slice, slice, texMidW, texMidH, texW, texH);
        }
        RenderSystem.disableBlend();
    }

    private static void texture(GuiGraphics graphics, ResourceLocation tex, int x, int y, int w, int h,
                                int texW, int texH) {
        smooth(tex);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(tex, x, y, w, h, 0, 0, texW, texH, texW, texH);
        RenderSystem.disableBlend();
    }

    private static void smooth(ResourceLocation tex) {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(tex);
        if (SMOOTHED.get(tex) != texture) {
            texture.setFilter(true, false);
            SMOOTHED.put(tex, texture);
        }
    }

    private static void vitalBar(UiCanvas ui, int x, int y, int width, float progress, int color) {
        int h = 7;
        arrowShape(ui, x, y, width, h, INK_LINE);
        arrowShape(ui, x + 1, y + 1, width - 2, h - 2, BAR_TRACK);
        float p = Math.max(0f, Math.min(1f, progress));
        int filled = Math.round((width - 2) * p);
        if (filled >= 4) {
            arrowShape(ui, x + 1, y + 1, filled, h - 2, color);
            if (filled > 8) ui.rect(x + 3, y + 2, filled - 8, 1, 0x40FFFFFF);
        }
    }

    private static void arrowShape(UiCanvas ui, int x, int y, int width, int height, int color) {
        int head = Math.max(2, height / 2 + 1);
        if (width <= head) return;
        ui.roundedRect(x, y, width - head, height, Math.max(1, height / 2), color);
        ui.triangle(x + width - head - 1, y, x + width, y + height / 2f, x + width - head - 1, y + height, color);
    }

    private static void heartGlyph(UiCanvas ui, int x, int y, int color) {
        ui.rect(x, y + 1, 2, 2, color)
                .rect(x + 3, y + 1, 2, 2, color)
                .rect(x + 1, y, 1, 1, color)
                .rect(x + 3, y, 1, 1, color)
                .rect(x, y + 3, 5, 1, color)
                .rect(x + 1, y + 4, 3, 1, color)
                .rect(x + 2, y + 5, 1, 1, color);
    }

    private static void vitalLabel(GuiGraphics graphics, Font font, String label, String value, int x, int y, int width) {
        int valueW = font.width(value);
        Ui.label(graphics, Ui.truncate(label, Math.max(10, width - valueW - 14)), x + 8, y, RotasTheme.TEXT_MUTED);
        Ui.labelRight(graphics, value, x + width, y, RotasTheme.TEXT_FAINT);
    }

    private static void statRow(GuiGraphics graphics, Font font, int x, int y, int width, String label, String value) {
        int valueW = font.width(value);
        Ui.label(graphics, Ui.truncate(label, Math.max(10, width - valueW - 4)), x, y, RotasTheme.TEXT);
        Ui.labelRight(graphics, value, x + width, y, RotasTheme.TEXT);
    }

    private static void renderWallet(GuiGraphics graphics, int left, int top, Layout l, int mouseX, int mouseY) {
        if (coinStack == null) {
            coinStack = new ItemStack(net.schwarz.rotasutils.registry.RotasRegistry.GOLD_COIN.get());
        }
        long balance = ClientState.progress().rpg().currency("rotas:gold");
        String amount = String.format(Locale.ROOT, "%,d", balance);
        Rect[] buttons = withdrawalRects(left, top, l);
        Font font = Minecraft.getInstance().font;

        try (UiCanvas ui = UiCanvas.begin(graphics, RotasTheme.MAIN)) {
            for (Rect button : buttons) {
                boolean hovered = button.contains(mouseX, mouseY);
                ui.borderedRoundedRect(button.x(), button.y(), button.width(), button.height(), 2,
                        hovered ? RotasTheme.ACCENT : INK_LINE, hovered ? SLOT_HOVER_FILL : CONTROL_FILL);
            }
        }
        for (int i = 0; i < buttons.length; i++) {
            Rect button = buttons[i];
            Ui.labelCentered(graphics, (WITHDRAW_AMOUNTS[i] >= 1000 ? WITHDRAW_AMOUNTS[i] / 1000 + "k" : Integer.toString(WITHDRAW_AMOUNTS[i])), button.x() + button.width() / 2,
                    button.y() + 3, RotasTheme.TEXT);
        }
        int amountRight = buttons[0].x() - 6;
        String shown = Ui.truncate(amount, WALLET_BALANCE_W - 20);
        Ui.labelRight(graphics, shown, amountRight, buttons[0].y() + 3, RotasTheme.TEXT);
        graphics.renderItem(coinStack, amountRight - font.width(shown) - 18, buttons[0].y() - 1);
        if (!l.compact()) {
            Rect last = buttons[buttons.length - 1];
            Ui.labelRight(graphics, L.t("rotasutils.hub.withdraw"), last.x() + last.width(), buttons[0].y() - 11,
                    RotasTheme.TEXT_MUTED);
        }
    }

    private static Rect[] withdrawalRects(int left, int top, Layout l) {
        int right = left + l.gridX() + l.slotBox() * 9 + (l.spacing() - l.slotBox()) * 8;
        int y = top + l.walletY();
        Rect[] out = new Rect[WITHDRAW_WIDTHS.length];
        int x = right - WALLET_BUTTONS_W;
        for (int i = 0; i < WITHDRAW_WIDTHS.length; i++) {
            out[i] = new Rect(x, y, WITHDRAW_WIDTHS[i], 14);
            x += WITHDRAW_WIDTHS[i] + 3;
        }
        return out;
    }

    public static int[][] withdrawalButtonBounds(int left, int top, Layout l) {
        Rect[] buttons = withdrawalRects(left, top, l);
        int[][] bounds = new int[buttons.length][];
        for (int i = 0; i < buttons.length; i++) bounds[i] = new int[]{buttons[i].x(), buttons[i].y(), buttons[i].width(), buttons[i].height()};
        return bounds;
    }

    private static Rect statsButtonRect(int left, int top, Layout l) {
        return new Rect(left + l.chipX(), top + l.chipY(), l.chipW(), STATS_CHIP_H);
    }

    private static Rect statsChipRect(int left, int top, Layout l, int points) {
        Rect slot = statsButtonRect(left, top, l);
        int width = Math.min(slot.width(), Ui.textWidth(statsChipText(points)) + 14);
        return new Rect(slot.x() + slot.width() - width, slot.y(), width, slot.height());
    }

    private static String statsChipText(int points) {
        return points > 0 ? L.t("rotasutils.hub.spend_points", points) : L.t("rotasutils.hub.open_stats");
    }

    private static boolean statsButtonVisible(Layout l) {
        return !l.compact() && l.chipW() >= 60;
    }

    public static int[] statsButtonBounds(int left, int top, Layout l) {
        if (!statsButtonVisible(l)) return null;
        Rect r = statsButtonRect(left, top, l);
        return new int[]{r.x(), r.y(), r.width(), r.height()};
    }

    private static void drawStatsButton(GuiGraphics graphics, Rect r, int points, boolean hovered) {
        boolean ready = points > 0;
        try (UiCanvas ui = UiCanvas.begin(graphics, RotasTheme.MAIN)) {
            ui.borderedRoundedRect(r.x(), r.y(), r.width(), r.height(), 2,
                    ready || hovered ? RotasTheme.ACCENT : INK_LINE,
                    hovered ? SLOT_HOVER_FILL : CONTROL_FILL);
        }
        String text = ready ? L.t("rotasutils.hub.spend_points", points) : L.t("rotasutils.hub.open_stats");
        Ui.labelCentered(graphics, Ui.truncate(text, r.width() - 6), r.x() + r.width() / 2, r.y() + 3, RotasTheme.TEXT);
    }

    private static Rect actionButtonRect(int left, int top, Layout l, CharacterHubTab tab) {
        int x = left + l.rightX() + l.rightW() - 14 - 98;
        int y = showsHotbar(tab)
                ? top + l.hotbarY() - 16 - 22
                : top + l.panelY() + l.panelH() - 34;
        return new Rect(x, y, 98, 22);
    }

    private static void socketState(UiCanvas ui, Layout l, int left, int top, int socket, EquipmentSlot type,
                                    int mouseX, int mouseY, boolean compatible, boolean occupied) {
        Rect rect = socketRect(l, left, top, socket);
        boolean hovered = rect.contains(mouseX, mouseY);
        float r = rect.width() / 2f;
        float cx = rect.x() + r;
        float cy = rect.y() + r;
        if (compatible) {
            ui.ring(cx, cy, r - 2.5f, r, 32, RotasTheme.ACCENT);
        } else if (hovered) {
            ui.ring(cx, cy, r - 1.5f, r, 32, UiColor.multiplyAlpha(SLOT_HOVER_BORDER, 0.9f));
        }
        if (occupied) return;

        int icx = Math.round(cx);
        int icy = Math.round(cy);
        int iconColor = compatible ? GEAR_ICON_ACTIVE : hovered ? GEAR_ICON_HOVER : GEAR_ICON_IDLE;
        switch (type) {
            case HEAD -> drawHelmetIcon(ui, icx, icy, iconColor);
            case CHEST -> drawChestIcon(ui, icx, icy, iconColor);
            case LEGS -> drawLegsIcon(ui, icx, icy, iconColor);
            case FEET -> drawFeetIcon(ui, icx, icy, iconColor);
            default -> drawShieldIcon(ui, icx, icy, iconColor);
        }
    }

    private static void drawHelmetIcon(UiCanvas ui, int cx, int cy, int color) {
        for (int dy = -2; dy <= 3; dy++) {
            int span = dy < 0 ? 5 - Math.abs(dy) : (dy <= 1 ? 6 : 4 - dy);
            ui.rect(cx - span, cy + dy, span * 2, 1, color);
        }
        ui.rect(cx - 6, cy + 3, 12, 2, color);
    }

    private static void drawChestIcon(UiCanvas ui, int cx, int cy, int color) {
        ui.rect(cx - 6, cy - 4, 12, 2, color);
        ui.rect(cx - 5, cy - 2, 10, 4, color);
        ui.rect(cx - 4, cy + 2, 8, 3, color);
        ui.rect(cx - 1, cy - 2, 2, 6, color);
    }

    private static void drawLegsIcon(UiCanvas ui, int cx, int cy, int color) {
        ui.rect(cx - 5, cy - 4, 4, 8, color);
        ui.rect(cx + 1, cy - 4, 4, 8, color);
        ui.rect(cx - 5, cy - 4, 10, 2, color);
    }

    private static void drawFeetIcon(UiCanvas ui, int cx, int cy, int color) {
        ui.rect(cx - 5, cy - 2, 4, 5, color);
        ui.rect(cx + 1, cy - 2, 4, 5, color);
        ui.rect(cx - 5, cy - 2, 4, 2, color);
        ui.rect(cx + 1, cy - 2, 4, 2, color);
    }

    private static void drawShieldIcon(UiCanvas ui, int cx, int cy, int color) {
        ui.rect(cx - 3, cy - 5, 6, 1, color);
        ui.rect(cx - 5, cy - 3, 10, 1, color);
        ui.rect(cx - 6, cy - 1, 12, 1, color);
        ui.rect(cx - 5, cy + 1, 10, 1, color);
        ui.rect(cx - 3, cy + 3, 6, 1, color);
        ui.rect(cx - 1, cy + 5, 2, 1, color);
    }

    private static void actionButton(GuiGraphics graphics, Rect r, String text, boolean hovered) {
        try (UiCanvas ui = UiCanvas.begin(graphics, RotasTheme.MAIN)) {
            ui.borderedRoundedRect(r.x(), r.y(), r.width(), r.height(), 2,
                    hovered ? RotasTheme.ACCENT : INK_LINE,
                    hovered ? SLOT_HOVER_FILL : TAB_ACTIVE_FILL);
        }
        Ui.labelCentered(graphics, Ui.truncate(text, r.width() - 6), r.x() + r.width() / 2, r.y() + 7, RotasTheme.TEXT);
    }

    private static String resolveQuestId(CharacterHubModel.Snapshot model, String selectedQuestId) {
        if (selectedQuestId != null && !selectedQuestId.isBlank()) {
            for (CharacterHubModel.QuestEntry quest : model.quests()) {
                if (quest.id().equals(selectedQuestId)) return selectedQuestId;
            }
        }
        return model.quests().isEmpty() ? "" : model.quests().get(0).id();
    }

    private static CharacterHubModel.QuestEntry findQuest(CharacterHubModel.Snapshot model, String id) {
        for (CharacterHubModel.QuestEntry quest : model.quests()) {
            if (quest.id().equals(id)) return quest;
        }
        return null;
    }

    private static String trim(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.005) return Integer.toString((int) Math.rint(value));
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public record Layout(int width, int height, int curioCount, boolean compact, int pad,
                         int leftPane, int rightX, int rightW, int tabX, int tabY, int tabW, int tabH,
                         int panelY, int panelH, int contentShift,
                         int nameY, int portraitCenterX, int portraitCenterY, int portraitDiameter, int socketSize,
                         int headX, int headY, int chestX, int chestY, int legsX, int legsY, int feetX, int feetY,
                         int offhandX, int offhandY,
                         int statusY, int barX, int barW, int statsW, int healthY, int spellY,
                         int chipX, int chipY, int chipW, int statsY,
                         int craftX, int craftY, int craftBox, int resultX, int resultY, int walletY,
                         int gridX, int gridY, int hotbarY, int slotBox, int spacing) {
    }
}
