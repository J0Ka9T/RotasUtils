package net.schwarz.rotasutils.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZoneCompassTest {
    @Test
    void eachWayOfTheCompassIsNamedFromAnOffset() {
        assertEquals("east", ZoneCommands.compass(10, 0));
        assertEquals("south", ZoneCommands.compass(0, 10));
        assertEquals("west", ZoneCommands.compass(-10, 0));
        assertEquals("north", ZoneCommands.compass(0, -10), "north is negative z, as in the game");
        assertEquals("south-east", ZoneCommands.compass(7, 7));
        assertEquals("north-west", ZoneCommands.compass(-7, -7));
    }
}
