package net.schwarz.rotasutils.client;

import net.schwarz.rotasutils.core.ZoneArea;
import net.schwarz.rotasutils.core.ZoneDef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ZoneColors {
    public static final int[][] PALETTE = {
            {90, 217, 230},
            {255, 166, 64},
            {160, 125, 255},
            {96, 224, 140},
            {255, 100, 160},
            {240, 220, 80},
            {80, 150, 255},
            {255, 80, 80},
            {175, 240, 90},
            {230, 130, 255},
            {60, 200, 170},
            {215, 175, 125},
    };
    static final int NEIGHBOUR_GAP = 16;

    private ZoneColors() {
    }

    public static Map<String, Integer> assign(Collection<ZoneDef> zones) {
        List<ZoneDef> ordered = new ArrayList<>(zones);
        ordered.sort(Comparator.comparing(ZoneDef::id));
        List<ZoneArea.Bounds> envelopes = new ArrayList<>(ordered.size());
        ordered.forEach(zone -> envelopes.add(envelope(zone)));
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            int[] taken = new int[PALETTE.length];
            for (int j = 0; j < i; j++) {
                if (neighbours(ordered.get(i), envelopes.get(i), ordered.get(j), envelopes.get(j))) {
                    taken[result.get(ordered.get(j).id())]++;
                }
            }
            int best = 0;
            for (int colour = 0; colour < PALETTE.length; colour++) {
                if (taken[colour] < taken[best]) {
                    best = colour;
                }
                if (taken[colour] == 0) {
                    best = colour;
                    break;
                }
            }
            result.put(ordered.get(i).id(), best);
        }
        return result;
    }

    public static Map<String, int[]> colours(Collection<ZoneDef> zones) {
        Map<String, int[]> result = new HashMap<>();
        assign(zones).forEach((id, index) -> result.put(id, PALETTE[index]));
        return result;
    }

    static boolean neighbours(ZoneDef a, ZoneArea.Bounds boundsA, ZoneDef b, ZoneArea.Bounds boundsB) {
        if (!a.dimension().isEmpty() && !b.dimension().isEmpty() && !a.dimension().equals(b.dimension())) {
            return false;
        }
        if (boundsA == null || boundsB == null) {
            return true;
        }
        return boundsA.minX() - NEIGHBOUR_GAP <= boundsB.maxX() && boundsA.maxX() + NEIGHBOUR_GAP >= boundsB.minX()
                && boundsA.minZ() - NEIGHBOUR_GAP <= boundsB.maxZ() && boundsA.maxZ() + NEIGHBOUR_GAP >= boundsB.minZ()
                && boundsA.minY() - NEIGHBOUR_GAP <= boundsB.maxY() && boundsA.maxY() + NEIGHBOUR_GAP >= boundsB.minY();
    }

    static ZoneArea.Bounds envelope(ZoneDef zone) {
        if (zone.areas().isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (ZoneArea area : zone.areas()) {
            ZoneArea.Bounds bounds = area.bounds();
            minX = Math.min(minX, bounds.minX());
            minY = Math.min(minY, bounds.minY());
            minZ = Math.min(minZ, bounds.minZ());
            maxX = Math.max(maxX, bounds.maxX());
            maxY = Math.max(maxY, bounds.maxY());
            maxZ = Math.max(maxZ, bounds.maxZ());
        }
        return new ZoneArea.Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
