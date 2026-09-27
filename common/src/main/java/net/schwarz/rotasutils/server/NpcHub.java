package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.level.XpSource;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.npc.NpcDef;
import net.schwarz.rotasutils.progress.PlayerProgress;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * The service NPCs: blacksmith, enchanter, alchemist, innkeeper, priest, fortune teller, banker, bounty master,
 * guard, trainer, cartographer and collector. Each role is a list of entries sent to one shared screen; a paid
 * entry is re-priced and re-checked here when the player clicks it, so the screen is only ever a view.
 *
 * <p>Entry kinds: {@code service} runs {@link #perform}; {@code action} makes the client send an existing
 * action (opening the refine bench, the waystones...); {@code screen} opens a client screen; {@code info} is text.</p>
 */
public final class NpcHub {
    private NpcHub() {
    }

    private static final class Entries {
        final ListTag list = new ListTag();
        String section = "";

        CompoundTag add(String kind, String id, String title, String desc, long cost, ItemStack icon, String blocked) {
            CompoundTag tag = new CompoundTag();
            tag.putString("kind", kind);
            tag.putString("id", id);
            tag.putString("title", title);
            tag.putString("desc", desc == null ? "" : desc);
            tag.putLong("cost", cost);
            tag.put("icon", icon.save(new CompoundTag()));
            tag.putString("blocked", blocked == null ? "" : blocked);
            tag.putString("section", section);
            list.add(tag);
            return tag;
        }

        void service(String id, String title, String desc, long cost, ItemStack icon, String blocked) {
            add("service", id, title, desc, cost, icon, blocked);
        }

        void action(String action, String title, String desc, ItemStack icon) {
            add("action", action, title, desc, 0, icon, null);
        }

        void screen(String screen, String title, String desc, ItemStack icon) {
            add("screen", screen, title, desc, 0, icon, null);
        }

        void info(String title, String desc, ItemStack icon) {
            add("info", "", title, desc, 0, icon, null);
        }
    }

    public static boolean serves(NpcDef.Role role) {
        return switch (role) {
            case BLACKSMITH, ENCHANTER, ALCHEMIST, INNKEEPER, PRIEST, FORTUNE_TELLER, BANKER, BOUNTY_MASTER, GUARD,
                 TRAINER, CARTOGRAPHER, COLLECTOR -> true;
            default -> false;
        };
    }

    private static SeasonRules.NpcServiceRules rules(RotasData data) {
        return SeasonService.rules(data).npcServices;
    }

    private static long balance(RotasData data, ServerPlayer player) {
        return data.progress(player.getUUID()).rpg().currency(GoldCoinService.CURRENCY);
    }

    private static String afford(RotasData data, ServerPlayer player, long cost) {
        return balance(data, player) >= cost ? null : "เงินไม่พอ";
    }

    private static boolean pay(RotasData data, ServerPlayer player, long cost) {
        if (cost <= 0) return true;
        PlayerProgress progress = data.progress(player.getUUID());
        if (progress.rpg().currency(GoldCoinService.CURRENCY) < cost) return false;
        progress.rpg().currency(GoldCoinService.CURRENCY, -cost);
        data.setDirty();
        return true;
    }

    // Screen ---------------------------------------------------------------------------------------

    public static void open(ServerPlayer player, RotasData data, NpcDef npc) {
        Entries entries = new Entries();
        switch (npc.role()) {
            case BLACKSMITH -> blacksmith(player, data, entries);
            case ENCHANTER -> enchanter(player, data, entries);
            case ALCHEMIST -> alchemist(player, data, entries);
            case INNKEEPER -> innkeeper(player, data, entries);
            case PRIEST -> priest(player, data, entries);
            case FORTUNE_TELLER -> fortuneTeller(player, data, entries);
            case BANKER -> banker(player, data, entries);
            case BOUNTY_MASTER -> bountyMaster(player, data, entries);
            case GUARD -> guard(player, data, entries);
            case TRAINER -> trainer(player, data, entries);
            case CARTOGRAPHER -> cartographer(player, data, entries);
            case COLLECTOR -> collector(player, data, entries);
            default -> {
                NpcService.openDialogue(player, data, npc);
                return;
            }
        }
        CompoundTag payload = new CompoundTag();
        payload.putString("npc", npc.id());
        payload.putString("name", npc.name());
        payload.putString("role", npc.role().name());
        payload.putString("role_name", npc.role().display());
        payload.putString("greeting", greeting(npc));
        payload.putLong("balance", balance(data, player));
        ListTag buffs = new ListTag();
        BuffService.describe(data.progress(player.getUUID())).forEach(line -> buffs.add(StringTag.valueOf(line)));
        payload.put("buffs", buffs);
        payload.put("entries", entries.list);
        RotasNetwork.openScreen(player, "npc_hub", payload);
    }

    private static String greeting(NpcDef npc) {
        return switch (npc.role()) {
            case BLACKSMITH -> "เหล็กดีต้องตีตอนร้อน อยากให้ซ่อมหรือเสริมอะไรล่ะ";
            case ENCHANTER -> "มนตร์ทุกบทมีราคา... และทุกบทถอดคืนได้";
            case ALCHEMIST -> "ระวังนะ ขวดนั้นยังเดือดอยู่";
            case INNKEEPER -> "ยินดีต้อนรับ นักเดินทาง! เตียงอุ่น ซุปร้อน พร้อมเสมอ";
            case PRIEST -> "ขอแสงสว่างนำทางท่าน";
            case FORTUNE_TELLER -> "ดวงดาววันนี้... มีเรื่องจะบอกท่าน";
            case BANKER -> "ทองของท่านปลอดภัยกับเรา";
            case BOUNTY_MASTER -> "มีหัวให้ล่า มีทองให้จ่าย สนใจไหม";
            case GUARD -> "หยุดก่อน! ...อ้อ นักผจญภัยนี่เอง มีอะไรให้ช่วย";
            case TRAINER -> "ร่างกายแข็งแรง จิตใจก็แข็งแกร่ง มาฝึกกัน";
            case CARTOGRAPHER -> "โลกกว้างกว่าที่ท่านคิด ข้ามีแผนที่ทุกเส้นทาง";
            case COLLECTOR -> "ข้ารับซื้อของดี วันนี้ต้องการของพวกนี้เป็นพิเศษ";
            default -> "";
        };
    }

    // Blacksmith -----------------------------------------------------------------------------------

    static long repairCost(SeasonRules.NpcServiceRules rules, ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageableItem() || stack.getDamageValue() <= 0) return 0;
        return Math.max(rules.repairMinCost, (long) Math.ceil(stack.getDamageValue() * rules.repairPerDurability));
    }

    private static List<ItemStack> gear(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        List<ItemStack> all = new ArrayList<>(inventory.items);
        all.addAll(inventory.armor);
        all.addAll(inventory.offhand);
        return all;
    }

    private static void blacksmith(ServerPlayer player, RotasData data, Entries entries) {
        SeasonRules.NpcServiceRules rules = rules(data);
        ItemStack held = player.getMainHandItem();
        long hand = repairCost(rules, held);
        long all = gear(player).stream().mapToLong(stack -> repairCost(rules, stack)).sum();
        entries.section = "ซ่อม";
        entries.service("repair_hand", "ซ่อมของในมือ", hand > 0 ? held.getHoverName().getString() + " กลับมาใหม่เอี่ยม"
                : "ของในมือไม่ได้เสียหาย", hand, held.isEmpty() ? new ItemStack(Items.ANVIL) : held.copyWithCount(1),
                hand <= 0 ? "ไม่มีอะไรต้องซ่อม" : afford(data, player, hand));
        entries.service("repair_all", "ซ่อมทุกชิ้น", "ซ่อมอาวุธ เกราะ และเครื่องมือทั้งหมดในตัว", all,
                new ItemStack(Items.ANVIL), all <= 0 ? "ไม่มีอะไรต้องซ่อม" : afford(data, player, all));
        entries.section = "งานช่าง";
        entries.action("open_refine", "ตีบวกอาวุธ", "เสริมพลังอาวุธและเกราะ เสี่ยงแตกได้", new ItemStack(Items.SMITHING_TABLE));
        entries.action("open_salvage", "แยกชิ้นส่วน", "ทุบของที่ไม่ใช้ เอาแร่และทองคืน", new ItemStack(Items.GRINDSTONE));
        entries.action("open_sockets", "เจาะช่องการ์ด", "ใส่การ์ดมอนสเตอร์ลงอาวุธ", new ItemStack(Items.PAPER));
    }

    // Enchanter ------------------------------------------------------------------------------------

    private static Map<Enchantment, Integer> removable(ItemStack stack) {
        Map<Enchantment, Integer> result = new LinkedHashMap<>();
        if (stack.isEmpty() || stack.is(Items.ENCHANTED_BOOK)) return result;
        EnchantmentHelper.getEnchantments(stack).forEach((enchantment, level) -> {
            if (!enchantment.isCurse()) result.put(enchantment, level);
        });
        return result;
    }

    static long disenchantCost(SeasonRules.NpcServiceRules rules, Map<Enchantment, Integer> enchantments) {
        if (enchantments.isEmpty()) return 0;
        long levels = enchantments.values().stream().mapToLong(Integer::longValue).sum();
        return rules.disenchantBaseCost + levels * rules.disenchantPerLevel;
    }

    private static void enchanter(ServerPlayer player, RotasData data, Entries entries) {
        ItemStack held = player.getMainHandItem();
        Map<Enchantment, Integer> enchantments = removable(held);
        long cost = disenchantCost(rules(data), enchantments);
        entries.section = "มนตร์";
        entries.service("disenchant", "ถอดมนตร์เป็นหนังสือ", enchantments.isEmpty() ? "ถือของที่มีมนตร์ไว้ในมือ"
                : "ถอด " + enchantments.size() + " มนตร์จาก " + held.getHoverName().getString() + " ใส่หนังสือ (คำสาปไม่ออก)",
                cost, new ItemStack(Items.ENCHANTED_BOOK), enchantments.isEmpty() ? "ไม่มีมนตร์ให้ถอด" : afford(data, player, cost));
        entries.action("open_runes", "สลักรูน", "สลักรูนลงอาวุธเพื่อพลังพิเศษ", new ItemStack(Items.AMETHYST_SHARD));
        entries.action("open_sockets", "เจาะช่องการ์ด", "ใส่การ์ดมอนสเตอร์ลงอาวุธ", new ItemStack(Items.PAPER));
    }

    // Alchemist ------------------------------------------------------------------------------------

    private static MobEffect effect(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? null : BuiltInRegistries.MOB_EFFECT.get(key);
    }

    private static void alchemist(ServerPlayer player, RotasData data, Entries entries) {
        SeasonRules.Brew[] brews = rules(data).brews;
        entries.section = "ยาปรุงสด (ดื่มทันที)";
        for (int i = 0; i < brews.length; i++) {
            SeasonRules.Brew brew = brews[i];
            MobEffect effect = effect(brew.effect);
            if (effect == null) continue;
            ItemStack icon = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);
            PotionUtils.setCustomEffects(icon, List.of(new MobEffectInstance(effect, brew.seconds, brew.amplifier)));
            String name = effect.getDisplayName().getString() + (brew.amplifier > 0 ? " " + roman(brew.amplifier + 1) : "");
            entries.service("brew:" + i, name, "อยู่ได้ " + duration(brew.seconds), brew.price, icon, afford(data, player, brew.price));
        }
    }

    // Innkeeper ------------------------------------------------------------------------------------

    private static void innkeeper(ServerPlayer player, RotasData data, Entries entries) {
        SeasonRules.NpcServiceRules rules = rules(data);
        entries.section = "ที่พัก";
        entries.service("rest", "พักค้างคืน", "ฟื้นเลือด อิ่มท้อง ล้างผลร้าย และได้ 'พักผ่อนเต็มที่' EXP +"
                        + Math.round(rules.restedXp * 100) + "% " + rules.restedMinutes + " นาที",
                rules.restCost, new ItemStack(Items.RED_BED), afford(data, player, rules.restCost));
        entries.service("home", "ตั้งจุดเกิดใหม่ที่นี่", "ตายแล้วจะฟื้นที่โรงเตี๊ยมนี้", rules.homeCost,
                new ItemStack(Items.RESPAWN_ANCHOR), afford(data, player, rules.homeCost));
    }

    private static void rest(ServerPlayer player, RotasData data) {
        player.setHealth(player.getMaxHealth());
        player.getFoodData().eat(20, 20f);
        clearHarmful(player);
        PlayerProgress progress = data.progress(player.getUUID());
        BuffService.grant(progress, BuffService.Buff.RESTED, rules(data).restedMinutes * 60L);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 0.6f);
    }

    private static void clearHarmful(ServerPlayer player) {
        for (MobEffectInstance instance : new ArrayList<>(player.getActiveEffects())) {
            if (instance.getEffect().getCategory() == MobEffectCategory.HARMFUL) player.removeEffect(instance.getEffect());
        }
        player.clearFire();
    }

    // Priest ---------------------------------------------------------------------------------------

    private static boolean curseRemovable(ItemStack stack) {
        return !stack.isEmpty() && EnchantmentHelper.getEnchantments(stack).keySet().stream().anyMatch(Enchantment::isCurse);
    }

    private static void priest(ServerPlayer player, RotasData data, Entries entries) {
        SeasonRules.NpcServiceRules rules = rules(data);
        entries.section = "พิธี";
        if (rules.dailyPrayer) {
            boolean prayed = data.counter("pray|" + player.getUUID(), SeasonService.today()) > 0;
            entries.service("pray", "สวดมนต์ (ฟรีวันละครั้ง)", "ฟื้นเลือดครึ่งหนึ่งและได้พรสั้น ๆ", 0,
                    new ItemStack(Items.CANDLE), prayed ? "วันนี้สวดแล้ว" : null);
        }
        entries.service("cleanse", "ชำระล้าง", "ล้างพิษและผลร้ายทั้งหมด ดับไฟ ฟื้นเลือดเต็ม", rules.cleanseCost,
                new ItemStack(Items.MILK_BUCKET), afford(data, player, rules.cleanseCost));
        entries.service("bless", "ขอพร", "ฟื้นฟู เกราะดูดซับ และโชค " + rules.blessingMinutes + " นาที", rules.blessingCost,
                new ItemStack(Items.TOTEM_OF_UNDYING), afford(data, player, rules.blessingCost));
        ItemStack held = player.getMainHandItem();
        entries.service("lift_curse", "ถอนคำสาป", curseRemovable(held) ? "ลบคำสาปทั้งหมดออกจาก " + held.getHoverName().getString()
                        : "ถือของต้องสาปไว้ในมือ", rules.liftCurseCost, new ItemStack(Items.WITHER_ROSE),
                curseRemovable(held) ? afford(data, player, rules.liftCurseCost) : "ของในมือไม่มีคำสาป");
    }

    // Fortune teller -------------------------------------------------------------------------------

    private static void fortuneTeller(ServerPlayer player, RotasData data, Entries entries) {
        SeasonRules.NpcServiceRules rules = rules(data);
        entries.section = "ดวงชะตา";
        entries.service("fortune", "ดูดวง", "ดวงดีเพิ่ม EXP +" + Math.round(rules.fortuneXp * 100) + "% ในกิจกรรมหนึ่ง "
                        + rules.fortuneMinutes + " นาที และกระซิบข่าวลือ (อาจเจอลางร้าย)", rules.fortuneCost,
                new ItemStack(Items.ENDER_EYE), afford(data, player, rules.fortuneCost));
    }

    private static final BuffService.Buff[] FORTUNES = {BuffService.Buff.FORTUNE_WARRIOR, BuffService.Buff.FORTUNE_ARTISAN,
            BuffService.Buff.FORTUNE_WANDERER, BuffService.Buff.FORTUNE_SCHOLAR};

    private static String fortune(ServerPlayer player, RotasData data) {
        SeasonRules.NpcServiceRules rules = rules(data);
        PlayerProgress progress = data.progress(player.getUUID());
        BuffService.clearFortunes(progress);
        SplittableRandom random = new SplittableRandom(player.getRandom().nextLong());
        String reading;
        if (random.nextDouble() < rules.badOmenChance) {
            BuffService.grant(progress, BuffService.Buff.BAD_OMEN, rules.fortuneMinutes * 60L);
            reading = "ไพ่ใบสุดท้ายคือหอคอยที่พังทลาย... ระวังตัวไว้ วันนี้ดวงไม่เข้าข้างท่าน";
        } else {
            BuffService.Buff buff = FORTUNES[random.nextInt(FORTUNES.length)];
            BuffService.grant(progress, buff, rules.fortuneMinutes * 60L);
            reading = switch (buff) {
                case FORTUNE_WARRIOR -> "ข้าเห็นดาบเปื้อนเลือดและชัยชนะ วันนี้การต่อสู้จะให้ผลงาม";
                case FORTUNE_ARTISAN -> "มือของท่านเปล่งแสง สิ่งที่ท่านสร้าง ขุด หรือเก็บ จะงอกเงย";
                case FORTUNE_WANDERER -> "ทางที่ไม่เคยเดินกำลังเรียกหา จงออกเดินทาง";
                default -> "คำตอบซ่อนอยู่ในคำขอของผู้อื่น ภารกิจวันนี้จะสอนท่านมาก";
            };
        }
        return reading + " — " + rumor(player, data, random);
    }

    /** Something true about the world, told as a rumour. */
    private static String rumor(ServerPlayer player, RotasData data, SplittableRandom random) {
        List<String> rumors = new ArrayList<>();
        for (var nemesis : data.nemeses().values()) {
            String where = NemesisService.whereabouts(player.server, player, nemesis);
            rumors.add("ว่ากันว่า " + nemesis.displayName() + " ยังล่าเหยื่ออยู่" + (where.isBlank() ? "" : " " + where));
        }
        for (Component line : WorldEventService.describe(player, data)) rumors.add("ข่าวลือ: " + line.getString());
        int unknown = 0;
        PlayerProgress progress = data.progress(player.getUUID());
        for (String id : data.waystones().keySet()) if (!progress.knowsWaystone(id)) unknown++;
        if (unknown > 0) rumors.add("ยังมีหินวาร์ปอีก " + unknown + " แห่งที่ท่านยังไม่เคยพบ");
        if (rumors.isEmpty()) rumors.add("โลกเงียบสงบ... เงียบเกินไปด้วยซ้ำ");
        return rumors.get(random.nextInt(rumors.size()));
    }

    // Banker ---------------------------------------------------------------------------------------

    private static long coinsCarried(ServerPlayer player) {
        long total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (net.schwarz.rotasutils.item.GoldCoins.is(stack)) total += net.schwarz.rotasutils.item.GoldCoins.amount(stack);
        }
        return total;
    }

    private static void banker(ServerPlayer player, RotasData data, Entries entries) {
        long carried = coinsCarried(player);
        entries.section = "บัญชี";
        entries.info("ยอดในบัญชี " + balance(data, player) + " ทอง", "ทองในบัญชีไม่หายเมื่อตาย และใช้จ่ายกับ NPC ได้ทันที",
                new ItemStack(Items.GOLD_BLOCK));
        entries.service("deposit", "ฝากเหรียญทองทั้งหมด", carried > 0 ? "ฝาก " + carried + " ทองที่พกอยู่" : "ไม่ได้พกเหรียญทอง",
                0, new ItemStack(Items.GOLD_NUGGET), carried > 0 ? null : "ไม่มีเหรียญให้ฝาก");
        entries.section = "ถอนเป็นเหรียญ";
        for (long step : rules(data).withdrawSteps) {
            if (step <= 0 || step > GoldCoinService.MAX_WITHDRAWAL) continue;
            entries.service("withdraw:" + step, "ถอน " + step + " ทอง", "ได้เหรียญทองเข้ากระเป๋า", 0,
                    new ItemStack(Items.GOLD_INGOT), balance(data, player) >= step ? null : "ยอดไม่พอ");
        }
    }

    // Bounty master --------------------------------------------------------------------------------

    private static void bountyMaster(ServerPlayer player, RotasData data, Entries entries) {
        SeasonRules.NpcServiceRules rules = rules(data);
        PlayerProgress progress = data.progress(player.getUUID());
        BountyService.Active active = BountyService.active(progress);
        long day = SeasonService.today();
        int done = BountyService.doneToday(progress, day);
        if (active != null) {
            entries.section = "งานของท่าน";
            entries.service("bounty_turnin", "ส่งงาน: " + BountyService.targetName(active.entity()) + " " + active.count() + "/" + active.need(),
                    "รางวัล " + active.gold() + " ทอง · " + active.xp() + " EXP", 0, new ItemStack(Items.GOLDEN_SWORD),
                    active.complete() ? null : "ยังล่าไม่ครบ");
            entries.service("bounty_abandon", "ยกเลิกงาน", "ทิ้งงานนี้ ความคืบหน้าจะหายไป", 0,
                    new ItemStack(Items.BARRIER), null);
        }
        entries.section = "กระดานค่าหัววันนี้ · ส่งแล้ว " + done + "/" + rules.bountiesCompletedPerDay;
        for (BountyService.Contract contract : BountyService.today(rules, day)) {
            SeasonRules.BountyDef def = contract.def();
            String blocked = active != null ? "ถืองานอยู่แล้ว" : done >= rules.bountiesCompletedPerDay ? "วันนี้ครบแล้ว"
                    : progress.level() < def.minLevel ? "ต้องเลเวล " + def.minLevel : null;
            ItemStack icon = spawnEgg(def.entity);
            entries.service("bounty_take:" + contract.index(), "ล่า " + BountyService.targetName(def.entity) + " ×" + def.count,
                    "รางวัล " + def.gold + " ทอง · " + def.xp + " EXP", 0, icon, blocked);
        }
        if (!data.nemeses().isEmpty()) {
            entries.section = "ประกาศจับ: ศัตรูคู่แค้น";
            for (var nemesis : data.nemeses().values()) {
                String where = NemesisService.whereabouts(player.server, player, nemesis);
                entries.info(nemesis.displayName() + " · แรงค์ " + nemesis.rank(), "ฆ่าผู้เล่นแล้ว " + nemesis.kills()
                        + " ครั้ง" + (where.isBlank() ? "" : " · " + where), new ItemStack(Items.WITHER_SKELETON_SKULL));
            }
        }
    }

    private static ItemStack spawnEgg(String entity) {
        ResourceLocation id = ResourceLocation.tryParse(entity);
        if (id != null) {
            var type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            var egg = type == null ? null : net.minecraft.world.item.SpawnEggItem.byId(type);
            if (egg != null) return new ItemStack(egg);
        }
        return new ItemStack(Items.IRON_SWORD);
    }

    // Guard ----------------------------------------------------------------------------------------

    private static void guard(ServerPlayer player, RotasData data, Entries entries) {
        entries.section = "รายงานพื้นที่";
        var level = player.serverLevel();
        var region = ZoneService.region(data, level, player.getX(), player.getY(), player.getZ());
        int mine = data.progress(player.getUUID()).level();
        if (region != null) {
            String danger = region.safe() ? "เขตปลอดภัย" : region.level() > mine + 5 ? "อันตรายมากสำหรับท่าน!"
                    : region.level() > mine ? "ระวังตัว" : "ท่านรับมือได้สบาย";
            entries.info("ที่นี่: " + (region.name().isBlank() ? "ป่าเถื่อน" : region.name()),
                    "มอนเลเวลราว " + region.level() + " · " + danger, new ItemStack(Items.SHIELD));
        }
        net.schwarz.rotasutils.waystone.Waystone nearest = null;
        double best = Double.MAX_VALUE;
        String dimension = level.dimension().location().toString();
        for (var waystone : data.waystones().values()) {
            if (!waystone.dimension().equals(dimension)) continue;
            double distance = waystone.pos().distSqr(player.blockPosition());
            if (distance < best) {
                best = distance;
                nearest = waystone;
            }
        }
        if (nearest != null) {
            entries.info("หินวาร์ปใกล้สุด: " + nearest.name(), direction(player, nearest.pos()) + " · "
                    + Math.round(Math.sqrt(best)) + " บล็อก", new ItemStack(Items.LODESTONE));
        }
        for (Component line : WorldEventService.describe(player, data)) {
            entries.info("เหตุการณ์", line.getString(), new ItemStack(Items.BELL));
        }
        int nearby = 0;
        for (var nemesis : data.nemeses().values()) {
            if (!NemesisService.whereabouts(player.server, player, nemesis).isBlank()) nearby++;
        }
        entries.info(nearby > 0 ? "มีรายงานศัตรูคู่แค้น " + nearby + " ตัว" : "ไม่มีรายงานศัตรูคู่แค้น",
                nearby > 0 ? "ถามนักล่าค่าหัวเพื่อดูประกาศจับ" : "ขอให้สงบแบบนี้ต่อไป", new ItemStack(Items.SPYGLASS));
    }

    static String direction(ServerPlayer player, BlockPos target) {
        double dx = target.getX() + 0.5 - player.getX();
        double dz = target.getZ() + 0.5 - player.getZ();
        String[] names = {"ใต้", "ตะวันตกเฉียงใต้", "ตะวันตก", "ตะวันตกเฉียงเหนือ", "เหนือ", "ตะวันออกเฉียงเหนือ", "ตะวันออก", "ตะวันออกเฉียงใต้"};
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        int index = (int) Math.floorMod(Math.round(angle / 45.0), 8);
        return "ทาง" + names[index];
    }

    // Trainer --------------------------------------------------------------------------------------

    private static void trainer(ServerPlayer player, RotasData data, Entries entries) {
        entries.section = "ฝึกฝน";
        entries.screen("stats", "จัดสรรค่าสถานะ", "ลงแต้ม STR VIT INT AGI", new ItemStack(Items.IRON_CHESTPLATE));
        entries.action("open_skills", "ต้นไม้สกิล", "เรียนสกิลอาชีพ", new ItemStack(Items.ENCHANTED_BOOK));
        entries.screen("job", "อาชีพ", "ดูหรือเปลี่ยนอาชีพ", new ItemStack(Items.NAME_TAG));
        entries.add("action", "respec_stats", "ล้างค่าสถานะ", "คืนแต้มทั้งหมดเพื่อจัดใหม่",
                SeasonService.rules(data).stats.respecCost, new ItemStack(Items.LAVA_BUCKET), null).putBoolean("confirm", true);
    }

    // Cartographer ---------------------------------------------------------------------------------

    private static void cartographer(ServerPlayer player, RotasData data, Entries entries) {
        PlayerProgress progress = data.progress(player.getUUID());
        int known = 0;
        for (String id : data.waystones().keySet()) if (progress.knowsWaystone(id)) known++;
        entries.section = "แผนที่";
        entries.info("หินวาร์ปที่รู้จัก " + known + "/" + data.waystones().size(), "ไปหาหินที่เหลือเพื่อเดินทางได้ไกลขึ้น",
                new ItemStack(Items.FILLED_MAP));
        entries.action("waystone_open", "เดินทางด้วยหินวาร์ป", "วาร์ปไปหินที่เคยพบ (เสียค่าเดินทาง)", new ItemStack(Items.ENDER_PEARL));
        net.schwarz.rotasutils.waystone.Waystone nearest = null;
        double best = Double.MAX_VALUE;
        String dimension = player.level().dimension().location().toString();
        for (var waystone : data.waystones().values()) {
            if (progress.knowsWaystone(waystone.id()) || !waystone.dimension().equals(dimension)) continue;
            double distance = waystone.pos().distSqr(player.blockPosition());
            if (distance < best) {
                best = distance;
                nearest = waystone;
            }
        }
        if (nearest != null) {
            entries.info("หินที่ยังไม่พบใกล้สุด", direction(player, nearest.pos()) + " ราว " + Math.round(Math.sqrt(best)) + " บล็อก",
                    new ItemStack(Items.COMPASS));
        }
        entries.action("open_bestiary", "สมุดมอนสเตอร์", "ดูมอนที่พบแล้วและจุดที่มันอยู่", new ItemStack(Items.BOOK));
    }

    // Collector ------------------------------------------------------------------------------------

    /** Indexes of today's specially wanted items, the same for everyone. */
    static List<Integer> picks(SeasonRules.NpcServiceRules rules, long day) {
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < rules.wanted.length; i++) all.add(i);
        SplittableRandom random = new SplittableRandom(day * 31 + 7);
        List<Integer> picks = new ArrayList<>();
        while (!all.isEmpty() && picks.size() < rules.collectorPicksPerDay) picks.add(all.remove(random.nextInt(all.size())));
        return picks;
    }

    private static Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? Items.AIR : BuiltInRegistries.ITEM.get(key);
    }

    private static int carried(ServerPlayer player, Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item) && !stack.hasTag()) count += stack.getCount();
        }
        return count;
    }

    static long price(SeasonRules.NpcServiceRules rules, int index, List<Integer> picks) {
        long base = rules.wanted[index].price;
        return picks.contains(index) ? Math.round(base * rules.collectorBonus) : base;
    }

    private static void collector(ServerPlayer player, RotasData data, Entries entries) {
        SeasonRules.NpcServiceRules rules = rules(data);
        long day = SeasonService.today();
        List<Integer> picks = picks(rules, day);
        long sold = data.counter("collector|" + player.getUUID(), day);
        long left = Math.max(0, rules.collectorDailyItems - sold);
        String limit = left <= 0 ? "วันนี้ขายครบแล้ว" : null;
        entries.section = "ต้องการพิเศษวันนี้ (×" + rules.collectorBonus + ") · ขายได้อีก " + left + " ชิ้น";
        for (int index : picks) addWanted(player, rules, entries, index, picks, limit);
        entries.section = "รับซื้อทั่วไป";
        for (int i = 0; i < rules.wanted.length; i++) {
            if (!picks.contains(i)) addWanted(player, rules, entries, i, picks, limit);
        }
    }

    private static void addWanted(ServerPlayer player, SeasonRules.NpcServiceRules rules, Entries entries, int index,
                                  List<Integer> picks, String limit) {
        Item item = item(rules.wanted[index].item);
        if (item == Items.AIR) return;
        int have = carried(player, item);
        long price = price(rules, index, picks);
        entries.service("sell:" + index, new ItemStack(item).getHoverName().getString() + " · " + price + " ทอง/ชิ้น",
                have > 0 ? "มีอยู่ " + have + " ชิ้น ขายทั้งหมดได้ " + price * have + " ทอง" : "ไม่มีในกระเป๋า",
                0, new ItemStack(item), have <= 0 ? "ไม่มีของ" : limit);
    }

    // Performing -----------------------------------------------------------------------------------

    /** Runs one service entry, then shows the refreshed screen. */
    public static void perform(ServerPlayer player, RotasData data, NpcDef npc, String service) {
        String result = run(player, data, npc, service == null ? "" : service);
        boolean ok = result == null || result.startsWith("+");
        RotasNetwork.feedback(player, ok, result == null ? "เรียบร้อย" : ok ? result.substring(1) : result);
        RotasNetwork.syncProgress(player);
        open(player, data, npc);
    }

    /** Null or "+message" on success, otherwise why it failed. */
    private static String run(ServerPlayer player, RotasData data, NpcDef npc, String service) {
        SeasonRules.NpcServiceRules rules = rules(data);
        String id = service.contains(":") ? service.substring(0, service.indexOf(':')) : service;
        String arg = service.contains(":") ? service.substring(service.indexOf(':') + 1) : "";
        NpcDef.Role role = npc.role();
        switch (id) {
            case "repair_hand", "repair_all" -> {
                if (role != NpcDef.Role.BLACKSMITH) return "ช่างคนนี้ไม่รับซ่อม";
                List<ItemStack> targets = id.equals("repair_hand") ? List.of(player.getMainHandItem()) : gear(player);
                long cost = targets.stream().mapToLong(stack -> repairCost(rules, stack)).sum();
                if (cost <= 0) return "ไม่มีอะไรต้องซ่อม";
                if (!pay(data, player, cost)) return "เงินไม่พอ (" + cost + " ทอง)";
                targets.forEach(stack -> { if (repairCost(rules, stack) > 0) stack.setDamageValue(0); });
                player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.7f, 1.1f);
                return "+ซ่อมเสร็จแล้ว (" + cost + " ทอง)";
            }
            case "disenchant" -> {
                if (role != NpcDef.Role.ENCHANTER) return "ที่นี่ถอดมนตร์ไม่ได้";
                ItemStack held = player.getMainHandItem();
                Map<Enchantment, Integer> enchantments = removable(held);
                long cost = disenchantCost(rules, enchantments);
                if (enchantments.isEmpty()) return "ไม่มีมนตร์ให้ถอด";
                if (!pay(data, player, cost)) return "เงินไม่พอ (" + cost + " ทอง)";
                ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                enchantments.forEach((enchantment, level) -> EnchantedBookItem.addEnchantment(book, new EnchantmentInstance(enchantment, level)));
                Map<Enchantment, Integer> kept = new LinkedHashMap<>();
                EnchantmentHelper.getEnchantments(held).forEach((enchantment, level) -> { if (enchantment.isCurse()) kept.put(enchantment, level); });
                EnchantmentHelper.setEnchantments(kept, held);
                if (!player.getInventory().add(book)) player.drop(book, false);
                player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8f, 0.8f);
                return "+ถอดมนตร์ใส่หนังสือแล้ว";
            }
            case "brew" -> {
                if (role != NpcDef.Role.ALCHEMIST) return "ที่นี่ไม่ได้ขายยา";
                int index = parseInt(arg);
                if (index < 0 || index >= rules.brews.length) return "ไม่มียานี้แล้ว";
                SeasonRules.Brew brew = rules.brews[index];
                MobEffect effect = effect(brew.effect);
                if (effect == null) return "ยานี้ปรุงไม่ได้";
                if (!pay(data, player, brew.price)) return "เงินไม่พอ (" + brew.price + " ทอง)";
                player.addEffect(new MobEffectInstance(effect, brew.seconds * 20, brew.amplifier));
                player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.8f, 1f);
                return "+ดื่ม " + effect.getDisplayName().getString() + " แล้ว";
            }
            case "rest" -> {
                if (role != NpcDef.Role.INNKEEPER) return "ที่นี่ไม่มีห้องพัก";
                if (!pay(data, player, rules.restCost)) return "เงินไม่พอ";
                rest(player, data);
                return "+หลับสบาย ตื่นมาพร้อมลุย! EXP +" + Math.round(rules.restedXp * 100) + "% " + rules.restedMinutes + " นาที";
            }
            case "home" -> {
                if (role != NpcDef.Role.INNKEEPER) return "ที่นี่ตั้งจุดเกิดไม่ได้";
                if (!pay(data, player, rules.homeCost)) return "เงินไม่พอ";
                player.setRespawnPosition(player.level().dimension(), player.blockPosition(), player.getYRot(), true, false);
                return "+ตั้งจุดเกิดใหม่ที่โรงเตี๊ยมแล้ว";
            }
            case "pray" -> {
                if (role != NpcDef.Role.PRIEST || !rules.dailyPrayer) return "ที่นี่ไม่มีพิธีนี้";
                if (!data.addCounter("pray|" + player.getUUID(), SeasonService.today(), 1, 1)) return "วันนี้สวดแล้ว";
                player.heal(player.getMaxHealth() / 2);
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 20, 0));
                return "+จิตใจสงบ ร่างกายเบาสบาย";
            }
            case "cleanse" -> {
                if (role != NpcDef.Role.PRIEST) return "ที่นี่ไม่มีพิธีนี้";
                if (!pay(data, player, rules.cleanseCost)) return "เงินไม่พอ";
                clearHarmful(player);
                player.setHealth(player.getMaxHealth());
                return "+ชำระล้างแล้ว";
            }
            case "bless" -> {
                if (role != NpcDef.Role.PRIEST) return "ที่นี่ไม่มีพิธีนี้";
                if (!pay(data, player, rules.blessingCost)) return "เงินไม่พอ";
                int ticks = rules.blessingMinutes * 60 * 20;
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, Math.min(ticks, 20 * 60), 0));
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ticks, 1));
                player.addEffect(new MobEffectInstance(MobEffects.LUCK, ticks, 0));
                player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7f, 1.4f);
                return "+พรคุ้มครองท่านแล้ว";
            }
            case "lift_curse" -> {
                if (role != NpcDef.Role.PRIEST) return "ที่นี่ไม่มีพิธีนี้";
                ItemStack held = player.getMainHandItem();
                if (!curseRemovable(held)) return "ของในมือไม่มีคำสาป";
                if (!pay(data, player, rules.liftCurseCost)) return "เงินไม่พอ";
                Map<Enchantment, Integer> kept = new LinkedHashMap<>();
                EnchantmentHelper.getEnchantments(held).forEach((enchantment, level) -> { if (!enchantment.isCurse()) kept.put(enchantment, level); });
                EnchantmentHelper.setEnchantments(kept, held);
                return "+คำสาปสลายไปแล้ว";
            }
            case "fortune" -> {
                if (role != NpcDef.Role.FORTUNE_TELLER) return "คนนี้ไม่ได้ดูดวง";
                if (!pay(data, player, rules.fortuneCost)) return "เงินไม่พอ";
                String reading = fortune(player, data);
                player.sendSystemMessage(Component.literal(npc.name() + ": " + reading).withStyle(ChatFormatting.LIGHT_PURPLE));
                return "+ไพ่ถูกเปิดแล้ว ดูคำทำนายในแชท";
            }
            case "deposit" -> {
                if (role != NpcDef.Role.BANKER) return "ที่นี่ไม่ใช่ธนาคาร";
                boolean any = false;
                for (ItemStack stack : player.getInventory().items) {
                    if (net.schwarz.rotasutils.item.GoldCoins.is(stack)) any |= GoldCoinService.depositHeld(player, stack);
                }
                return any ? "+ฝากเรียบร้อย" : "ไม่มีเหรียญให้ฝาก";
            }
            case "withdraw" -> {
                if (role != NpcDef.Role.BANKER) return "ที่นี่ไม่ใช่ธนาคาร";
                long amount = parseLong(arg);
                boolean allowed = false;
                for (long step : rules.withdrawSteps) allowed |= step == amount;
                if (!allowed || amount > GoldCoinService.MAX_WITHDRAWAL) return "จำนวนไม่ถูกต้อง";
                return GoldCoinService.withdraw(player, (int) amount) ? "+ถอนแล้ว" : "ถอนไม่สำเร็จ";
            }
            case "bounty_take" -> {
                if (role != NpcDef.Role.BOUNTY_MASTER) return "ที่นี่ไม่มีงานค่าหัว";
                String blocked = BountyService.take(player, data, parseInt(arg));
                return blocked == null ? "+รับงานแล้ว ออกล่าได้!" : blocked;
            }
            case "bounty_abandon" -> {
                if (role != NpcDef.Role.BOUNTY_MASTER) return "ที่นี่ไม่มีงานค่าหัว";
                BountyService.abandon(data, data.progress(player.getUUID()));
                return "+ยกเลิกงานแล้ว";
            }
            case "bounty_turnin" -> {
                if (role != NpcDef.Role.BOUNTY_MASTER) return "ที่นี่ไม่มีงานค่าหัว";
                String blocked = BountyService.turnIn(player, data);
                if (blocked != null) return blocked;
                player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.2f);
                TitleService.check(player, data);
                return "+รับค่าหัวแล้ว!";
            }
            case "sell" -> {
                if (role != NpcDef.Role.COLLECTOR) return "คนนี้ไม่รับซื้อของ";
                int index = parseInt(arg);
                if (index < 0 || index >= rules.wanted.length) return "ไม่รับซื้อของนี้แล้ว";
                Item item = item(rules.wanted[index].item);
                if (item == Items.AIR) return "ไม่รับซื้อของนี้แล้ว";
                long day = SeasonService.today();
                long left = Math.max(0, rules.collectorDailyItems - data.counter("collector|" + player.getUUID(), day));
                int sell = (int) Math.min(left, carried(player, item));
                if (sell <= 0) return left <= 0 ? "วันนี้ขายครบแล้ว" : "ไม่มีของ";
                long price = price(rules, index, picks(rules, day));
                int removed = 0;
                for (ItemStack stack : player.getInventory().items) {
                    if (removed >= sell) break;
                    if (!stack.is(item) || stack.hasTag()) continue;
                    int take = Math.min(stack.getCount(), sell - removed);
                    stack.shrink(take);
                    removed += take;
                }
                data.addCounter("collector|" + player.getUUID(), day, rules.collectorDailyItems, removed);
                long gold = price * removed;
                data.progress(player.getUUID()).rpg().currency(GoldCoinService.CURRENCY, gold);
                long xp = Math.round(gold * rules.collectorXpPerGold);
                if (xp > 0) ProgressService.awardFromSource(player, data, XpSource.TRADING, "collector", xp);
                TitleService.count(player.server, data, player.getUUID(), net.schwarz.rotasutils.title.TitleCounters.TRADE_GOLD, gold);
                data.setDirty();
                player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8f, 1f);
                return "+ขาย " + removed + " ชิ้น ได้ " + gold + " ทอง";
            }
            default -> {
                return "ไม่รู้จักบริการนี้";
            }
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException bad) {
            return -1;
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException bad) {
            return -1;
        }
    }

    private static String roman(int value) {
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return numerals[Math.max(0, Math.min(numerals.length - 1, value - 1))];
    }

    private static String duration(int seconds) {
        return seconds >= 60 ? seconds / 60 + " นาที" : seconds + " วินาที";
    }
}
