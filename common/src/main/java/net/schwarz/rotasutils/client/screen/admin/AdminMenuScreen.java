package net.schwarz.rotasutils.client.screen.admin;

import dev.architectury.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasButton;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.quest.QuestDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Environment(EnvType.CLIENT)
public class AdminMenuScreen extends RotasScreen {
    private static final int ROW_HEIGHT = 40;

    private enum Group {
        NONE(""),
        CONTENT("CONTENT"),
        WORLD("IN THE WORLD"),
        PLAYERS("PLAYERS"),
        SERVER("SERVER");

        final String label;

        Group(String label) {
            this.label = label;
        }
    }

    public enum Section {
        DASHBOARD("Overview", "Where things stand, and what to do first", "", Group.NONE, ""),
        QUESTS("Quests", "Write and publish contracts", "New Quest", Group.CONTENT, ""),
        MONSTERS("Monsters", "Edit health, damage, armor, movement and level scaling for each mob", "Open Mob Setup", Group.CONTENT, ""),
        NPCS("NPCs", "Quest givers, shopkeepers and board clerks", "New NPC", Group.WORLD, ""),
        BOARDS("Quest Boards", "The billboards players read quests from", "New Board", Group.WORLD, ""),
        ZONES("Level Zones", "Level bands by region, for every mob", "New Zone", Group.WORLD, ""),
        HOUSES(L.t("rotasutils.house.admin.nav_label"), L.t("rotasutils.house.admin.nav_help"),
                L.t("rotasutils.house.admin.nav_action"), Group.WORLD, ""),
        LEVELS("Progression", "Levels, ranks and how XP is earned", "Open Progression", Group.PLAYERS, ""),
        SKILLS("Skills", "Skill trees for everyone, each job and each race", "New Skill Tree", Group.PLAYERS, ""),
        JOBS("Jobs & Stats", "Classes players choose, and what level-up points buy", "Open Jobs", Group.PLAYERS, ""),
        PLAYERS("Player Records & Stats", "Edit STR, VIT, INT and AGI, then manage one player's progression", "Open Player Editor", Group.PLAYERS, ""),
        SYSTEMS("Game Systems", "Every season system: farming, drops, refine, events, tracks, horses", "Open All Settings",
                Group.SERVER, ""),
        VALIDATION("Check Content", "Find missing settings and broken references", "Run Check", Group.SERVER, ""),
        SETTINGS("Server Rules", "Global quest and progression rules", "Open Settings", Group.SERVER, ""),
        ECONOMY(L.t("rotasutils.econ.nav_label"), L.t("rotasutils.econ.nav_help"), L.t("rotasutils.econ.nav_action"), Group.SERVER, ""),
        ADVANCED("Advanced", "Items, bosses, merchants, files and change history", "", Group.SERVER, ""),
        ITEMS("Items & Loot", "Items, rarities, sets and loot tables", "Open Item Editor", Group.SERVER, "ADVANCED"),
        BOSSES("Bosses", "Phases, arena settings and rewards", "Open Boss Editor", Group.SERVER, "ADVANCED"),
        MERCHANTS("Merchants", "Trades, prices, stock and limits", "Open Merchant Editor", Group.SERVER, "ADVANCED"),
        AUDIT("Change History", "See who changed server content and when", "", Group.SERVER, "ADVANCED");

        final String label;
        final String help;
        final String action;
        final Group group;
        final String parent;

        Section(String label, String help, String action, Group group, String parent) {
            this.label = label;
            this.help = help;
            this.action = action;
            this.group = group;
            this.parent = parent;
        }

        boolean hidden() {
            return !parent.isEmpty();
        }

        boolean highlightedFor(Section open) {
            return this == open || name().equals(open.parent);
        }
    }

    private record NavRow(Group heading, Section section) {
    }

    private static List<NavRow> navigationRows() {
        List<NavRow> rows = new ArrayList<>();
        Group current = Group.NONE;
        for (Section value : Section.values()) {
            if (value.hidden()) {
                continue;
            }
            if (value.group != current && value.group != Group.NONE) {
                rows.add(new NavRow(value.group, null));
                current = value.group;
            }
            rows.add(new NavRow(null, value));
        }
        return rows;
    }

    private record Entry(String id, String label, String meta, String tag, int tagColor, ItemStack icon) {
    }

    private Section section = Section.DASHBOARD;
    private ScrollPanel list;
    private EditBox search;
    private String searchText = "";
    private final List<Entry> entries = new ArrayList<>();
    private static Section lastSection = Section.DASHBOARD;
    private static String lastSearch = "";
    private static int lastScroll;
    private boolean restoreScroll = true;

    public AdminMenuScreen() {
        super("RotasUtils Administration", null);
        section = lastSection;
        searchText = lastSearch;
    }

    public AdminMenuScreen(String sectionName) {
        this();
        if (sectionName != null && !sectionName.isEmpty()) {
            searchText = "";
        }
        for (Section value : Section.values()) {
            if (value.name().equals(sectionName)) {
                section = value;
            }
        }
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 900);
        guiHeight = Ui.fill(height, 520);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        int margin = 10;
        int sidebarWidth = 190;
        int mainX = guiLeft + sidebarWidth + 22;
        int mainWidth = guiLeft + guiWidth - mainX - margin;

        List<NavRow> navRows = navigationRows();
        ScrollPanel navigation = new ScrollPanel(guiLeft + margin + 2, guiTop + 62,
                sidebarWidth - 14, Math.max(48, guiHeight - 110), 22);
        navigation.setRows(navRows.size(), (g, i, x, y, w, h, hovered) -> {
            NavRow row = navRows.get(i);
            if (row.heading() != null) {
                Ui.label(g, row.heading().label, x + 4, y + 9, Ui.TEXT_MUTED);
                g.fill(x + 4, y + h - 3, x + w - 6, y + h - 2, Ui.BORDER_SUBTLE);
                return;
            }
            Section value = row.section();
            boolean selected = value.highlightedFor(section);
            if (selected || hovered) {
                Ui.rowCard(g, x + 2, y + 1, w - 6, h - 3, hovered, selected);
            }
            String count = countSuffix(value);
            Ui.label(g, Ui.truncate(value.label, w - 30), x + 10, y + 7, selected ? Ui.ACCENT : Ui.TEXT);
            if (!count.isBlank()) {
                Ui.labelRight(g, count.trim(), x + w - 10, y + 7, Ui.TEXT_MUTED);
            }
        }, (i, button) -> {
            NavRow row = navRows.get(i);
            if (row.section() == null) {
                return;
            }
            if (row.section() == Section.QUESTS) {
                Sfx.page();
                minecraft.setScreen(new QuestCatalogScreen(this));
                return;
            }
            section = row.section();
            searchText = "";
            Sfx.page();
            rebuild();
        });
        registerPanel(navigation);

        boolean searchable = isSearchable(section);
        search = null;
        int listY;
        if (searchable) {
            int searchY = guiTop + 76;
            search = new EditBox(font, mainX + 12, searchY + 7,
                    Math.max(80, mainWidth - 24), 14, net.schwarz.rotasutils.client.screen.Ui.text("Search"));
            search.setBordered(false);
            search.setHint(net.schwarz.rotasutils.client.screen.Ui.text("Search " + section.label.toLowerCase(Locale.ROOT)));
            search.setValue(searchText);
            search.setTextColor(Ui.TEXT_BRIGHT);
            search.setTextColorUneditable(Ui.TEXT_MUTED);
            search.setResponder(value -> {
                searchText = value;
                refreshRows();
            });
            addRenderableWidget(search);
            listY = guiTop + 110;
        } else {
            listY = guiTop + 78;
        }

        int footerY = guiTop + guiHeight - 34;
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Back"), button -> goBack())
                .bounds(guiLeft + margin + 2, footerY, 88, 24).build());
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Check Content"), button -> {
            send("validate");
            Sfx.commit();
        }).tooltip(Tooltip.create(net.schwarz.rotasutils.client.screen.Ui.text(
                        "Scans quests and boards for missing or invalid content.")))
                .bounds(guiLeft + margin + 96, footerY, 102, 24).build());
        if (!section.action.isEmpty()) {
            addRenderableWidget(Ui.primaryButton(net.schwarz.rotasutils.client.screen.Ui.text(section.action), button -> createNew())
                    .bounds(guiLeft + guiWidth - margin - 146, footerY, 146, 24).build());
        }

        list = new ScrollPanel(mainX + 4, listY, mainWidth - 8,
                Math.max(ROW_HEIGHT, footerY - listY - 12), ROW_HEIGHT)
                .withoutBackground()
                .rowHitInsets(0, 4);
        registerPanel(list);
        refreshRows();
        if (restoreScroll && section == lastSection) {
            list.setScroll(lastScroll);
        }
        restoreScroll = false;
        lastSection = section;
        lastSearch = searchText;
    }

    @Override
    public void removed() {
        lastSection = section;
        lastSearch = searchText;
        if (list != null) {
            lastScroll = list.scroll();
        }
        restoreScroll = true;
        super.removed();
    }

    private static boolean isSearchable(Section section) {
        return section == Section.QUESTS
                || section == Section.BOARDS
                || section == Section.NPCS
                || section == Section.HOUSES
                || section == Section.VALIDATION
                || section == Section.AUDIT;
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.window(graphics, guiLeft, guiTop, guiWidth, guiHeight);

        Ui.scaledLabel(graphics, "RotasUtils Admin", guiLeft + 16, guiTop + 12,
                1.20f, Ui.TEXT_BRIGHT);
        Ui.label(graphics, "Pick a section on the left. Overview walks through the usual order.",
                guiLeft + 17, guiTop + 29, Ui.TEXT_MUTED);
        Ui.infoPill(graphics, guiLeft + guiWidth - 128, guiTop + 11,
                ClientState.operator() ? "OPERATOR" : "ADMIN", Ui.ACCENT);

        int margin = 10;
        int sidebarWidth = 190;
        int mainX = guiLeft + sidebarWidth + 22;
        int mainWidth = guiLeft + guiWidth - mainX - margin;
        int footerY = guiTop + guiHeight - 34;

        Ui.modernPanel(graphics, guiLeft + margin, guiTop + 46,
                sidebarWidth - 2, footerY - guiTop - 54);
        Ui.modernPanel(graphics, mainX, guiTop + 46,
                mainWidth, footerY - guiTop - 54);

        graphics.fill(guiLeft + sidebarWidth + 15, guiTop + 58,
                guiLeft + sidebarWidth + 16, footerY - 16, 0x59BE9E68);
        graphics.fill(guiLeft + 14, footerY - 7, guiLeft + guiWidth - 14,
                footerY - 6, 0x4DBE9E68);
    }

    private static Boolean systemEnabled(com.google.gson.JsonObject season, String id) {
        if (id.equals("leveling")) return season.has("enabled") ? season.get("enabled").getAsBoolean() : null;
        if (!season.has(id) || !season.get(id).isJsonObject()) return null;
        com.google.gson.JsonObject block = season.getAsJsonObject(id);
        if (block.has("enabled")) return block.get("enabled").getAsBoolean();
        Boolean any = null;
        for (var entry : block.entrySet()) {
            if (entry.getKey().endsWith("Enabled") && entry.getValue().isJsonPrimitive()
                    && entry.getValue().getAsJsonPrimitive().isBoolean()) {
                any = (any != null && any) || entry.getValue().getAsBoolean();
            }
        }
        return any;
    }

    private static String countSuffix(Section value) {
        int count = switch (value) {
            case QUESTS -> ClientState.quests().size();
            case BOARDS -> ClientState.boards().size();
            case ZONES -> ClientState.zones().size();
            case NPCS -> ClientState.npcs().size();
            case SKILLS -> ClientState.pufferfishSkills() ? -1 : ClientState.categories().size();
            case JOBS -> ClientState.jobs().size();
            case HOUSES -> ClientState.houseAdmin() == null ? -1 : ClientState.houseAdmin().definitions().size();
            case VALIDATION -> ClientState.issues().size();
            case AUDIT -> ClientState.audit().size();
            default -> -1;
        };
        return count < 0 ? "" : "   " + count;
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    @Override
    public void onDataRefreshed() {
        refreshRows();
    }

    private void createNew() {
        switch (section) {
            case MONSTERS -> minecraft.setScreen(new MobSetupScreen(this));
            case ITEMS, BOSSES, MERCHANTS -> minecraft.setScreen(new AdminStudioScreen(this, studioKind(section)));
            case QUESTS -> {
                send("new_quest");
                Sfx.add();
            }
            case BOARDS -> {
                send("new_board");
                Sfx.add();
            }
            case NPCS -> {
                send("new_npc");
                Sfx.add();
            }
            case ZONES -> {
                send("new_zone");
                Sfx.add();
            }
            case SKILLS -> {
                if (ClientState.pufferfishSkills()) {
                    send("open_skills");
                    Sfx.page();
                } else {
                    send("new_category");
                    Sfx.add();
                }
            }
            case JOBS -> minecraft.setScreen(new JobManagerScreen(this));
            case ECONOMY -> send("worth_open");
            case HOUSES -> minecraft.setScreen(new HouseManagerScreen(this));
            case LEVELS -> minecraft.setScreen(new LevelManagerScreen(this));
            case PLAYERS -> minecraft.setScreen(new PlayerManagerScreen(this));
            case SETTINGS -> minecraft.setScreen(new ServerSettingsScreen(this));
            case SYSTEMS -> minecraft.setScreen(new SeasonSettingsScreen(this));
            case VALIDATION -> {
                send("validate");
                Sfx.commit();
            }
            default -> {
            }
        }
    }

    private void refreshRows() {
        entries.clear();
        switch (section) {
            case MONSTERS -> {
                entries.add(new Entry("mob_setup", "Mob Setup",
                        "Pick mobs by their picture, then set level, strength, XP and loot. Click a card later to edit it.",
                        "EASIEST", Ui.GOOD, new ItemStack(net.minecraft.world.item.Items.ZOMBIE_SPAWN_EGG)));
                entries.add(new Entry("drop_tables", "Drop tables",
                        "Coins and loot per monster rank, loot grades, and mobs with their own drop. Pick items, no typing.",
                        "DROPS", Ui.GOOD, new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT)));
                entries.add(new Entry("mob_drops", "Drops per mob",
                        "See what a mob normally drops, and switch any of those drops off.",
                        "DROPS", Ui.ACCENT, new ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH)));
                entries.add(new Entry("item_drops", "Every item",
                        "Browse every item in the game and decide whether mobs may drop it at all.",
                        "DROPS", Ui.ACCENT, new ItemStack(net.minecraft.world.item.Items.CHEST)));
                entries.add(new Entry("nav:ZONES", "Level Zones",
                        "Give every mob in an area a level band, traced with the Zone Wand.",
                        "", Ui.TEXT_DIM, ItemStack.EMPTY));
                entries.add(new Entry("", "Need scripted monsters, tiers or affixes?",
                        "Those editors are under Advanced. Mob Setup covers normal servers.",
                        "", Ui.TEXT_MUTED, ItemStack.EMPTY));
            }
            case ITEMS, BOSSES, MERCHANTS -> {
                String[] kinds = section == Section.ITEMS ? new String[]{"item", "rarity", "set", "loot"} : new String[]{studioKind(section)};
                for (String kind : kinds) { entries.add(new Entry("studio:"+kind, "Edit " + kind, "Choose a template, fill in settings, then review and apply", "CONTENT",Ui.ACCENT,ItemStack.EMPTY)); }
            }
            case ADVANCED -> {
                entries.add(new Entry("world_events", "World events",
                        "Create and edit event types and their schedule, start or stop events, switch automatic events.",
                        "LIVE", Ui.GOOD, new ItemStack(net.minecraft.world.item.Items.BELL)));
                entries.add(new Entry("titles_admin", "Titles",
                        "Create and edit titles, their conditions and colours; grant or revoke them for a player.",
                        "CONTENT", Ui.ACCENT, new ItemStack(net.minecraft.world.item.Items.NAME_TAG)));
                entries.add(new Entry("mines_admin", "Mining sites",
                        "Make a mine: add blocks you look at or scan an area, set respawn time, gold, EXP and loot.",
                        "WORLD", Ui.GOOD, new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE)));
                entries.add(new Entry("nemesis_admin", "Nemeses",
                        "See every nemesis, who it hunts and where; remove one or summon it to test.",
                        "WORLD", Ui.WARN, new ItemStack(net.minecraft.world.item.Items.WITHER_SKELETON_SKULL)));
                entries.add(new Entry("block_log", "Block log",
                        "Who broke which block, where and when, across every dimension; filter and teleport there.",
                        "PLAYERS", Ui.WARN, new ItemStack(net.minecraft.world.item.Items.WRITABLE_BOOK)));
                entries.add(new Entry("player_tools", "Player tools",
                        "Give a monster card, reset a player's daily missions.",
                        "PLAYERS", Ui.ACCENT, new ItemStack(net.minecraft.world.item.Items.PAPER)));
                entries.add(new Entry("events", "Event catalogue",
                        "Every moment the server can react to - kills, crafts, quests, refines - with what each one pays and announces. Add a line for one mod or one id.",
                        "EVENTS", Ui.ACCENT, new ItemStack(net.minecraft.world.item.Items.CLOCK)));
                entries.add(new Entry("", "For experienced admins",
                        "Most servers only need the sections above. These edit raw content with drafts and rollback.",
                        "", Ui.TEXT_MUTED, ItemStack.EMPTY));
                entries.add(new Entry("nav:ITEMS", "Items & Loot", "Items, rarities, sets and loot tables",
                        "", Ui.TEXT_DIM, ItemStack.EMPTY));
                entries.add(new Entry("nav:BOSSES", "Bosses", "Phases, arena settings and rewards",
                        "", Ui.TEXT_DIM, ItemStack.EMPTY));
                entries.add(new Entry("nav:MERCHANTS", "Merchants",
                        "Content-pack shops. For a simple shop, give an NPC trades instead.",
                        "", Ui.TEXT_DIM, ItemStack.EMPTY));
                entries.add(new Entry("studio:monster", "Monster profiles",
                        "Tiers, affixes and scripted monsters", "", Ui.TEXT_DIM, ItemStack.EMPTY));
                entries.add(new Entry("files", "Files and drafts",
                        "Import, review, apply or export configuration", "", Ui.TEXT_DIM, ItemStack.EMPTY));
                entries.add(new Entry("nav:AUDIT", "Change history",
                        "See who changed server content and when", "", Ui.TEXT_DIM, ItemStack.EMPTY));
                entries.add(new Entry("rpg_console", "Live console",
                        "What the running server currently has loaded", "", Ui.TEXT_DIM, ItemStack.EMPTY));
            }
            case DASHBOARD -> {
                int totalQuests = ClientState.quests().size();
                long published = ClientState.quests().values().stream().filter(QuestDef::published).count();
                int boardCount = ClientState.boards().size();
                long boundNpcs = ClientState.npcs().values().stream()
                        .filter(net.schwarz.rotasutils.npc.NpcDef::bound).count();
                int issueCount = ClientState.issues().size();

                entries.add(new Entry("action:new_quest", "1.  Write a quest",
                        published > 0 ? published + " published, " + totalQuests + " in total"
                                : "Opens the guided creator with safe defaults.",
                        published > 0 ? "DONE" : "START",
                        published > 0 ? Ui.GOOD : Ui.WARN, ItemStack.EMPTY));
                entries.add(new Entry("nav:BOARDS", "2.  Post it on a board",
                        boardCount == 0 ? "No boards yet. A board is where players read quests."
                                : boardCount + " board(s) configured",
                        boardCount > 0 ? "DONE" : "TODO",
                        boardCount > 0 ? Ui.GOOD : Ui.WARN, ItemStack.EMPTY));
                entries.add(new Entry("nav:NPCS", "3.  Or give it to an NPC",
                        boundNpcs == 0 ? "Optional. Right-click a villager with the NPC Wand."
                                : boundNpcs + " NPC(s) bound to an entity",
                        boundNpcs > 0 ? "DONE" : "OPTIONAL",
                        boundNpcs > 0 ? Ui.GOOD : Ui.TEXT_DIM, ItemStack.EMPTY));
                entries.add(new Entry("nav:VALIDATION", "4.  Check for setup problems",
                        issueCount == 0 ? "Open Check Content to validate the current setup"
                                : issueCount + " issue(s) need attention",
                        issueCount == 0 ? "OK" : "CHECK",
                        issueCount == 0 ? Ui.GOOD : Ui.WARN, ItemStack.EMPTY));

                entries.add(new Entry("", "Tuning and operations", "", "", Ui.TEXT_MUTED, ItemStack.EMPTY));
                entries.add(new Entry("mob_setup", "Mob Setup",
                        "Pick mobs by picture and set their level, strength and XP",
                        "EASY", Ui.GOOD, new ItemStack(net.minecraft.world.item.Items.ZOMBIE_SPAWN_EGG)));
                entries.add(new Entry("nav:LEVELS", "Progression",
                        "Level curve, rank clearance and how monsters pay XP",
                        "", Ui.TEXT_DIM, ItemStack.EMPTY));
                entries.add(new Entry("world_events", "World events",
                        "Event types, schedule, and live start / stop",
                        "", Ui.TEXT_DIM, ItemStack.EMPTY));
                entries.add(new Entry("titles_admin", "Titles",
                        "Create, edit, grant and revoke titles",
                        "", Ui.TEXT_DIM, ItemStack.EMPTY));
                entries.add(new Entry("events", "Event catalogue",
                        "What the server pays and announces for each thing that happens",
                        "", Ui.TEXT_DIM, ItemStack.EMPTY));
                entries.add(new Entry("nav:SKILLS", "Skills",
                        ClientState.pufferfishSkills()
                                ? "Pufferfish Skills receives the points Rotas levels award"
                                : ClientState.categories().size() + " tree(s) for everyone, jobs and races",
                        ClientState.pufferfishSkills() ? "PUFFERFISH" : "ROTAS",
                        Ui.GOOD, ItemStack.EMPTY));
                entries.add(new Entry("nav:JOBS", "Jobs & Stats",
                        ClientState.jobs().isEmpty() ? "No jobs yet. Players pick one to open job skill trees."
                                : ClientState.jobs().size() + " job(s) players can choose",
                        ClientState.jobs().isEmpty() ? "TODO" : "", Ui.WARN, ItemStack.EMPTY));
            }
            case QUESTS -> {
                entries.add(new Entry("quest_catalog", "Open the Quest Catalog",
                        "Every quest as a card, grouped by story chain and category", "CARDS", Ui.GOOD, ItemStack.EMPTY));
                for (QuestDef quest : ClientState.quests().values()) {
                    entries.add(new Entry(quest.id(), quest.name(),
                            quest.rank().display() + "-rank  -  " + quest.category() + "  -  v" + quest.version(),
                            quest.published() ? "LIVE" : "DRAFT", quest.published() ? Ui.GOOD : Ui.WARN, quest.icon()));
                }
            }
            case NPCS -> {
                entries.add(new Entry("give_wand", "Get the NPC Wand",
                        "Right-click any mob with it to make an NPC or edit one. Shift + right-click tests talking.",
                        "EASIEST", Ui.GOOD, new ItemStack(net.schwarz.rotasutils.registry.RotasRegistry.NPC_WAND.get())));
                ClientState.npcs().values().forEach(npc ->
                    entries.add(new Entry(npc.id(), npc.name(),
                            (npc.title().isBlank() ? npc.role().display() : npc.title())
                                    + "  -  " + npc.questIds().size() + " quests",
                            npc.bound() ? "PLACED" : "NOT PLACED",
                            npc.bound() ? Ui.GOOD : Ui.WARN, npc.icon())));
            }
            case BOARDS -> ClientState.boards().values().forEach(board ->
                    entries.add(new Entry(board.id(), board.name(),
                            board.questIds().size() + " quests  -  " + board.style().display(),
                            board.visible() ? "LIVE" : "HIDDEN", board.visible() ? Ui.GOOD : Ui.TEXT_MUTED, board.icon())));
            case ZONES -> {
                entries.add(new Entry("give_zone_wand", "Get the Zone Wand",
                        "Right-click blocks to trace level areas. The wand is handed only to administrators.",
                        "ADMIN", Ui.ACCENT, new ItemStack(net.schwarz.rotasutils.registry.RotasRegistry.ZONE_WAND.get())));
                if (ClientState.zones().isEmpty()) {
                    entries.add(new Entry("", "No zones yet",
                            "A zone sets the level band for every mob inside it; outside them the spawn ramp applies.",
                            "", Ui.TEXT_MUTED, ItemStack.EMPTY));
                }
                ClientState.zones().values().forEach(zone -> entries.add(new Entry(zone.id(), zone.name(),
                        zone.dimension().replace("minecraft:", "") + "  -  " + zone.areaLabel()
                                + "  -  priority " + zone.priority(),
                        zone.levelLabel(), zone.enabled() ? Ui.GOOD : Ui.TEXT_MUTED, ItemStack.EMPTY)));
            }
            case ECONOMY -> {
                entries.add(new Entry("econ_worth", L.t("rotasutils.econ.worth_label"), L.t("rotasutils.econ.worth_help"),
                        L.t("rotasutils.econ.tag"), Ui.ACCENT, new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT)));
                entries.add(new Entry("econ_mines", L.t("rotasutils.econ.mines_label"), L.t("rotasutils.econ.mines_help"),
                        L.t("rotasutils.econ.tag"), Ui.ACCENT, new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE)));
                entries.add(new Entry("econ_settings", L.t("rotasutils.econ.settings_label"), L.t("rotasutils.econ.settings_help"),
                        L.t("rotasutils.econ.tag"), Ui.ACCENT, new ItemStack(net.minecraft.world.item.Items.COMPARATOR)));
            }
            case HOUSES -> {
                entries.add(new Entry("open_houses", L.t("rotasutils.house.admin.open_label"),
                        L.t("rotasutils.house.admin.open_help"),
                        L.t("rotasutils.house.admin.tag"), Ui.ACCENT, ItemStack.EMPTY));
                entries.add(new Entry("give_house_wand", L.t("rotasutils.house.admin.wand_label"),
                        L.t("rotasutils.house.admin.wand_help"),
                        L.t("rotasutils.house.admin.tag"), Ui.ACCENT,
                        new ItemStack(net.schwarz.rotasutils.registry.RotasRegistry.HOUSE_WAND.get())));
                net.schwarz.rotasutils.client.ClientHouseAdminState housing = ClientState.houseAdmin();
                if (housing == null) {
                    entries.add(new Entry("", L.t("rotasutils.house.admin.unavailable_label"),
                            L.t("rotasutils.house.admin.unavailable_help"),
                            "", Ui.TEXT_MUTED, ItemStack.EMPTY));
                } else if (housing.definitions().isEmpty()) {
                    entries.add(new Entry("", L.t("rotasutils.house.admin.no_houses_label"),
                            L.t("rotasutils.house.admin.no_houses"), "", Ui.TEXT_MUTED, ItemStack.EMPTY));
                } else {
                    for (HouseAdminPresentation.OverviewRow row : HouseAdminPresentation.overviewRows(housing, "")) {
                        entries.add(new Entry("house:" + row.id(), row.name(),
                                houseFormat("rotasutils.house.admin.row_identity", "{0}  -  {1}  -  tier {2}",
                                        row.dimension(), row.size(), row.tier()),
                                houseStatusText(row), houseStatusColor(row), ItemStack.EMPTY));
                    }
                }
            }
            case SKILLS -> {
                if (ClientState.pufferfishSkills()) {
                    entries.add(new Entry("open_puffish", "Open Pufferfish Skills",
                            "Switch to RotasUtils trees under Progression to use job and race trees.",
                            "ACTIVE", Ui.GOOD, ItemStack.EMPTY));
                    entries.add(new Entry("", "How the systems connect",
                            "Rotas XP -> Rotas Level -> skill-point milestones -> Pufferfish Skills.",
                            "", Ui.TEXT_MUTED, ItemStack.EMPTY));
                } else {
                    entries.add(new Entry("", "Who sees a tree",
                            "Category settings pick jobs and races. A tree with neither is open to everyone.",
                            "", Ui.TEXT_MUTED, ItemStack.EMPTY));
                    ClientState.categories().values().forEach(category -> {
                        String group = !category.jobs().isEmpty() ? "JOB" : !category.races().isEmpty() ? "RACE" : "GENERAL";
                        String who = !category.jobs().isEmpty()
                                ? String.join(", ", category.jobs().stream().map(ClientState::jobName).toList())
                                : !category.races().isEmpty()
                                ? String.join(", ", category.races().stream().map(ClientState::raceName).toList())
                                : "Everyone";
                        entries.add(new Entry(category.id(), category.name(),
                                who + "  -  " + category.nodes().size() + " skills  -  v" + category.version(),
                                group, group.equals("GENERAL") ? Ui.TEXT_DIM : Ui.ACCENT, category.icon()));
                    });
                }
            }
            case JOBS -> {
                entries.add(new Entry("open_jobs", "Open Job Manager",
                        "Create jobs here, then limit skill trees to them in Skills > Category settings.",
                        "", Ui.ACCENT, ItemStack.EMPTY));
                ClientState.jobs().values().forEach(job -> entries.add(new Entry("open_jobs", job.name(),
                        job.id() + "  -  from level " + job.minLevel(),
                        job.enabled() ? "OPEN" : "HIDDEN", job.enabled() ? Ui.GOOD : Ui.TEXT_MUTED, job.icon())));
                var statRules = ClientState.levelConfig().season().stats;
                entries.add(new Entry("open_stats", "Stat rules (STR / VIT / INT / AGI)",
                        statRules.startPoints + " start + " + statRules.pointsPerLevel + " per level, max "
                                + statRules.maxPerStat + " per stat. Edit in Season rules > Stats.",
                        "", Ui.ACCENT, ItemStack.EMPTY));
                for (var stat : net.schwarz.rotasutils.stat.CoreStat.ALL) {
                    entries.add(new Entry("open_stats", stat.displayName(), "Each point " + stat.describe(statRules, 1),
                            "", Ui.TEXT_MUTED, stat.icon()));
                }
            }
            case LEVELS -> {
                entries.add(new Entry("open_levels", "Open Progression Manager",
                        "Tune the level curve, rank gates, monster XP and milestone rewards.",
                        "CORE", Ui.ACCENT, ItemStack.EMPTY));
                entries.add(new Entry("open_season", "กฎซีซั่น",
                        "ทุกค่าของทุกระบบ: EXP ฟาร์ม ดรอป ตีบวก การ์ด เหตุการณ์ ภารกิจรายวัน ม้า (ดูที่ Game Systems ด้วย)",
                        ClientState.levelConfig().season().enabled ? "ON" : "OFF",
                        ClientState.levelConfig().season().enabled ? Ui.GOOD : Ui.TEXT_MUTED, ItemStack.EMPTY));
                entries.add(new Entry("", "XP model",
                        "Combat XP is rated from monster threat; quest rewards feed the same Rotas XP pool.",
                        "", Ui.TEXT_MUTED, ItemStack.EMPTY));
                entries.add(new Entry("", "Vanilla XP",
                        "Disabled as progression. Compatibility values mirror Rotas Level for other mods.",
                        "REPLACED", Ui.GOOD, ItemStack.EMPTY));
            }
            case PLAYERS -> entries.add(new Entry("open_players", "Open Player Records & Stats",
                    "Set a player's four core stats and manage level, XP, job, skills and quests.",
                    "ADMIN", Ui.TEXT_DIM, ItemStack.EMPTY));
            case SYSTEMS -> {
                com.google.gson.JsonObject season = com.google.gson.JsonParser
                        .parseString(ClientState.levelConfig().season().toJson()).getAsJsonObject();
                for (var system : net.schwarz.rotasutils.core.SeasonSettingsCatalog.SECTIONS) {
                    if (system.id().equals(net.schwarz.rotasutils.core.SeasonSettingsCatalog.OTHER)) continue;
                    Boolean on = systemEnabled(season, system.id());
                    entries.add(new Entry("season:" + system.id(), system.title(), system.blurb(),
                            on == null ? "" : on ? "ON" : "OFF", on == null ? Ui.TEXT_DIM : on ? Ui.GOOD : Ui.TEXT_MUTED,
                            ItemStack.EMPTY));
                }
            }
            case SETTINGS -> entries.add(new Entry("open_settings", "Open Server Settings",
                    "Quest limits, parties, anti-farm rules, saving and administration.",
                    "SERVER", Ui.TEXT_DIM, ItemStack.EMPTY));
            case VALIDATION -> {
                for (var issue : ClientState.issues()) {
                    entries.add(new Entry("", issue.message(),
                            issue.targetKind() + "  " + issue.targetId(),
                            issue.severity().name(),
                            switch (issue.severity()) {
                                case ERROR -> Ui.BAD;
                                case WARNING -> Ui.WARN;
                                case INFO -> Ui.TEXT_DIM;
                            },
                            ItemStack.EMPTY));
                }
                if (entries.isEmpty()) {
                    entries.add(new Entry("", "Every definition compiled on the last check.",
                            "Run the check after publishing or changing progression content.", "OK", Ui.GOOD, ItemStack.EMPTY));
                }
            }
            case AUDIT -> {
                for (String line : ClientState.audit()) {
                    entries.add(new Entry("", line, "", "", Ui.TEXT, ItemStack.EMPTY));
                }
                if (entries.isEmpty()) {
                    entries.add(new Entry("", "No administrative changes recorded yet.",
                            "Applying a draft, a rollback or a reload writes a line here.",
                            "", Ui.TEXT_DIM, ItemStack.EMPTY));
                }
            }
        }
        if (isSearchable(section) && !searchText.isBlank()) {
            String query = searchText.toLowerCase(Locale.ROOT);
            entries.removeIf(entry -> !entry.label().toLowerCase(Locale.ROOT).contains(query)
                    && !entry.id().toLowerCase(Locale.ROOT).contains(query)
                    && !entry.meta().toLowerCase(Locale.ROOT).contains(query));
        }
        list.setRows(entries.size(), this::renderRow, this::clickRow);
    }

    private void renderRow(GuiGraphics graphics, int index, int x, int y,
                           int rowWidth, int rowHeight, boolean hovered) {
        Entry entry = entries.get(index);
        boolean clickable = !entry.id().isEmpty();
        int cardHeight = rowHeight - 4;
        int usable = rowWidth - 2;
        int right = x + usable;

        if (clickable) {
            Ui.rowCard(graphics, x, y, usable, cardHeight, hovered, false);
            if (hovered) {
                Ui.roundedRect(graphics, x + 3, y + 8, 2, cardHeight - 16, 1, Ui.ACCENT);
            }
        } else {
            Ui.roundedSurface(graphics, x, y, usable, cardHeight, 7,
                    Ui.PANEL_ALT, Ui.BORDER_SUBTLE);
        }

        int textX = x + 12;
        if (!entry.icon().isEmpty()) {
            graphics.renderFakeItem(entry.icon(), x + 10, y + 9);
            textX = x + 36;
        }

        int tagWidth = entry.tag().isEmpty() ? 0 : font.width(entry.tag()) + 14;
        if (tagWidth > 0) {
            Ui.tag(graphics, right - tagWidth - (clickable ? 20 : 8), y + 11,
                    entry.tag(), entry.tagColor());
        }

        int trailing = tagWidth + (clickable ? 34 : 12);
        int labelWidth = Math.max(30, right - textX - trailing);
        Ui.label(graphics, Ui.truncate(entry.label(), labelWidth), textX, y + 8,
                clickable ? Ui.TEXT_BRIGHT : Ui.TEXT_DIM);
        if (!entry.meta().isEmpty()) {
            Ui.label(graphics, Ui.truncate(entry.meta(), labelWidth), textX, y + 23, Ui.TEXT_MUTED);
        }
        if (clickable) {
            Ui.labelRight(graphics, ">", right - 10, y + 15,
                    hovered ? Ui.ACCENT : Ui.TEXT_MUTED);
        }
    }

    private void clickRow(int index, int button) {
        String id = entries.get(index).id();
        if (id.isEmpty()) {
            return;
        }
        Sfx.select();
        if (id.startsWith("studio:")) { minecraft.setScreen(new AdminStudioScreen(this,id.substring(7))); return; }
        if (id.equals("quest_catalog")) { minecraft.setScreen(new QuestCatalogScreen(this)); return; }
        if (id.equals("files")) { minecraft.setScreen(new ConfigFilesScreen(this)); return; }
        if (id.equals("mob_setup")) { minecraft.setScreen(new MobSetupScreen(this)); return; }
        if (id.equals("mob_drops")) { minecraft.setScreen(new MobDropListScreen(this)); return; }
        if (id.equals("drop_tables")) { minecraft.setScreen(new DropTablesScreen(this)); return; }
        if (id.equals("world_events")) { send("worldevent_open"); return; }
        if (id.equals("titles_admin")) { minecraft.setScreen(new TitleManagerScreen(this)); return; }
        if (id.equals("mines_admin")) { send("mine_admin_open"); return; }
        if (id.equals("nemesis_admin")) { send("nemesis_admin_open"); return; }
        if (id.equals("block_log")) { send("block_log_open"); return; }
        if (id.equals("player_tools")) { minecraft.setScreen(new PlayerToolsScreen(this)); return; }
        if (id.equals("events")) { minecraft.setScreen(new EventCatalogScreen(this)); return; }
        if (id.equals("item_drops")) { minecraft.setScreen(new ItemDropScreen(this)); return; }

        if (id.equals("rpg_console")) {
            minecraft.setScreen(net.schwarz.rotasutils.client.screen.kernel.KernelHubScreen.open(
                    net.schwarz.rotasutils.client.screen.kernel.KernelHubScreen.Section.MONSTERS));
            return;
        }
        if (id.startsWith("nav:")) {
            section = Section.valueOf(id.substring(4));
            searchText = "";
            rebuild();
            return;
        }
        if (section == Section.DASHBOARD) {
            if (id.equals("action:new_quest")) {
                send("new_quest");
                Sfx.add();
            }
            return;
        }

        switch (section) {
            case QUESTS -> minecraft.setScreen(new QuestCreatorScreen(id, this));
            case BOARDS -> minecraft.setScreen(new BoardConfigScreen(id, this));
            case NPCS -> {
                if (id.equals("give_wand")) {
                    send("give_npc_wand");
                } else {
                    minecraft.setScreen(new NpcEditorScreen(id, this));
                }
            }
            case ZONES -> {
                if (id.equals("give_zone_wand")) {
                    send("give_zone_wand");
                } else {
                    CompoundTag payload = new CompoundTag();
                    payload.putString("zone", id);
                    send("open_zone", payload);
                }
            }
            case ECONOMY -> {
                if (id.equals("econ_worth")) {
                    send("worth_open");
                } else if (id.equals("econ_mines")) {
                    CompoundTag payload = new CompoundTag();
                    payload.putString("scope", "mines");
                    send("settings_open", payload);
                } else {
                    CompoundTag payload = new CompoundTag();
                    payload.putString("scope", "slots");
                    send("settings_open", payload);
                }
            }
            case HOUSES -> {
                if (id.equals("open_houses")) {
                    minecraft.setScreen(new HouseManagerScreen(this));
                } else if (id.equals("give_house_wand")) {
                    send("give_house_wand");
                    Sfx.add();
                } else if (id.startsWith("house:")) {
                    minecraft.setScreen(new HouseEditScreen(id.substring("house:".length()), this));
                }
            }
            case SKILLS -> {
                if (ClientState.pufferfishSkills()) {
                    send("open_skills");
                } else {
                    minecraft.setScreen(new SkillEditorScreen(id, this));
                }
            }
            case JOBS -> minecraft.setScreen(id.equals("open_stats")
                    ? new SeasonSettingsScreen(this, "stats") : new JobManagerScreen(this));
            case LEVELS -> minecraft.setScreen(id.equals("open_season")
                    ? new SeasonSettingsScreen(this) : new LevelManagerScreen(this));
            case PLAYERS -> minecraft.setScreen(new PlayerManagerScreen(this));
            case SETTINGS -> minecraft.setScreen(new ServerSettingsScreen(this));
            case SYSTEMS -> minecraft.setScreen(new SeasonSettingsScreen(this,
                    id.startsWith("season:") ? id.substring("season:".length()) : ""));
            case VALIDATION -> send("validate");
            default -> {
            }
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int margin = 10;
        int sidebarWidth = 190;
        int mainX = guiLeft + sidebarWidth + 22;
        int mainWidth = guiLeft + guiWidth - mainX - margin;
        int footerY = guiTop + guiHeight - 34;

        Ui.label(graphics, "SECTIONS", guiLeft + margin + 8, guiTop + 49, Ui.TEXT_MUTED);

        Ui.scaledLabel(graphics, section.label, mainX + 8, guiTop + 48, 1.25f, Ui.TEXT_BRIGHT);
        Ui.labelRight(graphics, Ui.truncate(section.help, Math.max(120, mainWidth / 2)),
                guiLeft + guiWidth - margin - 8, guiTop + 51, Ui.TEXT_MUTED);

        if (search != null) {
            Ui.searchFrame(graphics, mainX + 4, guiTop + 76, mainWidth - 8, 28, search.isFocused());
        }

        if (section == Section.BOARDS && entries.isEmpty() && searchText.isBlank()) {
            int centerX = mainX + mainWidth / 2;
            int centerY = guiTop + (footerY - guiTop) / 2 + 24;
            Ui.questBoardGlyph(graphics, centerX, centerY - 30, 0xFFB08D57);
            Ui.scaledCentered(graphics, "No quest boards yet", centerX, centerY + 8,
                    1.15f, Ui.TEXT_BRIGHT);
            Ui.labelCentered(graphics, "Use New Board, or place a billboard block and right-click it.",
                    centerX, centerY + 28, Ui.TEXT_DIM);
        } else if (section == Section.NPCS && entries.isEmpty() && searchText.isBlank()) {
            int centerX = mainX + mainWidth / 2;
            int centerY = guiTop + (footerY - guiTop) / 2 + 24;
            Ui.questBoardGlyph(graphics, centerX, centerY - 30, 0xFFB08D57);
            Ui.scaledCentered(graphics, "No NPCs yet", centerX, centerY + 8, 1.15f, Ui.TEXT_BRIGHT);
            Ui.labelCentered(graphics, "Get the NPC Wand, then right-click any mob to turn it into an NPC.",
                    centerX, centerY + 28, Ui.TEXT_DIM);
        } else if (entries.isEmpty()) {
            int centerX = mainX + mainWidth / 2;
            int centerY = guiTop + (footerY - guiTop) / 2 + 12;
            Ui.scaledCentered(graphics, searchText.isBlank() ? "This section is empty" : "No matches",
                    centerX, centerY, 1.15f, Ui.TEXT_BRIGHT);
            Ui.labelCentered(graphics, searchText.isBlank()
                            ? "This section has no entries yet."
                            : "No entry matches \"" + searchText + "\".",
                    centerX, centerY + 20, Ui.TEXT_DIM);
        }

        if (!ClientState.admin()) {
            Ui.labelCentered(graphics, "You are not recognised as an administrator.",
                    guiLeft + guiWidth / 2, footerY - 18, Ui.BAD);
        }
    }

    public static CompoundTag questPayload(String questId) {
        CompoundTag tag = new CompoundTag();
        tag.putString("quest", questId);
        return tag;
    }
    private static String studioKind(Section section) {
        return switch (section) { case MONSTERS -> "monster"; case ITEMS -> "item"; case BOSSES -> "boss"; case MERCHANTS -> "merchant"; default -> ""; };
    }

    private static int houseStatusColor(HouseAdminPresentation.OverviewRow row) {
        return switch (row.statusRole()) {
            case GOOD -> Ui.GOOD;
            case ACCENT -> Ui.ACCENT;
            case WARNING -> Ui.WARN;
            case OWNED -> Ui.ACCENT;
            case MUTED -> Ui.TEXT_MUTED;
        };
    }

    private static String houseStatusText(HouseAdminPresentation.OverviewRow row) {
        String tag = row.tag().toLowerCase(Locale.ROOT);
        return L.t("rotasutils.house.status." + tag);
    }

    private static String houseFormat(String key, String fallback, Object... args) {
        String value = L.t(key);
        if (value.equals(key)) {
            value = fallback;
        }
        for (int i = 0; i < args.length; i++) {
            value = value.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return value;
    }
}
