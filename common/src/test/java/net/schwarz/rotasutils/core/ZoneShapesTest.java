package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneShapesTest {
    @Test
    void aSphereIsCentredOnTheBlockAtTheChosenRadius() {
        var area = (ZoneArea.Sphere) ZoneShapes.area(ZoneShapes.Shape.SPHERE, 2, 100, 70, -40, -64, 319);
        assertEquals(64, area.radius());
        assertTrue(area.contains(100, 70, -40) && area.contains(160, 70, -40) && !area.contains(170, 70, -40));
    }

    @Test
    void aBoxRoundMeSpansTheWholeHeightAndContainsTheCentre() {
        var area = (ZoneArea.Box) ZoneShapes.area(ZoneShapes.Shape.BOX, 1, 10, 70, 10, -64, 319);
        assertEquals(-64, area.minY());
        assertEquals(319, area.maxY());
        assertEquals(64, area.maxX() - area.minX() + 1);
        assertEquals(64, area.maxZ() - area.minZ() + 1);
        assertTrue(area.contains(10, 70, 10) && area.contains(10, -60, 10) && area.contains(10, 300, 10));
    }

    @Test
    void theWholeDimensionIsNoShapeAtAll() {
        assertNull(ZoneShapes.area(ZoneShapes.Shape.DIMENSION, 0, 0, 0, 0, -64, 319));
    }

    @Test
    void sizesAreClampedLabelledAndCycleThroughTheShapes() {
        assertEquals(5, ZoneShapes.sizeCount(ZoneShapes.Shape.SPHERE));
        assertEquals(1, ZoneShapes.sizeCount(ZoneShapes.Shape.DIMENSION));
        assertEquals("Sphere r256", ZoneShapes.label(ZoneShapes.Shape.SPHERE, 99));
        assertEquals("Box 32 x 32", ZoneShapes.label(ZoneShapes.Shape.BOX, -5));
        assertEquals(ZoneShapes.Shape.BOX, ZoneShapes.Shape.SPHERE.next());
        assertEquals(ZoneShapes.Shape.SPHERE, ZoneShapes.Shape.DIMENSION.next());
        assertEquals(ZoneShapes.Shape.SPHERE, ZoneShapes.Shape.parse("nonsense"));
        assertEquals(ZoneShapes.Shape.BOX, ZoneShapes.Shape.parse(" box "));
    }
}
