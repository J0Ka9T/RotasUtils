package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;

/**
 * The refinement bench (ตีบวก).
 *
 * <p>Everything the player weighs before pressing the hammer is on one page: what the attempt does to
 * the item, how likely it is, what it costs, and which of the three scrolls they own. The numbers are
 * the server's - the screen only shows them and asks for an attempt, and the server reopens the bench
 * on the result, so the page can never show a level the world does not have.</p>
 */
@Environment(EnvType.CLIENT)
public class RefineScreen extends RotasScreen {
    private final CompoundTag state;
    private boolean enriched;
    private boolean protection;
    private boolean blessing;
    private boolean certificate;

    public RefineScreen(CompoundTag payload) {
        super(L.t("rotasutils.refine.title"), null);
        this.state = payload == null ? new CompoundTag() : payload;
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

    /** The chance shown, after the scrolls the player has switched on. */
    private double chance() {
        if (certificate) {
            return 1.0;
        }
        double chance = quote().getDouble("chance");
        return chance >= 1.0 ? chance : Math.min(1.0, chance + (blessing ? state.getDouble("blessing_bonus") : 0));
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 420);
        guiHeight = Ui.fill(height, 300);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        int contentX = guiLeft + 12;
        int contentWidth = guiWidth - 24;
        int barY = guiTop + guiHeight - 34;
        int toggleY = guiTop + guiHeight - 98;
        int toggleWidth = (contentWidth - 12) / 2;

        String oreKey = armour() ? "elunium" : "oridecon";
        String enrichedKey = armour() ? "enriched_elunium" : "enriched_oridecon";
        addRenderableWidget(toggle(L.t("rotasutils.refine.use_enriched"), enriched, have(enrichedKey) > 0,
                contentX, toggleY, toggleWidth, button -> {
                    enriched = !enriched;
                    rebuildWidgets();
                }));
        addRenderableWidget(toggle(L.t("rotasutils.refine.use_protection"), protection, have("protection_scroll") > 0,
                contentX + toggleWidth + 12, toggleY, toggleWidth, button -> {
                    protection = !protection;
                    rebuildWidgets();
                }));
        addRenderableWidget(toggle(L.t("rotasutils.refine.use_blessing"), blessing, have("blessing_scroll") > 0,
                contentX, toggleY + 28, toggleWidth, button -> {
                    blessing = !blessing;
                    rebuildWidgets();
                }));
        addRenderableWidget(toggle(L.t("rotasutils.refine.use_certificate"), certificate, have("certificate_scroll") > 0,
                contentX + toggleWidth + 12, toggleY + 28, toggleWidth, button -> {
                    certificate = !certificate;
                    rebuildWidgets();
                }));

        addRenderableWidget(Ui.boardButton(L.c("rotasutils.refine.close"), button -> onClose())
                .bounds(contentX, barY, 110, 24).build());
        boolean possible = quote().getBoolean("possible") && have(enriched ? enrichedKey : oreKey) > 0
                && state.getLong("gold") >= quote().getLong("cost");
        var hammer = Ui.boardPrimaryButton(L.c("rotasutils.refine.hammer"), button -> {
            CompoundTag payload = new CompoundTag();
            payload.putBoolean("enriched", enriched);
            payload.putBoolean("protection", protection);
            payload.putBoolean("blessing", blessing);
            payload.putBoolean("certificate", certificate);
            send("refine_attempt", payload);
        }).bounds(contentX + contentWidth - 160, barY, 160, 24).build();
        hammer.active = possible;
        addRenderableWidget(hammer);
    }

    /** A material switch: on, off, or greyed out because the player is not carrying one. */
    private net.minecraft.client.gui.components.AbstractWidget toggle(String label, boolean on, boolean owned,
                                                                     int x, int y, int width,
                                                                     net.minecraft.client.gui.components.Button.OnPress press) {
        String mark = on ? "[x] " : "[ ] ";
        var builder = on ? Ui.boardPrimaryButton(Ui.text(mark + label), press) : Ui.boardButton(Ui.text(mark + label), press);
        var widget = builder.bounds(x, y, width, 24).build();
        widget.active = owned;
        return widget;
    }

    @Override
    protected void renderBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, Ui.BOARD_SCRIM_TOP, Ui.BOARD_SCRIM_BOTTOM);
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.woodFrame(graphics, guiLeft, guiTop, guiWidth, guiHeight);
        Ui.parchment(graphics, guiLeft + 12, guiTop + 12, guiWidth - 24, guiHeight - 118, false);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int contentX = guiLeft + 12;
        int contentWidth = guiWidth - 24;
        int centerX = guiLeft + guiWidth / 2;
        CompoundTag quote = quote();

        Ui.scaledCentered(graphics, L.t("rotasutils.refine.title"), centerX, guiTop + 22, 1.3f, Ui.INK);

        ItemStack stack = ItemStack.of(state.getCompound("item"));
        if (!quote.getBoolean("possible")) {
            Ui.wrapped(graphics, quote.getString("message").isBlank()
                            ? L.t("rotasutils.refine.nothing_held") : quote.getString("message"),
                    contentX + 12, guiTop + 54, contentWidth - 24, Ui.INK_BAD);
            return;
        }

        Ui.icon(graphics, stack, contentX + 14, guiTop + 44);
        Ui.label(graphics, Ui.truncate(stack.getHoverName().getString(), contentWidth - 60),
                contentX + 36, guiTop + 48, Ui.INK);

        int y = guiTop + 72;
        y = row(graphics, contentX, contentWidth, y, L.t("rotasutils.refine.step"),
                "+" + quote.getInt("level") + "  ->  +" + quote.getInt("target"), Ui.INK);
        int percent = (int) Math.round(chance() * 100);
        y = row(graphics, contentX, contentWidth, y, L.t("rotasutils.refine.chance"), percent + "%",
                percent >= 100 ? Ui.INK_GOOD : percent >= 50 ? Ui.INK : Ui.INK_WARN);
        long cost = quote.getLong("cost");
        y = row(graphics, contentX, contentWidth, y, L.t("rotasutils.refine.cost"),
                cost + " / " + state.getLong("gold"),
                state.getLong("gold") >= cost ? Ui.INK : Ui.INK_BAD);
        String oreKey = armour() ? "elunium" : "oridecon";
        String enrichedKey = armour() ? "enriched_elunium" : "enriched_oridecon";
        String using = enriched ? enrichedKey : oreKey;
        y = row(graphics, contentX, contentWidth, y, L.t("rotasutils.refine.material"),
                L.t("item.rotasutils." + using) + " x1  (" + have(using) + ")",
                have(using) > 0 ? Ui.INK : Ui.INK_BAD);
        row(graphics, contentX, contentWidth, y, L.t("rotasutils.refine.safe_level"),
                "+" + state.getInt("safe_level"), Ui.INK_SOFT);

        String risk = protection ? L.t("rotasutils.refine.risk_protected")
                : L.t("rotasutils.refine.risk." + state.getString("on_fail").toLowerCase(java.util.Locale.ROOT));
        Ui.wrapped(graphics, risk, contentX + 12, guiTop + guiHeight - 126, contentWidth - 24,
                protection ? Ui.INK_GOOD : Ui.INK_BAD);
    }

    private int row(GuiGraphics graphics, int x, int width, int y, String label, String value, int color) {
        Ui.label(graphics, label, x + 12, y, Ui.INK_SOFT);
        Ui.labelRight(graphics, value, x + width - 12, y, color);
        return y + 13;
    }
}
