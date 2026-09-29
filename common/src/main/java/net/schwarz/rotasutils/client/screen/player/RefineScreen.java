package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.screen.AnimeUi;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.ForgeTiming;
import net.schwarz.rotasutils.registry.RotasRegistry;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The Refine Forge (ตีบวก).
 *
 * <p>The item stands on a lit stage with what the attempt does to it, its odds and its price. The odds
 * are the server's table as ever; on top of them the player can heat the forge and play a short timing
 * game (three strikes, wide windows, misses free) that adds up to {@link ForgeTiming#MAX_BONUS} to the
 * chance, or skip it and hammer straight away. The marker is a pure function of the server's clock, so
 * the screen only draws it and asks for a strike; the server grades and reopens the bench on the result.</p>
 */
@Environment(EnvType.CLIENT)
public class RefineScreen extends RotasScreen {
    private static final String[] GRADE_NAMES = {"PERFECT!", "GOOD", "MISS"};
    private static final int[] GRADE_COLORS = {AnimeUi.GOLD, AnimeUi.CYAN, AnimeUi.MUTED};

    private final CompoundTag state;
    private final long openedAt = Util.getMillis();
    private boolean enriched;
    private boolean protection;
    private boolean blessing;
    private boolean certificate;
    private long lastStrike;

    public RefineScreen(CompoundTag payload) {
        super(L.t("rotasutils.refine.title"), null);
        this.state = payload == null ? new CompoundTag() : payload;
        if (session() != null) {
            CompoundTag s = session();
            enriched = s.getBoolean("enriched");
            protection = s.getBoolean("protection");
            blessing = s.getBoolean("blessing");
            certificate = s.getBoolean("certificate");
        }
    }

    // State ------------------------------------------------------------------------------------------

    private CompoundTag session() {
        return state.contains("session", Tag.TAG_COMPOUND) ? state.getCompound("session") : null;
    }

    private CompoundTag finished() {
        return state.contains("finished", Tag.TAG_COMPOUND) ? state.getCompound("finished") : null;
    }

    private CompoundTag quote() {
        return state.getCompound(enriched ? "enriched" : "plain");
    }

    private int have(String key) {
        return state.getInt("have_" + key);
    }

    private boolean armour() {
        return "armor".equals(quote().getString("category"));
    }

    private List<Integer> grades(CompoundTag from) {
        List<Integer> out = new ArrayList<>();
        if (from != null) {
            ListTag list = from.getList("grades", Tag.TAG_INT);
            for (int i = 0; i < list.size(); i++) {
                out.add(list.getInt(i));
            }
        }
        return out;
    }

    /** Odds shown: the table, the blessing scroll, and what the strikes have earned so far. */
    private double baseChance() {
        if (certificate) {
            return 1.0;
        }
        double chance = quote().getDouble("chance");
        return chance >= 1.0 ? chance : Math.min(1.0, chance + (blessing ? state.getDouble("blessing_bonus") : 0));
    }

    private double bonusChance() {
        CompoundTag s = session();
        if (s == null || baseChance() >= 1.0) {
            return 0;
        }
        return Math.min(1.0 - baseChance(), s.getDouble("bonus"));
    }

    /** The marker now, from the server's clock as of the payload plus the time this screen has been open. */
    private double markerNow() {
        CompoundTag s = session();
        double ticks = (s.getLong("now") - s.getLong("start")) + (Util.getMillis() - openedAt) / 50.0;
        return ForgeTiming.marker(ticks);
    }

    private boolean sessionLive() {
        CompoundTag s = session();
        if (s == null) {
            return false;
        }
        double ticks = (s.getLong("now") - s.getLong("start")) + (Util.getMillis() - openedAt) / 50.0;
        return ticks < ForgeTiming.SESSION_TICKS;
    }

    // Layout -----------------------------------------------------------------------------------------

    private int stageW() {
        return 176;
    }

    /** The bottom band is taller while the timing game is on, to hold the bar, the pips and the buttons. */
    private int bottomH() {
        return sessionLive() ? 100 : 68;
    }

    private int topH() {
        return guiHeight - bottomH() - 12;
    }

    private int barY() {
        return guiTop + topH() + 24;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 520);
        guiHeight = Ui.fill(height, 320);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        int by = guiTop + guiHeight - 40;
        int right = guiLeft + guiWidth - 8;

        addRenderableWidget(new AnimeUi.Btn(guiLeft + 8, by, 84, 30, L.t("rotasutils.refine.close"), AnimeUi.PANEL_LIGHT, 1,
                button -> onClose()));

        if (sessionLive()) {
            boolean more = grades(session()).size() < ForgeTiming.STRIKES;
            var strike = new AnimeUi.Btn(right - 170, by, 170, 30, L.t("rotasutils.refine.strike") + " [SPACE]", AnimeUi.GOLD, 2,
                    button -> strike());
            strike.active = more;
            addRenderableWidget(strike);
            return;
        }
        boolean possible = quote().getBoolean("possible") && affordable();
        var heat = new AnimeUi.Btn(right - 268, by, 176, 30, L.t("rotasutils.refine.heat"), AnimeUi.PINK, 2, button -> {
            send("forge_begin", options());
        });
        heat.active = possible;
        addRenderableWidget(heat);
        var quick = new AnimeUi.Btn(right - 84, by, 84, 30, L.t("rotasutils.refine.quick"), AnimeUi.CYAN, 1, button -> {
            send("refine_attempt", options());
        });
        quick.active = possible;
        addRenderableWidget(quick);
    }

    private boolean affordable() {
        String key = armour() ? (enriched ? "enriched_elunium" : "elunium") : (enriched ? "enriched_oridecon" : "oridecon");
        return have(key) > 0 && state.getLong("gold") >= quote().getLong("cost");
    }

    private CompoundTag options() {
        CompoundTag payload = new CompoundTag();
        payload.putBoolean("enriched", enriched);
        payload.putBoolean("protection", protection);
        payload.putBoolean("blessing", blessing);
        payload.putBoolean("certificate", certificate);
        return payload;
    }

    private void strike() {
        long now = Util.getMillis();
        if (now - lastStrike < 220 || !sessionLive() || grades(session()).size() >= ForgeTiming.STRIKES) {
            return;
        }
        lastStrike = now;
        send("forge_strike");
    }

    // Material tiles ----------------------------------------------------------------------------------

    private record Tile(String key, String label, ItemStack icon, int have) {
    }

    private List<Tile> tiles() {
        String enrichedKey = armour() ? "enriched_elunium" : "enriched_oridecon";
        var enrichedItem = armour() ? RotasRegistry.ENRICHED_ELUNIUM.get() : RotasRegistry.ENRICHED_ORIDECON.get();
        return List.of(
                new Tile("enriched", L.t("rotasutils.refine.use_enriched"), new ItemStack(enrichedItem), have(enrichedKey)),
                new Tile("protection", L.t("rotasutils.refine.use_protection"), new ItemStack(RotasRegistry.PROTECTION_SCROLL.get()), have("protection_scroll")),
                new Tile("blessing", L.t("rotasutils.refine.use_blessing"), new ItemStack(RotasRegistry.BLESSING_SCROLL.get()), have("blessing_scroll")),
                new Tile("certificate", L.t("rotasutils.refine.use_certificate"), new ItemStack(RotasRegistry.CERTIFICATE_SCROLL.get()), have("certificate_scroll")));
    }

    private boolean on(String key) {
        return switch (key) {
            case "enriched" -> enriched;
            case "protection" -> protection;
            case "blessing" -> blessing;
            default -> certificate;
        };
    }

    private int tileX(int i) {
        return guiLeft + stageW() + 16 + i * 68;
    }

    private int tileY() {
        return guiTop + topH() - 66;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!sessionLive()) {
            List<Tile> tiles = tiles();
            for (int i = 0; i < tiles.size(); i++) {
                Tile tile = tiles.get(i);
                if (tile.have() > 0 && Ui.inside((int) mouseX, (int) mouseY, tileX(i), tileY(), 62, 54)) {
                    switch (tile.key()) {
                        case "enriched" -> enriched = !enriched;
                        case "protection" -> protection = !protection;
                        case "blessing" -> blessing = !blessing;
                        default -> certificate = !certificate;
                    }
                    rebuildWidgets();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) && sessionLive()) {
            strike();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // Drawing ----------------------------------------------------------------------------------------

    @Override
    protected void renderBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, 0xE60A0818, 0xF01A1038);
        AnimeUi.glow(graphics, width / 5, height / 4, height, AnimeUi.alpha(AnimeUi.PINK, 0.16f));
        AnimeUi.glow(graphics, width * 4 / 5, height * 3 / 4, height, AnimeUi.alpha(AnimeUi.CYAN, 0.14f));
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        int right = guiLeft + guiWidth;
        AnimeUi.panel(graphics, guiLeft, guiTop, stageW(), topH(), AnimeUi.GOLD);
        AnimeUi.panel(graphics, guiLeft + stageW() + 12, guiTop, guiWidth - stageW() - 12, topH(), AnimeUi.PINK);
        AnimeUi.panel(graphics, guiLeft, guiTop + topH() + 12, guiWidth, bottomH(), AnimeUi.CYAN);
        renderFeedback(graphics, right - Math.max(40, feedbackWidth()), guiTop - 22,
                0xFFE8FFE0, 0xFFFFD8D0, Ui.GOOD, Ui.BAD);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        CompoundTag quote = quote();
        ItemStack stack = ItemStack.of(state.getCompound("item"));
        int stageCx = guiLeft + stageW() / 2;
        int stageCy = guiTop + topH() / 2 + 6;

        boolean possible = quote.getBoolean("possible");
        double chance = Math.min(1.0, baseChance() + bonusChance());
        int mood = chance >= 0.75 ? AnimeUi.LIME : chance >= 0.4 ? AnimeUi.GOLD : AnimeUi.PINK;
        AnimeUi.burst(g, stageCx, stageCy + 6, 66, possible ? mood : AnimeUi.MUTED, 14f);

        AnimeUi.outlinedCentered(g, L.t("rotasutils.refine.title"), stageCx, guiTop + 12, 2, AnimeUi.WHITE);
        if (!possible) {
            String message = quote.getString("message").isBlank() ? L.t("rotasutils.refine.nothing_held") : quote.getString("message");
            int y = guiTop + 40;
            for (String line : Ui.wrap(message, guiWidth - stageW() - 56)) {
                Ui.label(g, line, guiLeft + stageW() + 28, y, AnimeUi.RED);
                y += 12;
            }
            AnimeUi.bigItem(g, stack, stageCx, stageCy, 5f, true);
            renderBar(g);
            return;
        }
        AnimeUi.bigItem(g, stack, stageCx, stageCy, 5f, true);
        String name = Ui.truncate(stack.getHoverName().getString(), stageW() - 16);
        AnimeUi.outlinedCentered(g, name, stageCx, guiTop + topH() - 22, 1, AnimeUi.WHITE);

        // Right: the step, the odds, the price, the risk.
        int rx = guiLeft + stageW() + 30;
        int rw = guiWidth - stageW() - 12 - 36;
        AnimeUi.outlined(g, "+" + quote.getInt("level") + " > +" + quote.getInt("target"), rx, guiTop + 14, 3, AnimeUi.GOLD);
        Ui.label(g, L.t("rotasutils.refine.chance"), rx, guiTop + 46, AnimeUi.MUTED);
        int gy = guiTop + 58;
        AnimeUi.gauge(g, rx, gy, rw - 52, 14, (float) baseChance(), mood);
        if (bonusChance() > 0) {
            int from = (int) ((rw - 52) * baseChance());
            int to = (int) ((rw - 52) * (baseChance() + bonusChance()));
            AnimeUi.band(g, rx + from - 3, gy, to - from + 3, 14, AnimeUi.CYAN);
        }
        int percent = (int) Math.round(chance * 100);
        AnimeUi.outlined(g, percent + "%", rx + rw - 46, gy - 4, 2, mood);
        if (bonusChance() > 0) {
            Ui.label(g, "+" + (int) Math.round(bonusChance() * 100) + "% " + L.t("rotasutils.refine.forge_bonus"), rx, gy + 16, AnimeUi.CYAN);
        }
        long cost = quote.getLong("cost");
        boolean rich = state.getLong("gold") >= cost;
        String using = armour() ? (enriched ? "enriched_elunium" : "elunium") : (enriched ? "enriched_oridecon" : "oridecon");
        Ui.label(g, L.t("rotasutils.refine.cost"), rx, guiTop + 92, AnimeUi.MUTED);
        Ui.label(g, cost + "  /  " + state.getLong("gold"), rx + 44, guiTop + 92, rich ? AnimeUi.WHITE : AnimeUi.RED);
        Ui.label(g, L.t("rotasutils.refine.material"), rx, guiTop + 106, AnimeUi.MUTED);
        Ui.label(g, L.t("item.rotasutils." + using) + "  x1  (" + have(using) + ")", rx + 44, guiTop + 106,
                have(using) > 0 ? AnimeUi.WHITE : AnimeUi.RED);
        String risk = protection ? L.t("rotasutils.refine.risk_protected")
                : L.t("rotasutils.refine.risk." + state.getString("on_fail").toLowerCase(java.util.Locale.ROOT));
        int ry = guiTop + 124;
        for (String line : Ui.wrap(risk, rw)) {
            Ui.label(g, line, rx, ry, protection ? AnimeUi.LIME : AnimeUi.RED);
            ry += 11;
        }
        renderTiles(g, mouseX, mouseY);
        renderBar(g);
        renderBanner(g, stageCx, stageCy);
    }

    private void renderTiles(GuiGraphics g, int mouseX, int mouseY) {
        List<Tile> tiles = tiles();
        boolean locked = sessionLive();
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            int x = tileX(i), y = tileY();
            boolean owned = tile.have() > 0;
            boolean hot = owned && !locked && Ui.inside(mouseX, mouseY, x, y, 62, 54);
            boolean active = on(tile.key());
            AnimeUi.card(g, x, y, 62, 54, active ? AnimeUi.LIME : hot ? AnimeUi.CYAN : AnimeUi.PANEL_LIGHT, active || hot);
            g.pose().pushPose();
            g.pose().translate(x + 31, y + 20, 100);
            g.pose().scale(1.5f, 1.5f, 1f);
            g.renderItem(tile.icon(), -8, -8);
            g.pose().popPose();
            Ui.labelCentered(g, Ui.truncate(tile.label(), 56), x + 31, y + 35, owned ? AnimeUi.WHITE : AnimeUi.MUTED);
            Ui.labelCentered(g, "x" + tile.have(), x + 31, y + 44, owned ? (active ? AnimeUi.LIME : AnimeUi.GOLD) : AnimeUi.MUTED);
            if (!owned) {
                AnimeUi.band(g, x, y, 62, 54, 0x99000000);
            }
        }
    }

    /** The bottom band: the timing bar while heating, the strike pips, or the hint. */
    private void renderBar(GuiGraphics g) {
        int bx = guiLeft + 20;
        int bw = guiWidth - 40;
        int by = barY();
        if (!sessionLive()) {
            int hy = guiTop + topH() + 24;
            for (String line : Ui.wrap(L.t("rotasutils.refine.hint"), guiWidth - 40)) {
                Ui.label(g, line, bx, hy, AnimeUi.MUTED);
                hy += 11;
            }
            return;
        }
        CompoundTag s = session();
        List<Integer> done = grades(s);
        int index = Math.min(done.size(), ForgeTiming.STRIKES - 1);
        double center = s.getList("centers", Tag.TAG_DOUBLE).getDouble(index);
        boolean over = done.size() >= ForgeTiming.STRIKES;

        AnimeUi.gauge(g, bx, by, bw, 26, 0f, AnimeUi.WHITE);
        int gx0 = bx + (int) ((center - ForgeTiming.GOOD) * bw), gx1 = bx + (int) ((center + ForgeTiming.GOOD) * bw);
        int px0 = bx + (int) ((center - ForgeTiming.PERFECT) * bw), px1 = bx + (int) ((center + ForgeTiming.PERFECT) * bw);
        float pulse = 0.8f + 0.2f * (float) Math.sin(AnimeUi.time() * 8f);
        AnimeUi.band(g, gx0, by + 3, gx1 - gx0, 20, AnimeUi.alpha(AnimeUi.CYAN, 0.8f));
        AnimeUi.band(g, px0, by + 3, px1 - px0, 20, AnimeUi.alpha(AnimeUi.GOLD, pulse));
        Ui.labelCentered(g, "PERFECT", (px0 + px1) / 2, by + 9, 0xFF3A2400);

        if (!over) {
            int mx = bx + (int) (markerNow() * bw);
            AnimeUi.glow(g, mx, by + 13, 44, AnimeUi.alpha(AnimeUi.WHITE, 0.7f));
            g.fill(mx - 1, by + 1, mx + 1, by + 25, AnimeUi.WHITE);
            AnimeUi.spark(g, mx, by + 13, 22, AnimeUi.WHITE);
        }
        // Strike pips with what each earned.
        for (int i = 0; i < ForgeTiming.STRIKES; i++) {
            int x = guiLeft + 108 + i * 84;
            int y = guiTop + guiHeight - 36;
            String text;
            int color;
            if (i < done.size()) {
                text = GRADE_NAMES[done.get(i)];
                color = GRADE_COLORS[done.get(i)];
            } else {
                text = i == done.size() ? L.t("rotasutils.refine.next") : "-";
                color = i == done.size() ? AnimeUi.WHITE : AnimeUi.MUTED;
            }
            AnimeUi.socket(g, x + 8, y + 8, 22, i < done.size() ? AnimeUi.alpha(color, 0.9f) : 0, AnimeUi.PANEL_LIGHT);
            Ui.label(g, text, x + 24, y + 4, color);
        }
    }

    /** The result of the game just finished, popping over the stage for a moment. */
    private void renderBanner(GuiGraphics g, int cx, int cy) {
        CompoundTag f = finished();
        long age = Util.getMillis() - openedAt;
        if (f == null || age > 3600) {
            return;
        }
        String result = f.getString("result");
        boolean ok = "SUCCESS".equals(result);
        String text = ok ? L.t("rotasutils.refine.banner_success") : "BROKEN".equals(result) ? L.t("rotasutils.refine.banner_broken")
                : L.t("rotasutils.refine.banner_fail");
        float pop = Math.min(1f, age / 180f);
        float fade = age > 3000 ? 1f - (age - 3000) / 600f : 1f;
        int bw = Math.min(guiWidth - 40, 320);
        AnimeUi.glow(g, guiLeft + guiWidth / 2, cy, 320, AnimeUi.alpha(ok ? AnimeUi.GOLD : AnimeUi.RED, 0.5f * fade * pop));
        AnimeUi.ribbon(g, guiLeft + (guiWidth - bw) / 2, cy - 30, bw, 60, AnimeUi.alpha(ok ? 0xFF3A2A70 : 0xFF5A1E2A, fade * pop));
        int scale = pop < 1f ? 3 : 4;
        AnimeUi.outlinedCentered(g, text, guiLeft + guiWidth / 2, cy - 14, scale, ok ? AnimeUi.GOLD : AnimeUi.RED);
        if (ok) {
            AnimeUi.outlinedCentered(g, "+" + f.getInt("level"), guiLeft + guiWidth / 2, cy + 16, 1, AnimeUi.WHITE);
        }
    }
}
