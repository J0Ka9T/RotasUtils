package net.schwarz.rotasutils.client.screen.admin;

import net.minecraft.core.BlockPos;
import net.schwarz.rotasutils.client.ClientHouseAdminState;
import net.schwarz.rotasutils.house.HouseConfig;
import net.schwarz.rotasutils.house.HouseDefinition;
import net.schwarz.rotasutils.house.HouseStatus;
import net.schwarz.rotasutils.house.HouseTier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class HouseAdminPresentation {
    private static final String EMPTY_OVERVIEW =
            "No houses yet. Use the House Wand to mark two corners, then create a house.";

    private HouseAdminPresentation() {
    }

    public enum StatusRole {
        GOOD,
        ACCENT,
        WARNING,
        OWNED,
        MUTED
    }

    public enum SelectionState {
        MISSING,
        PARTIAL,
        COMPLETE
    }

    public record OverviewRow(String id, String name, String dimension, String size,
                              long sizeX, long sizeY, long sizeZ, long volume, String tier,
                              boolean enabled, HouseStatus status, String tag, StatusRole statusRole,
                              int memberCount, long definitionRevision) {
        public OverviewRow {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(size, "size");
            Objects.requireNonNull(tier, "tier");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(tag, "tag");
            Objects.requireNonNull(statusRole, "statusRole");
            if (sizeX < 1 || sizeY < 1 || sizeZ < 1 || volume < 1 || memberCount < 0
                    || definitionRevision < 0) {
                throw new IllegalArgumentException("Invalid housing overview row");
            }
        }

        public String statusTag() {
            return tag;
        }

        public StatusRole role() {
            return statusRole;
        }
    }

    public record SelectionSummary(SelectionState state, String text, String dimension,
                                   BlockPos first, BlockPos second, long sizeX, long sizeY,
                                   long sizeZ, long volume) {
        public SelectionSummary {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(text, "text");
            if (state == SelectionState.COMPLETE
                    && (first == null || second == null || sizeX < 1 || sizeY < 1 || sizeZ < 1 || volume < 1)) {
                throw new IllegalArgumentException("Complete selection must have dimensions");
            }
            if (state != SelectionState.COMPLETE && (sizeX != 0 || sizeY != 0 || sizeZ != 0 || volume != 0)) {
                throw new IllegalArgumentException("Incomplete selection cannot have dimensions");
            }
        }

        public boolean complete() {
            return state == SelectionState.COMPLETE;
        }

        public boolean partial() {
            return state == SelectionState.PARTIAL;
        }
    }

    public record CreateDraft(String id, String name, String tier, long configRevision,
                              SelectionSummary selection) {
        public CreateDraft {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(tier, "tier");
            if (configRevision < 0) throw new IllegalArgumentException("Negative config revision");
        }

        public CreateDraft withId(String value) { return new CreateDraft(value, name, tier, configRevision, selection); }
        public CreateDraft withName(String value) { return new CreateDraft(id, value, tier, configRevision, selection); }
        public CreateDraft withTier(String value) { return new CreateDraft(id, name, value, configRevision, selection); }
    }

    public record EditDraft(String id, String name, String tier, boolean enabled,
                            long definitionRevision) {
        public EditDraft {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(tier, "tier");
            if (definitionRevision < 0) throw new IllegalArgumentException("Negative definition revision");
        }

        public EditDraft withName(String value) { return new EditDraft(id, value, tier, enabled, definitionRevision); }
        public EditDraft withTier(String value) { return new EditDraft(id, name, value, enabled, definitionRevision); }
        public EditDraft withEnabled(boolean value) { return new EditDraft(id, name, tier, value, definitionRevision); }
    }

    public record SettingsDraft(String currency, long paymentIntervalMillis, long reminderLeadMillis,
                                long graceMillis, int buyoutMultiplier, int baseMemberLimit,
                                long memberSlotPrice, int maxPurchasedMemberSlots,
                                List<HouseTier> tiers, long configRevision) {
        public SettingsDraft {
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(tiers, "tiers");
            tiers = List.copyOf(tiers);
            if (configRevision < 0) throw new IllegalArgumentException("Negative config revision");
        }
    }

    public static List<OverviewRow> overviewRows(ClientHouseAdminState state, String search) {
        Objects.requireNonNull(state, "state");
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        Map<String, ClientHouseAdminState.StatusSummary> statusById = state.statuses().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        ClientHouseAdminState.StatusSummary::houseId, value -> value));
        List<OverviewRow> rows = new ArrayList<>();
        for (HouseDefinition definition : state.definitions()) {
            if (!query.isEmpty()
                    && !definition.id().toLowerCase(Locale.ROOT).contains(query)
                    && !definition.name().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            ClientHouseAdminState.StatusSummary summary = statusById.get(definition.id());
            if (summary == null) continue;
            rows.add(row(definition, summary));
        }
        rows.sort(Comparator.comparing(OverviewRow::enabled).reversed()
                .thenComparingInt(row -> statusPriority(row.status(), row.enabled()))
                .thenComparing(OverviewRow::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(OverviewRow::id, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(OverviewRow::name)
                .thenComparing(OverviewRow::id));
        return List.copyOf(rows);
    }

    public static List<OverviewRow> rows(ClientHouseAdminState state, String search) {
        return overviewRows(state, search);
    }

    public static String statusTag(HouseStatus status, boolean enabled) {
        Objects.requireNonNull(status, "status");
        if (!enabled) return "DISABLED";
        return status == HouseStatus.BOUGHT_OUT ? "OWNED" : status.name();
    }

    public static StatusRole statusRole(HouseStatus status, boolean enabled) {
        return switch (statusTag(status, enabled)) {
            case "AVAILABLE" -> StatusRole.GOOD;
            case "ACTIVE" -> StatusRole.ACCENT;
            case "OVERDUE" -> StatusRole.WARNING;
            case "OWNED" -> StatusRole.OWNED;
            default -> StatusRole.MUTED;
        };
    }

    private static OverviewRow row(HouseDefinition definition, ClientHouseAdminState.StatusSummary summary) {
        var bounds = definition.bounds();
        long sizeX = (long) bounds.maxX() - bounds.minX() + 1L;
        long sizeY = (long) bounds.maxY() - bounds.minY() + 1L;
        long sizeZ = (long) bounds.maxZ() - bounds.minZ() + 1L;
        String tag = statusTag(summary.status(), definition.enabled());
        StatusRole role = statusRole(summary.status(), definition.enabled());
        return new OverviewRow(definition.id(), definition.name(), bounds.dimension(),
                sizeX + "x" + sizeY + "x" + sizeZ, sizeX, sizeY, sizeZ, bounds.volume(),
                definition.tier(), definition.enabled(), summary.status(), tag, role,
                summary.memberCount(), definition.revision());
    }

    private static int statusPriority(HouseStatus status, boolean enabled) {
        if (!enabled) return 100;
        return switch (status) {
            case AVAILABLE -> 0;
            case ACTIVE -> 1;
            case OVERDUE -> 2;
            case BOUGHT_OUT -> 3;
        };
    }

    public static SelectionSummary selection(ClientHouseAdminState state) {
        Objects.requireNonNull(state, "state");
        ClientHouseAdminState.SelectionSummary value = state.selection();
        if (value == null) {
            return new SelectionSummary(SelectionState.MISSING,
                    "No House Wand selection. Mark two corners to create a house.", null,
                    null, null, 0, 0, 0, 0);
        }
        if (value.first() == null || value.second() == null) {
            return new SelectionSummary(SelectionState.PARTIAL,
                    "Selection is partial. Mark the second corner with the House Wand.", value.dimension(),
                    value.first(), value.second(), 0, 0, 0, 0);
        }
        String text = "Selection: " + value.dimension() + " " + value.sizeX() + "x" + value.sizeY()
                + "x" + value.sizeZ() + " (" + value.volume() + " blocks)";
        return new SelectionSummary(SelectionState.COMPLETE, text, value.dimension(), value.first(), value.second(),
                value.sizeX(), value.sizeY(), value.sizeZ(), value.volume());
    }

    public static SelectionSummary selectionSummary(ClientHouseAdminState state) {
        return selection(state);
    }

    public static String emptyOverviewText() {
        return EMPTY_OVERVIEW;
    }

    public static String emptyStateText() {
        return emptyOverviewText();
    }

    public static CreateDraft createDraft(ClientHouseAdminState state) {
        Objects.requireNonNull(state, "state");
        String tier = state.tiers().isEmpty() ? "" : state.tiers().get(0).id();
        return new CreateDraft("", "", tier, state.configRevision(), selection(state));
    }

    public static CreateDraft createDraft(ClientHouseAdminState state, String id, String name, String tier) {
        Objects.requireNonNull(state, "state");
        return new CreateDraft(id, name, tier, state.configRevision(), selection(state));
    }

    public static EditDraft editDraft(ClientHouseAdminState state, String houseId) {
        Objects.requireNonNull(state, "state");
        HouseDefinition definition = state.definition(houseId);
        if (definition == null) throw new IllegalArgumentException("Unknown house: " + houseId);
        return new EditDraft(definition.id(), definition.name(), definition.tier(), definition.enabled(),
                definition.revision());
    }

    public static SettingsDraft settingsDraft(ClientHouseAdminState state) {
        Objects.requireNonNull(state, "state");
        HouseConfig config = state.config();
        return new SettingsDraft(config.currency(), config.paymentIntervalMillis(), config.reminderLeadMillis(),
                config.graceMillis(), config.buyoutMultiplier(), config.baseMemberLimit(), config.memberSlotPrice(),
                config.maxPurchasedMemberSlots(), state.tiers(), config.revision());
    }
}
