package net.schwarz.rotasutils.client.inventory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.schwarz.rotasutils.client.screen.L;

/** Stable tab ids for the inventory-backed Character Hub. */
@Environment(EnvType.CLIENT)
public enum CharacterHubTab {
    BAG("rotasutils.hub.bag"),
    JOB("rotasutils.hub.job"),
    SUB("rotasutils.hub.subclass"),
    PARTY("rotasutils.hub.party"),
    QUEST("rotasutils.hub.quest");

    private final String labelKey;

    CharacterHubTab(String labelKey) {
        this.labelKey = labelKey;
    }

    public String label() {
        return L.t(labelKey);
    }
}
