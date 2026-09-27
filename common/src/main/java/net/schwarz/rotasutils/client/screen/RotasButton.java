package net.schwarz.rotasutils.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

/**
 * Button for the console screens, plus the wooden variants the quest board uses.
 *
 * <p>Three console tiers exist and they are visually distinct at a glance: PRIMARY is the one
 * action a screen exists for and is the only filled-accent element; DEFAULT is every other
 * action; NAVIGATION reads as a list entry, not a button.</p>
 */
@Environment(EnvType.CLIENT)
public final class RotasButton extends Button {
    public enum Style {
        DEFAULT,
        PRIMARY,
        DANGER,
        NAVIGATION,
        NAVIGATION_SELECTED,
        /** Wooden button used by the quest board screens. */
        BOARD,
        /** Wax-sealed board button for a screen's single main action. */
        BOARD_PRIMARY,
        BOARD_TAB,
        BOARD_TAB_SELECTED
    }

    private final Style style;

    private RotasButton(Builder builder) {
        super(builder.x, builder.y, builder.width, builder.height,
                Ui.readableComponent(builder.message), builder.onPress, builder.createNarration);
        this.style = builder.style;
        setTooltip(builder.tooltip);
    }

    public static Builder create(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager handler) {
        switch (style) {
            case BOARD, BOARD_TAB, BOARD_TAB_SELECTED -> Sfx.wood();
            case BOARD_PRIMARY -> Sfx.stamp();
            default -> super.playDownSound(handler);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean highlighted = isHoveredOrFocused();
        // Board variants keep their sounds but share the Rotas look.
        Style style = switch (this.style) {
            case BOARD -> Style.DEFAULT;
            case BOARD_PRIMARY -> Style.PRIMARY;
            case BOARD_TAB -> Style.NAVIGATION;
            case BOARD_TAB_SELECTED -> Style.NAVIGATION_SELECTED;
            default -> this.style;
        };

        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();
        int radius = RotasTheme.RADIUS_CONTROL;

        if (!active) {
            PixelUi.frame(graphics, x, y, width, height,
                    radius, RotasTheme.SEPARATOR, RotasTheme.CONTROL_DISABLED);
            paint(graphics, RotasTheme.TEXT_FAINT);
            return;
        }

        if (style == Style.NAVIGATION || style == Style.NAVIGATION_SELECTED) {
            boolean selected = style == Style.NAVIGATION_SELECTED;
            int fill = selected ? RotasTheme.ACCENT_WASH
                    : highlighted ? RotasTheme.SURFACE_HIGH : RotasTheme.SURFACE;
            int edge = selected ? RotasTheme.ACCENT
                    : highlighted ? RotasTheme.PANEL_BORDER : RotasTheme.SEPARATOR;
            PixelUi.frame(graphics, x, y, width, height,
                    radius, edge, fill);
            if (selected) {
                PixelUi.fill(graphics, x + 2, y + 3, 2,
                        height - 6, 1, Ui.ACCENT);
            }
            if (isFocused()) {
                PixelUi.focus(graphics, x, y, width, height);
            }
            paint(graphics, selected ? RotasTheme.TEXT : highlighted ? RotasTheme.TEXT : RotasTheme.TEXT_MUTED);
            return;
        }

        switch (style) {
            case PRIMARY -> {
                if (highlighted || isFocused()) {
                    PixelUi.shadow(graphics, x, y, width, height,
                            radius, 0x402A2015);
                }
                int fill = highlighted ? RotasTheme.ACCENT_STRONG : Ui.ACCENT;
                PixelUi.frame(graphics, x, y, width, height,
                        radius, RotasTheme.ACCENT_STRONG, fill);
                // One-pixel bevel on the primary action.
                graphics.fill(x + 4, y + 1, x + width - 4, y + 2, 0x55FFFFFF);
                if (isFocused()) {
                    PixelUi.focus(graphics, x - 1, y - 1,
                            width + 2, height + 2);
                }
                // Dark ink remains readable on the copper primary action.
                paint(graphics, Ui.INK);
            }
            case DANGER -> {
                PixelUi.frame(graphics, x, y, width, height,
                        radius, Ui.DANGER_BORDER, highlighted ? Ui.DANGER_HOVER : Ui.DANGER_SOFT);
                paint(graphics, Ui.DANGER_TEXT);
            }
            default -> {
                PixelUi.frame(graphics, x, y, width, height,
                        radius, highlighted ? RotasTheme.TEXT_FAINT : RotasTheme.PANEL_BORDER,
                        highlighted ? RotasTheme.CONTROL_HOVER : RotasTheme.CONTROL);
                graphics.fill(x + 4, y + 1, x + width - 4, y + 2, 0x22FFFFFF);
                if (isFocused()) {
                    PixelUi.focus(graphics, x, y, width, height);
                }
                paint(graphics, RotasTheme.TEXT);
            }
        }
    }

    /** Draws the label in the alignment this style calls for. */
    private void paint(GuiGraphics graphics, int color) {
        if (leftAligned()) {
            renderLeftAlignedLabel(graphics, color);
        } else {
            renderCenteredLabel(graphics, color);
        }
    }

    /**
     * Centred label without a drop shadow. Vanilla's scrolling string always draws one, which
     * doubles the strokes of outline-heavy resource-pack fonts (Thai TTF packs especially).
     */
    private void renderCenteredLabel(GuiGraphics graphics, int color) {
        var font = Minecraft.getInstance().font;
        int available = getWidth() - 12;
        String text = getMessage().getString();
        Component component = Ui.readableComponent(Component.literal(text));
        if (font.width(component) > available) {
            component = Ui.readableComponent(Component.literal(Ui.truncate(text, available)));
        }
        graphics.drawString(font, component, getX() + (getWidth() - font.width(component)) / 2,
                getY() + (getHeight() - 8) / 2, color, false);
    }

    /**
     * Navigation and tab styles read as a list, so their labels sit flush left instead
     * of centred; action buttons stay centred.
     */
    private boolean leftAligned() {
        return style == Style.NAVIGATION || style == Style.NAVIGATION_SELECTED
                || style == Style.BOARD_TAB || style == Style.BOARD_TAB_SELECTED;
    }

    private void renderLeftAlignedLabel(GuiGraphics graphics, int color) {
        var font = Minecraft.getInstance().font;
        int padding = 8;
        int available = getWidth() - padding - 6;
        String text = getMessage().getString();
        Component component = Ui.readableComponent(Component.literal(text));
        if (font.width(component) > available) {
            text = Ui.truncate(text, available);
            component = Ui.readableComponent(Component.literal(text));
        }
        graphics.drawString(font, component, getX() + padding,
                getY() + (getHeight() - 8) / 2, color, false);
    }

    @Environment(EnvType.CLIENT)
    public static final class Builder {
        private final Component message;
        private final OnPress onPress;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;
        private Style style = Style.DEFAULT;
        private CreateNarration createNarration = DEFAULT_NARRATION;
        @Nullable
        private Tooltip tooltip;

        private Builder(Component message, OnPress onPress) {
            // A plain English literal is shown in Thai; translated and styled components are kept as given.
            this.message = message != null && message.getSiblings().isEmpty()
                    && message.getContents() instanceof net.minecraft.network.chat.contents.LiteralContents literal
                    && message.getStyle().isEmpty()
                    ? Component.literal(net.schwarz.rotasutils.util.ThaiText.phrase(literal.text()))
                    : message;
            this.onPress = onPress;
        }

        public Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder bounds(int x, int y, int width, int height) {
            return pos(x, y).size(width, height);
        }

        public Builder style(Style style) {
            this.style = style;
            return this;
        }

        public Builder tooltip(@Nullable Tooltip tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public Builder createNarration(CreateNarration createNarration) {
            this.createNarration = createNarration;
            return this;
        }

        public RotasButton build() {
            return new RotasButton(this);
        }
    }
}
