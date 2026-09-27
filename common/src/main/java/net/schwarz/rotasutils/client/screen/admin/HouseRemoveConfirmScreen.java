package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasButton;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;

/** Explicit confirmation surface for removing an unoccupied house definition. */
@Environment(EnvType.CLIENT)
public final class HouseRemoveConfirmScreen extends RotasScreen {
    private final HouseScreenActions.EditorContext context;
    private final HouseEditScreen editor;
    private HouseAdminLayout.Confirm layout;
    private boolean submitted;

    public HouseRemoveConfirmScreen(HouseEditScreen editor) {
        super(L.t("rotasutils.house.remove.title"), editor);
        this.editor = editor;
        this.context = editor.editorContext();
    }

    public HouseRemoveConfirmScreen(String houseId, HouseAdminPresentation.EditDraft draft, Screen parent) {
        super(L.t("rotasutils.house.remove.title"), parent);
        this.editor = parent instanceof HouseEditScreen edit ? edit : null;
        this.context = HouseScreenActions.editorContext(houseId, draft);
    }

    public HouseScreenActions.EditorContext context() {
        return context;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 560);
        guiHeight = Ui.fill(height, 320);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        setHeader(format("rotasutils.house.remove.header", "Remove House: {0}", context.draft().name()));
        layout = HouseAdminLayout.houseConfirm(guiWidth, guiHeight);

        HouseAdminLayout.Rect actions = at(layout.actions());
        int gap = Math.min(Ui.GAP, Math.max(0, actions.width() / 16));
        int confirmWidth = Math.max(1, (actions.width() - gap) / 2);
        int actionHeight = Math.max(1, actions.height());
        RotasButton confirm = addRenderableWidget(Ui.dangerButton(L.c("rotasutils.house.remove.confirm"), button -> confirmRemoval())
                .bounds(actions.x(), actions.y(), confirmWidth, actionHeight).build());
        confirm.active = !submitted;
        addRenderableWidget(Ui.button(L.c("rotasutils.common.cancel"), button -> cancel())
                .bounds(actions.x() + confirmWidth + gap, actions.y(),
                        Math.max(1, actions.width() - confirmWidth - gap), actionHeight).build());

        HouseAdminLayout.Rect footer = at(layout.footer());
        addRenderableWidget(Ui.button(L.c("rotasutils.common.back"), button -> cancel())
                .bounds(footer.x(), footer.y(), Math.max(1, footer.width()), footer.height()).build());
    }

    private void confirmRemoval() {
        if (submitted) {
            return;
        }
        try {
            HouseScreenActions.RemovalResult result = HouseScreenActions.confirmRemoval(
                    context.houseId(), context.draft().definitionRevision());
            CompoundTag payload = HouseScreenActions.removePayload(result);
            send("house_remove", payload);
            submitted = true;
            Sfx.remove();
            // Keep the editor instance so a failed server response still exposes
            // the same typed draft and revision token.
            if (editor != null) {
                minecraft.setScreen(editor);
            } else {
                goBack();
            }
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            Sfx.error();
        }
    }

    private void cancel() {
        Sfx.click();
        if (editor != null) {
            minecraft.setScreen(editor);
        } else {
            goBack();
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        HouseAdminLayout.Rect content = at(layout.content());
        HouseAdminLayout.Rect actions = at(layout.actions());
        HouseAdminLayout.MessageLayout messageText = layout.messageText();
        if (content.height() > 0) {
            Ui.modernPanel(graphics, content.x(), content.y(), content.width(), content.height());
        }
        HouseAdminLayout.Rect heading = at(messageText.heading());
        if (heading.height() > 0) {
            Ui.sectionHeading(graphics, text("rotasutils.house.remove.heading", "REMOVE DEFINITION"),
                    heading.x(), heading.y(), heading.width());
        }
        HouseAdminLayout.Rect name = at(messageText.name());
        if (name.height() > 0) {
            Ui.scaledCentered(graphics, Ui.truncate(context.draft().name(), Math.max(1, name.width())),
                    name.x() + name.width() / 2, name.y(), 1.0f, Ui.TEXT_BRIGHT);
        }
        String warning = text("rotasutils.house.remove.warning",
                "The house definition and empty tenancy record will be removed. World blocks and items remain.");
        renderBounded(graphics, messageText.warning(), warning, Ui.WARN);
        HouseAdminLayout.Rect revision = at(messageText.revision());
        if (revision.height() > 0) {
            Ui.label(graphics, format("rotasutils.house.remove.revision_line", "ID: {0}  -  revision {1}",
                            context.houseId(), context.draft().definitionRevision()),
                    revision.x(), revision.y(), Ui.TEXT_MUTED);
        }
        if (actions.height() > 0) {
            Ui.modernPanel(graphics, actions.x(), actions.y(), actions.width(), actions.height());
        }
        if (submitted && actions.height() > 0) {
            Ui.labelCentered(graphics, text("rotasutils.house.remove.sent", "Removal request sent."),
                    actions.x() + actions.width() / 2, actions.y() + actions.height() / 2 - 4, Ui.TEXT_MUTED);
        }
    }

    private void renderBounded(GuiGraphics graphics, HouseAdminLayout.TextRegion region,
                               String value, int color) {
        if (region.maxLines() <= 0 || region.bounds().width() <= 0 || region.bounds().height() <= 0) {
            return;
        }
        var lines = Ui.wrap(value, region.bounds().width());
        int count = Math.min(region.maxLines(), lines.size());
        for (int i = 0; i < count; i++) {
            String line = lines.get(i);
            if (i == count - 1 && lines.size() > count) {
                line = Ui.truncate(line + "…", region.bounds().width());
            }
            HouseAdminLayout.Rect lineRect = at(region.line(i));
            Ui.label(graphics, Ui.truncate(line, Math.max(1, lineRect.width())),
                    lineRect.x(), lineRect.y(), color);
        }
    }

    private HouseAdminLayout.Rect at(HouseAdminLayout.Rect rect) {
        return new HouseAdminLayout.Rect(guiLeft + rect.x(), guiTop + rect.y(), rect.width(), rect.height());
    }

    private static String text(String key, String fallback) {
        String value = L.t(key);
        return value.equals(key) ? fallback : value;
    }

    private static String format(String key, String fallback, Object... args) {
        String value = text(key, fallback);
        for (int i = 0; i < args.length; i++) {
            value = value.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return value;
    }
}
