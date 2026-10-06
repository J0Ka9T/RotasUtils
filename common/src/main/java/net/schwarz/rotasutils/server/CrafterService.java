package net.schwarz.rotasutils.server;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.job.JobDef;
import net.schwarz.rotasutils.level.SeasonMath;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.npc.NpcDef;
import net.schwarz.rotasutils.npc.NpcServiceDef;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CrafterService {
    private static final String DAY = "rpg.crafter.day";
    private static final String USED = "rpg.crafter.used";

    public record Result(boolean ok, String message) {
    }

    record Material(Ingredient ingredient, int count) {
    }

    record Offer(String key, JobDef.ProductionEntry entry, ItemStack result, List<Material> materials, int tier, long fee) {
    }

    private CrafterService() {
    }

    public static JobDef job(RotasData data, NpcDef npc) {
        NpcServiceDef service = npc.crafterService();
        JobDef job = service == null ? null : data.job(service.jobId());
        return job != null && job.enabled() ? job : null;
    }

    public static void open(ServerPlayer player, RotasData data, NpcDef npc) {
        RotasNetwork.openScreen(player, "crafter", snapshot(player, data, npc));
    }

    static List<Offer> offers(ServerPlayer player, RotasData data, NpcDef npc) {
        JobDef job = job(data, npc);
        if (job == null) {
            return List.of();
        }
        int maxLevel = npc.crafterService().level();
        SeasonRules rules = SeasonService.rules(data);
        RecipeManager recipes = player.server.getRecipeManager();
        RegistryAccess registries = player.server.registryAccess();
        List<Offer> offers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JobDef.ProductionEntry entry : job.production()) {
            boolean craft = entry.activity() == JobDef.ProductionEntry.Activity.CRAFT;
            if (!craft && entry.activity() != JobDef.ProductionEntry.Activity.SMELT) {
                continue;
            }
            if (entry.selector().startsWith("#") || (maxLevel > 0 && entry.unlockLevel() > maxLevel)) {
                continue;
            }
            String key = entry.activity().name().toLowerCase(Locale.ROOT) + ":" + entry.selector();
            if (!seen.add(key)) {
                continue;
            }
            Recipe<?> recipe = recipeFor(recipes, registries, craft, entry.selector());
            if (recipe == null) {
                continue;
            }
            int tier = SeasonMath.tierIndex(entry.unlockLevel(), rules.tierMaxLevel);
            offers.add(new Offer(key, entry, recipe.getResultItem(registries).copy(), materials(recipe), tier,
                    rules.crafterFeeFor(tier)));
        }
        offers.sort(Comparator.comparingInt(offer -> offer.entry().unlockLevel()));
        return offers;
    }

    private static Recipe<?> recipeFor(RecipeManager recipes, RegistryAccess registries, boolean craft, String itemId) {
        List<? extends Recipe<?>> candidates;
        if (craft) {
            candidates = recipes.getAllRecipesFor(RecipeType.CRAFTING);
        } else {
            candidates = recipes.getAllRecipesFor(RecipeType.SMELTING);
        }
        Recipe<?> best = null;
        long bestInputs = Long.MAX_VALUE;
        for (Recipe<?> recipe : candidates) {
            if (recipe.isSpecial()) {
                continue;
            }
            ItemStack out = recipe.getResultItem(registries);
            if (out.isEmpty() || !itemId.equals(String.valueOf(BuiltInRegistries.ITEM.getKey(out.getItem())))) {
                continue;
            }
            long inputs = recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).count();
            if (inputs == 0) {
                continue;
            }
            if (best == null || inputs < bestInputs || (inputs == bestInputs && recipe.getId().compareTo(best.getId()) < 0)) {
                best = recipe;
                bestInputs = inputs;
            }
        }
        return best;
    }

    private static List<Material> materials(Recipe<?> recipe) {
        Map<String, Material> grouped = new LinkedHashMap<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            grouped.merge(ingredient.toJson().toString(), new Material(ingredient, 1),
                    (known, added) -> new Material(known.ingredient(), known.count() + 1));
        }
        return List.copyOf(grouped.values());
    }

    private static boolean usable(ItemStack stack) {
        return !stack.isEmpty() && !ItemFactory.isRpgItem(stack);
    }

    private static long count(Inventory inventory, Ingredient ingredient) {
        long total = 0;
        for (ItemStack stack : inventory.items) {
            if (usable(stack) && ingredient.test(stack)) total += stack.getCount();
        }
        return total;
    }

    private static boolean take(Inventory inventory, List<Material> materials, int crafts, List<ItemStack> remainders) {
        List<ItemStack> items = inventory.items;
        int[] left = new int[items.size()];
        for (int slot = 0; slot < items.size(); slot++) {
            left[slot] = items.get(slot).getCount();
        }
        for (Material material : materials) {
            long needed = (long) material.count() * crafts;
            for (int slot = 0; slot < items.size() && needed > 0; slot++) {
                ItemStack stack = items.get(slot);
                if (left[slot] <= 0 || !usable(stack) || !material.ingredient().test(stack)) {
                    continue;
                }
                int used = (int) Math.min(needed, left[slot]);
                left[slot] -= used;
                needed -= used;
            }
            if (needed > 0) {
                return false;
            }
        }
        if (remainders == null) {
            return true;
        }
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            int used = stack.getCount() - left[slot];
            if (used <= 0) {
                continue;
            }
            Item remaining = stack.getItem().getCraftingRemainingItem();
            if (remaining != null) {
                remainders.add(new ItemStack(remaining, used));
            }
            stack.shrink(used);
        }
        inventory.setChanged();
        return true;
    }

    public static int remaining(int limit, int used) {
        return limit <= 0 ? -1 : Math.max(0, limit - used);
    }

    static int left(PlayerProgress progress, SeasonRules rules) {
        return remaining(rules.crafterDailyLimit, usedToday(progress));
    }

    private static int usedToday(PlayerProgress progress) {
        if (!Long.toString(SeasonService.today()).equals(progress.questVariables().get(DAY))) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(progress.questVariables().getOrDefault(USED, "0")));
        } catch (NumberFormatException malformed) {
            return 0;
        }
    }

    private static void recordUse(PlayerProgress progress, int crafts) {
        int used = usedToday(progress);
        progress.questVariables().put(DAY, Long.toString(SeasonService.today()));
        progress.questVariables().put(USED, Integer.toString(used + crafts));
        progress.markDirty();
    }

    private static int affordable(ServerPlayer player, PlayerProgress progress, SeasonRules rules, Offer offer) {
        long max = 64;
        int left = left(progress, rules);
        if (left >= 0) max = Math.min(max, left);
        if (offer.fee() > 0) max = Math.min(max, progress.rpg().currency(rules.currency) / offer.fee());
        int low = 0;
        int high = (int) Math.max(0, max);
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (take(player.getInventory(), offer.materials(), mid, null)) low = mid; else high = mid - 1;
        }
        return low;
    }

    public static Result commission(ServerPlayer player, RotasData data, NpcDef npc, String key, int crafts) {
        if (npc.role() != NpcDef.Role.CRAFTER || job(data, npc) == null) {
            return new Result(false, ThaiText.t("rotasutils.msg.crafter.unavailable"));
        }
        Offer offer = null;
        for (Offer candidate : offers(player, data, npc)) {
            if (candidate.key().equals(key)) {
                offer = candidate;
                break;
            }
        }
        if (offer == null) {
            return new Result(false, ThaiText.t("rotasutils.msg.crafter.unknown"));
        }
        crafts = Math.max(1, Math.min(64, crafts));
        SeasonRules rules = SeasonService.rules(data);
        PlayerProgress progress = data.progress(player.getUUID());
        int left = left(progress, rules);
        if (left >= 0 && crafts > left) {
            return new Result(false, ThaiText.t("rotasutils.msg.crafter.limit", rules.crafterDailyLimit));
        }
        long fee = Math.multiplyExact(offer.fee(), crafts);
        if (progress.rpg().currency(rules.currency) < fee) {
            return new Result(false, ThaiText.t("rotasutils.msg.crafter.no_money"));
        }
        if (!take(player.getInventory(), offer.materials(), crafts, null)) {
            return new Result(false, ThaiText.t("rotasutils.msg.crafter.no_materials"));
        }
        List<ItemStack> remainders = new ArrayList<>();
        take(player.getInventory(), offer.materials(), crafts, remainders);
        if (fee > 0) {
            progress.rpg().currency(rules.currency, -fee);
        }
        recordUse(progress, crafts);
        data.setDirty();
        for (int i = 0; i < crafts; i++) {
            RewardService.give(player, offer.result().copy());
        }
        remainders.forEach(stack -> RewardService.give(player, stack));
        player.level().playSound(null, player.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.NEUTRAL, 0.8f, 1.0f);
        return new Result(true, ThaiText.t("rotasutils.msg.crafter.done", offer.result().getHoverName().getString(),
                offer.result().getCount() * crafts));
    }

    static CompoundTag snapshot(ServerPlayer player, RotasData data, NpcDef npc) {
        CompoundTag tag = new CompoundTag();
        tag.putString("npc", npc.id());
        tag.putString("name", npc.name());
        tag.putString("title", npc.title());
        tag.putString("entity", npc.entityUuid());
        tag.putString("greeting", npc.greeting());
        SeasonRules rules = SeasonService.rules(data);
        PlayerProgress progress = data.progress(player.getUUID());
        JobDef job = job(data, npc);
        if (job != null) {
            tag.putString("job", job.name());
            tag.putInt("job_color", job.color());
            tag.put("job_icon", job.icon().save(new CompoundTag()));
        }
        tag.putInt("left", left(progress, rules));
        tag.putInt("limit", rules.crafterDailyLimit);
        tag.putString("currency", rules.currency);
        tag.putLong("wallet", progress.rpg().currency(rules.currency));
        ListTag list = new ListTag();
        for (Offer offer : offers(player, data, npc)) {
            CompoundTag entry = new CompoundTag();
            entry.putString("key", offer.key());
            entry.put("result", offer.result().save(new CompoundTag()));
            entry.putInt("level", offer.entry().unlockLevel());
            entry.putInt("tier", offer.tier());
            entry.putLong("fee", offer.fee());
            entry.putBoolean("smelt", offer.entry().activity() == JobDef.ProductionEntry.Activity.SMELT);
            ListTag materials = new ListTag();
            for (Material material : offer.materials()) {
                CompoundTag row = new CompoundTag();
                ItemStack[] options = material.ingredient().getItems();
                row.put("item", (options.length == 0 ? ItemStack.EMPTY : options[0].copyWithCount(1)).save(new CompoundTag()));
                row.putInt("need", material.count());
                row.putLong("have", count(player.getInventory(), material.ingredient()));
                materials.add(row);
            }
            entry.put("materials", materials);
            entry.putInt("affordable", affordable(player, progress, rules, offer));
            list.add(entry);
        }
        tag.put("offers", list);
        return tag;
    }
}
