package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.data.ServerSettings;

import java.util.List;

@Environment(EnvType.CLIENT)
public class ServerSettingsScreen extends SimpleFieldScreen {
    private CompoundTag editingBaseline;
    private ServerSettings draft;

    public ServerSettingsScreen(Screen parent) {
        super("Server Settings", parent);
    }

    @Override
    protected Refresh refreshMode() {
        return Refresh.BANNER;
    }

    @Override
    protected Object watchedSource() {
        return net.schwarz.rotasutils.client.ClientState.serverSettings().save();
    }

    @Override
    protected void buildContent() {
        if (draft == null) {
            draft = ServerSettings.load(ClientState.serverSettings().save());
            editingBaseline = draft.save();
        }
        super.buildContent();
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Review"), button -> {
            if (!validateFields()) return;
            CompoundTag payload = new CompoundTag();
            payload.put("server_settings", draft.save());
            minecraft.setScreen(new ConfigReviewScreen(this, "save_server_settings", payload, editingBaseline));
        }).bounds(guiLeft + guiWidth - 68, guiTop + guiHeight - 24, 62, 18).build());
    }

    @Override
    protected String helpFor(Field field) {
        return switch (field.label()) {
            case "Administrator op level" -> "2-4; default 2. Permission required for administration.";
            case "Maximum active quests per player" -> "At least 1 quest; default 10.";
            case "Maximum party size" -> "1-64 players; default 10.";
            case "Party nearby radius" -> "At least 4 blocks; default 64.";
            case "Anti-farm memory (seconds)" -> "At least 60 seconds; default 3600.";
            case "Client request cooldown (ms)" -> "Advanced: 0 or more milliseconds; default 200.";
            case "Autosave interval (seconds)" -> "Advanced: at least 10 seconds; default 120.";
            case "Job change cooldown (hours)" -> "0 lets players change job any time; default 24.";
            case "Waystone recording cost (gold)" -> "0-1,000,000 gold, taken the first time a player records a waystone; default 100.";
            case "Waystone warp cost (gold)" -> "0-1,000,000 gold per warp; default 50.";
            case "Waystone cost per 1,000 blocks" -> "0-1,000,000 extra gold per 1,000 blocks warped in the same world; default 25.";
            default -> super.helpFor(field);
        };
    }

    @Override
    protected String validateValue(Field field, String value) {
        String error = super.validateValue(field, value);
        if (!error.isEmpty() || (field.kind() != Field.Kind.INT && field.kind() != Field.Kind.DOUBLE)) return error;
        double number = Double.parseDouble(value.trim());
        double min = switch (field.label()) {
            case "Maximum active quests per player", "Maximum party size" -> 1;
            case "Party nearby radius" -> 4;
            case "Anti-farm memory (seconds)" -> 60;
            case "Autosave interval (seconds)" -> 10;
            default -> 0;
        };
        double max = switch (field.label()) {
            case "Administrator op level" -> 4;
            case "Maximum party size" -> 64;
            case "Waystone recording cost (gold)", "Waystone warp cost (gold)",
                 "Waystone cost per 1,000 blocks" -> 1_000_000;
            default -> Double.MAX_VALUE;
        };
        return number < min || number > max ? helpFor(field) : "";
    }

    @Override
    protected void collectFields(List<Field> target) {
        target.add(number("Administrator op level",
                () -> String.valueOf(draft.adminOpLevel()),
                value -> draft.setAdminOpLevel(parseInt(value, 2))));
        target.add(toggle("Allow quest commands",
                () -> draft.allowQuestCommands() ? "Yes" : "No",
                value -> draft.setAllowQuestCommands(!draft.allowQuestCommands())));
        target.add(toggle("Quests must be accepted at a board",
                () -> draft.requireBoardForAccept() ? "Yes" : "No",
                value -> draft.setRequireBoardForAccept(!draft.requireBoardForAccept())));
        target.add(number("Maximum active quests per player",
                () -> String.valueOf(draft.maxActiveQuests()),
                value -> draft.setMaxActiveQuests(parseInt(value, 10))));
        target.add(toggle("Party system enabled",
                () -> draft.partySystemEnabled() ? "Yes" : "No",
                value -> draft.setPartySystemEnabled(!draft.partySystemEnabled())));
        target.add(number("Maximum party size",
                () -> String.valueOf(draft.maxPartySize()),
                value -> draft.setMaxPartySize(parseInt(value, 10))));
        target.add(decimal("Party nearby radius",
                () -> String.valueOf(draft.partyNearbyRadius()),
                value -> draft.setPartyNearbyRadius(parseDouble(value, 64))));
        target.add(toggle("Player-kill quests enabled",
                () -> draft.pvpQuestsEnabled() ? "Yes" : "No",
                value -> draft.setPvpQuestsEnabled(!draft.pvpQuestsEnabled())));
        target.add(toggle("Anti-farming enabled",
                () -> draft.antiFarmEnabled() ? "Yes" : "No",
                value -> draft.setAntiFarmEnabled(!draft.antiFarmEnabled())));
        target.add(number("Anti-farm memory (seconds)",
                () -> String.valueOf(draft.antiFarmMemorySeconds()),
                value -> draft.setAntiFarmMemorySeconds(parseInt(value, 3600))));
        target.add(number("Client request cooldown (ms)",
                () -> String.valueOf(draft.clientRequestCooldownMillis()),
                value -> draft.setClientRequestCooldownMillis(parseInt(value, 200))));
        target.add(number("Autosave interval (seconds)",
                () -> String.valueOf(draft.autosaveIntervalSeconds()),
                value -> draft.setAutosaveIntervalSeconds(parseInt(value, 120))));
        target.add(decimal("Job change cooldown (hours)",
                () -> trimHours(draft.jobChangeCooldownSeconds()),
                value -> draft.setJobChangeCooldownSeconds(Math.round(Math.max(0, parseDouble(value, 24)) * 3600))));
        target.add(toggle("Waystones enabled",
                () -> draft.waystonesEnabled() ? "Yes" : "No",
                value -> draft.setWaystonesEnabled(!draft.waystonesEnabled())));
        target.add(number("Waystone recording cost (gold)",
                () -> String.valueOf(draft.waystoneDiscoverCost()),
                value -> draft.setWaystoneDiscoverCost(parseInt(value, 100))));
        target.add(number("Waystone warp cost (gold)",
                () -> String.valueOf(draft.waystoneWarpCost()),
                value -> draft.setWaystoneWarpCost(parseInt(value, 50))));
        target.add(number("Waystone cost per 1,000 blocks",
                () -> String.valueOf(draft.waystoneWarpCostPerThousandBlocks()),
                value -> draft.setWaystoneWarpCostPerThousandBlocks(parseInt(value, 25))));
        target.add(toggle("Monster rank drops enabled",
                () -> draft.monsterDropsEnabled() ? "Yes" : "No",
                value -> draft.setMonsterDropsEnabled(!draft.monsterDropsEnabled())));
        target.add(toggle("Audit log enabled",
                () -> draft.auditLogEnabled() ? "Yes" : "No",
                value -> draft.setAuditLogEnabled(!draft.auditLogEnabled())));
        target.add(toggle("Validate before publishing",
                () -> draft.validateOnPublish() ? "Yes" : "No",
                value -> draft.setValidateOnPublish(!draft.validateOnPublish())));
    }

    private static String trimHours(long seconds) {
        double hours = seconds / 3600.0;
        return hours == Math.rint(hours) ? Long.toString((long) hours) : Double.toString(hours);
    }

    @Override protected void goBack() {
        if (draft == null || editingBaseline == null) { super.goBack(); return; }
        confirmLeavingDraft(draft.save(), editingBaseline, () -> {
            if (parentScreen() == null) { minecraft.setScreen(null); }
            else { minecraft.setScreen(parentScreen()); }
        });
    }
    @Override public void onClose() { goBack(); }
}
