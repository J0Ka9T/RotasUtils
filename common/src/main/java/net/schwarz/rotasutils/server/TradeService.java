package net.schwarz.rotasutils.server;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.core.StarRules;
import net.schwarz.rotasutils.core.TradeBook;
import net.schwarz.rotasutils.core.TradeKit;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.item.StarGear;
import net.schwarz.rotasutils.item.StarQuality;
import net.schwarz.rotasutils.job.JobDef;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TradeService {
    private TradeService() {
    }

    private static String learnedKey(TradeBook book) {
        return "rpg.trade." + book.trade() + ".learned";
    }

    private static String queueKey(TradeBook book) {
        return "rpg.trade." + book.trade() + ".queue";
    }

public static Component recipeName(String id) {
        return Component.translatableWithFallback("rotasutils.trade.recipe." + id, pretty(id));
    }

    private static String pretty(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.split("_")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.toString();
    }

private static boolean works(PlayerProgress progress, TradeBook book) {
        return progress.subJob().equals(book.job());
    }

    private static int level(RotasData data, PlayerProgress progress, TradeBook book) {
        JobDef job = data.job(book.job());
        return job == null || !works(progress, book) ? 0 : ProductionService.subLevel(progress, job);
    }

    private static List<String> learned(PlayerProgress progress, TradeBook book) {
        return TradeKit.parseLearned(progress.questVariables().get(learnedKey(book)));
    }

    private static List<TradeKit.Cooking> queue(PlayerProgress progress, TradeBook book) {
        return TradeKit.parseQueue(progress.questVariables().get(queueKey(book)));
    }

    private static void saveQueue(PlayerProgress progress, TradeBook book, List<TradeKit.Cooking> queue) {
        if (queue.isEmpty()) {
            progress.questVariables().remove(queueKey(book));
        } else {
            progress.questVariables().put(queueKey(book), TradeKit.formatQueue(queue));
        }
        progress.markDirty();
    }

    private static boolean atStation(ServerPlayer player, TradeBook book) {
        ResourceLocation id = ResourceLocation.tryParse(book.station());
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return false;
        Block block = BuiltInRegistries.BLOCK.get(id);
        return StationService.near(player, block);
    }

public static void openStation(ServerPlayer player, String trade) {
        open(player, TradeConfig.book(trade), true);
    }

    public static void openBook(ServerPlayer player, String trade) {
        TradeBook book = trade == null || trade.isBlank() ? null : TradeConfig.book(trade);
        if (book == null) {
            book = TradeConfig.byJob(RotasData.get(player.server).progress(player.getUUID()).subJob());
        }
        if (book == null) {
            book = TradeConfig.all().get(0);
        }
        open(player, book, false);
    }

    private static void open(ServerPlayer player, TradeBook book, boolean station) {
        if (book != null) {
            RotasNetwork.openScreen(player, "trade", view(player, book, station));
        }
    }

    public static CompoundTag view(ServerPlayer player, TradeBook book, boolean station) {
        RotasData data = RotasData.get(player.server);
        PlayerProgress progress = data.progress(player.getUUID());
        StarRules stars = TradeConfig.stars();
        int level = level(data, progress, book);
        List<String> known = learned(progress, book);
        List<TradeKit.Held> bag = bag(player, null);
        long now = System.currentTimeMillis();

        CompoundTag tag = new CompoundTag();
        tag.putString("trade", book.trade());
        tag.putString("job", book.job());
        tag.putBoolean("member", works(progress, book));
        tag.putInt("level", level);
        tag.putInt("slots", book.slots(level));
        tag.putIntArray("slot_levels", book.slotLevels());
        tag.putBoolean("station", station && atStation(player, book));
        ListTag tabs = new ListTag();
        for (TradeBook other : TradeConfig.all()) {
            CompoundTag tab = new CompoundTag();
            tab.putString("trade", other.trade());
            tab.putString("job", other.job());
            tab.putBoolean("own", works(progress, other));
            tabs.add(tab);
        }
        tag.put("tabs", tabs);
        ListTag perkList = new ListTag();
        for (net.schwarz.rotasutils.core.PerkRules.Perk perk : TradeConfig.perks().forRole(book.job())) {
            CompoundTag row = new CompoundTag();
            row.putString("type", perk.type());
            row.putString("value", String.format(java.util.Locale.ROOT, "%.1f", perk.value(Math.max(1, level))).replace(".0", ""));
            perkList.add(row);
        }
        tag.put("perks", perkList);
        ListTag recipes = new ListTag();
        for (TradeBook.Recipe recipe : book.recipes()) {
            if (!usable(recipe)) {
                continue;
            }
            TradeBook.State state = TradeBook.state(recipe, level, known.contains(recipe.id()));
            CompoundTag entry = new CompoundTag();
            entry.putString("id", recipe.id());
            entry.putInt("state", state.ordinal());
            if (state != TradeBook.State.SECRET) {
                entry.putInt("level", recipe.level());
                entry.putString("icon", recipe.outputs().get(0).item());
            }
            if (state == TradeBook.State.AVAILABLE) {
                entry.putInt("seconds", recipe.seconds());
                entry.putBoolean("quality", recipe.quality());
                entry.putInt("best", Math.max(0, TradeKit.bestTarget(recipe, bag)));
                ListTag ingredients = new ListTag();
                for (TradeBook.Ingredient need : recipe.ingredients()) {
                    CompoundTag row = new CompoundTag();
                    row.putString("item", icon(need));
                    row.putInt("count", need.count());
                    row.putInt("star", need.minStar());
                    row.putBoolean("graded", need.graded());
                    row.putIntArray("have", new int[]{haveAtLeast(need, bag, 0), haveAtLeast(need, bag, 1), haveAtLeast(need, bag, 2)});
                    ListTag from = new ListTag();
                    for (String job : stars.jobsFor(need.options())) {
                        from.add(net.minecraft.nbt.StringTag.valueOf(job));
                    }
                    row.put("from", from);
                    ingredients.add(row);
                }
                entry.put("ingredients", ingredients);
                ListTag outputs = new ListTag();
                for (TradeBook.Output out : recipe.outputs()) {
                    CompoundTag row = new CompoundTag();
                    row.putString("item", out.item());
                    row.putInt("count", out.count());
                    outputs.add(row);
                }
                entry.put("outputs", outputs);
            }
            recipes.add(entry);
        }
        tag.put("recipes", recipes);
        ListTag cooking = new ListTag();
        for (TradeKit.Cooking c : queue(progress, book)) {
            CompoundTag row = new CompoundTag();
            row.putString("recipe", c.recipe());
            row.putInt("star", c.star());
            row.putLong("left", Math.max(0, c.finishAt() - now));
            cooking.add(row);
        }
        tag.put("queue", cooking);
        return tag;
    }

    private static int haveAtLeast(TradeBook.Ingredient need, List<TradeKit.Held> bag, int star) {
        return TradeKit.have(new TradeBook.Ingredient(need.item(), need.count(), Math.max(need.minStar(), star), false), bag, 0);
    }

public static boolean learn(ServerPlayer player, String trade, String id) {
        TradeBook book = trade == null || trade.isBlank() ? findBookWith(id) : TradeConfig.book(trade);
        TradeBook.Recipe recipe = book == null ? null : book.find(id);
        if (recipe == null || !recipe.secret()) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.trade.msg.scroll_blank"));
            return false;
        }
        PlayerProgress progress = RotasData.get(player.server).progress(player.getUUID());
        List<String> known = learned(progress, book);
        if (known.contains(id)) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.trade.msg.already_known", recipeName(id).getString()));
            return false;
        }
        known.add(id);
        progress.questVariables().put(learnedKey(book), TradeKit.formatLearned(known));
        progress.markDirty();
        RotasData.get(player.server).setDirty();
        player.sendSystemMessage(Component.translatable("rotasutils.trade.msg.learned", recipeName(id)).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.5f);
        return true;
    }

    private static TradeBook findBookWith(String id) {
        for (TradeBook book : TradeConfig.all()) {
            if (book.find(id) != null) return book;
        }
        return null;
    }

public static void cook(ServerPlayer player, String trade, String id, int quality) {
        RotasData data = RotasData.get(player.server);
        PlayerProgress progress = data.progress(player.getUUID());
        TradeBook book = TradeConfig.book(trade);
        TradeBook.Recipe recipe = book == null ? null : book.find(id);
        String refusal = null;
        int target = recipe == null ? 0 : recipe.quality() ? Math.max(0, Math.min(2, quality)) : 0;
        if (recipe == null) {
            refusal = "unknown";
        } else if (!works(progress, book)) {
            refusal = "role_only";
        } else if (!atStation(player, book)) {
            refusal = "no_station";
        } else if (TradeBook.state(recipe, level(data, progress, book), learned(progress, book).contains(id)) != TradeBook.State.AVAILABLE) {
            refusal = "locked";
        } else if (queue(progress, book).size() >= book.slots(level(data, progress, book))) {
            refusal = "slots_full";
        } else if (!usable(recipe)) {
            refusal = "broken";
        }
        int[] taken = null;
        List<ItemStack> source = new ArrayList<>();
        if (refusal == null) {
            taken = TradeKit.plan(recipe.ingredients(), bag(player, source), target);
            if (taken == null) {
                refusal = "missing";
            }
        }
        if (refusal != null) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.trade.msg." + refusal));
            return;
        }
        for (int i = 0; i < taken.length; i++) {
            if (taken[i] > 0) {
                source.get(i).shrink(taken[i]);
            }
        }
        player.getInventory().setChanged();
        List<TradeKit.Cooking> queue = queue(progress, book);
        queue.add(new TradeKit.Cooking(id, System.currentTimeMillis() + recipe.seconds() * 1000L, recipe.outputStar(target)));
        saveQueue(progress, book, queue);
        data.setDirty();
        data.audit(player.getGameProfile().getName() + " trade_cook " + trade + "/" + id + " star=" + target);
        net.minecraft.sounds.SoundEvent craftSound = switch (trade) {
            case "miner" -> SoundEvents.FURNACE_FIRE_CRACKLE;
            case "blacksmith" -> SoundEvents.ANVIL_USE;
            case "alchemy" -> SoundEvents.BREWING_STAND_BREW;
            case "rancher" -> SoundEvents.ARMOR_EQUIP_LEATHER;
            case "fisher" -> SoundEvents.FISHING_BOBBER_SPLASH;
            case "farmer" -> SoundEvents.CROP_BREAK;
            default -> SoundEvents.BREWING_STAND_BREW;
        };
        player.level().playSound(null, player.blockPosition(), craftSound, SoundSource.BLOCKS, 0.8f, 1.1f);
        RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.trade.msg.started", recipeName(id).getString()));
        ResourceLocation stationId = ResourceLocation.tryParse(book.station());
        if (stationId != null && BuiltInRegistries.BLOCK.containsKey(stationId)) {
            Block stationBlock = BuiltInRegistries.BLOCK.get(stationId);
            net.minecraft.core.BlockPos stationPos = StationService.find(player, stationBlock);
            if (stationPos != null) {
                net.schwarz.rotasutils.block.StationBlock.lightUp(player.level(), stationPos, recipe.seconds() * 20);
            }
        }
        openStation(player, trade);
    }

    public static void claim(ServerPlayer player, String trade) {
        RotasData data = RotasData.get(player.server);
        PlayerProgress progress = data.progress(player.getUUID());
        TradeBook book = TradeConfig.book(trade);
        if (book == null) {
            return;
        }
        if (!atStation(player, book)) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.trade.msg.no_station"));
            return;
        }
        long now = System.currentTimeMillis();
        List<TradeKit.Cooking> waiting = new ArrayList<>();
        List<ItemStack> plates = new ArrayList<>();
        int served = 0;
        for (TradeKit.Cooking cooking : queue(progress, book)) {
            TradeBook.Recipe recipe = book.find(cooking.recipe());
            if (!cooking.done(now)) {
                waiting.add(cooking);
                continue;
            }
            if (recipe == null) {
                Rotasutils.LOG.warn("Dropping finished '{}' for {}: not in {}.json any more",
                        cooking.recipe(), player.getGameProfile().getName(), trade);
                continue;
            }
            List<TradeBook.Output> outputs = recipe.outputs();
            for (int i = 0; i < outputs.size(); i++) {
                ItemStack made = make(outputs.get(i), i == 0 ? cooking.star() : 0);
                if (!made.isEmpty()) {
                    plates.add(made);
                }
            }
            ProductionService.producedAt(player, book.job(), recipe.level(), trade + ":" + recipe.id(), 1);
            served++;
        }
        if (served == 0) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.trade.msg.nothing_ready"));
            openStation(player, trade);
            return;
        }
        saveQueue(progress, book, waiting);
        data.setDirty();
        TitleService.stat(player, data, "crafted", served);
        TitleService.stat(player, data, "crafted." + trade, served);
        int mailed = LootService.deliver(player, plates);
        RotasNetwork.feedback(player, true, ThaiText.t(mailed > 0 ? "rotasutils.trade.msg.served_mail" : "rotasutils.trade.msg.served", served));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.6f);
        ResourceLocation stationId = ResourceLocation.tryParse(book.station());
        if (stationId != null && BuiltInRegistries.BLOCK.containsKey(stationId)) {
            Block stationBlock = BuiltInRegistries.BLOCK.get(stationId);
            net.minecraft.core.BlockPos stationPos = StationService.find(player, stationBlock);
            if (stationPos != null && player.level().getBlockState(stationPos).hasProperty(net.schwarz.rotasutils.block.StationBlock.LIT)) {
                if (!StationService.hasActiveQueueNearby(player.level(), stationPos, ((net.schwarz.rotasutils.block.StationBlock) stationBlock).kind())) {
                    player.level().setBlock(stationPos, player.level().getBlockState(stationPos).setValue(net.schwarz.rotasutils.block.StationBlock.LIT, false), Block.UPDATE_ALL);
                }
            }
        }
        openStation(player, trade);
    }

    private static ItemStack make(TradeBook.Output out, int star) {
        Item item = item(out.item());
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, out.count());
        if (!out.nbt().isBlank()) {
            try {
                stack.setTag(TagParser.parseTag(out.nbt()));
            } catch (CommandSyntaxException bad) {
                Rotasutils.LOG.warn("Recipe output {} has unreadable NBT '{}': {}", out.item(), out.nbt(), bad.getMessage());
            }
        }
        return StarGear.apply(stack, star);
    }

    private static boolean usable(TradeBook.Recipe recipe) {
        for (String mod : recipe.requires()) {
            if (!dev.architectury.platform.Platform.isModLoaded(mod)) return false;
        }
        for (TradeBook.Ingredient need : recipe.ingredients()) {
            boolean any = false;
            for (String option : need.options()) {
                any |= option.startsWith("#") || item(option) != Items.AIR;
            }
            if (!any) return false;
        }
        for (TradeBook.Output out : recipe.outputs()) {
            if (item(out.item()) == Items.AIR) return false;
        }
        return true;
    }

    private static String icon(TradeBook.Ingredient need) {
        for (String option : need.options()) {
            if (!option.startsWith("#") && item(option) != Items.AIR) return option;
        }
        return need.icon();
    }

    private static Item item(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? Items.AIR : BuiltInRegistries.ITEM.get(location);
    }

    private static List<TradeKit.Held> bag(ServerPlayer player, List<ItemStack> stacks) {
        List<TradeKit.Held> held = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty() || ItemFactory.isRpgItem(stack)) continue;
            java.util.Set<String> tags = new java.util.HashSet<>();
            stack.getTags().forEach(tag -> tags.add(tag.location().toString()));
            held.add(new TradeKit.Held(String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem())),
                    StarQuality.of(stack), stack.getCount(), tags));
            if (stacks != null) stacks.add(stack);
        }
        return held;
    }

public static List<ItemStack> starDrops(ServerPlayer player, BlockState state, List<ItemStack> drops) {
        if (drops == null || drops.isEmpty()) {
            return null;
        }
        RotasData data = RotasData.get(player.server);
        String job = data.progress(player.getUUID()).subJob();
        if (job.isEmpty() || ("farmer".equals(job) && !ProductionService.harvestable(state))) {
            return null;
        }
        return star(player, data, job, drops);
    }

    public static ItemStack starCatch(ServerPlayer player, ItemStack caught) {
        RotasData data = RotasData.get(player.server);
        String job = data.progress(player.getUUID()).subJob();
        if (caught.isEmpty() || !"fisher".equals(job)) {
            return caught;
        }
        List<ItemStack> starred = star(player, data, job, List.of(caught));
        return starred == null ? caught : starred.get(0);
    }

    public static List<ItemStack> starAny(ServerPlayer player, List<ItemStack> stacks) {
        RotasData data = RotasData.get(player.server);
        String job = data.progress(player.getUUID()).subJob();
        if (job.isEmpty() || stacks.isEmpty()) {
            return stacks;
        }
        List<ItemStack> starred = star(player, data, job, stacks);
        return starred == null ? stacks : starred;
    }

    public static List<ItemStack> starLoot(ServerPlayer player, List<ItemStack> drops) {
        RotasData data = RotasData.get(player.server);
        String job = data.progress(player.getUUID()).subJob();
        return "rancher".equals(job) ? star(player, data, job, drops) : null;
    }

    private static List<ItemStack> star(ServerPlayer player, RotasData data, String job, List<ItemStack> drops) {
        JobDef jobDef = data.job(job);
        if (jobDef == null) {
            return null;
        }
        StarRules rules = TradeConfig.stars();
        int level = ProductionService.subLevel(data.progress(player.getUUID()), jobDef);
        List<ItemStack> out = new ArrayList<>(drops.size() + 2);
        boolean changed = false;
        int stars = 0, goldStars = 0;
        for (ItemStack stack : drops) {
            String id = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            StarRules.Source source = stack.isEmpty() || StarQuality.of(stack) > 0 ? null
                    : rules.sourceFor(job, id, tag -> {
                        ResourceLocation location = ResourceLocation.tryParse(tag);
                        return location != null && stack.is(net.minecraft.tags.TagKey.create(
                                net.minecraft.core.registries.Registries.ITEM, location));
                    });
            if (source == null) {
                out.add(stack);
                continue;
            }
            int silver = 0, gold = 0;
            for (int i = 0; i < stack.getCount(); i++) {
                int roll = source.roll(level, player.getRandom().nextDouble());
                if (roll == 1) silver++;
                if (roll == 2) gold++;
            }
            int plain = stack.getCount() - silver - gold;
            if (plain > 0) out.add(stack.copyWithCount(plain));
            if (silver > 0) out.add(StarQuality.apply(stack.copyWithCount(silver), 1));
            if (gold > 0) out.add(StarQuality.apply(stack.copyWithCount(gold), 2));
            changed |= silver + gold > 0;
            stars += silver + gold;
            goldStars += gold;
        }
        if (stars > 0) {
            TitleService.stat(player, data, "stars", stars);
            TitleService.stat(player, data, "gold_stars", goldStars);
        }
        return changed ? out : null;
    }

public static void onEat(ServerPlayer player, ItemStack eaten) {
        int star = StarQuality.of(eaten);
        if (star == 0 || !eaten.isEdible()) {
            return;
        }
        for (StarRules.Buff buff : TradeConfig.stars().meal(star)) {
            ResourceLocation id = ResourceLocation.tryParse(buff.effect());
            if (id != null && BuiltInRegistries.MOB_EFFECT.containsKey(id)) {
                player.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.get(id), buff.seconds() * 20, buff.amplifier()));
            }
        }
        player.displayClientMessage(Component.translatable(star == 2 ? "rotasutils.trade.meal.gold" : "rotasutils.trade.meal.silver"), true);
        PerkService.banquet(player, star);
        TitleService.stat(player, RotasData.get(player.server), "star_meals", 1);
    }
}
