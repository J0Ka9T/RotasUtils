package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ZoneDraftTest {
    @Test void historyIsBoundedAndSupportsUndoRedo() {
        ZoneDraft draft = new ZoneDraft(zone("z", 4));
        for (int i=0;i<60;i++) draft.replace(zone("z", 4).withSettings("Name "+i,1,2,0,true,ZoneDef.Danger.NORMAL,1,2,1,16,false));
        assertEquals(50, draft.undoSize());
        assertTrue(draft.undo()); assertTrue(draft.redo());
        assertEquals("Name 59", draft.candidate().name());
    }
    @Test void applyRejectsStaleCandidateAndKeepsDraft() {
        ZoneDraft draft = new ZoneDraft(zone("z", 4));
        draft.replace(draft.candidate().withSettings("edited",1,2,0,true,ZoneDef.Danger.NORMAL,1,2,1,16,false));
        ZoneApplyResult result = ZoneValidator.apply(zone("z", 5), draft.candidate(), draft.baseRevision());
        assertEquals(ZoneApplyResult.Status.STALE, result.status());
        assertEquals("edited", draft.candidate().name());
    }
    @Test void validApplyIsAtomicAndIncrementsRevision() {
        ZoneDef live=zone("z",4), candidate=live.withSettings("edited",1,2,0,true,ZoneDef.Danger.NORMAL,1,2,1,16,false);
        ZoneApplyResult result=ZoneValidator.apply(live,candidate,4);
        assertEquals(ZoneApplyResult.Status.APPLIED,result.status());
        assertEquals(5,result.zone().revision()); assertEquals("edited",result.zone().name());
    }
    private static ZoneDef zone(String id,long rev) { return new ZoneDef(id,id,"minecraft:overworld",List.of(),1,2,0,true,
            ZoneDef.Danger.NORMAL,1,2,1,16,false,List.of(),rev,ZoneCombatRules.inherit(),List.of()); }
}
