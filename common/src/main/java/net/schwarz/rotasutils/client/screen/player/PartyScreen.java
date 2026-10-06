package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;

import java.util.List;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class PartyScreen extends RotasScreen {
    private static final int ROW_HEIGHT = 32;
    private static final int INVITE_COLUMN_W = 210;
    private static final int NAME_ROW_H = 40;

    private ScrollPanel list;
    private ScrollPanel nearbyList;
    private EditBox inviteBox;
    private UUID selected;

    public PartyScreen(Screen parent) {
        super("Party", parent);
    }

    private List<ClientState.PartyMember> members() {
        return ClientState.party();
    }

    private List<ClientState.InvitablePlayer> nearby() {
        return ClientState.partyInvitable();
    }

    private boolean inParty() {
        return ClientState.progress().partyId() != null;
    }

    private boolean canInvite() {
        return ClientState.partyEnabled() && (!inParty() || ClientState.isPartyLeader());
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 680);
        guiHeight = Ui.fill(height, 440);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        int contentX = guiLeft + 10;
        int contentWidth = guiWidth - 20;
        int barY = guiTop + guiHeight - 34;

        send("party_refresh");

        boolean leader = ClientState.isPartyLeader();
        boolean invited = !ClientState.partyInviteFrom().isEmpty();
        boolean invite = canInvite();

        int listTop = guiTop + (invited ? 106 : 76);
        int listBottom = barY - 8;
        int rosterWidth = invite ? contentWidth - INVITE_COLUMN_W - 10 : contentWidth;

        list = new ScrollPanel(contentX, listTop, rosterWidth,
                Math.max(ROW_HEIGHT, listBottom - listTop), ROW_HEIGHT).parchment();
        registerPanel(list);
        list.setRows(members().size(), this::renderMember, this::clickMember);

        if (invite) {
            int inviteX = contentX + rosterWidth + 10;
            int nearbyBottom = listBottom - NAME_ROW_H;
            nearbyList = new ScrollPanel(inviteX, listTop, INVITE_COLUMN_W,
                    Math.max(ROW_HEIGHT, nearbyBottom - listTop), ROW_HEIGHT).parchment();
            registerPanel(nearbyList);
            nearbyList.setRows(nearby().size(), this::renderNearby, this::clickNearby);

            inviteBox = new EditBox(font, inviteX + 6, nearbyBottom + 23, INVITE_COLUMN_W - 84, 12,
                    L.c("rotasutils.party.invite_hint"));
            inviteBox.setBordered(false);
            inviteBox.setTextColor(Ui.INK);
            inviteBox.setHint(L.c("rotasutils.party.invite_hint"));
            addRenderableWidget(inviteBox);
            addRenderableWidget(Ui.boardButton(L.c("rotasutils.party.invite_send"), button -> sendInvite())
                    .bounds(inviteX + INVITE_COLUMN_W - 72, nearbyBottom + 16, 72, 24).build());
        }

        if (invited) {
            addRenderableWidget(Ui.boardPrimaryButton(L.c("rotasutils.party.accept"), button -> {
                send("party_accept");
                Sfx.stamp();
            }).bounds(contentX + contentWidth - 190, guiTop + 74, 90, 24).build());
            addRenderableWidget(Ui.boardButton(L.c("rotasutils.party.decline"), button -> {
                send("party_decline");
                Sfx.remove();
            }).bounds(contentX + contentWidth - 96, guiTop + 74, 96, 24).build());
        }

        if (inParty()) {
            if (leader) {
                addRenderableWidget(Ui.boardButton(L.c(selected == null
                                ? "rotasutils.party.remove"
                                : "rotasutils.party.remove_selected"), button -> withSelected("party_kick"))
                        .bounds(contentX + 96, barY, 96, 24).build());
                addRenderableWidget(Ui.boardButton(L.c("rotasutils.party.promote"),
                        button -> withSelected("party_promote"))
                        .bounds(contentX + 198, barY, 136, 24).build());
                addRenderableWidget(Ui.dangerButton(L.c("rotasutils.party.disband"), button ->
                        confirm(L.c("rotasutils.party.disband"),
                                L.t("rotasutils.party.disband_confirm"), "party_disband"))
                        .bounds(contentX + contentWidth - 160, barY, 76, 24).build());
            }
            addRenderableWidget(Ui.dangerButton(L.c("rotasutils.party.leave"), button ->
                    confirm(L.c("rotasutils.party.leave"), L.t("rotasutils.party.leave_confirm"), "party_leave"))
                    .bounds(contentX + contentWidth - 80, barY, 80, 24).build());
        }

        addRenderableWidget(Ui.boardButton(L.c("rotasutils.common.back"), button -> goBack())
                .bounds(contentX, barY, 90, 24).build());
    }

    private void confirm(net.minecraft.network.chat.Component title, String message, String action) {
        minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
            if (yes) {
                send(action);
                Sfx.remove();
            }
            minecraft.setScreen(this);
        }, title, Ui.text(message)));
    }

    private void invite(String name) {
        CompoundTag payload = new CompoundTag();
        payload.putString("player", name);
        send("party_invite", payload);
    }

    private void sendInvite() {
        String name = inviteBox == null ? "" : inviteBox.getValue().trim();
        if (name.isEmpty()) {
            ClientState.feedback(false, L.t("rotasutils.party.need_name"));
            Sfx.error();
            return;
        }
        invite(name);
        inviteBox.setValue("");
        Sfx.commit();
    }

    private void withSelected(String action) {
        if (selected == null) {
            ClientState.feedback(false, L.t("rotasutils.party.need_selection"));
            Sfx.error();
            return;
        }
        CompoundTag payload = new CompoundTag();
        payload.putUUID("target", selected);
        send(action, payload);
        selected = null;
        Sfx.click();
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    @Override
    public void onDataRefreshed() {
        rebuild();
    }

    private void renderMember(GuiGraphics graphics, int index, int x, int y,
                              int rowWidth, int rowHeight, boolean hovered) {
        ClientState.PartyMember member = members().get(index);
        int usable = rowWidth - 6;
        int right = x + usable;
        boolean isSelected = member.id().equals(selected);

        Ui.parchment(graphics, x, y + 1, usable, rowHeight - 3, hovered || isSelected);
        if (isSelected) {
            Ui.border(graphics, x, y + 1, usable, rowHeight - 3, Ui.WAX);
        }

        int centerY = y + 1 + (rowHeight - 3) / 2;
        if (member.leader()) {
            Ui.rankSeal(graphics, x + 16, centerY, 9, "L", Ui.PARCHMENT_ALT);
        } else {
            Ui.disc(graphics, x + 16, centerY, 7, Ui.PARCHMENT_DEEP);
        }

        Ui.label(graphics, Ui.truncate(member.name(), usable / 2), x + 32, y + 6, Ui.INK);
        Ui.label(graphics, L.t("rotasutils.party.level", member.level()), x + 32, y + 18, Ui.INK_SOFT);

        String status;
        int statusColor;
        if (!member.online()) {
            status = L.t("rotasutils.party.offline");
            statusColor = Ui.INK_FADE;
        } else if (member.nearby()) {
            status = L.t("rotasutils.party.nearby");
            statusColor = Ui.INK_GOOD;
        } else {
            status = L.t("rotasutils.party.far");
            statusColor = Ui.INK_WARN;
        }
        Ui.statusTag(graphics, right - 6, y + 1 + (rowHeight - 3 - Ui.STATUS_TAG_H) / 2, status, statusColor);
    }

    private void clickMember(int index, int button) {
        if (!ClientState.isPartyLeader()) {
            return;
        }
        UUID id = members().get(index).id();
        selected = id.equals(selected) ? null : id;
        Sfx.select();
        rebuild();
    }

    private void renderNearby(GuiGraphics graphics, int index, int x, int y,
                              int rowWidth, int rowHeight, boolean hovered) {
        ClientState.InvitablePlayer candidate = nearby().get(index);
        int usable = rowWidth - 6;
        Ui.parchment(graphics, x, y + 1, usable, rowHeight - 3, hovered);
        Ui.disc(graphics, x + 14, y + 1 + (rowHeight - 3) / 2, 6, Ui.PARCHMENT_DEEP);
        Ui.label(graphics, Ui.truncate(candidate.name(), usable / 2), x + 28, y + 6, Ui.INK);
        Ui.label(graphics, L.t("rotasutils.party.level", candidate.level()), x + 28, y + 18, Ui.INK_SOFT);
        Ui.statusTag(graphics, x + usable - 6, y + 1 + (rowHeight - 3 - Ui.STATUS_TAG_H) / 2,
                L.t("rotasutils.party.invite_click"), hovered ? Ui.INK_GOOD : Ui.INK_FADE);
    }

    private void clickNearby(int index, int button) {
        invite(nearby().get(index).name());
        Sfx.stamp();
    }

    @Override
    protected void renderBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, Ui.BOARD_SCRIM_TOP, Ui.BOARD_SCRIM_BOTTOM);
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.woodFrame(graphics, guiLeft, guiTop, guiWidth, guiHeight);
        int contentX = guiLeft + 10;
        int contentWidth = guiWidth - 20;

        Ui.boardHeader(graphics, contentX + 2, guiTop + 10,
                contentWidth - 2 - (feedbackWidth() == 0 ? 0 : feedbackWidth() + 8),
                L.t("rotasutils.party.title"), inParty()
                        ? L.t("rotasutils.party.members", members().size(), ClientState.partyMaxSize())
                        : L.t("rotasutils.party.none"), Ui.INK_SOFT);
        renderFeedback(graphics, contentX + contentWidth - feedbackWidth(), guiTop + 12,
                net.schwarz.rotasutils.client.screen.RotasTheme.SURFACE_HIGH, Ui.DANGER_SOFT, Ui.GOOD, Ui.BAD);
        if (inviteBox != null) {
            Ui.searchFrame(graphics, inviteBox.getX() - 6, inviteBox.getY() - 7,
                    inviteBox.getWidth() + 12, 24, inviteBox.isFocused());
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int contentX = guiLeft + 10;
        int contentWidth = guiWidth - 20;

        if (!ClientState.partyEnabled()) {
            Ui.labelCentered(graphics, L.t("rotasutils.party.disabled"),
                    guiLeft + guiWidth / 2, guiTop + guiHeight / 2, Ui.INK_BAD);
            return;
        }

        String invite = ClientState.partyInviteFrom();
        if (!invite.isEmpty()) {
            Ui.parchment(graphics, contentX, guiTop + 70, contentWidth, 32, true);
            Ui.border(graphics, contentX, guiTop + 70, contentWidth, 32, Ui.WAX);
            Ui.label(graphics, L.t("rotasutils.party.invite_from", invite), contentX + 8, guiTop + 76, Ui.INK);
            Ui.label(graphics, L.t("rotasutils.party.invite_expiry"), contentX + 8, guiTop + 88, Ui.INK_FADE);
        }

        if (list != null) {
            Ui.label(graphics, inParty()
                            ? L.t("rotasutils.party.roster_caption")
                            : L.t("rotasutils.party.roster_empty_caption"),
                    list.x() + 2, list.y() - 12, Ui.INK_SOFT);
        }
        if (nearbyList != null) {
            Ui.label(graphics, L.t("rotasutils.party.nearby_caption"),
                    nearbyList.x() + 2, nearbyList.y() - 12, Ui.INK_SOFT);
            if (nearby().isEmpty()) {
                Ui.labelCentered(graphics, L.t("rotasutils.party.nobody_nearby"),
                        nearbyList.x() + nearbyList.width() / 2, nearbyList.y() + 16, Ui.INK_FADE);
            }
        }

        if (!inParty() && members().isEmpty()) {
            int centerX = list == null ? guiLeft + guiWidth / 2 : list.x() + list.width() / 2;
            int centerY = guiTop + guiHeight / 2 - 10;
            Ui.scaledCentered(graphics, L.t("rotasutils.party.tagline"), centerX, centerY, 1.2f, Ui.INK_SOFT);
            Ui.labelCentered(graphics, L.t("rotasutils.party.start_hint"), centerX, centerY + 18, Ui.INK_FADE);
        } else if (inParty() && members().isEmpty()) {
            Ui.labelCentered(graphics, L.t("rotasutils.party.loading"),
                    guiLeft + guiWidth / 2, guiTop + guiHeight / 2, Ui.INK_FADE);
        }
    }
}
