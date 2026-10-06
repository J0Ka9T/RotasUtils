package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.quest.DangerRank;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.objective.ObjectiveType;
import net.schwarz.rotasutils.quest.requirement.RequirementType;
import net.schwarz.rotasutils.quest.reward.RewardType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Environment(EnvType.CLIENT)
public class QuestCatalogScreen extends RotasScreen {
    private static final int CARD_HEIGHT = 58;
    private static final int GAP = 6;
    private static final int HEADER = 18;

    private static boolean byRank;
    private static String lastSearch = "";
    private static double lastScroll;

    private record Group(String title, String hint, List<QuestDef> quests) {
    }

    private record Hit(String questId, int x, int y, int w, int h) {
    }

    private EditBox search;
    private String searchText = lastSearch;
    private double scroll = lastScroll;
    private int contentHeight;
    private final List<Hit> hits = new ArrayList<>();
    private Map<String, Set<String>> next = Map.of();
    private Map<String, Set<String>> previous = Map.of();

    public QuestCatalogScreen(Screen parent) {
        super("Quest Catalog", parent);
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 960);
        guiHeight = Ui.fill(height, 540);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        setHeader("Quest Catalog");

        search = new EditBox(font, guiLeft + 14, guiTop + 34, Math.max(80, guiWidth - 330), 14, Ui.text("Search"));
        search.setBordered(false);
        search.setHint(Ui.text("Search name, id or category"));
        search.setValue(searchText);
        search.setTextColor(Ui.TEXT_BRIGHT);
        search.setResponder(value -> {
            searchText = value;
            scroll = 0;
        });
        addRenderableWidget(search);

        addRenderableWidget(Ui.button(Ui.text(byRank ? "Group: Rank" : "Group: Chain & category"), button -> {
            byRank = !byRank;
            scroll = 0;
            Sfx.page();
            rebuild();
        }).bounds(guiLeft + guiWidth - 308, guiTop + 30, 180, 20).build());
        addRenderableWidget(Ui.primaryButton(Ui.text("+ New Quest"), button -> {
            send("new_quest");
            Sfx.add();
        }).bounds(guiLeft + guiWidth - 122, guiTop + 30, 110, 20).build());
        addBackButton(guiLeft + 8, 70);
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

private void link() {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        Map<String, Set<String>> in = new LinkedHashMap<>();
        var quests = ClientState.quests();
        for (QuestDef quest : quests.values()) {
            for (var reward : quest.rewards()) {
                if (reward.type() == RewardType.UNLOCK_QUEST) {
                    edge(out, in, quest.id(), reward.params().getString("quest", ""));
                }
            }
            for (var requirement : quest.requirements()) {
                if (requirement.type() == RequirementType.QUEST_COMPLETED) {
                    edge(out, in, requirement.params().getString("quest", ""), quest.id());
                }
            }
            for (var objective : quest.objectives()) {
                if (objective.type() == ObjectiveType.COMPLETE_QUEST) {
                    edge(out, in, objective.params().getString("quest", ""), quest.id());
                }
            }
        }
        out.keySet().removeIf(id -> !quests.containsKey(id));
        out.values().forEach(targets -> targets.removeIf(id -> !quests.containsKey(id)));
        next = out;
        previous = in;
    }

    private static void edge(Map<String, Set<String>> out, Map<String, Set<String>> in, String from, String to) {
        if (from.isEmpty() || to.isEmpty() || from.equals(to)) {
            return;
        }
        out.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(to);
        in.computeIfAbsent(to, k -> new LinkedHashSet<>()).add(from);
    }

    private List<Group> groups() {
        link();
        List<QuestDef> all = new ArrayList<>(ClientState.quests().values());
        String query = searchText.trim().toLowerCase(Locale.ROOT);
        all.removeIf(quest -> !query.isEmpty()
                && !quest.name().toLowerCase(Locale.ROOT).contains(query)
                && !quest.id().toLowerCase(Locale.ROOT).contains(query)
                && !quest.category().toLowerCase(Locale.ROOT).contains(query));
        all.sort(Comparator.comparing((QuestDef q) -> q.rank().ordinal()).thenComparing(QuestDef::name));
        List<Group> groups = new ArrayList<>();
        if (byRank) {
            for (DangerRank rank : DangerRank.VALUES) {
                List<QuestDef> members = all.stream().filter(q -> q.rank() == rank).toList();
                if (!members.isEmpty()) {
                    groups.add(new Group(rank.display() + "-Rank", members.size() + " quest(s)", members));
                }
            }
            return groups;
        }
        Set<String> placed = new LinkedHashSet<>();
        Map<String, QuestDef> visible = new LinkedHashMap<>();
        all.forEach(q -> visible.put(q.id(), q));
        for (QuestDef start : all) {
            if (placed.contains(start.id()) || !linked(start.id())) {
                continue;
            }
            List<QuestDef> chain = chainOf(start.id(), visible);
            if (chain.size() < 2) {
                continue;
            }
            chain.forEach(q -> placed.add(q.id()));
            String title = "Chain: " + (chain.get(0).name().isBlank() ? chain.get(0).id() : chain.get(0).name());
            groups.add(new Group(title, chain.size() + " linked quests, in play order", chain));
        }
        Map<String, List<QuestDef>> byCategory = new TreeMap<>();
        for (QuestDef quest : all) {
            if (!placed.contains(quest.id())) {
                String category = quest.category().isBlank() ? "Uncategorized" : quest.category();
                byCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(quest);
            }
        }
        byCategory.forEach((category, members) -> groups.add(new Group(category, members.size() + " quest(s)", members)));
        return groups;
    }

    private boolean linked(String id) {
        return next.containsKey(id) || previous.containsKey(id);
    }

    private List<QuestDef> chainOf(String seed, Map<String, QuestDef> visible) {
        Set<String> component = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(List.of(seed));
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (!component.add(id)) {
                continue;
            }
            queue.addAll(next.getOrDefault(id, Set.of()));
            queue.addAll(previous.getOrDefault(id, Set.of()));
        }
        Map<String, Integer> incoming = new LinkedHashMap<>();
        component.forEach(id -> incoming.put(id, (int) previous.getOrDefault(id, Set.of()).stream().filter(component::contains).count()));
        List<String> order = new ArrayList<>();
        Deque<String> ready = new ArrayDeque<>();
        incoming.forEach((id, n) -> { if (n == 0) ready.add(id); });
        while (!ready.isEmpty()) {
            String id = ready.poll();
            order.add(id);
            for (String target : next.getOrDefault(id, Set.of())) {
                if (incoming.containsKey(target) && incoming.merge(target, -1, Integer::sum) == 0) {
                    ready.add(target);
                }
            }
        }
        component.stream().filter(id -> !order.contains(id)).forEach(order::add);
        List<QuestDef> chain = new ArrayList<>();
        for (String id : order) {
            if (visible.containsKey(id)) {
                chain.add(visible.get(id));
            }
        }
        return chain;
    }

@Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.searchFrame(graphics, guiLeft + 8, guiTop + 29, Math.max(80, guiWidth - 324), 22, search.isFocused());
        int left = guiLeft + 10;
        int width = guiWidth - 20;
        int top = guiTop + 58;
        int bottom = guiTop + guiHeight - 36;
        int columns = Math.max(1, width / 230);
        int cardWidth = (width - (columns - 1) * GAP) / columns;

        hits.clear();
        graphics.enableScissor(left - 2, top, left + width + 2, bottom);
        List<Group> groups = groups();
        int y = top - (int) scroll;
        if (groups.isEmpty()) {
            Ui.label(graphics, ClientState.quests().isEmpty() ? "No quests yet. Press + New Quest." : "Nothing matches the search.",
                    left, y + 6, Ui.TEXT_MUTED);
        }
        for (Group group : groups) {
            Ui.label(graphics, group.title(), left, y + 4, Ui.ACCENT);
            Ui.label(graphics, group.hint(), left + font.width(group.title()) + 10, y + 4, Ui.TEXT_MUTED);
            y += HEADER;
            int column = 0;
            for (QuestDef quest : group.quests()) {
                int x = left + column * (cardWidth + GAP);
                if (y + CARD_HEIGHT >= top && y <= bottom) {
                    card(graphics, quest, x, y, cardWidth, mouseX, mouseY, top, bottom);
                }
                if (++column == columns) {
                    column = 0;
                    y += CARD_HEIGHT + GAP;
                }
            }
            if (column != 0) {
                y += CARD_HEIGHT + GAP;
            }
            y += 4;
        }
        graphics.disableScissor();
        contentHeight = y + (int) scroll - top;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - (bottom - top))));
        lastScroll = scroll;
        lastSearch = searchText;
        Ui.labelRight(graphics, ClientState.quests().size() + " quests", guiLeft + guiWidth - 10, guiTop + guiHeight - 22, Ui.TEXT_MUTED);
    }

    private void card(GuiGraphics graphics, QuestDef quest, int x, int y, int w, int mouseX, int mouseY, int top, int bottom) {
        boolean hovered = mouseY >= top && mouseY <= bottom && Ui.inside(mouseX, mouseY, x, y, w, CARD_HEIGHT);
        Ui.rowCard(graphics, x, y, w, CARD_HEIGHT, hovered, false);
        Ui.roundedRect(graphics, x + 3, y + 6, 2, CARD_HEIGHT - 12, 1, quest.rank().argb());
        int textX = x + 10;
        if (!quest.icon().isEmpty()) {
            graphics.renderFakeItem(quest.icon(), x + 9, y + 6);
            textX = x + 30;
        }
        String status = quest.published() ? "LIVE" : "DRAFT";
        int statusWidth = font.width(status) + 8;
        Ui.label(graphics, Ui.truncate(quest.name().isBlank() ? "(no name) " + quest.id() : quest.name(), x + w - textX - statusWidth - 12),
                textX, y + 7, Ui.TEXT_BRIGHT);
        Ui.labelRight(graphics, status, x + w - 8, y + 7, quest.published() ? Ui.GOOD : Ui.WARN);

        int chipX = textX;
        chipX += chip(graphics, chipX, y + 21, quest.rank().display(), quest.rank().argb());
        long givers = ClientState.npcs().values().stream().filter(npc -> npc.questIds().contains(quest.id())).count();
        if (quest.followUp()) chipX += chip(graphics, chipX, y + 21, "Follow-up", Ui.ACCENT);
        if (!quest.boardIds().isEmpty()) chipX += chip(graphics, chipX, y + 21, "Board", Ui.TEXT);
        if (givers > 0) chipX += chip(graphics, chipX, y + 21, "NPC", Ui.TEXT);
        if (quest.autoComplete()) chipX += chip(graphics, chipX, y + 21, "Instant", Ui.GOOD);
        if (!quest.followUp() && quest.boardIds().isEmpty() && givers == 0) {
            chip(graphics, chipX, y + 21, "No source", Ui.BAD);
        }

        String flow = flow(quest);
        Ui.label(graphics, Ui.truncate(flow.isEmpty() ? quest.objectives().size() + " objective(s), "
                + quest.rewards().size() + " reward(s)" : flow, w - 20), x + 10, y + 40, Ui.TEXT_MUTED);
        hits.add(new Hit(quest.id(), x, y, w, CARD_HEIGHT));
    }

    private int chip(GuiGraphics graphics, int x, int y, String text, int color) {
        int w = font.width(text) + 8;
        Ui.roundedRect(graphics, x, y, w, 12, 3, (color & 0x00FFFFFF) | 0x33000000);
        Ui.label(graphics, text, x + 4, y + 2, color);
        return w + 4;
    }

    private String flow(QuestDef quest) {
        List<String> parts = new ArrayList<>();
        Set<String> from = previous.getOrDefault(quest.id(), Set.of());
        Set<String> to = next.getOrDefault(quest.id(), Set.of());
        if (!from.isEmpty()) parts.add("after " + names(from));
        if (!to.isEmpty()) parts.add("-> " + names(to));
        return String.join("   ", parts);
    }

    private static String names(Set<String> ids) {
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            QuestDef quest = ClientState.quest(id);
            names.add(quest == null || quest.name().isBlank() ? id : quest.name());
        }
        return String.join(", ", names);
    }

@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int top = guiTop + 58;
        int bottom = guiTop + guiHeight - 36;
        if (button == 0 && mouseY >= top && mouseY <= bottom) {
            for (Hit hit : hits) {
                if (Ui.inside((int) mouseX, (int) mouseY, hit.x(), hit.y(), hit.w(), hit.h())) {
                    Sfx.select();
                    minecraft.setScreen(new QuestCreatorScreen(hit.questId(), this));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Math.max(0, scroll - delta * 24);
        return true;
    }
}
