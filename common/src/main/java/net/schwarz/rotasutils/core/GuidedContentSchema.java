package net.schwarz.rotasutils.core;

import com.google.gson.*;
import java.util.*;

public final class GuidedContentSchema {
    public record Template(String name, String kind, JsonObject document) {
        public Template { document = document.deepCopy(); }
        @Override public JsonObject document() { return document.deepCopy(); }
    }
    private static final List<Template> TEMPLATES = new ArrayList<>();
    private static final Map<String, JsonObject> FIELDS = new LinkedHashMap<>();
    static {
        add("action-gold", """
{
  "schema": 1,
  "id": "rotas:action/warden_gold",
  "kind": "action",
  "body": {
    "type": "currency",
    "id": "rotas:gold",
    "amount": 40
  }
}
""");
        add("boss-warden", """
{
  "schema": 1,
  "id": "rotas:boss/warden",
  "kind": "boss",
  "body": {
    "label": "The Smoke Warden",
    "arena_radius": 48,
    "leash": true,
    "reset_seconds": 45,
    "enrage_seconds": 300,
    "enrage_attributes": {
      "minecraft:generic.attack_damage": {
        "multiplier": 2
      }
    },
    "minimum_share": 0.1,
    "reward": "rotas:reward/warden",
    "loot": "rotas:loot/warden",
    "phases": [
      {
        "threshold": 1,
        "label": "The Smoke Warden wakes"
      },
      {
        "threshold": 0.5,
        "label": "The Smoke Warden roars",
        "attributes": {
          "minecraft:generic.armor": {
            "add": 8
          }
        }
      }
    ]
  }
}
""");
        add("item-warden-blade", """
{
  "schema": 1,
  "id": "rotas:item/warden_blade",
  "kind": "item",
  "body": {
    "item": "minecraft:iron_sword",
    "slot": "MAINHAND",
    "min_level": 1,
    "max_level": 60,
    "name": "{rarity} Warden Blade Lv{level}",
    "lore": [
      "Taken from the smoke"
    ],
    "rarities": {
      "rotas:rarity/common": 9,
      "rotas:rarity/rare": 2
    },
    "modifiers": [
      {
        "attribute": "minecraft:generic.attack_damage",
        "base": 2,
        "per_level": 0.4
      }
    ],
    "requirement": {
      "min_level": 5
    }
  }
}
""");
        add("item-warden-boots", """
{
  "schema": 1,
  "id": "rotas:item/warden_boots",
  "kind": "item",
  "body": {
    "item": "minecraft:iron_boots",
    "slot": "FEET",
    "set": "rotas:set/warden",
    "rarities": {
      "rotas:rarity/common": 1
    },
    "modifiers": [
      {
        "attribute": "minecraft:generic.armor",
        "base": 1,
        "per_level": 0.1
      }
    ]
  }
}
""");
        add("item-warden-helm", """
{
  "schema": 1,
  "id": "rotas:item/warden_helm",
  "kind": "item",
  "body": {
    "item": "minecraft:iron_helmet",
    "slot": "HEAD",
    "set": "rotas:set/warden",
    "rarities": {
      "rotas:rarity/common": 1
    },
    "modifiers": [
      {
        "attribute": "minecraft:generic.armor",
        "base": 1,
        "per_level": 0.1
      }
    ]
  }
}
""");
        add("loot-warden", """
{
  "schema": 1,
  "id": "rotas:loot/warden",
  "kind": "loot",
  "body": {
    "min_rolls": 1,
    "max_rolls": 2,
    "entries": [
      {
        "weight": 8,
        "item": "minecraft:bone",
        "min_count": 1,
        "max_count": 3
      },
      {
        "weight": 3,
        "profile": "rotas:item/warden_helm"
      },
      {
        "weight": 3,
        "profile": "rotas:item/warden_boots"
      },
      {
        "weight": 1,
        "profile": "rotas:item/warden_blade"
      }
    ]
  }
}
""");
        add("merchant-smith", """
{
  "schema": 1,
  "id": "rotas:merchant/town_smith",
  "kind": "merchant",
  "body": {
    "label": "Town Smith",
    "trades": [
      {
        "key": "blade",
        "label": "Warden Blade",
        "profile": "rotas:item/warden_blade",
        "item_level": 20,
        "stock": 4,
        "restock_seconds": 86400,
        "per_player_limit": 1,
        "costs": [
          {
            "currency": "rotas:gold",
            "amount": 250
          }
        ]
      },
      {
        "key": "trade_up",
        "label": "Iron for a diamond",
        "item": "minecraft:diamond",
        "count": 1,
        "costs": [
          {
            "item": "minecraft:iron_ingot",
            "amount": 6
          },
          {
            "currency": "rotas:gold",
            "amount": 10
          }
        ]
      }
    ]
  }
}
""");
        add("monster-warden", """
{
  "schema": 1,
  "id": "rotas:monster/warden",
  "kind": "monster",
  "body": {
    "manual_only": true,
    "priority": 50,
    "selector": {
      "entities": [
        "minecraft:zombie"
      ]
    },
    "level": {
      "strategy": "NEAREST_PLAYER",
      "min": 10,
      "max": 60,
      "offset": 5
    },
    "base_xp": 400,
    "xp_per_level": 20,
    "tiers": {
      "rotas:tier/warden": 1
    },
    "boss": "rotas:boss/warden",
    "loot": "rotas:loot/warden",
    "name": "The Smoke Warden Lv{level}"
  }
}
""");
        add("quest-daily-hunt", """
{
  "schema": 1,
  "id": "rotas:quest/daily_hunt",
  "kind": "quest",
  "body": {
    "label": "Daily Hunt",
    "reset": "DAILY",
    "bounty_limit": 20,
    "reward": "rotas:reward/warden",
    "stages": [
      {
        "label": "Defeat monsters",
        "objectives": [
          {
            "event": "rotas:monster_defeated",
            "count": 5,
            "label": "Defeat 5 marked monsters"
          }
        ]
      }
    ]
  }
}
""");
        add("rarity-common", """
{
  "schema": 1,
  "id": "rotas:rarity/common",
  "kind": "rarity",
  "body": {
    "label": "Common",
    "color": "white",
    "rank": 0,
    "modifier_multiplier": 1
  }
}
""");
        add("rarity-rare", """
{
  "schema": 1,
  "id": "rotas:rarity/rare",
  "kind": "rarity",
  "body": {
    "label": "Rare",
    "color": "blue",
    "rank": 2,
    "modifier_multiplier": 1.8
  }
}
""");
        add("reward-warden", """
{
  "schema": 1,
  "id": "rotas:reward/warden",
  "kind": "reward",
  "body": {
    "actions": [
      "rotas:action/warden_gold"
    ]
  }
}
""");
        add("set-warden", """
{
  "schema": 1,
  "id": "rotas:set/warden",
  "kind": "set",
  "body": {
    "label": "Warden Regalia",
    "pieces": [
      "rotas:item/warden_helm",
      "rotas:item/warden_boots"
    ],
    "bonuses": [
      {
        "pieces": 2,
        "modifiers": [
          {
            "attribute": "minecraft:generic.armor",
            "base": 4
          }
        ]
      }
    ]
  }
}
""");
        add("tier-warden", """
{
  "schema": 1,
  "id": "rotas:tier/warden",
  "kind": "tier",
  "body": {
    "label": "Warden",
    "rank": 5,
    "affix_count": 0,
    "xp_multiplier": 4,
    "loot_multiplier": 3,
    "boss": true,
    "attributes": {
      "minecraft:generic.max_health": {
        "multiplier": 6
      }
    }
  }
}
""");
        add("action-swift-burst", """
{
  "schema": 1,
  "id": "rotas:action/swift_burst",
  "kind": "action",
  "body": {
    "type": "effect",
    "target": "self",
    "id": "minecraft:speed",
    "duration": 100,
    "amplifier": 1
  }
}
""");
        add("affix-armored", """
{
  "schema": 1,
  "id": "rotas:affix/armored",
  "kind": "affix",
  "body": {
    "weight": 3,
    "min_tier": 1,
    "tags": [
      "slow"
    ],
    "incompatible": [
      "speed"
    ],
    "xp_multiplier": 1.1,
    "attributes": {
      "minecraft:generic.armor": {
        "add": 6
      },
      "minecraft:generic.movement_speed": {
        "multiplier": 0.9
      }
    }
  }
}
""");
        add("affix-swift", """
{
  "schema": 1,
  "id": "rotas:affix/swift",
  "kind": "affix",
  "body": {
    "weight": 4,
    "min_tier": 1,
    "tags": [
      "speed"
    ],
    "incompatible": [
      "slow"
    ],
    "xp_multiplier": 1.2,
    "attributes": {
      "minecraft:generic.movement_speed": {
        "multiplier": 1.2
      }
    },
    "hooks": [
      {
        "event": "HURT",
        "cooldown": 200,
        "actions": [
          "rotas:action/swift_burst"
        ]
      }
    ]
  }
}
""");
        add("monster-undead", """
{
  "schema": 1,
  "id": "rotas:monster/undead",
  "kind": "monster",
  "body": {
    "priority": 10,
    "apply_existing": false,
    "selector": {
      "entities": [
        "minecraft:zombie",
        "minecraft:skeleton"
      ],
      "dimensions": [
        "minecraft:overworld"
      ]
    },
    "level": {
      "strategy": "NEAREST_PLAYER",
      "min": 1,
      "max": 60,
      "offset": 0
    },
    "base_xp": 40,
    "xp_per_level": 6,
    "tiers": {
      "rotas:tier/normal": 20,
      "rotas:tier/elite": 3
    },
    "affixes": [
      "rotas:affix/swift",
      "rotas:affix/armored"
    ],
    "attributes": {
      "minecraft:generic.max_health": {
        "multiplier": 1,
        "per_level": 0.04
      }
    },
    "name": "{name} [{tier} {level}]"
  }
}
""");
        add("tier-elite", """
{
  "schema": 1,
  "id": "rotas:tier/elite",
  "kind": "tier",
  "body": {
    "label": "Elite",
    "rank": 2,
    "affix_count": 1,
    "xp_multiplier": 2.5,
    "loot_multiplier": 2,
    "attributes": {
      "minecraft:generic.max_health": {
        "multiplier": 2
      },
      "minecraft:generic.attack_damage": {
        "multiplier": 1.4
      }
    }
  }
}
""");
        add("tier-normal", """
{
  "schema": 1,
  "id": "rotas:tier/normal",
  "kind": "tier",
  "body": {
    "label": "Common",
    "rank": 0,
    "affix_count": 0,
    "xp_multiplier": 1,
    "loot_multiplier": 1
  }
}
""");
        add("action", """
{
  "schema": 1,
  "id": "rotas:action/welcome",
  "kind": "action",
  "body": {
    "type": "set_variable",
    "key": "rpg.welcomed",
    "value": "yes"
  }
}
""");
        add("condition", """
{
  "schema": 1,
  "id": "rotas:condition/welcome",
  "kind": "condition",
  "body": {
    "type": "requirement",
    "name": "MIN_LEVEL",
    "params": {
      "level": "1"
    }
  }
}
""");
        add("reward", """
{
  "schema": 1,
  "id": "rotas:reward/welcome",
  "kind": "reward",
  "body": {
    "actions": [
      "rotas:action/welcome"
    ]
  }
}
""");
        add("rule", """
{
  "schema": 1,
  "id": "rotas:rule/welcome",
  "kind": "rule",
  "body": {
    "event": "rotas:player_login",
    "scope": "once",
    "condition": {
      "type": "ref",
      "id": "rotas:condition/welcome"
    },
    "reward": "rotas:reward/welcome"
  }
}
""");
        add("gold", """
{
  "schema": 1,
  "id": "rotas:action/starter_gold",
  "kind": "action",
  "body": {
    "type": "currency",
    "id": "rotas:gold",
    "amount": 25
  }
}
""");
        add("points", """
{
  "schema": 1,
  "id": "rotas:action/starter_points",
  "kind": "action",
  "body": {
    "type": "stat_points",
    "amount": 2
  }
}
""");
        add("starter", """
{
  "schema": 1,
  "id": "rotas:reward/starter",
  "kind": "reward",
  "body": {
    "actions": [
      "rotas:action/starter_gold",
      "rotas:action/starter_points"
    ]
  }
}
""");
        add("vitality", """
{
  "schema": 1,
  "id": "rotas:stat/vitality",
  "kind": "stat",
  "body": {
    "base": 0,
    "per_level": 0,
    "min": 0,
    "max": 100,
    "attribute": "minecraft:generic.max_health",
    "attribute_scale": 2,
    "operation": "ADD"
  }
}
""");
        fields("condition", """
{"type": "always", "id": "rotas:condition/welcome", "children": [{"type": "always"}], "count": 1, "name": "player.level", "op": "ge", "value": 1, "params": {}}
""");
        fields("action", """
{"type": "stat_points", "target": "self", "id": "rotas:gold", "amount": 1, "count": 1, "profile": "rotas:item/warden_blade", "table": "rotas:loot/warden", "level": 1, "multiplier": 1, "key": "rpg.example", "value": "yes", "duration": 100, "amplifier": 0}
""");
        fields("reward", """
{"condition": {"type": "always"}, "actions": ["rotas:action/welcome"]}
""");
        fields("rule", """
{"event": "rotas:player_login", "scope": "once", "condition": {"type": "always"}, "reward": "rotas:reward/welcome"}
""");
        fields("monster", """
{"manual_only": false, "apply_existing": false, "reward": "rotas:reward/warden", "loot": "rotas:loot/warden", "boss": "rotas:boss/warden"}
""");
        fields("monster/level", """
{"strategy": "FIXED", "value": 1, "min": 1, "max": 60, "offset": 0, "expression": "player.level"}
""");
        fields("affix", """
{"min_level": 1, "selector": {}, "condition": {"type": "always"}, "hooks": [{"event": "HURT", "cooldown": 20, "interval": 100, "condition": {"type": "always"}, "actions": ["rotas:action/swift_burst"]}]}
""");
        fields("boss", """
{"reset_seconds": 45, "enrage_seconds": 300, "minimum_share": 0.1}
""");
        fields("boss/phases/*", """
{"on_enter": [], "interval": 100, "on_interval": [], "summon": "rotas:monster/warden", "summon_count": 1}
""");
        fields("item", """
{"curios_slot": "", "requirement": {"min_level": 1, "stats": {}}, "set": "rotas:set/warden"}
""");
        fields("item/modifiers/*", """
{"operation": "ADD", "base": 0, "per_level": 0}
""");
        fields("loot/entries/*", """
{"condition": {"type": "always"}, "profile": "rotas:item/warden_blade", "item": "minecraft:stone", "weight": 1, "min_count": 1, "max_count": 1}
""");
        fields("quest", """
{"requirement": {"type": "always"}, "cooldown_seconds": 0, "repeatable": false}
""");
        fields("quest/stages/*", """
{"reward": "rotas:reward/warden", "branches": [{"stage": 0, "condition": {"type": "always"}}]}
""");
        fields("quest/stages/*/objectives/*", """
{"match": {}, "condition": {"type": "always"}}
""");
        fields("quest/stages/*/objectives/*/match", """
{"event.entity_type":"minecraft:zombie","event.item":"minecraft:apple","event.block":"minecraft:stone", "event.dimension":"minecraft:overworld", "event.biome":"minecraft:plains", "event.dialogue":"hello", "event.choice":"response", "event.dialogue_complete":"true"}
""");
        fields("merchant", """
{"requirement": {"type": "always"}}
""");
        fields("merchant/trades/*", """
{"condition": {"type": "always"}, "restock_seconds": 0, "per_player_limit": 0, "stock": 0, "count": 1, "item_level": 1}
""");
        fields("monster/selector", """
{"entities": [], "entity_tags": [], "namespaces": [], "biomes": [], "dimensions": [], "spawn_reasons": [], "regions": [], "exclude_entities": []}
""");
        fields("affix/selector", """
{"entities": [], "entity_tags": [], "namespaces": [], "biomes": [], "dimensions": [], "spawn_reasons": [], "regions": [], "exclude_entities": []}
""");
        fields("root", """
{"schema":1,"id":"rotas:stat/new","kind":"stat","enabled":true,"requires":[],"body":{}}
""");
    }
    private GuidedContentSchema() { }
    private static void add(String name, String text) {
        JsonObject document = JsonParser.parseString(text).getAsJsonObject();
        String kind = document.get("kind").getAsString();
        TEMPLATES.add(new Template(name, kind, document));
        learn(kind, document.getAsJsonObject("body"));
    }
    private static void learn(String path, JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject defaults = FIELDS.computeIfAbsent(path, key -> new JsonObject());
            element.getAsJsonObject().entrySet().forEach(entry -> {
                if (!defaults.has(entry.getKey())) { defaults.add(entry.getKey(), entry.getValue().deepCopy()); }
                learn(path + "/" + entry.getKey(), entry.getValue());
            });
        } else if (element.isJsonArray()) {
            for (JsonElement value : element.getAsJsonArray()) { learn(path + "/*", value); }
        }
    }
    private static void fields(String path, String json) { learn(path, JsonParser.parseString(json)); }
    public static List<Template> templates() { return List.copyOf(TEMPLATES); }
    public static JsonObject defaults(String kind, List<String> path) {
        String key = path.isEmpty() ? "root" : kind + path.stream().skip(1).map(part -> "/" + (part.matches("[0-9]+") ? "*" : part)).reduce("", String::concat);
        return FIELDS.getOrDefault(key, new JsonObject()).deepCopy();
    }
    public static JsonElement newEntry(String kind, List<String> path, JsonArray array) {
        if (!array.isEmpty()) { return array.get(array.size() - 1).deepCopy(); }
        List<String> child = new ArrayList<>(path); child.add("0");
        JsonObject defaults = defaults(kind, child);
        return defaults.size() == 0 ? new JsonPrimitive("") : defaults;
    }
    public static String label(String field) {
        String text = field.replace('_', ' ');
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    public static List<String> options(String kind, List<String> path) {
        if (path.isEmpty()) { return List.of(); }
        String field = path.get(path.size() - 1);
        if (field.equals("kind") && path.size() == 1) { return Arrays.stream(ContentRegistry.Kind.values()).map(v -> v.name().toLowerCase(Locale.ROOT)).toList(); }
        if (field.equals("operation")) { return List.of("ADD", "MULTIPLY_BASE", "MULTIPLY_TOTAL"); }
        if (field.equals("strategy") && kind.equals("monster")) { return Arrays.stream(MonsterDefinitions.Strategy.values()).map(Enum::name).toList(); }
        return List.of();
    }
}
