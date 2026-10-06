package net.schwarz.rotasutils.core;

import java.util.ArrayList;
import java.util.List;

public final class TradeKit {
    private TradeKit() {
    }

    public record Cooking(String recipe, long finishAt, int star) {
        public Cooking(String recipe, long finishAt) {
            this(recipe, finishAt, 0);
        }

        public boolean done(long now) {
            return now >= finishAt;
        }
    }

public static List<String> parseLearned(String text) {
        List<String> out = new ArrayList<>();
        for (String part : text == null ? new String[0] : text.split(",")) {
            if (!part.isBlank() && !out.contains(part.trim())) out.add(part.trim());
        }
        return out;
    }

    public static String formatLearned(List<String> ids) {
        return String.join(",", ids);
    }

    public static List<Cooking> parseQueue(String text) {
        List<Cooking> out = new ArrayList<>();
        for (String part : text == null ? new String[0] : text.split(",")) {
            int at = part.indexOf('@');
            if (at <= 0) continue;
            try {
                String rest = part.substring(at + 1);
                int colon = rest.indexOf(':');
                long finish = Long.parseLong(colon < 0 ? rest : rest.substring(0, colon));
                int star = colon < 0 ? 0 : Math.max(0, Math.min(2, Integer.parseInt(rest.substring(colon + 1))));
                out.add(new Cooking(part.substring(0, at), finish, star));
            } catch (NumberFormatException malformed) {
            }
        }
        return out;
    }

    public static String formatQueue(List<Cooking> queue) {
        StringBuilder out = new StringBuilder();
        for (Cooking cooking : queue) {
            if (out.length() > 0) out.append(',');
            out.append(cooking.recipe()).append('@').append(cooking.finishAt());
            if (cooking.star() > 0) out.append(':').append(cooking.star());
        }
        return out.toString();
    }

public record Held(String item, int star, int count, java.util.Set<String> tags) {
        public Held(String item, int star, int count) {
            this(item, star, count, java.util.Set.of());
        }

        boolean pays(TradeBook.Ingredient need) {
            for (String option : need.options()) {
                if (matchesOption(option)) return true;
            }
            return false;
        }

        private boolean matchesOption(String option) {
            if (option.startsWith("#")) {
                String targetTag = option.substring(1);
                if (tags.contains(targetTag)) return true;
                if (targetTag.startsWith("forge:")) {
                    String sub = targetTag.substring(6);
                    if (tags.contains("c:" + sub)) return true;
                    int slash = sub.lastIndexOf('/');
                    if (slash >= 0 && tags.contains("c:" + sub.substring(slash + 1))) return true;
                } else if (targetTag.startsWith("c:")) {
                    String sub = targetTag.substring(2);
                    if (tags.contains("forge:" + sub)) return true;
                    int slash = sub.lastIndexOf('/');
                    if (slash >= 0 && tags.contains("forge:" + sub.substring(slash + 1))) return true;
                }
                return false;
            }
            if (item.equals(option)) return true;
            return matchesCommonTagForVanillaItem(option);
        }

        private boolean matchesCommonTagForVanillaItem(String option) {
            return switch (option) {
                case "minecraft:wheat" -> hasAnyTag("forge:crops/wheat", "c:crops/wheat", "c:wheat", "forge:wheat");
                case "minecraft:carrot" -> hasAnyTag("forge:crops/carrot", "c:crops/carrot", "c:carrots", "forge:carrots");
                case "minecraft:potato" -> hasAnyTag("forge:crops/potato", "c:crops/potato", "c:potatoes", "forge:potatoes");
                case "minecraft:beetroot" -> hasAnyTag("forge:crops/beetroot", "c:crops/beetroot", "c:beetroots", "forge:beetroots");
                case "minecraft:sugar_cane" -> hasAnyTag("forge:crops/sugar_cane", "c:crops/sugar_cane", "c:sugar_cane");
                case "minecraft:sweet_berries" -> hasAnyTag("forge:berries", "c:berries");
                case "minecraft:apple" -> hasAnyTag("forge:fruits/apple", "c:fruits/apple", "c:apples");
                case "minecraft:melon_slice" -> hasAnyTag("forge:fruits/melon", "c:fruits/melon", "c:melon_slices");
                case "minecraft:pumpkin" -> hasAnyTag("forge:crops/pumpkin", "c:crops/pumpkin", "c:pumpkins");
                case "minecraft:iron_ingot" -> hasAnyTag("forge:ingots/iron", "c:iron_ingots", "c:ingots/iron");
                case "minecraft:gold_ingot" -> hasAnyTag("forge:ingots/gold", "c:gold_ingots", "c:ingots/gold");
                case "minecraft:copper_ingot" -> hasAnyTag("forge:ingots/copper", "c:copper_ingots", "c:ingots/copper");
                case "minecraft:diamond" -> hasAnyTag("forge:gems/diamond", "c:diamonds", "c:gems/diamond");
                case "minecraft:emerald" -> hasAnyTag("forge:gems/emerald", "c:emeralds", "c:gems/emerald");
                case "minecraft:netherite_ingot" -> hasAnyTag("forge:ingots/netherite", "c:netherite_ingots");
                case "minecraft:raw_iron" -> hasAnyTag("forge:raw_materials/iron", "c:raw_iron_ores", "c:raw_iron");
                case "minecraft:raw_gold" -> hasAnyTag("forge:raw_materials/gold", "c:raw_gold_ores", "c:raw_gold");
                case "minecraft:raw_copper" -> hasAnyTag("forge:raw_materials/copper", "c:raw_copper_ores", "c:raw_copper");
                case "minecraft:beef" -> hasAnyTag("forge:raw_beef", "c:raw_beef", "forge:raw_meats");
                case "minecraft:porkchop" -> hasAnyTag("forge:raw_porkchop", "c:raw_pork", "forge:raw_meats");
                case "minecraft:mutton" -> hasAnyTag("forge:raw_mutton", "c:raw_mutton", "forge:raw_meats");
                case "minecraft:chicken" -> hasAnyTag("forge:raw_chicken", "c:raw_chicken", "forge:raw_meats");
                case "minecraft:cod" -> hasAnyTag("forge:raw_fishes/cod", "c:raw_fish", "forge:raw_fishes");
                case "minecraft:salmon" -> hasAnyTag("forge:raw_fishes/salmon", "c:raw_fish", "forge:raw_fishes");
                case "minecraft:leather" -> hasAnyTag("forge:leather", "c:leather");
                case "minecraft:string" -> hasAnyTag("forge:string", "c:string");
                case "minecraft:feather" -> hasAnyTag("forge:feathers", "c:feathers");
                case "minecraft:egg" -> hasAnyTag("forge:eggs", "c:eggs");
                case "minecraft:milk_bucket" -> hasAnyTag("forge:milk", "c:milk");
                default -> false;
            };
        }

        private boolean hasAnyTag(String... checkTags) {
            for (String t : checkTags) {
                if (tags.contains(t)) return true;
            }
            return false;
        }
    }

    public static int[] plan(List<TradeBook.Ingredient> ingredients, List<Held> bag, int target) {
        int[] taken = new int[bag.size()];
        for (TradeBook.Ingredient need : ingredients) {
            int left = need.count();
            for (int star = need.need(target); star <= 2 && left > 0; star++) {
                for (int i = 0; i < bag.size() && left > 0; i++) {
                    Held held = bag.get(i);
                    if (!held.pays(need) || held.star() != star) continue;
                    int take = Math.min(left, held.count() - taken[i]);
                    taken[i] += take;
                    left -= take;
                }
            }
            if (left > 0) return null;
        }
        return taken;
    }

    public static int have(TradeBook.Ingredient need, List<Held> bag, int target) {
        int minimum = need.need(target);
        int total = 0;
        for (Held held : bag) {
            if (held.pays(need) && held.star() >= minimum) total += held.count();
        }
        return total;
    }

    public static int bestTarget(TradeBook.Recipe recipe, List<Held> bag) {
        for (int target = recipe.quality() ? 2 : 0; target >= 0; target--) {
            if (plan(recipe.ingredients(), bag, target) != null) return target;
            if (!recipe.quality()) break;
        }
        return -1;
    }
}
