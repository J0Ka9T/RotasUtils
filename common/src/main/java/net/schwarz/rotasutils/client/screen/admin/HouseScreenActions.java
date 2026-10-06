package net.schwarz.rotasutils.client.screen.admin;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

public final class HouseScreenActions {
    public enum OverviewTarget {
        NONE,
        GET_WAND,
        CREATE,
        RENTAL_SETTINGS,
        EDIT
    }

    public record Route(OverviewTarget target, String houseId) {
        public Route {
            Objects.requireNonNull(target, "target");
            houseId = houseId == null ? "" : houseId;
        }
    }

    public record EditorContext(String houseId, HouseAdminPresentation.EditDraft draft) {
        public EditorContext {
            if (houseId == null || houseId.isBlank()) {
                throw new IllegalArgumentException("House id is required");
            }
            Objects.requireNonNull(draft, "draft");
            if (!houseId.equals(draft.id())) {
                throw new IllegalArgumentException("Editor house and draft ids differ");
            }
        }
    }

    public static final class RemovalResult {
        private final String houseId;
        private final long revision;
        private final boolean confirmed;

        private RemovalResult(String houseId, long revision, boolean confirmed) {
            this.houseId = requireHouseId(houseId);
            if (revision < 0) {
                throw new IllegalArgumentException("Revision must be non-negative");
            }
            this.revision = revision;
            this.confirmed = confirmed;
        }

        public String houseId() {
            return houseId;
        }

        public long revision() {
            return revision;
        }

        public boolean confirmed() {
            return confirmed;
        }
    }

    private HouseScreenActions() {
    }

    public static Route routeOverview(String action) {
        return routeOverview(action, "");
    }

    public static Route routeOverview(String action, String selectedHouseId) {
        String value = action == null ? "" : action.trim();
        if (value.equals("get_wand") || value.equals("give_house_wand")) {
            return new Route(OverviewTarget.GET_WAND, "");
        }
        if (value.equals("create") || value.equals("create_from_selection")) {
            return new Route(OverviewTarget.CREATE, "");
        }
        if (value.equals("rental_settings") || value.equals("settings")) {
            return new Route(OverviewTarget.RENTAL_SETTINGS, "");
        }
        if (value.startsWith("house:")) {
            return new Route(OverviewTarget.EDIT, value.substring("house:".length()));
        }
        if (value.startsWith("edit:")) {
            return new Route(OverviewTarget.EDIT, value.substring("edit:".length()));
        }
        if (value.equals("edit") && selectedHouseId != null && !selectedHouseId.isBlank()) {
            return new Route(OverviewTarget.EDIT, selectedHouseId);
        }
        return new Route(OverviewTarget.NONE, "");
    }

    public static CompoundTag createPayload(String id, String name, String tier, long configRevision, boolean configure) {
        CompoundTag payload = createPayload(id, name, tier, configRevision);
        if (configure) {
            payload.putBoolean("configure", true);
        }
        return payload;
    }

    public static CompoundTag createPayload(String id, String name, String tier, long configRevision) {
        requireRevision(configRevision);
        CompoundTag payload = new CompoundTag();
        payload.putString("id", requireHouseId(id));
        payload.putString("name", requireText(name, "Name"));
        payload.putString("tier", requireHouseId(tier));
        payload.putLong("config_revision", configRevision);
        return payload;
    }

    public static CompoundTag editPayload(String id, String name, String tier,
                                          boolean enabled, long definitionRevision) {
        requireRevision(definitionRevision);
        CompoundTag payload = new CompoundTag();
        payload.putString("id", requireHouseId(id));
        payload.putString("name", requireText(name, "Name"));
        payload.putString("tier", requireHouseId(tier));
        payload.putBoolean("enabled", enabled);
        payload.putLong("revision", definitionRevision);
        return payload;
    }

    public static CompoundTag replaceBoundsPayload(String id, long definitionRevision) {
        requireRevision(definitionRevision);
        CompoundTag payload = new CompoundTag();
        payload.putString("id", requireHouseId(id));
        payload.putLong("revision", definitionRevision);
        return payload;
    }

    public static RemovalResult confirmRemoval(String id, long revision) {
        return new RemovalResult(id, revision, true);
    }

    public static RemovalResult cancelRemoval(String id, long revision) {
        return new RemovalResult(id, revision, false);
    }

    public static CompoundTag removePayload(RemovalResult result) {
        Objects.requireNonNull(result, "result");
        if (!result.confirmed()) {
            throw new IllegalStateException("House removal was not confirmed");
        }
        CompoundTag payload = new CompoundTag();
        payload.putString("id", result.houseId());
        payload.putLong("revision", result.revision());
        return payload;
    }

    public static boolean canDisableOrRemove(HouseAdminPresentation.OverviewRow row) {
        return row != null && row.memberCount() == 0
                && row.status() == net.schwarz.rotasutils.house.HouseStatus.AVAILABLE;
    }

    public static String occupiedReason(HouseAdminPresentation.OverviewRow row) {
        return canDisableOrRemove(row) ? "" : "House is occupied";
    }

    public static EditorContext editorContext(String houseId, HouseAdminPresentation.EditDraft draft) {
        return new EditorContext(houseId, draft);
    }

    private static String requireHouseId(String value) {
        if (value == null || !value.matches("[a-z0-9_.-]{1,64}")) {
            throw new IllegalArgumentException("Invalid house id");
        }
        return value;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank() || value.length() > 96) {
            throw new IllegalArgumentException(label + " is invalid");
        }
        return value;
    }

    private static void requireRevision(long revision) {
        if (revision < 0) {
            throw new IllegalArgumentException("Revision must be non-negative");
        }
    }
}
