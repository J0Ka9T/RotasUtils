package net.schwarz.rotasutils.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.client.screen.admin.AdminMenuScreen;
import net.schwarz.rotasutils.client.screen.admin.BoardConfigScreen;
import net.schwarz.rotasutils.client.screen.admin.LevelManagerScreen;
import net.schwarz.rotasutils.client.screen.admin.QuestCreatorScreen;
import net.schwarz.rotasutils.client.screen.admin.SkillEditorScreen;
import net.schwarz.rotasutils.client.screen.admin.ValidationScreen;
import net.schwarz.rotasutils.client.screen.player.BoardBrowserScreen;
import net.schwarz.rotasutils.client.screen.player.MainMenuScreen;
import net.schwarz.rotasutils.client.screen.player.SkillTreeScreen;

/** Maps the server's screen ids onto client screens and routes world-selection results. */
@Environment(EnvType.CLIENT)
public final class ScreenRouter {
    /** The screen that started a world selection, reopened when the pick comes back. */
    private static RotasScreen pendingPickScreen;
    /**
     * A screen that arrived while the world was still loading. Vanilla's loading-terrain screen closes
     * itself once chunks arrive and would wipe anything opened on top of it, so it waits for {@link #tick}.
     */
    private static Screen deferred;

    private ScreenRouter() {
    }

    public static void rememberPending(RotasScreen screen) {
        pendingPickScreen = screen;
    }

    public static void open(String screenId, CompoundTag payload) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = switch (screenId) {
            case "npc_conversation" -> new net.schwarz.rotasutils.client.screen.player.NpcConversationScreen(payload);
            case "shop" -> new net.schwarz.rotasutils.client.screen.player.ShopScreen(payload);
            case "crafter" -> new net.schwarz.rotasutils.client.screen.player.CrafterScreen(payload);
            case "stable" -> new net.schwarz.rotasutils.client.screen.player.StableScreen(payload);
            case "npc_hub" -> new net.schwarz.rotasutils.client.screen.player.NpcHubScreen(payload);
            case "auction" -> new net.schwarz.rotasutils.client.screen.player.AuctionScreen(payload);
            case "main_menu" -> new MainMenuScreen(MainMenuScreen.Tab.OVERVIEW);
            case "journal" -> new MainMenuScreen(MainMenuScreen.Tab.JOURNAL);
            case "skill_tree" -> new SkillTreeScreen(null);
            case "stats" -> new net.schwarz.rotasutils.client.screen.player.StatsScreen(null);
            case "job" -> new net.schwarz.rotasutils.client.screen.player.JobScreen(null);
            case "party" -> new net.schwarz.rotasutils.client.screen.player.PartyScreen(null);
            case "waystone" -> new net.schwarz.rotasutils.client.screen.player.WaystoneScreen(payload);
            case "refine" -> new net.schwarz.rotasutils.client.screen.player.RefineScreen(payload);
            case "titles" -> new net.schwarz.rotasutils.client.screen.player.TitleScreen(null);
            case "sockets" -> new net.schwarz.rotasutils.client.screen.player.SocketScreen(payload);
            case "runes" -> new net.schwarz.rotasutils.client.screen.player.RuneScreen(payload);
            case "house" -> new net.schwarz.rotasutils.client.screen.player.HouseScreen(payload);
            case "house_create" -> new net.schwarz.rotasutils.client.screen.admin.HouseCreateScreen();
            case "house_quick" -> new net.schwarz.rotasutils.client.screen.admin.HouseQuickSettingsScreen(
                    Minecraft.getInstance().screen, payload.getString("house"), payload.getString("name"),
                    payload.getCompound("settings"));
            case "journey" -> new net.schwarz.rotasutils.client.screen.player.JourneyScreen(payload, null);
            case "job_select" -> new net.schwarz.rotasutils.client.screen.player.JobScreen(null, true);
            case "bestiary" -> new net.schwarz.rotasutils.client.screen.player.BestiaryScreen(payload);
            case "salvage" -> new net.schwarz.rotasutils.client.screen.player.SalvageScreen(payload);
            case "world_events" -> new net.schwarz.rotasutils.client.screen.admin.WorldEventAdminScreen(payload);
            case "mine_admin" -> new net.schwarz.rotasutils.client.screen.admin.MineAdminScreen(payload);
            case "nemesis_admin" -> new net.schwarz.rotasutils.client.screen.admin.NemesisAdminScreen(payload);
            case "mob_drops" -> new net.schwarz.rotasutils.client.screen.admin.MobDropScreen(payload);
            case "board_browser" -> new BoardBrowserScreen(payload.getString("board_id"),
                    net.schwarz.rotasutils.util.Nbt.loadStrings(payload, "visible"), payload.getString("npc"));
            case "admin_menu" -> new AdminMenuScreen(payload.getString("section"));
            case "kernel_hub" -> new net.schwarz.rotasutils.client.screen.kernel.KernelHubScreen(
                    net.schwarz.rotasutils.client.screen.kernel.KernelHubScreen.Section.byName(payload.getString("section")), payload.getString("selected"), null);
            case "board_config" -> new BoardConfigScreen(payload.getString("board_id"), null);
            case "npc_dialogue" -> new net.schwarz.rotasutils.client.screen.player.NpcDialogueScreen(
                    payload.getString("npc_id"), payload.getCompound("npc"), payload.getString("state"),
                    net.schwarz.rotasutils.util.Nbt.loadStrings(payload, "offers"));
            case "npc_config" -> new net.schwarz.rotasutils.client.screen.admin.NpcEditorScreen(
                    payload.getString("npc_id"), null);
            case "quest_creator" -> new QuestCreatorScreen(payload.getString("quest_id"), null);
            case "skill_editor" -> new SkillEditorScreen(payload.getString("category_id"), null);
            case "level_manager" -> new LevelManagerScreen(null);
            case "zone_manager" -> new net.schwarz.rotasutils.client.screen.admin.ZoneManagerScreen(null);
            case "zone_edit" -> new net.schwarz.rotasutils.client.screen.admin.ZoneEditScreen(
                    payload.getString("zone"), new net.schwarz.rotasutils.client.screen.admin.ZoneManagerScreen(null));
            case "validation" -> new ValidationScreen(minecraft.screen);
            default -> null;
        };
        if (screen != null && loading(minecraft)) {
            deferred = screen;
        } else if (screen != null) {
            minecraft.setScreen(screen);
        }
    }

    private static boolean loading(Minecraft minecraft) {
        return minecraft.player == null || minecraft.level == null
                || minecraft.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen;
    }

    public static void tick(Minecraft minecraft) {
        if (deferred != null && !loading(minecraft)) {
            Screen screen = deferred;
            deferred = null;
            minecraft.setScreen(screen);
        }
    }

    /** Drops a screen queued for a world the player has since left. */
    public static void clearDeferred() {
        deferred = null;
    }

    public static void refreshCurrent() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof RotasScreen rotasScreen) {
            rotasScreen.onDataRefreshed();
        }
    }

    /** Reopens the screen that asked for a world selection and hands it the value. */
    public static void deliverPick(String screenKey, String fieldKey, String value) {
        RotasScreen screen = pendingPickScreen;
        pendingPickScreen = null;
        if (screen == null || !screen.screenKey().equals(screenKey)) {
            return;
        }
        Minecraft.getInstance().setScreen(screen);
        screen.onPick(fieldKey, value);
    }
}
