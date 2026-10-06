package net.schwarz.rotasutils.core;

import java.util.ArrayList;
import java.util.List;

public final class ZoneValidator {
    private ZoneValidator() {}
    public static ZoneApplyResult apply(ZoneDef live, ZoneDef candidate, long baseRevision) {
        if (live == null || candidate == null) return invalid(candidate, "Zone no longer exists");
        if (!live.id().equals(candidate.id())) return invalid(candidate, "Zone id is immutable");
        if (live.revision()!=baseRevision) return new ZoneApplyResult(ZoneApplyResult.Status.STALE,candidate,
                List.of("Live zone changed from revision "+baseRevision+" to "+live.revision()),List.of());
        List<String> warnings=new ArrayList<>();
        if (candidate.areas().isEmpty()) warnings.add("Empty Add list covers the whole dimension");
        ZoneDef applied=candidate.withRevision(Math.addExact(live.revision(),1));
        return new ZoneApplyResult(ZoneApplyResult.Status.APPLIED,applied,List.of(),warnings);
    }
    private static ZoneApplyResult invalid(ZoneDef zone,String error) { return new ZoneApplyResult(ZoneApplyResult.Status.INVALID,zone,List.of(error),List.of()); }
}
