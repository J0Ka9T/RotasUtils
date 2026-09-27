package net.schwarz.rotasutils.core;

import java.util.List;

public record ZoneApplyResult(Status status, ZoneDef zone, List<String> errors, List<String> warnings) {
    public enum Status { APPLIED, STALE, INVALID }
    public ZoneApplyResult { errors=List.copyOf(errors); warnings=List.copyOf(warnings); }
}
