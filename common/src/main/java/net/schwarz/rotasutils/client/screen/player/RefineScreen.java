package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.ForgeTiming;
import net.schwarz.rotasutils.registry.RotasRegistry;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The Refine Forge (ตีบวก), in the same board style as the other Rotas screens.
 *
 * <p>The item and what the attempt does to it sit on the left; the odds, the price, the risk and the
 * four things the player can spend on it sit on the right. Below, the player either hammers at once
 * (the plain odds) or heats the forge and plays a short timing game - three strikes, a marker sweeping
 * a bar, a wide "good" zone and a narrow "perfect" one - that adds up to {@link ForgeTiming#MAX_BONUS}
 * to the chance. The marker is a pure function of the server's clock; the screen only draws it and asks
 * for a strike, and the server grades it and reopens the bench on the result.</p>
 */
@Environment(EnvType.CLIENT)
public class RefineScreen extends RotasScreen {
    private static final int PAD = 14;
    private static final int LEFT_W = 142;

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
        CompoundTag s = session();
        if (s != null) {
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

    private String oreKey() {
        return armour() ? (enriched ? "enriched_elunium" : "elunium") : (enriched ? "enriched_oridecon" : "oridecon");
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

    /** Odds shown: the table plus the blessing scroll, before the forge game's bonus. */
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

    /** Server ticks since the game began: the server's clock at the payload plus the time this screen has been open. */
    private double sessionTicks() {
        CompoundTag s = session();
        return (s.getLong("now") - s.getLong("start")) + (Util.getMillis() - openedAt) / 50.0;
    }

    /** Ticks into the current strike's sweep, on the same clock. */
    private double strikeTicks() {
        CompoundTag s = session();
        return (s.getLong("now") - s.getLong("strike_start")) + (Util.getMillis() - openedAt) / 50.0;
    }

    private boolean sessionLive() {
        return session() != null && sessionTicks() < ForgeTiming.SESSION_TICKS;
    }

    private boolean affordable() {
        return have(oreKey()) > 0 && state.getLong("gold") >= quote().getLong("cost");
    }

    // Layout -----------------------------------------------------------------------------------------

    private int top() {
        return guiTop + 56;
    }

    private int rightX() {
        return guiLeft + PAD + LEFT_W + 12;
    }

    private int rightW() {
        return guiWidth - 2 * PAD - LEFT_W - 12;
    }

    private int tileY() {
        return top() + 96;
    }

    private int tileW() {
        return (rightW() - 3 * 6) / 4;
    }

    private int tileX(int i) {
        return rightX() + i * (tileW() + 6);
    }

    private int lowerY() {
        return top() + 162;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 480);
        guiHeight = Ui.fill(height, 330);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        int by = guiTop + guiHeight - 36;
        int right = guiLeft + guiWidth - PAD;

        addRenderableWidget(Ui.boardButton(L.c("rotasutils.refine.close"), button -> onClose())
                .bounds(guiLeft + PAD, by, 90, 24).build());
        if (sessionLive()) {
            var strike = Ui.boardPrimaryButton(Ui.text(L.t("rotasutils.refine.strike") + "  [Space]"), button -> strike())
                    .bounds(right - 170, by, 170, 24).build();
            strike.active = grades(session()).size() < ForgeTiming.STRIKES;
            addRenderableWidget(strike);
            return;
        }
        boolean possible = quote().getBoolean("possible") && affordable();
        var heat = Ui.boardPrimaryButton(L.c("rotasutils.refine.heat"), button -> send("forge_begin", options()))
                .bounds(right - 262, by, 170, 24).build();
        heat.active = possible;
        addRenderableWidget(heat);
        var quick = Ui.boardButton(L.c("rotasutils.refine.quick"), button -> send("refine_attempt", options()))
                .bounds(right - 84, by, 84, 24).build();
        quick.active = possible;
        addRenderableWidget(quick);
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

    // Spendable materials -----------------------------------------------------------------------------

    private record Tile(String key, String label, ItemStack icon, int have) {
    }

    private List<Tile> tiles() {
        var enrichedItem = armour() ? RotasRegistry.ENRICHED_ELUNIUM.get() : RotasRegistry.ENRICHED_ORIDECON.get();
        String enrichedKey = armour() ? "enriched_elunium" : "enriched_oridecon";
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!sessionLive() && quote().getBoolean("possible")) {
            List<Tile> tiles = tiles();
            for (int i = 0; i < tiles.size(); i++) {
                Tile tile = tiles.get(i);
                if (tile.have() > 0 && Ui.inside((int) mouseX, (int) mouseY, tileX(i), tileY(), tileW(), 50)) {
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
        graphics.fillGradient(0, 0, width, height, Ui.BOARD_SCRIM_TOP, Ui.BOARD_SCRIM_BOTTOM);
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.woodFrame(graphics, guiLeft, guiTop, guiWidth, guiHeight);
        renderFeedback(graphics, guiLeft + guiWidth - Math.max(40, feedbackWidth()) - PAD, guiTop + 14,
                0xFFE8FFE0, 0xFFFFD8D0, Ui.GOOD, Ui.BAD);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        CompoundTag quote = quote();
        ItemStack stack = ItemStack.of(state.getCompound("item"));
        boolean possible = quote.getBoolean("possible");
        Ui.boardHeader(g, guiLeft + PAD, guiTop + 12, guiWidth - 2 * PAD - Math.max(0, feedbackWidth()) - 8,
                L.t("rotasutils.refine.title"),
                possible ? stack.getHoverName().getString() : "", Ui.INK_SOFT);
        if (!possible) {
            String message = quote.getString("message").isBlank() ? L.t("rotasutils.refine.nothing_held") : quote.getString("message");
            Ui.wrapped(g, message, guiLeft + PAD + 6, top() + 6, guiWidth - 2 * PAD - 12, Ui.INK_BAD);
            return;
        }
        renderItemCard(g, stack, quote);
        renderNumbers(g, quote);
        renderTiles(g, mouseX, mouseY);
        renderLower(g);
        renderResult(g);
    }

    private void renderItemCard(GuiGraphics g, ItemStack stack, CompoundTag quote) {
        int x = guiLeft + PAD, y = top(), h = 150;
        Ui.parchment(g, x, y, LEFT_W, h, false);
        Ui.parchmentInset(g, x + 10, y + 10, LEFT_W - 20, 76);
        g.pose().pushPose();
        g.pose().translate(x + LEFT_W / 2f, y + 48, 100);
        g.pose().scale(3.5f, 3.5f, 1f);
        g.renderItem(stack, -8, -8);
        g.pose().popPose();
        Ui.scaledCentered(g, "+" + quote.getInt("level") + " > +" + quote.getInt("target"), x + LEFT_W / 2, y + 96, 2.0f, Ui.INK);
        double chance = Math.min(1.0, baseChance() + bonusChance());
        int color = chance >= 0.75 ? Ui.INK_GOOD : chance >= 0.4 ? Ui.INK_WARN : Ui.INK_BAD;
        Ui.labelCentered(g, L.t("rotasutils.refine.chance") + "  " + Math.round(chance * 100) + "%", x + LEFT_W / 2, y + 122, color);
        Ui.labelCentered(g, L.t("rotasutils.refine.safe_level") + " +" + state.getInt("safe_level"), x + LEFT_W / 2, y + 134, Ui.INK_SOFT);
    }

    private void renderNumbers(GuiGraphics g, CompoundTag quote) {
        int x = rightX(), w = rightW(), y = top();
        double chance = Math.min(1.0, baseChance() + bonusChance());
        int color = chance >= 0.75 ? Ui.INK_GOOD : chance >= 0.4 ? Ui.INK_WARN : Ui.INK_BAD;
        Ui.label(g, L.t("rotasutils.refine.chance"), x, y, Ui.INK_SOFT);
        String bar = Math.round(chance * 100) + "%" + (bonusChance() > 0
                ? "   (+" + Math.round(bonusChance() * 100) + "% " + L.t("rotasutils.refine.forge_bonus") + ")" : "");
        Ui.bar(g, x, y + 12, w, 14, chance, color, bar);

        long cost = quote.getLong("cost");
        boolean rich = state.getLong("gold") >= cost;
        row(g, x, w, y + 36, L.t("rotasutils.refine.cost"), cost + "  /  " + state.getLong("gold"), rich ? Ui.INK : Ui.INK_BAD);
        row(g, x, w, y + 50, L.t("rotasutils.refine.material"),
                L.t("item.rotasutils." + oreKey()) + "  x1  (" + have(oreKey()) + ")", have(oreKey()) > 0 ? Ui.INK : Ui.INK_BAD);
        String risk = protection ? L.t("rotasutils.refine.risk_protected")
                : L.t("rotasutils.refine.risk." + state.getString("on_fail").toLowerCase(java.util.Locale.ROOT));
        int ry = y + 68;
        for (String line : Ui.wrap(risk, w)) {
            Ui.label(g, line, x, ry, protection ? Ui.INK_GOOD : Ui.INK_BAD);
            ry += 11;
        }
    }

    private void row(GuiGraphics g, int x, int w, int y, String label, String value, int color) {
        Ui.label(g, label, x, y, Ui.INK_SOFT);
        Ui.labelRight(g, value, x + w, y, color);
    }

    private void renderTiles(GuiGraphics g, int mouseX, int mouseY) {
        List<Tile> tiles = tiles();
        boolean locked = sessionLive();
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            int x = tileX(i), y = tileY(), w = tileW();
            boolean owned = tile.have() > 0;
            boolean hot = owned && !locked && Ui.inside(mouseX, mouseY, x, y, w, 50);
            Ui.rowCard(g, x, y, w, 50, hot, on(tile.key()));
            g.renderItem(tile.icon(), x + w / 2 - 8, y + 6);
            Ui.labelCentered(g, Ui.truncate(tile.label(), w - 6), x + w / 2, y + 26, owned ? Ui.INK : Ui.INK_FADE);
            Ui.labelCentered(g, "x" + tile.have(), x + w / 2, y + 37, owned ? (on(tile.key()) ? Ui.INK_GOOD : Ui.INK_SOFT) : Ui.INK_FADE);
        }
    }

    /** The band under the numbers: the timing bar and strike results while heating, the hint otherwise. */
    private void renderLower(GuiGraphics g) {
        int x = guiLeft + PAD, w = guiWidth - 2 * PAD, y = lowerY();
        if (!sessionLive()) {
            int hy = y + 4;
            for (String line : Ui.wrap(L.t("rotasutils.refine.hint"), w)) {
                Ui.label(g, line, x, hy, Ui.INK_SOFT);
                hy += 11;
            }
            return;
        }
        CompoundTag s = session();
        List<Integer> done = grades(s);
        int index = Math.min(done.size(), ForgeTiming.STRIKES - 1);
        double center = s.getList("centers", Tag.TAG_DOUBLE).getDouble(index);
        boolean over = done.size() >= ForgeTiming.STRIKES;

        Ui.parchmentInset(g, x, y, w, 26);
        int bx = x + 4, bw = w - 8;
        double good = ForgeTiming.goodHalf(index), perfect = ForgeTiming.perfectHalf(index);
        int gx0 = bx + (int) ((center - good) * bw), gx1 = bx + (int) ((center + good) * bw);
        int px0 = bx + (int) ((center - perfect) * bw), px1 = bx + (int) ((center + perfect) * bw);
        g.fill(gx0, y + 4, gx1, y + 22, 0xAAC9A45C);
        g.fill(px0, y + 4, px1, y + 22, 0xFF8FB06A);
        g.fill(px0, y + 4, px1, y + 6, 0x66FFFFFF);
        if (!over) {
            int mx = bx + (int) (ForgeTiming.marker(strikeTicks(), index) * bw);
            g.fill(mx - 1, y + 1, mx + 2, y + 25, Ui.INK);
            g.fill(mx - 3, y + 1, mx + 4, y + 3, Ui.INK);
            g.fill(mx - 3, y + 23, mx + 4, y + 25, Ui.INK);
        }
        // What each strike earned.
        int tx = x;
        int ty = y + 34;
        for (int i = 0; i < ForgeTiming.STRIKES; i++) {
            String text;
            int color;
            if (i < done.size()) {
                int grade = done.get(i);
                text = (i + 1) + ".  " + L.t("rotasutils.refine.grade_" + grade);
                color = grade == 0 ? Ui.INK_GOOD : grade == 1 ? Ui.INK_WARN : Ui.INK_FADE;
            } else {
                text = (i + 1) + ".  " + (i == done.size() ? L.t("rotasutils.refine.next") : "-");
                color = i == done.size() ? Ui.INK : Ui.INK_FADE;
            }
            tx += Ui.tag(g, tx, ty, text, color);
        }
        Ui.labelRight(g, L.t("rotasutils.refine.forge_bonus_total", String.valueOf(Math.round(ForgeTiming.total(gradeEnums(done)) * 100))),
                x + w, ty + 4, Ui.INK_SOFT);
    }

    private List<ForgeTiming.Grade> gradeEnums(List<Integer> ordinals) {
        List<ForgeTiming.Grade> out = new ArrayList<>();
        for (int o : ordinals) {
            out.add(ForgeTiming.Grade.values()[Math.max(0, Math.min(2, o))]);
        }
        return out;
    }

    /** The result of the game just finished, as a plain notice over the middle for a few seconds. */
    private void renderResult(GuiGraphics g) {
        CompoundTag f = finished();
        long age = Util.getMillis() - openedAt;
        if (f == null || age > 4000) {
            return;
        }
        String result = f.getString("result");
        boolean ok = "SUCCESS".equals(result);
        String text = ok ? L.t("rotasutils.refine.banner_success", String.valueOf(f.getInt("level")))
                : "BROKEN".equals(result) ? L.t("rotasutils.refine.banner_broken") : L.t("rotasutils.refine.banner_fail");
        int w = Math.min(guiWidth - 60, 300);
        int x = guiLeft + (guiWidth - w) / 2, y = top() + 40;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        Ui.parchment(g, x, y, w, 44, true);
        Ui.scaledCentered(g, text, guiLeft + guiWidth / 2, y + 14, 2.0f, ok ? Ui.INK_GOOD : Ui.INK_BAD);
        g.pose().popPose();
    }
}
