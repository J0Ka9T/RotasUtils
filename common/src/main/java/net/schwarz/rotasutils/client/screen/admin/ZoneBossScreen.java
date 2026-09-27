package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.client.ClientKernelState;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.MobSetupForm;
import net.schwarz.rotasutils.core.ZoneFeatures;
import net.schwarz.rotasutils.core.ZoneSpawnPoint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Miniboss and boss spawn points of one zone. Each point keeps one mob of a Mob Setup alive: it wakes when
 * a player comes within its radius and returns after its respawn time. Edits go to the zone editor's unsaved
 * copy and are saved with the zone.
 */
@Environment(EnvType.CLIENT)
public class ZoneBossScreen extends RotasScreen {
    private static final int ROW = 30;
    private final ZoneEditScreen editor;
    private int selected = -1;
    private String pendingProfile;
    private String typedRespawn;
    private String typedRadius;
    private EditBox respawnBox;
    private EditBox radiusBox;

    public ZoneBossScreen(ZoneEditScreen editor) {
        super("Bosses and minibosses", editor);
        this.editor = editor;
    }

    @Override
    protected int maxGuiWidth() {
        return 640;
    }

    @Override
    protected int maxGuiHeight() {
        return 400;
    }

    private List<ZoneSpawnPoint> points() {
        return editor.features().spawnPoints();
    }

    private int listW() {
        return (guiWidth - Ui.PAD * 3) * 45 / 100;
    }

    private int detailX() {
        return guiLeft + Ui.PAD * 2 + listW();
    }

    private int detailW() {
        return guiLeft + guiWidth - Ui.PAD - detailX();
    }

    private int top() {
        return guiTop + 56;
    }

    @Override
    protected void buildContent() {
        if (selected >= points().size()) {
            selected = points().size() - 1;
        }
        int footer = guiTop + guiHeight - 28;
        int half = (listW() - Ui.GAP) / 2;
        addRenderableWidget(Ui.primaryButton(Ui.text("+ At my position"), button -> {
            BlockPos here = minecraft.player.blockPosition();
            addPoint(here.getX(), here.getY(), here.getZ());
        }).bounds(guiLeft + Ui.PAD, footer - 26, half, 20).build());
        addRenderableWidget(Ui.button(Ui.text("+ At a block..."), button -> {
            if (applyNumbers()) {
                requestPick("POSITION", "point_new");
            }
        })
                .bounds(guiLeft + Ui.PAD + half + Ui.GAP, footer - 26, half, 20).build());

        ScrollPanel list = new ScrollPanel(guiLeft + Ui.PAD, top(), listW(), Math.max(ROW, footer - 32 - top()), ROW)
                .rowHitInsets(0, 2);
        registerPanel(list);
        list.setRows(points().size(), this::renderRow, (index, button) -> {
            if (!applyNumbers()) {
                return;
            }
            selected = index;
            typedRespawn = null;
            typedRadius = null;
            Sfx.select();
            rebuild(false);
        });

        respawnBox = null;
        radiusBox = null;
        if (selected >= 0) {
            buildDetail(points().get(selected));
        }
        addBackButton();
        addRenderableWidget(Ui.primaryButton(Ui.text("Done"), button -> goBack())
                .bounds(guiLeft + guiWidth - Ui.PAD - 110, footer, 110, 22).build());
    }

    private void buildDetail(ZoneSpawnPoint point) {
        int x = detailX();
        int w = detailW();
        int y = top();
        int half = (w - Ui.GAP) / 2;
        addRenderableWidget(Ui.button(Ui.text(Ui.truncate("Mob: " + setupName(point.profile()), w - 10)), button -> {
            if (applyNumbers()) {
                minecraft.setScreen(new ZoneSetupChoiceScreen(this, editor.zoneId(), id -> pendingProfile = id));
            }
        })
                .bounds(x, y + 14, w, 20).build());
        addRenderableWidget(Ui.button(Ui.text(point.kind() == ZoneSpawnPoint.Kind.BOSS
                ? "Kind: Boss (boss bar)" : "Kind: Miniboss"), button -> {
            ZoneSpawnPoint.Kind next = point.kind() == ZoneSpawnPoint.Kind.BOSS ? ZoneSpawnPoint.Kind.MINIBOSS : ZoneSpawnPoint.Kind.BOSS;
            if (applyNumbers()) {
                replace(copy(points().get(selected), null, null, null, next, -1, -1));
                rebuild(false);
            }
        }).bounds(x, y + 38, w, 20).build());
        respawnBox = new EditBox(font, x, y + 76, half, 18, Ui.text(""));
        respawnBox.setMaxLength(6);
        respawnBox.setValue(typedRespawn != null ? typedRespawn : Integer.toString(point.respawnSeconds()));
        addRenderableWidget(respawnBox);
        radiusBox = new EditBox(font, x + half + Ui.GAP, y + 76, half, 18, Ui.text(""));
        radiusBox.setMaxLength(3);
        radiusBox.setValue(typedRadius != null ? typedRadius : Integer.toString(point.activationRadius()));
        addRenderableWidget(radiusBox);
        addRenderableWidget(Ui.button(Ui.text("Move to my position"), button -> {
            BlockPos here = minecraft.player.blockPosition();
            if (applyNumbers()) {
                replace(copy(points().get(selected), here.getX(), here.getY(), here.getZ(), null, -1, -1));
                rebuild(false);
            }
        }).bounds(x, y + 104, half, 20).build());
        addRenderableWidget(Ui.button(Ui.text("Move to a block..."), button -> {
            if (applyNumbers()) {
                requestPick("POSITION", "point_move");
            }
        }).bounds(x + half + Ui.GAP, y + 104, half, 20).build());
        addRenderableWidget(Ui.button(Ui.text("Respawn now"), button -> {
            CompoundTag payload = new CompoundTag();
            payload.putString("zone", editor.zoneId());
            payload.putString("point", point.id());
            send("zone_point_respawn", payload);
            Sfx.commit();
        }).bounds(x, y + 128, half, 20).build());
        addRenderableWidget(Ui.dangerButton(Ui.text("Remove point"), button -> {
            List<ZoneSpawnPoint> next = new ArrayList<>(points());
            next.remove(selected);
            editor.setFeatures(editor.features().withSpawnPoints(next));
            selected = -1;
            Sfx.remove();
            rebuild(false);
        }).bounds(x + half + Ui.GAP, y + 128, half, 20).build());
    }

    private void rebuild(boolean keepTyped) {
        if (keepTyped) {
            capture();
        }
        clearWidgets();
        clearPanels();
        buildContent();
    }

    private void capture() {
        if (respawnBox != null) {
            typedRespawn = respawnBox.getValue();
        }
        if (radiusBox != null) {
            typedRadius = radiusBox.getValue();
        }
    }

    /** Writes the typed respawn time and radius to the selected point; false (with feedback) when invalid. */
    private boolean applyNumbers() {
        if (selected < 0 || respawnBox == null || radiusBox == null) {
            return true;
        }
        capture();
        try {
            int respawn = Integer.parseInt(typedRespawn.trim());
            int radius = Integer.parseInt(typedRadius.trim());
            replace(copy(points().get(selected), null, null, null, null, respawn, radius));
            return true;
        } catch (NumberFormatException malformed) {
            ClientState.feedback(false, "Respawn time and wake radius must be whole numbers.");
        } catch (IllegalArgumentException invalid) {
            ClientState.feedback(false, invalid.getMessage());
        }
        return false;
    }

    private void addPoint(int x, int y, int z) {
        if (!applyNumbers()) {
            return;
        }
        String profile = defaultProfile();
        if (profile == null) {
            ClientState.feedback(false, "Make a Mob Setup first (zone editor > Mobs > + New mob for this zone).");
            return;
        }
        if (points().size() >= ZoneFeatures.MAX_SPAWN_POINTS) {
            ClientState.feedback(false, "A zone holds at most " + ZoneFeatures.MAX_SPAWN_POINTS + " spawn points.");
            return;
        }
        Set<String> used = new HashSet<>();
        points().forEach(point -> used.add(point.id()));
        int number = 1;
        while (used.contains("point_" + number)) {
            number++;
        }
        try {
            List<ZoneSpawnPoint> next = new ArrayList<>(points());
            next.add(new ZoneSpawnPoint("point_" + number, x, y, z, profile, ZoneSpawnPoint.Kind.BOSS, 300, 32));
            editor.setFeatures(editor.features().withSpawnPoints(next));
            selected = next.size() - 1;
            typedRespawn = null;
            typedRadius = null;
            Sfx.add();
            rebuild(false);
        } catch (IllegalArgumentException invalid) {
            ClientState.feedback(false, invalid.getMessage());
        }
    }

    /** A setup scoped to this zone, else any setup, else null. */
    private String defaultProfile() {
        String any = null;
        for (ClientKernelState.MonsterEntry entry : ClientKernelState.monsters()) {
            if (any == null) {
                any = entry.id();
            }
            try {
                if (MobSetupForm.parse(entry.body()).scopeZones().contains(editor.zoneId())) {
                    return entry.id();
                }
            } catch (RuntimeException ignored) {
                // Unreadable setups can still be chosen by hand.
            }
        }
        return any;
    }

    private void replace(ZoneSpawnPoint next) {
        List<ZoneSpawnPoint> list = new ArrayList<>(points());
        list.set(selected, next);
        editor.setFeatures(editor.features().withSpawnPoints(list));
    }

    private static ZoneSpawnPoint copy(ZoneSpawnPoint point, Integer x, Integer y, Integer z, ZoneSpawnPoint.Kind kind,
                                       int respawn, int radius) {
        return copy(point, x, y, z, kind, respawn, radius, null);
    }

    private static ZoneSpawnPoint copy(ZoneSpawnPoint point, Integer x, Integer y, Integer z, ZoneSpawnPoint.Kind kind,
                                       int respawn, int radius, String profile) {
        return new ZoneSpawnPoint(point.id(), x == null ? point.x() : x, y == null ? point.y() : y, z == null ? point.z() : z,
                profile == null ? point.profile() : profile, kind == null ? point.kind() : kind,
                respawn < 0 ? point.respawnSeconds() : respawn, radius < 0 ? point.activationRadius() : radius);
    }

    @Override
    public void tick() {
        super.tick();
        if (pendingProfile != null && selected >= 0) {
            String profile = pendingProfile;
            pendingProfile = null;
            capture();
            replace(copy(points().get(selected), null, null, null, null, -1, -1, profile));
            rebuild(false);
        }
    }

    @Override
    public void onPick(String fieldKey, String value) {
        String[] parts = value.trim().split("\\s+");
        if (parts.length < 3) {
            return;
        }
        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            if (fieldKey.equals("point_new")) {
                addPoint(x, y, z);
            } else if (fieldKey.equals("point_move") && selected >= 0) {
                replace(copy(points().get(selected), x, y, z, null, -1, -1));
                rebuild(true);
            }
        } catch (NumberFormatException ignored) {
            // A malformed pick changes nothing.
        }
    }

    @Override
    protected void goBack() {
        if (applyNumbers()) {
            super.goBack();
        }
    }

    private static String setupName(String id) {
        for (ClientKernelState.MonsterEntry entry : ClientKernelState.monsters()) {
            if (entry.id().equals(id)) {
                return entry.name() == null || entry.name().isBlank() ? entry.id() : entry.name();
            }
        }
        return id + " (missing)";
    }

    private void renderRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        ZoneSpawnPoint point = points().get(index);
        int usable = rowWidth - 6;
        Ui.rowCard(graphics, x, y, usable, rowHeight - 2, hovered, index == selected);
        Ui.label(graphics, Ui.truncate(setupName(point.profile()), usable - 70), x + 8, y + 4, Ui.TEXT_BRIGHT);
        Ui.labelRight(graphics, point.kind() == ZoneSpawnPoint.Kind.BOSS ? "BOSS" : "MINIBOSS", x + usable - 8, y + 4,
                point.kind() == ZoneSpawnPoint.Kind.BOSS ? Ui.BAD : Ui.WARN);
        Ui.label(graphics, Ui.truncate(point.id() + "  at " + point.x() + " " + point.y() + " " + point.z()
                + "  every " + point.respawnSeconds() + "s", usable - 16), x + 8, y + 16, Ui.TEXT_MUTED);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.wrapped(graphics, "Each point keeps one mob alive. It wakes when a player comes within its radius, is "
                + "pulled back if lured out of the zone, heals after 30 s with nobody inside, and returns after its "
                + "respawn time. Save the zone to apply.", guiLeft + Ui.PAD, guiTop + 26, guiWidth - Ui.PAD * 2, Ui.TEXT_MUTED);
        if (points().isEmpty()) {
            Ui.wrapped(graphics, "No spawn points yet.", guiLeft + Ui.PAD + 6, top() + 6, listW() - 12, Ui.TEXT_DIM);
        }
        if (selected < 0) {
            Ui.wrapped(graphics, points().isEmpty() ? "Add a point where the boss should wait."
                    : "Click a point to edit it.", detailX(), top() + 14, detailW(), Ui.TEXT_DIM);
            return;
        }
        int half = (detailW() - Ui.GAP) / 2;
        Ui.label(graphics, "Point " + points().get(selected).id(), detailX(), top(), Ui.TEXT_DIM);
        Ui.label(graphics, "Respawn after (s)", detailX(), top() + 64, Ui.TEXT_DIM);
        Ui.label(graphics, "Wake radius (blocks)", detailX() + half + Ui.GAP, top() + 64, Ui.TEXT_DIM);
        Ui.wrapped(graphics, "Respawn 10-86400 s, radius 16-128 blocks. 'Respawn now' works once the zone is saved "
                + "and the old mob is dead.", detailX(), top() + 154, detailW(), Ui.TEXT_MUTED);
    }
}
