package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.schwarz.rotasutils.client.ClientKernelState;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.PixelUi;
import net.schwarz.rotasutils.client.screen.RotasButton;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.RotasTheme;
import net.schwarz.rotasutils.client.screen.ScreenRouter;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;
import net.schwarz.rotasutils.npc.NpcDef;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.registry.RotasRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * NPC editor built around one plain question per page: who is this, what do they do, what do they
 * say, and who may talk to them.
 *
 * <p>Every setting is a visible field on its page, the bound mob is shown live on the left, and a
 * single Save applies the changes to the server immediately. There is no draft, review or apply
 * step to learn; the NPC Wand opens this screen straight from the mob.</p>
 */
@Environment(EnvType.CLIENT)
public class NpcEditorScreen extends RotasScreen {
    private static final int SIDE_W = 196;
    private static final int CARD_H = 46;

    private enum Page {
        WHO("1  Who"),
        ROLE("2  What they do"),
        TALK("3  What they say"),
        RULES("4  Rules");

        final String label;

        Page(String label) {
            this.label = label;
        }
    }

    private record Card(NpcDef.Role role, int x, int y, int w, int h) {
    }

    private record Label(String text, int x, int y, int color) {
    }

    private final String npcId;
    private NpcDef draft;
    private CompoundTag baseline;
    private boolean dirty;
    private Page page = Page.WHO;
    private boolean closingForPick;
    private final List<Card> cards = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private final List<String> questIds = new ArrayList<>();
    private ScrollPanel questList;
    private ScrollPanel tradeList;
    private Button saveButton;
    private double lastClickX;
    private LivingEntity cachedEntity;
    private long lastEntitySearch;
    private int sideX;
    private int sideY;
    private int sideH;
    private int mainX;
    private int mainY;
    private int mainW;
    private int mainH;

    public NpcEditorScreen(String npcId, Screen parent) {
        super("NPC", parent);
        this.npcId = npcId;
    }

    // Layout ---------------------------------------------------------------

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 900);
        guiHeight = Ui.fill(height, 520);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        if (draft == null) {
            NpcDef source = ClientState.npc(npcId);
            draft = source == null ? new NpcDef(npcId) : NpcDef.load(source.save());
            baseline = draft.save();
        }
        closingForPick = false;
        cards.clear();
        labels.clear();
        questList = null;
        tradeList = null;
        setHeader("NPC: " + displayName());

        int footerY = guiTop + guiHeight - 28;
        int tabsY = guiTop + 34;
        sideX = guiLeft + Ui.PAD;
        sideY = tabsY;
        sideH = footerY - Ui.GAP * 2 - tabsY;
        mainX = sideX + SIDE_W + Ui.GAP * 3;
        mainW = guiLeft + guiWidth - Ui.PAD - mainX;
        mainY = tabsY + 28;
        mainH = footerY - Ui.GAP * 2 - mainY;

        int tabW = (mainW - Ui.GAP * 3) / 4;
        for (Page value : Page.values()) {
            addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(Ui.truncate(value.label, tabW - 10)), button -> {
                page = value;
                Sfx.page();
                rebuild();
            }).style(value == page ? RotasButton.Style.NAVIGATION_SELECTED : RotasButton.Style.NAVIGATION)
                    .bounds(mainX + value.ordinal() * (tabW + Ui.GAP), tabsY, tabW, 22).build());
        }

        buildSide();
        switch (page) {
            case WHO -> buildWho();
            case ROLE -> buildRole();
            case TALK -> buildTalk();
            case RULES -> buildRules();
        }
        buildFooter(footerY);
        changed();
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    private String displayName() {
        return draft.name().isBlank() ? draft.id() : draft.name();
    }

    private void buildSide() {
        int x = sideX + 8;
        int w = SIDE_W - 16;
        int bottom = sideY + sideH - 8;
        if (!draft.bound()) {
            addRenderableWidget(Ui.primaryButton(net.schwarz.rotasutils.client.screen.Ui.text("Place on a mob"), button -> placeOnMob())
                    .bounds(x, bottom - 20, w, 20).build());
        } else {
            addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Move to another mob"), button -> placeOnMob())
                    .bounds(x, bottom - 44, w, 20).build());
            addRenderableWidget(Ui.dangerButton(net.schwarz.rotasutils.client.screen.Ui.text("Detach from mob"), button -> detach())
                    .bounds(x, bottom - 20, w, 20).build());
        }
    }

    private void buildWho() {
        int x = mainX + 12;
        int w = mainW - 24;
        int y = mainY + 10;
        labels.add(new Label("Name", x, y, Ui.TEXT_BRIGHT));
        box(x, y + 12, w, draft.name(), 64, value -> {
            draft.setName(value);
            setHeader("NPC: " + displayName());
        });
        y += 46;
        labels.add(new Label("Title  (a small line under the name, like Blacksmith or Guild Clerk)", x, y, Ui.TEXT_BRIGHT));
        box(x, y + 12, w, draft.title(), 64, draft::setTitle);
        y += 46;
        labels.add(new Label("Icon in admin lists", x, y, Ui.TEXT_BRIGHT));
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(Ui.truncate(draft.icon().getHoverName().getString()
                + "   (click to change)", w - 12)), button -> minecraft.setScreen(new PickerScreen(ParamKind.ITEM, this, value -> {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                draft.setIcon(new ItemStack(BuiltInRegistries.ITEM.get(id)));
            }
        }))).bounds(x, y + 12, w, 20).build());
        y += 46;
        labels.add(new Label("Active", x, y, Ui.TEXT_BRIGHT));
        toggle(x, y + 12, w, draft.enabled(),
                "Yes - players talk to this NPC",
                "No - the mob acts like a normal mob again",
                () -> draft.setEnabled(!draft.enabled()));
    }

    private void buildRole() {
        int x = mainX + 12;
        int w = mainW - 24;
        int y = mainY + 10;
        labels.add(new Label("When a player right-clicks this NPC, it...", x, y, Ui.TEXT_BRIGHT));
        y += 16;
        // Twenty roles: a compact four-column grid, with the picked role's description underneath.
        int columns = 4;
        int cardW = (w - Ui.GAP * (columns - 1)) / columns;
        int cardH = 22;
        NpcDef.Role[] roles = NpcDef.Role.values();
        for (int i = 0; i < roles.length; i++) {
            cards.add(new Card(roles[i], x + (i % columns) * (cardW + Ui.GAP), y + (i / columns) * (cardH + 2), cardW, cardH));
        }
        y += ((roles.length + columns - 1) / columns) * (cardH + 2) + 4;
        labels.add(new Label(roleTitle(draft.role()) + ": " + roleHelp(draft.role()), x, y, Ui.GOOD));
        y += 16;
        int bottom = mainY + mainH - 10;

        switch (draft.role()) {
            case DIALOGUE -> {
                labels.add(new Label("It just says its lines. Change them on page 3.", x, y, Ui.TEXT_MUTED));
                labels.add(new Label("Quests and the shop are not offered while this is picked.", x, y + 12, Ui.TEXT_MUTED));
            }
            case QUEST_GIVER -> {
                labels.add(new Label("Quests it gives out", x, y + 4, Ui.TEXT_BRIGHT));
                addRenderableWidget(Ui.primaryButton(net.schwarz.rotasutils.client.screen.Ui.text("+ Add quest"), button ->
                        minecraft.setScreen(new PickerScreen(ParamKind.QUEST, this, value -> draft.questIds().add(value))))
                        .bounds(x + w - 110, y, 110, 18).build());
                y += 22;
                questIds.clear();
                questIds.addAll(draft.questIds());
                questList = new ScrollPanel(x, y, w, Math.max(22, bottom - 26 - y), 22)
                        .withoutBackground()
                        .rowHitInsets(0, 2);
                questList.setRows(questIds.size(), this::renderQuestRow, this::clickQuestRow);
                registerPanel(questList);
                addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(Ui.truncate("Also offer a quest board: "
                        + (draft.boardId().isEmpty() ? "none (click to choose)" : boardName()), w - 12)), button -> chooseBoard())
                        .bounds(x, bottom - 20, w, 20).build());
            }
            case BOARD_KEEPER -> {
                labels.add(new Label("Quest board it opens", x, y, Ui.TEXT_BRIGHT));
                addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(Ui.truncate(draft.boardId().isEmpty()
                        ? "Choose a board" : boardName() + "   (click to change)", w - 12)), button -> chooseBoard())
                        .bounds(x, y + 12, w, 20).build());
                labels.add(new Label("Boards are made in Admin > Quest Boards.", x, y + 40, Ui.TEXT_MUTED));
            }
            case MERCHANT -> {
                labels.add(new Label("What it sells  (" + draft.trades().size() + " / " + NpcDef.MAX_TRADES
                        + ")   click a trade to change it", x, y + 4, Ui.TEXT_BRIGHT));
                Button add = addRenderableWidget(Ui.primaryButton(net.schwarz.rotasutils.client.screen.Ui.text("+ Add trade"), button ->
                        minecraft.setScreen(new TradeEditScreen(draft.trades(), -1, this)))
                        .bounds(x + w - 110, y, 110, 18).build());
                add.active = draft.trades().size() < NpcDef.MAX_TRADES;
                y += 22;
                tradeList = new ScrollPanel(x, y, w, Math.max(22, bottom - 26 - y), 24)
                        .withoutBackground()
                        .rowHitInsets(0, 2);
                tradeList.setRows(draft.trades().size(), this::renderTradeRow, this::clickTradeRow);
                registerPanel(tradeList);
                addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(Ui.truncate("Advanced: content-pack shop (used only with no trades): "
                        + (draft.merchantId().isEmpty() ? "none" : merchantName()), w - 12)), button ->
                        minecraft.setScreen(new PickerScreen(ParamKind.MERCHANT, this, draft::setMerchantId, true)))
                        .bounds(x, bottom - 20, w, 20).build());
            }
            case CRAFTER -> {
                labels.add(new Label("Makes its job's crafts from the player's materials, for a steep fee", x, y, Ui.TEXT_BRIGHT));
                y += 20;
                addRenderableWidget(Ui.button(Ui.text(Ui.truncate("Stands in for: " + crafterJob(), w - 12)),
                        button -> chooseCrafterJob()).bounds(x, y, w, 20).build());
                y += 26;
                Button level = addRenderableWidget(Ui.button(Ui.text("Makes unlocks up to: " + crafterLevelText()),
                        button -> cycleCrafterLevel()).bounds(x, y, w, 20).build());
                level.active = draft.crafterService() != null;
                labels.add(new Label("No marker is drawn over artisans. Fees and the daily limit are in Season rules.",
                        x, y + 28, Ui.TEXT_MUTED));
            }
            case JOB_MASTER -> {
                labels.add(new Label("Jobs granted when a player with an empty slot talks to this NPC", x, y, Ui.TEXT_BRIGHT));
                y += 20;
                addRenderableWidget(Ui.button(Ui.text("Main job: " + offeredJob(net.schwarz.rotasutils.job.JobSlot.MAIN)),
                        button -> chooseOfferedJob(net.schwarz.rotasutils.job.JobSlot.MAIN)).bounds(x, y, w, 20).build());
                y += 26;
                addRenderableWidget(Ui.button(Ui.text("Sub-job: " + offeredJob(net.schwarz.rotasutils.job.JobSlot.SUB)),
                        button -> chooseOfferedJob(net.schwarz.rotasutils.job.JobSlot.SUB)).bounds(x, y, w, 20).build());
                labels.add(new Label("Talking never replaces an occupied job slot.", x, y + 28, Ui.TEXT_MUTED));
            }
            case STABLE -> {
            }
        }
    }

    private void buildTalk() {
        int x = mainX + 12;
        int w = mainW - 24;
        int y = mainY + 10;
        var conversation = draft.interactions();
        if (conversation != null) {
            int replies = conversation.nodes().values().stream().mapToInt(node -> node.choices().size()).sum();
            labels.add(new Label(L.t("rotasutils.dialogue.studio.talk.branching_title"), x, y, Ui.ACCENT));
            labels.add(new Label(L.t("rotasutils.dialogue.studio.talk.counts", conversation.nodes().size(), replies), x, y + 16, Ui.TEXT_BRIGHT));
            labels.add(new Label(L.t("rotasutils.dialogue.studio.talk.branching_hint"), x, y + 30, Ui.TEXT_MUTED));
            int buttonW = Math.min(w, 280);
            addRenderableWidget(Ui.primaryButton(Ui.text(L.t("rotasutils.dialogue.studio.talk.open")), button ->
                    minecraft.setScreen(new NpcInteractionScreen(draft, this, this::save))).bounds(x, y + 50, buttonW, 22).build());
            addRenderableWidget(Ui.dangerButton(Ui.text(L.t("rotasutils.dialogue.studio.talk.remove")), button ->
                    minecraft.setScreen(new ConfirmScreen(yes -> {
                        if (yes) {
                            draft.setInteractionJson("");
                        }
                        minecraft.setScreen(this);
                    }, Ui.text(L.t("rotasutils.dialogue.studio.talk.remove_title")),
                            Ui.text(L.t("rotasutils.dialogue.studio.talk.remove_body")))))
                    .bounds(x, y + 78, buttonW, 20).build());
            return;
        }
        labels.add(new Label(L.t("rotasutils.dialogue.studio.talk.simple_title"), x, y, Ui.ACCENT));
        labels.add(new Label(L.t("rotasutils.dialogue.studio.talk.simple_hint"), x, y + 12, Ui.TEXT_MUTED));
        y += 28;
        String[] titles = {
                "When they greet you",
                "When they have a quest for you",
                "While you are doing their quest",
                "When you come back with it done",
                "When you are not ready yet (level too low)",
                "When you say goodbye"
        };
        String[] values = {draft.greeting(), draft.questAvailableLine(), draft.questActiveLine(),
                draft.questReadyLine(), draft.blockedLine(), draft.farewell()};
        List<Consumer<String>> setters = List.of(draft::setGreeting, draft::setQuestAvailableLine, draft::setQuestActiveLine,
                draft::setQuestReadyLine, draft::setBlockedLine, draft::setFarewell);
        int rowH = Math.max(29, Math.min(42, (mainH - 72) / titles.length));
        for (int i = 0; i < titles.length; i++) {
            labels.add(new Label(titles[i], x, y, Ui.TEXT_BRIGHT));
            box(x, y + 11, w, values[i], 256, setters.get(i));
            y += rowH;
        }
        addRenderableWidget(Ui.primaryButton(Ui.text(L.t("rotasutils.dialogue.studio.talk.create")), button ->
                minecraft.setScreen(new NpcInteractionScreen(draft, this, this::save)))
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.text(
                        L.t("rotasutils.dialogue.studio.talk.create_tip"))))
                .bounds(x, mainY + mainH - 28, Math.min(w, 280), 22).build());
    }

    private void buildRules() {
        int x = mainX + 12;
        int w = mainW - 24;
        int y = mainY + 10;
        stepper(x, y, w, "Lowest level that can talk to them  (0 = anyone)", draft.requiredLevel(), 1, 10,
                value -> draft.setRequiredLevel(Math.max(0, Math.min(10_000, value))));
        y += 38;
        stepper(x, y, w, "How close players must stand, in blocks", (int) Math.round(draft.interactionDistance()), 1, 5,
                value -> draft.setInteractionDistance(Math.max(1, Math.min(64, value))));
        y += 40;
        labels.add(new Label("The mob's own menu", x, y, Ui.TEXT_BRIGHT));
        toggle(x, y + 12, w, draft.captureInteraction(),
                "Hidden - villager trades and other menus do not open",
                "Shown - the mob's own menu can open too",
                () -> draft.setCaptureInteraction(!draft.captureInteraction()));
        y += 38;
        labels.add(new Label("Marker above their head", x, y, Ui.TEXT_BRIGHT));
        toggle(x, y + 12, w, draft.showMarker(),
                "Shown - ! when they have a quest, ? when you can hand one in",
                "Hidden",
                () -> draft.setShowMarker(!draft.showMarker()));
        y += 38;
        labels.add(new Label("Stand still", x, y, Ui.TEXT_BRIGHT));
        toggle(x, y + 12, w, draft.standStill(),
                "Yes - the mob stays where it is and never walks or attacks",
                "No - the mob moves around like normal",
                () -> draft.setStandStill(!draft.standStill()));
        y += 38;
        labels.add(new Label("Can be hurt", x, y, Ui.TEXT_BRIGHT));
        toggle(x, y + 12, w, draft.invulnerable(),
                "No - nothing can hurt or kill this NPC",
                "Yes - it can be hurt and killed like a normal mob",
                () -> draft.setInvulnerable(!draft.invulnerable()));
        y += 38;
        labels.add(new Label("Name above head", x, y, Ui.TEXT_BRIGHT));
        toggle(x, y + 12, w, draft.showName(),
                "Shown - the NPC name floats above the mob",
                "Hidden",
                () -> draft.setShowName(!draft.showName()));
        y += 38;
        labels.add(new Label("NPC id: " + draft.id(), x, y, Ui.TEXT_MUTED));
    }

    private void buildFooter(int footerY) {
        addBackButton();
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Test talking"), button -> {
            CompoundTag payload = new CompoundTag();
            payload.putString("npc", draft.id());
            payload.put("npc_draft", draft.save());
            send("test_npc_dialogue", payload);
            Sfx.page();
        }).tooltip(net.minecraft.client.gui.components.Tooltip.create(net.schwarz.rotasutils.client.screen.Ui.text(
                "Opens the conversation as a player would see it, including changes you have not saved.")))
                .bounds(guiLeft + 64, footerY, 92, 22).build());
        addRenderableWidget(Ui.dangerButton(net.schwarz.rotasutils.client.screen.Ui.text("Delete"), button ->
                minecraft.setScreen(new ConfirmScreen(yes -> {
                    if (!yes) {
                        minecraft.setScreen(this);
                        return;
                    }
                    CompoundTag payload = new CompoundTag();
                    payload.putString("npc", draft.id());
                    send("delete_npc", payload);
                    Sfx.remove();
                    minecraft.setScreen(parentScreen());
                }, net.schwarz.rotasutils.client.screen.Ui.text("Delete the NPC \"" + displayName() + "\"?"),
                        net.schwarz.rotasutils.client.screen.Ui.text("The mob stays in the world and goes back to normal."))))
                .bounds(guiLeft + 160, footerY, 70, 22).build());
        saveButton = addRenderableWidget(Ui.primaryButton(net.schwarz.rotasutils.client.screen.Ui.text("Save"), button -> save())
                .bounds(guiLeft + guiWidth - Ui.PAD - 110, footerY, 110, 22).build());
    }

    // Widgets --------------------------------------------------------------

    private void box(int x, int y, int w, String value, int maxLength, Consumer<String> setter) {
        EditBox box = new EditBox(font, x, y, w, 18, Component.empty());
        box.setMaxLength(maxLength);
        box.setValue(value);
        // The responder is attached after setValue so filling the box is not counted as an edit.
        box.setResponder(text -> {
            setter.accept(text);
            changed();
        });
        addRenderableWidget(box);
    }

    private void toggle(int x, int y, int w, boolean on, String onText, String offText, Runnable flip) {
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(Ui.truncate((on ? "[ON]  " : "[OFF]  ") + (on ? onText : offText), w - 12)),
                button -> {
                    flip.run();
                    Sfx.toggle(!on);
                    rebuild();
                }).style(on ? RotasButton.Style.PRIMARY : RotasButton.Style.DEFAULT)
                .bounds(x, y, w, 20).build());
    }

    private void stepper(int x, int y, int w, String title, int value, int step, int bigStep, IntConsumer setter) {
        labels.add(new Label(title, x, y + 6, Ui.TEXT_BRIGHT));
        labels.add(new Label("Shift = " + bigStep + " at a time", x, y + 18, Ui.TEXT_MUTED));
        int right = x + w;
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("-"), button -> {
            setter.accept(value - (hasShiftDown() ? bigStep : step));
            rebuild();
        }).bounds(right - 110, y + 4, 24, 20).build());
        labels.add(new Label(String.valueOf(value), right - 55 - font.width(String.valueOf(value)) / 2, y + 10, Ui.ACCENT));
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("+"), button -> {
            setter.accept(value + (hasShiftDown() ? bigStep : step));
            rebuild();
        }).bounds(right - 24, y + 4, 24, 20).build());
    }

    private void chooseBoard() {
        minecraft.setScreen(new PickerScreen(ParamKind.BOARD, this, draft::setBoardId, true));
    }

    private String boardName() {
        var board = ClientState.board(draft.boardId());
        return board == null ? draft.boardId() : board.name();
    }

    private String merchantName() {
        return ClientKernelState.merchants().stream()
                .filter(merchant -> merchant.id().equals(draft.merchantId()))
                .map(merchant -> merchant.label().isEmpty() ? merchant.id() : merchant.label())
                .findFirst().orElse(draft.merchantId());
    }

    private void changed() {
        dirty = !draft.save().equals(baseline);
        if (saveButton != null) {
            saveButton.active = dirty;
            saveButton.setMessage(net.schwarz.rotasutils.client.screen.Ui.text(dirty ? "Save" : "Saved"));
        }
    }

    // Actions --------------------------------------------------------------

    private void save() {
        CompoundTag payload = new CompoundTag();
        payload.put("npc", draft.save());
        send("save_npc", payload);
        baseline = draft.save();
        Sfx.save();
        changed();
    }

    private void placeOnMob() {
        requestPick("NPC_BIND", "bind");
    }

    @Override
    protected void requestPick(String kind, String fieldKey) {
        CompoundTag payload = new CompoundTag();
        payload.putString("kind", kind);
        payload.putString("screen", screenKey());
        payload.putString("field", fieldKey);
        // The server needs the NPC id to apply the new mob right away.
        payload.putString("npc", draft.id());
        ScreenRouter.rememberPending(this);
        closingForPick = true;
        send("pick", payload);
        onClose();
    }

    private void detach() {
        CompoundTag payload = new CompoundTag();
        payload.putString("npc", draft.id());
        send("npc_unbind", payload);
        setBinding("", "", "");
        Sfx.remove();
        rebuild();
    }

    @Override
    public void onPick(String fieldKey, String value) {
        if (!fieldKey.equals("bind")) {
            return;
        }
        // "uuid|entity type|dimension", written by WorldPicker; the server already applied it.
        String[] parts = value.split("\\|");
        setBinding(parts.length > 0 ? parts[0] : "", parts.length > 1 ? parts[1] : "", parts.length > 2 ? parts[2] : "");
        Sfx.commit();
        rebuild();
    }

    /** Binding changes are live on the server already, so the saved baseline follows them too. */
    private void setBinding(String uuid, String type, String dimension) {
        draft.setEntityUuid(uuid);
        draft.setEntityType(type);
        draft.setDimension(dimension);
        baseline.putString("entity_uuid", uuid);
        baseline.putString("entity_type", type);
        baseline.putString("dimension", dimension);
        cachedEntity = null;
    }

    @Override
    protected void goBack() {
        confirmLeavingDraft(draft.save(), baseline, () -> minecraft.setScreen(parentScreen()));
    }

    @Override
    public void onClose() {
        if (closingForPick) {
            super.onClose();
            return;
        }
        confirmLeavingDraft(draft.save(), baseline, () -> {
            closingForPick = true;
            super.onClose();
        });
    }

    // Quest list -----------------------------------------------------------

    private void renderQuestRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        String questId = questIds.get(index);
        QuestDef quest = ClientState.quest(questId);
        int w = rowWidth - 6;
        Ui.rowCard(graphics, x, y, w, rowHeight - 2, hovered, false);
        if (quest != null) {
            graphics.renderFakeItem(quest.icon(), x + 4, y + 2);
        }
        String label = quest == null ? questId + "  (missing)" : "[" + quest.rank().display() + "] " + quest.name()
                + (quest.published() ? "" : "  (not published)");
        Ui.label(graphics, Ui.truncate(label, w - 90), x + 24, y + 7, quest == null ? Ui.BAD : Ui.TEXT);
        Ui.labelRight(graphics, "remove", x + w - 8, y + 7, hovered ? Ui.BAD : Ui.TEXT_MUTED);
    }

    private void clickQuestRow(int index, int button) {
        if (button != 0 || questList == null || lastClickX < questList.x() + questList.width() - 60) {
            return;
        }
        draft.questIds().remove(questIds.get(index));
        Sfx.remove();
        rebuild();
    }

    // Trade list -----------------------------------------------------------

    private void renderTradeRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        if (index >= draft.trades().size()) {
            return;
        }
        NpcDef.Trade trade = draft.trades().get(index);
        int w = rowWidth - 6;
        Ui.rowCard(graphics, x, y, w, rowHeight - 2, hovered, false);
        int itemY = y + (rowHeight - 2 - 16) / 2;
        int itemX = x + 4;
        item(graphics, trade.costA(), itemX, itemY);
        itemX += 20;
        if (!trade.costB().isEmpty()) {
            Ui.label(graphics, "+", itemX, itemY + 4, Ui.TEXT_MUTED);
            item(graphics, trade.costB(), itemX + 8, itemY);
            itemX += 28;
        }
        Ui.label(graphics, "->", itemX + 2, itemY + 4, Ui.ACCENT);
        item(graphics, trade.result(), itemX + 16, itemY);
        String text = trade.costA().getCount() + " " + trade.costA().getHoverName().getString()
                + (trade.costB().isEmpty() ? "" : " + " + trade.costB().getCount() + " " + trade.costB().getHoverName().getString())
                + "  for  " + trade.result().getCount() + " " + trade.result().getHoverName().getString();
        int textX = itemX + 38;
        Ui.label(graphics, Ui.truncate(text, x + w - 70 - textX), textX, itemY + 4, trade.valid() ? Ui.TEXT : Ui.BAD);
        Ui.labelRight(graphics, "remove", x + w - 8, itemY + 4, hovered ? Ui.BAD : Ui.TEXT_MUTED);
    }

    private void item(GuiGraphics graphics, ItemStack stack, int x, int y) {
        if (!stack.isEmpty()) {
            graphics.renderFakeItem(stack, x, y);
            graphics.renderItemDecorations(font, stack, x, y);
        }
    }

    private void clickTradeRow(int index, int button) {
        if (button != 0 || tradeList == null || index >= draft.trades().size()) {
            return;
        }
        if (lastClickX >= tradeList.x() + tradeList.width() - 60) {
            draft.trades().remove(index);
            Sfx.remove();
            rebuild();
            return;
        }
        Sfx.select();
        minecraft.setScreen(new TradeEditScreen(draft.trades(), index, this));
    }

    // Rendering ------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        lastClickX = mouseX;
        if (page == Page.ROLE && button == 0) {
            for (Card card : cards) {
                if (Ui.inside((int) mouseX, (int) mouseY, card.x(), card.y(), card.w(), card.h())) {
                    if (draft.role() != card.role()) {
                        draft.setRole(card.role());
                        Sfx.select();
                        rebuild();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderSide(graphics, mouseX, mouseY);
        Ui.panel(graphics, mainX, mainY, mainW, mainH);
        for (Card card : cards) {
            renderCard(graphics, card, mouseX, mouseY);
        }
        for (Label label : labels) {
            Ui.label(graphics, label.text(), label.x(), label.y(), label.color());
        }
        if (page == Page.ROLE && draft.role() == NpcDef.Role.QUEST_GIVER && questIds.isEmpty() && questList != null) {
            Ui.labelCentered(graphics, "No quests yet. Press + Add quest.", questList.x() + questList.width() / 2,
                    questList.y() + 8, Ui.TEXT_MUTED);
        }
        if (page == Page.ROLE && draft.role() == NpcDef.Role.MERCHANT && draft.trades().isEmpty() && tradeList != null) {
            Ui.labelCentered(graphics, "No trades yet. Press + Add trade and pick items by their picture.",
                    tradeList.x() + tradeList.width() / 2, tradeList.y() + 8, Ui.TEXT_MUTED);
        }
        Ui.labelCentered(graphics, dirty ? "You have unsaved changes" : "Everything is saved",
                (guiLeft + 234 + guiLeft + guiWidth - Ui.PAD - 110) / 2, guiTop + guiHeight - 21,
                dirty ? Ui.WARN : Ui.GOOD);
    }

    private void renderSide(GuiGraphics graphics, int mouseX, int mouseY) {
        Ui.panel(graphics, sideX, sideY, SIDE_W, sideH);
        int previewX = sideX + 8;
        int previewY = sideY + 8;
        int previewW = SIDE_W - 16;
        int previewH = 132;
        Ui.inset(graphics, previewX, previewY, previewW, previewH);
        int centerX = previewX + previewW / 2;
        LivingEntity entity = boundEntity();
        if (entity != null) {
            float size = Math.max(entity.getBbHeight(), entity.getBbWidth());
            int scale = (int) Math.max(8, Math.min(60, 100 / Math.max(0.4f, size)));
            graphics.enableScissor(previewX + 1, previewY + 1, previewX + previewW - 1, previewY + previewH - 1);
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, centerX, previewY + previewH - 12, scale,
                    centerX - mouseX, previewY + 40 - mouseY, entity);
            graphics.disableScissor();
        } else {
            graphics.renderFakeItem(draft.icon(), centerX - 8, previewY + 42);
            Ui.labelCentered(graphics, draft.bound() ? "The mob is not nearby" : "Not placed on a mob yet",
                    centerX, previewY + 70, Ui.TEXT_MUTED);
            if (!draft.bound()) {
                Ui.labelCentered(graphics, "Use the button below", centerX, previewY + 82, Ui.TEXT_MUTED);
            }
        }

        int y = previewY + previewH + 8;
        Ui.label(graphics, Ui.truncate(displayName(), previewW), previewX, y, Ui.TEXT_BRIGHT);
        y += 11;
        if (!draft.title().isBlank()) {
            Ui.label(graphics, Ui.truncate(draft.title(), previewW), previewX, y, Ui.TEXT_MUTED);
            y += 11;
        }
        y += 3;
        int tagX = previewX;
        tagX += Ui.tag(graphics, tagX, y, draft.enabled() ? "ACTIVE" : "OFF", draft.enabled() ? Ui.GOOD : Ui.TEXT_MUTED);
        Ui.tag(graphics, tagX, y, roleShort(draft.role()), Ui.ACCENT);
        y += 22;

        int limit = sideY + sideH - (draft.bound() ? 60 : 36);
        for (String problem : draft.problems()) {
            for (String line : Ui.wrap("! " + problem, previewW)) {
                if (y > limit - 10) {
                    return;
                }
                Ui.label(graphics, line, previewX, y, Ui.WARN);
                y += 10;
            }
            y += 3;
        }
    }

    private void renderCard(GuiGraphics graphics, Card card, int mouseX, int mouseY) {
        boolean selected = draft.role() == card.role();
        boolean hovered = Ui.inside(mouseX, mouseY, card.x(), card.y(), card.w(), card.h());
        PixelUi.frame(graphics, card.x(), card.y(), card.w(), card.h(), 1,
                selected ? RotasTheme.ACCENT_STRONG : hovered ? RotasTheme.PANEL_BORDER : RotasTheme.SEPARATOR,
                selected ? RotasTheme.ACCENT_WASH : hovered ? RotasTheme.SURFACE_HIGH : RotasTheme.SURFACE);
        if (selected) {
            graphics.fill(card.x() + 3, card.y() + 5, card.x() + 5, card.y() + card.h() - 5, RotasTheme.ACCENT_STRONG);
        }
        graphics.renderFakeItem(roleIcon(card.role()), card.x() + 7, card.y() + (card.h() - 16) / 2);
        int textW = card.w() - 32;
        boolean compact = card.h() < 40;
        Ui.label(graphics, Ui.truncate(roleTitle(card.role()), textW), card.x() + 27,
                compact ? card.y() + (card.h() - 8) / 2 : card.y() + 10, selected ? RotasTheme.TEXT : Ui.TEXT_BRIGHT);
        if (!compact) Ui.label(graphics, Ui.truncate(roleHelp(card.role()), textW), card.x() + 27, card.y() + 24, Ui.TEXT_MUTED);
    }

    private static String roleTitle(NpcDef.Role role) {
        return switch (role) {
            case DIALOGUE -> "Just talks";
            case QUEST_GIVER -> "Gives quests";
            case BOARD_KEEPER -> "Opens a quest board";
            case MERCHANT -> "Runs a shop";
            case JOB_MASTER -> "Assigns jobs";
            case STABLE -> "Runs the stable";
            case CRAFTER -> "Hidden artisan";
            case BLACKSMITH -> "Blacksmith";
            case ENCHANTER -> "Enchanter";
            case ALCHEMIST -> "Alchemist";
            case INNKEEPER -> "Innkeeper";
            case PRIEST -> "Priest";
            case FORTUNE_TELLER -> "Fortune teller";
            case BANKER -> "Banker";
            case BOUNTY_MASTER -> "Bounty master";
            case GUARD -> "Guard";
            case TRAINER -> "Trainer";
            case CARTOGRAPHER -> "Cartographer";
            case COLLECTOR -> "Collector";
            case AUCTIONEER -> "Auctioneer";
        };
    }

    private static String roleShort(NpcDef.Role role) {
        return switch (role) {
            case DIALOGUE -> "TALKS";
            case QUEST_GIVER -> "QUESTS";
            case BOARD_KEEPER -> "BOARD";
            case MERCHANT -> "SHOP";
            case JOB_MASTER -> "JOBS";
            case STABLE -> "STABLE";
            case CRAFTER -> "ARTISAN";
            case BLACKSMITH -> "SMITH";
            case ENCHANTER -> "ENCHANT";
            case ALCHEMIST -> "BREWS";
            case INNKEEPER -> "INN";
            case PRIEST -> "PRIEST";
            case FORTUNE_TELLER -> "FORTUNE";
            case BANKER -> "BANK";
            case BOUNTY_MASTER -> "BOUNTY";
            case GUARD -> "GUARD";
            case TRAINER -> "TRAINER";
            case CARTOGRAPHER -> "MAPS";
            case COLLECTOR -> "BUYER";
            case AUCTIONEER -> "AUCTION";
        };
    }

    private static String roleHelp(NpcDef.Role role) {
        return switch (role) {
            case DIALOGUE -> "Says its lines. No quests or shop.";
            case QUEST_GIVER -> "Hands out quests and takes them back.";
            case BOARD_KEEPER -> "Opens a quest board right away.";
            case MERCHANT -> "Opens a villager-style trade screen.";
            case JOB_MASTER -> "Grants a configured main job and sub-job.";
            case STABLE -> "Horse draw, stable, horse market and horse sales.";
            case CRAFTER -> "Makes a sub-role's crafts for a fee. No marker.";
            case BLACKSMITH -> "Repairs gear; opens refine, salvage, sockets.";
            case ENCHANTER -> "Disenchants to books; runes and sockets.";
            case ALCHEMIST -> "Sells potion effects. Brews: season rules.";
            case INNKEEPER -> "Rest (heal + Rested EXP) and set respawn.";
            case PRIEST -> "Daily prayer, cleanse, blessing, lift curses.";
            case FORTUNE_TELLER -> "Fortune: EXP boon or bad omen + a rumour.";
            case BANKER -> "Deposits coins, withdraws gold.";
            case BOUNTY_MASTER -> "Daily kill contracts and nemesis posters.";
            case GUARD -> "Area danger, nearest waystone, events.";
            case TRAINER -> "Stats, skill tree, job and respec.";
            case CARTOGRAPHER -> "Waystone travel, unfound waystones, bestiary.";
            case COLLECTOR -> "Buys materials; daily picks pay more.";
            case AUCTIONEER -> "Player auction house for items.";
        };
    }

    private static ItemStack roleIcon(NpcDef.Role role) {
        return switch (role) {
            case DIALOGUE -> new ItemStack(Items.WRITABLE_BOOK);
            case QUEST_GIVER -> new ItemStack(Items.FILLED_MAP);
            case BOARD_KEEPER -> new ItemStack(RotasRegistry.QUEST_BOARD_ITEM.get());
            case MERCHANT -> new ItemStack(Items.EMERALD);
            case JOB_MASTER -> new ItemStack(Items.EXPERIENCE_BOTTLE);
            case STABLE -> new ItemStack(Items.SADDLE);
            case CRAFTER -> new ItemStack(Items.SMITHING_TABLE);
            case BLACKSMITH -> new ItemStack(Items.ANVIL);
            case ENCHANTER -> new ItemStack(Items.ENCHANTING_TABLE);
            case ALCHEMIST -> new ItemStack(Items.BREWING_STAND);
            case INNKEEPER -> new ItemStack(Items.RED_BED);
            case PRIEST -> new ItemStack(Items.TOTEM_OF_UNDYING);
            case FORTUNE_TELLER -> new ItemStack(Items.ENDER_EYE);
            case BANKER -> new ItemStack(Items.GOLD_INGOT);
            case BOUNTY_MASTER -> new ItemStack(Items.GOLDEN_SWORD);
            case GUARD -> new ItemStack(Items.SHIELD);
            case TRAINER -> new ItemStack(Items.IRON_CHESTPLATE);
            case CARTOGRAPHER -> new ItemStack(Items.COMPASS);
            case COLLECTOR -> new ItemStack(Items.CHEST);
            case AUCTIONEER -> new ItemStack(Items.BELL);
        };
    }

    /** Artisan levels follow the season tiers: every level, or the last level of tier A, B, C or D. */
    private static final int[] CRAFTER_LEVELS = {0, 4, 9, 14, 19};

    private String crafterJob() {
        var service = draft.crafterService();
        return service == null ? "none (click to choose)" : ClientState.jobName(service.jobId());
    }

    private String crafterLevelText() {
        var service = draft.crafterService();
        int level = service == null ? 0 : service.level();
        return level == 0 ? "every level" : "level " + level;
    }

    private void chooseCrafterJob() {
        java.util.LinkedHashMap<String, String> choices = new java.util.LinkedHashMap<>();
        ClientState.jobs().values().stream()
                .filter(job -> job.subAllowed() && !job.production().isEmpty())
                .sorted(java.util.Comparator.comparing(net.schwarz.rotasutils.job.JobDef::name))
                .forEach(job -> choices.put(job.id(), job.name()));
        var current = draft.crafterService();
        minecraft.setScreen(PickerScreen.choices("Choose the artisan's job", choices, this,
                id -> setCrafter(id, current == null ? 0 : current.level())));
    }

    private void cycleCrafterLevel() {
        var service = draft.crafterService();
        if (service == null) return;
        int next = 0;
        for (int i = 0; i < CRAFTER_LEVELS.length; i++) {
            if (CRAFTER_LEVELS[i] == service.level()) next = CRAFTER_LEVELS[(i + 1) % CRAFTER_LEVELS.length];
        }
        setCrafter(service.jobId(), next);
    }

    private void setCrafter(String jobId, int level) {
        draft.services().removeIf(service -> service.type() == net.schwarz.rotasutils.npc.NpcServiceDef.Type.CRAFTER);
        draft.services().add(new net.schwarz.rotasutils.npc.NpcServiceDef(
                net.schwarz.rotasutils.npc.NpcServiceDef.Type.CRAFTER, jobId, "", net.schwarz.rotasutils.job.JobSlot.SUB, level));
        rebuild();
    }

    private String offeredJob(net.schwarz.rotasutils.job.JobSlot slot) {
        String id = net.schwarz.rotasutils.server.JobService.offeredJob(draft, slot);
        return id.isBlank() ? "none" : ClientState.jobName(id);
    }

    private void chooseOfferedJob(net.schwarz.rotasutils.job.JobSlot slot) {
        java.util.LinkedHashMap<String, String> choices = new java.util.LinkedHashMap<>();
        ClientState.jobs().values().stream()
                .filter(job -> slot == net.schwarz.rotasutils.job.JobSlot.MAIN ? job.mainAllowed() : job.subAllowed())
                .sorted(java.util.Comparator.comparing(net.schwarz.rotasutils.job.JobDef::name))
                .forEach(job -> choices.put(job.id(), job.name()));
        minecraft.setScreen(PickerScreen.choices("Choose " + (slot == net.schwarz.rotasutils.job.JobSlot.MAIN ? "main job" : "sub-job"),
                choices, this, id -> {
                    draft.services().removeIf(service -> service.type() == net.schwarz.rotasutils.npc.NpcServiceDef.Type.JOB_MASTER && service.slot() == slot);
                    draft.services().add(new net.schwarz.rotasutils.npc.NpcServiceDef(
                            net.schwarz.rotasutils.npc.NpcServiceDef.Type.JOB_MASTER, id, "", slot));
                    rebuild();
                }));
    }

    /** The bound mob if it is loaded near this client; searched at most twice a second. */
    private LivingEntity boundEntity() {
        if (!draft.bound() || minecraft == null || minecraft.level == null) {
            return null;
        }
        if (cachedEntity != null && !cachedEntity.isRemoved()
                && cachedEntity.getUUID().toString().equals(draft.entityUuid())) {
            return cachedEntity;
        }
        cachedEntity = null;
        long now = Util.getMillis();
        if (now - lastEntitySearch < 500) {
            return null;
        }
        lastEntitySearch = now;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && draft.entityUuid().equals(entity.getUUID().toString())) {
                cachedEntity = living;
                break;
            }
        }
        return cachedEntity;
    }
}
