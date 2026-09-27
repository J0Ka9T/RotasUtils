package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GuidedContentTest {
    @Test void templatesCoverEveryRuntimeKind() {
        Set<String> kinds = new HashSet<>(); GuidedContentSchema.templates().forEach(t -> kinds.add(t.kind()));
        for (var kind : ContentRegistry.Kind.values()) { assertTrue(kinds.contains(kind.name().toLowerCase(Locale.ROOT)), kind.toString()); }
    }

    @Test void formRoundTripRetainsUnknownFieldsAndReturnsIndependentTemplates() {
        var template = GuidedContentSchema.templates().get(0); var original = template.document(); original.addProperty("addon_note", "retain");
        var form = new GuidedContentDocument(original);
        var copy = GuidedContentDocument.parse(form.json());
        assertEquals(original, copy.document());
        assertFalse(template.document().has("addon_note"));
    }
    @Test void suppliedTemplatesCompileTogetherThroughTheServerSchema() {
        var registry = new ContentRegistry(new ConditionEngine(net.schwarz.rotasutils.server.KernelPlayerContext.requirementAdapters()), new ActionEngine(Map.of()));
        var sources = GuidedContentSchema.templates().stream().map(template -> new ContentRegistry.Source(template.name(), ContentRegistry.Layer.SERVER, template.document())).toList();
        var prepared = registry.prepare(sources);
        assertTrue(prepared.valid(), prepared.issues().toString());
    }
    @Test void movingNestedEntriesPreservesTheirContentAndInvalidNumbersDoNotChangeTheDraft() {
        var form = GuidedContentDocument.parse("{\"schema\":1,\"kind\":\"boss\",\"body\":{\"phases\":[{\"label\":\"one\",\"threshold\":1},{\"label\":\"two\",\"threshold\":0.5}]}}");
        form.move(List.of("body","phases","1"),-1);
        assertEquals("two",form.node(List.of("body","phases","0","label")).getAsString());
        String before=form.json();
        assertThrows(IllegalArgumentException.class,()->form.setText(List.of("body","phases","0","threshold"),"NaN"));
        assertEquals(before,form.json());
    }
}
