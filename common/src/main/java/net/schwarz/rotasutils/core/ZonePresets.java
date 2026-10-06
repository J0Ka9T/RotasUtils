package net.schwarz.rotasutils.core;

public final class ZonePresets {
    private ZonePresets() {
    }

    public static ZoneDef apply(ZoneType type, ZoneDef zone) {
        ZoneCombatRules rules = zone.combatRules();
        ZoneFeatures features = zone.features().withType(type);
        return switch (type) {
            case CUSTOM -> zone.withFeatures(features);
            case TOWN -> danger(zone, ZoneDef.Danger.SAFE)
                    .withCombatRules(rules(rules, null, RuleBool.of(false), null, null))
                    .withFeatures(features.withIsolateMobs(false));
            case DUNGEON -> danger(zone, ZoneDef.Danger.DANGEROUS)
                    .withCombatRules(rules(rules, RuleBool.of(false), null, null, null))
                    .withFeatures(features.withIsolateMobs(true)
                            .withMovement(new ZoneMovement(true, features.movement().noFlight(), true))
                            .withMessages(titled(features.messages(), zone)));
            case BOSS_ARENA -> danger(zone, ZoneDef.Danger.DEADLY)
                    .withCombatRules(rules(rules, null, RuleBool.of(false), null, null))
                    .withFeatures(features.withIsolateMobs(true)
                            .withMovement(new ZoneMovement(true, true, true))
                            .withMessages(titled(features.messages(), zone)));
            case PVP_ARENA -> zone
                    .withCombatRules(rules(rules, RuleBool.of(true), RuleBool.of(false), RuleBool.of(true), RuleDouble.of(0)))
                    .withFeatures(features);
        };
    }

    private static ZoneDef danger(ZoneDef zone, ZoneDef.Danger danger) {
        return zone.withSettings(zone.name(), zone.levelMin(), zone.levelMax(), zone.priority(), zone.enabled(),
                danger, zone.recommendedMin(), zone.recommendedMax(), zone.xpMultiplier(), zone.transitionBlocks(),
                danger == ZoneDef.Danger.SAFE);
    }

    private static ZoneCombatRules rules(ZoneCombatRules current, RuleBool pvp, RuleBool hostileSpawning,
                                         RuleBool keepInventory, RuleDouble xpLoss) {
        return new ZoneCombatRules(pvp == null ? current.pvpEnabled() : pvp,
                hostileSpawning == null ? current.hostileSpawningEnabled() : hostileSpawning,
                current.playerDamageTakenMultiplier(), current.playerDamageDealtMultiplier(),
                current.healingMultiplier(), keepInventory == null ? current.keepInventory() : keepInventory,
                xpLoss == null ? current.rotasXpLossPercentage() : xpLoss, current.respawnTarget());
    }

    private static ZoneMessages titled(ZoneMessages messages, ZoneDef zone) {
        if (messages.hasEnter()) {
            return messages;
        }
        String name = zone.name().isBlank() ? zone.id() : zone.name();
        String title = name.length() > ZoneMessages.MAX_TEXT ? name.substring(0, ZoneMessages.MAX_TEXT) : name;
        return new ZoneMessages(title, messages.enterSubtitle(), messages.leaveTitle(), messages.sound());
    }
}
