package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;
import net.schwarz.rotasutils.quest.DangerRank;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.skill.SkillCategory;
import net.schwarz.rotasutils.skill.SkillNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class PickerScreen extends RotasScreen {
    private record Entry(String id, String label, ItemStack icon) {
    }

    private static final int CARD_MIN_W = 84;
    private static final int CARD_H = 78;
    private static final int GRID_INSET = 6;
    private static final int SCROLLBAR_ROOM = 8;

    private final ParamKind kind;
    private final java.util.Map<String, String> choices;
    private final Consumer<String> onPicked;
    private final boolean allowEmpty;
    private String query = "";
    private long catalogRevision = -1;
    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> filtered = new ArrayList<>();
    private EditBox search;
    private ScrollPanel list;
    private int columns = 1;
    private int cardWidth = CARD_MIN_W;
    private double lastMouseX;

    public PickerScreen(ParamKind kind, Screen parent, Consumer<String> onPicked) {
        this(kind, parent, onPicked, false);
    }

    public PickerScreen(ParamKind kind, Screen parent, Consumer<String> onPicked, boolean allowEmpty) {
        super("Select " + kind.name().toLowerCase(Locale.ROOT).replace('_', ' '), parent);
        this.kind = kind;
        this.choices = null;
        this.onPicked = onPicked;
        this.allowEmpty = allowEmpty;
    }

    private PickerScreen(String title, java.util.Map<String, String> choices, Screen parent, Consumer<String> onPicked) {
        super(title, parent);
        this.kind = null;
        this.choices = java.util.Map.copyOf(choices);
        this.onPicked = onPicked;
        this.allowEmpty = false;
    }

    public static Screen open(ParamKind kind, Screen parent, Consumer<String> onPicked, boolean allowEmpty) {
        return kind == ParamKind.ENTITY
                ? new EntityPickerScreen(parent, onPicked, allowEmpty)
                : new PickerScreen(kind, parent, onPicked, allowEmpty);
    }

    public static PickerScreen choices(String title, java.util.Map<String, String> options, Screen parent,
                                       Consumer<String> onPicked) {
        return new PickerScreen(title, options, parent, onPicked);
    }

    @Override
    protected Refresh refreshMode() {
        return Refresh.REBUILD;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 560);
        guiHeight = Ui.fill(height, 400);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        collect();

        search = new EditBox(font, guiLeft + 8, guiTop + 30, guiWidth - 16, 18, net.schwarz.rotasutils.client.screen.Ui.text("Search"));
        search.setHint(net.schwarz.rotasutils.client.screen.Ui.text("Type to filter, then press Enter to take the top result"));
        search.setValue(query);
        search.setResponder(value -> { query = value; filter(); });
        addRenderableWidget(search);
        setInitialFocus(search);

        int listWidth = guiWidth - 16;
        int gridWidth = listWidth - 2 - GRID_INSET * 2 - SCROLLBAR_ROOM;
        columns = Math.max(1, (gridWidth + Ui.GAP * 2) / (CARD_MIN_W + Ui.GAP * 2));
        cardWidth = (gridWidth - Ui.GAP * 2 * (columns - 1)) / columns;
        list = new ScrollPanel(guiLeft + 8, guiTop + 54, listWidth, guiHeight - 90, CARD_H + Ui.GAP * 2);
        registerPanel(list);
        filter();
        addBackButton(guiLeft + 8, 80);
        if (kind == ParamKind.MERCHANT) send("kernel_refresh");
        if (allowEmpty) addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Clear value"), button -> {
            onPicked.accept(""); goBack();
        }).bounds(guiLeft + 94, guiTop + guiHeight - 30, 90, 22).build());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && !filtered.isEmpty()) {
            pickEntry(0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void collect() {
        all.clear();
        catalogRevision = net.schwarz.rotasutils.client.ClientKernelState.revision();
        if (choices != null) {
            choices.forEach((id, label) -> all.add(new Entry(id, label, ItemStack.EMPTY)));
            all.sort((a, b) -> a.label().compareToIgnoreCase(b.label()));
            return;
        }
        switch (kind) {
            case ITEM -> BuiltInRegistries.ITEM.keySet().forEach(id -> all.add(new Entry(id.toString(),
                    new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString(),
                    new ItemStack(BuiltInRegistries.ITEM.get(id)))));
            case BLOCK -> BuiltInRegistries.BLOCK.keySet().forEach(id -> all.add(new Entry(id.toString(),
                    BuiltInRegistries.BLOCK.get(id).getName().getString(),
                    new ItemStack(BuiltInRegistries.BLOCK.get(id)))));
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.keySet().forEach(id -> all.add(new Entry(id.toString(),
                    BuiltInRegistries.ENTITY_TYPE.get(id).getDescription().getString(), ItemStack.EMPTY)));
            case EFFECT -> BuiltInRegistries.MOB_EFFECT.keySet().forEach(id ->
                    all.add(new Entry(id.toString(), id.getPath(), ItemStack.EMPTY)));
            case ATTRIBUTE -> BuiltInRegistries.ATTRIBUTE.keySet().forEach(id ->
                    all.add(new Entry(id.toString(), id.getPath(), ItemStack.EMPTY)));
            case ITEM_TAG -> BuiltInRegistries.ITEM.getTagNames().forEach(tag ->
                    all.add(new Entry(tag.location().toString(), tag.location().getPath(), ItemStack.EMPTY)));
            case ENTITY_TAG -> BuiltInRegistries.ENTITY_TYPE.getTagNames().forEach(tag ->
                    all.add(new Entry(tag.location().toString(), tag.location().getPath(), ItemStack.EMPTY)));
            case ENTITY_NAMESPACE -> BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                    .map(ResourceLocation::getNamespace).distinct().forEach(namespace ->
                            all.add(new Entry(namespace, namespace, ItemStack.EMPTY)));
            case BIOME -> {
                if (minecraft != null && minecraft.level != null)
                    minecraft.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
                            .keySet().forEach(id -> all.add(new Entry(id.toString(), id.getPath(), ItemStack.EMPTY)));
            }
            case QUEST -> {
                for (QuestDef quest : ClientState.quests().values()) {
                    all.add(new Entry(quest.id(), "[" + quest.rank().display() + "] " + quest.name(), quest.icon()));
                }
            }
            case SKILL -> {
                for (SkillCategory category : ClientState.categories().values()) {
                    for (SkillNode node : category.nodes().values()) {
                        all.add(new Entry(node.id(), category.name() + " / " + node.name(), node.icon()));
                    }
                }
            }
            case CATEGORY -> {
                for (SkillCategory category : ClientState.categories().values()) {
                    all.add(new Entry(category.id(), category.name(), category.icon()));
                }
            }
            case BOARD -> ClientState.boards().values().forEach(board ->
                    all.add(new Entry(board.id(), board.name(), board.icon())));
            case MERCHANT -> net.schwarz.rotasutils.client.ClientKernelState.merchants().forEach(merchant ->
                    all.add(new Entry(merchant.id(), merchant.label(), ItemStack.EMPTY)));
            case RANK -> {
                for (DangerRank rank : DangerRank.VALUES) {
                    all.add(new Entry(rank.name(), rank.display() + "-Rank", ItemStack.EMPTY));
                }
            }
            case DIMENSION -> {
                if (minecraft != null && minecraft.getConnection() != null) {
                    minecraft.getConnection().levels().forEach(key ->
                            all.add(new Entry(key.location().toString(), key.location().getPath(), ItemStack.EMPTY)));
                }
            }
            default -> {
            }
        }
        all.sort((a, b) -> a.label().compareToIgnoreCase(b.label()));
    }

    private void filter() {
        String query = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (Entry entry : all) {
            if (query.isEmpty()
                    || entry.label().toLowerCase(Locale.ROOT).contains(query)
                    || entry.id().toLowerCase(Locale.ROOT).contains(query)) {
                filtered.add(entry);
            }
            if (filtered.size() >= 500) {
                break;
            }
        }
        list.setRows((filtered.size() + columns - 1) / columns, this::renderLine, this::clickLine);
    }

    @Override public void tick() {
        super.tick();
        if (kind == ParamKind.MERCHANT && catalogRevision != net.schwarz.rotasutils.client.ClientKernelState.revision()) {
            collect(); filter();
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        lastMouseX = mouseX;
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        lastMouseX = mouseX;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int cardX(int lineX, int column) {
        return lineX + GRID_INSET + column * (cardWidth + Ui.GAP * 2);
    }

    private int columnAt(double mouseX, int lineX) {
        for (int column = 0; column < columns; column++) {
            int left = cardX(lineX, column);
            if (mouseX >= left && mouseX < left + cardWidth) return column;
        }
        return -1;
    }

    private void renderLine(GuiGraphics graphics, int line, int x, int y, int lineWidth, int lineHeight, boolean hovered) {
        int hoverColumn = hovered ? columnAt(lastMouseX, x) : -1;
        int cardY = y + Ui.GAP;
        for (int column = 0; column < columns; column++) {
            int index = line * columns + column;
            if (index >= filtered.size()) break;
            renderCard(graphics, filtered.get(index), cardX(x, column), cardY, column == hoverColumn,
                    index == 0 && column != hoverColumn);
        }
    }

    private void renderCard(GuiGraphics graphics, Entry entry, int x, int y, boolean hovered, boolean topMatch) {
        Ui.rowCard(graphics, x, y, cardWidth, CARD_H, hovered, topMatch);
        int centerX = x + cardWidth / 2;
        int textWidth = cardWidth - 10;
        if (!entry.icon().isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(centerX - 16, y + 7, 0);
            graphics.pose().scale(2f, 2f, 1f);
            graphics.renderFakeItem(entry.icon(), 0, 0);
            graphics.pose().popPose();
        } else {
            List<String> lines = Ui.wrap(entry.label(), textWidth);
            int shown = Math.min(3, lines.size());
            int top = y + 25 - shown * 6;
            for (int i = 0; i < shown; i++) {
                Ui.labelCentered(graphics, lines.get(i), centerX, top + i * 12, Ui.TEXT_BRIGHT);
            }
        }
        if (!entry.icon().isEmpty()) {
            Ui.labelCentered(graphics, Ui.truncate(entry.label(), textWidth), centerX, y + 47, Ui.TEXT_BRIGHT);
        }
        Ui.labelCentered(graphics, Ui.truncate(entry.id(), textWidth), centerX, y + 62, Ui.TEXT_MUTED);
    }

    private void clickLine(int line, int button) {
        int column = columnAt(lastMouseX, list.x() + 1);
        if (column < 0) return;
        if (button != 0) return;
        pickEntry(line * columns + column);
    }

    private void pickEntry(int index) {
        if (index < 0 || index >= filtered.size()) return;
        Sfx.select();
        onPicked.accept(filtered.get(index).id());
        goBack();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        String count = filtered.size() >= 500
                ? "showing first 500 of many - keep typing"
                : filtered.size() + " match" + (filtered.size() == 1 ? "" : "es");
        Ui.labelRight(graphics, count, guiLeft + guiWidth - 8, guiTop + guiHeight - 19, Ui.TEXT_DIM);
        if (filtered.isEmpty()) {
            Ui.labelCentered(graphics, "No entry matches that search. Clear it to see everything.",
                    guiLeft + guiWidth / 2, guiTop + guiHeight / 2, Ui.TEXT_DIM);
        }
    }

    public static boolean supports(ParamKind kind) {
        return switch (kind) {
            case ITEM, BLOCK, ENTITY, EFFECT, ATTRIBUTE, ITEM_TAG, ENTITY_TAG, ENTITY_NAMESPACE, BIOME,
                 QUEST, SKILL, CATEGORY, BOARD, MERCHANT, RANK, DIMENSION -> true;
            default -> false;
        };
    }

    public static ResourceLocation tryId(String raw) {
        return ResourceLocation.tryParse(raw);
    }
}
