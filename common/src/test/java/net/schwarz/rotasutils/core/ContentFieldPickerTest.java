package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.data.ParamSpec.ParamKind;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ContentFieldPickerTest {
    @Test void monsterSelectorsAndQuestMatchesUseEntityRegistry() {
        assertEquals(ParamKind.ENTITY, ContentFieldPicker.kind(List.of("body", "selector", "entities", "0")));
        assertEquals(ParamKind.ENTITY, ContentFieldPicker.kind(List.of("body", "selector", "exclude_entities", "2")));
        assertEquals(ParamKind.ENTITY, ContentFieldPicker.kind(List.of("body", "stages", "0", "objectives", "0", "match", "event.entity_type")));
        assertEquals(ParamKind.ENTITY_TAG, ContentFieldPicker.kind(List.of("body", "selector", "entity_tags", "0")));
        assertEquals(ParamKind.ENTITY_NAMESPACE, ContentFieldPicker.kind(List.of("body", "selector", "namespaces", "0")));
    }
    @Test void authoredContentIdsAreNotMistakenForVanillaEntityIds() {
        assertNull(ContentFieldPicker.kind(List.of("body", "phases", "0", "summon")));
        assertNull(ContentFieldPicker.kind(List.of("body", "reward")));
        assertNull(ContentFieldPicker.kind(List.of("id")));
        assertEquals(ParamKind.ITEM, ContentFieldPicker.kind(List.of("body", "trades", "0", "item")));
    }
}
