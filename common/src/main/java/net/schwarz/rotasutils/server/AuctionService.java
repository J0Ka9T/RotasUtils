package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.npc.NpcDef;
import net.schwarz.rotasutils.progress.PlayerProgress;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The auction house: players list an item at a fixed price, anyone buys it at an auctioneer, and the seller is paid
 * straight into their wallet (online or not) less the house fee. Unsold items come back to a claim box when they
 * expire. Listings live in their own save file so the item snapshots never touch the progress record.
 */
public final class AuctionService {
    public static final class Listing {
        public String id;
        public UUID seller;
        public String sellerName;
        public ItemStack item = ItemStack.EMPTY;
        public long price;
        public long expires;

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", id);
            tag.putUUID("seller", seller);
            tag.putString("seller_name", sellerName);
            tag.put("item", item.save(new CompoundTag()));
            tag.putLong("price", price);
            tag.putLong("expires", expires);
            return tag;
        }

        static Listing load(CompoundTag tag) {
            Listing listing = new Listing();
            listing.id = tag.getString("id");
            listing.seller = tag.getUUID("seller");
            listing.sellerName = tag.getString("seller_name");
            listing.item = ItemStack.of(tag.getCompound("item"));
            listing.price = tag.getLong("price");
            listing.expires = tag.getLong("expires");
            return listing;
        }
    }

    public static final class Store extends SavedData {
        final Map<String, Listing> listings = new LinkedHashMap<>();
        /** Items waiting to be collected: expired listings. */
        final Map<UUID, List<ItemStack>> claims = new LinkedHashMap<>();

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            listings.values().forEach(listing -> list.add(listing.save()));
            tag.put("listings", list);
            CompoundTag claimTag = new CompoundTag();
            claims.forEach((owner, items) -> {
                ListTag stacks = new ListTag();
                items.forEach(stack -> stacks.add(stack.save(new CompoundTag())));
                claimTag.put(owner.toString(), stacks);
            });
            tag.put("claims", claimTag);
            return tag;
        }

        static Store load(CompoundTag tag) {
            Store store = new Store();
            ListTag list = tag.getList("listings", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                try {
                    Listing listing = Listing.load(list.getCompound(i));
                    if (!listing.item.isEmpty()) store.listings.put(listing.id, listing);
                } catch (RuntimeException malformed) {
                    Rotasutils.LOG.error("Skipping a malformed auction listing: {}", malformed.toString());
                }
            }
            CompoundTag claimTag = tag.getCompound("claims");
            for (String key : claimTag.getAllKeys()) {
                try {
                    List<ItemStack> items = new ArrayList<>();
                    ListTag stacks = claimTag.getList(key, Tag.TAG_COMPOUND);
                    for (int i = 0; i < stacks.size(); i++) {
                        ItemStack stack = ItemStack.of(stacks.getCompound(i));
                        if (!stack.isEmpty()) items.add(stack);
                    }
                    store.claims.put(UUID.fromString(key), items);
                } catch (IllegalArgumentException malformed) {
                    Rotasutils.LOG.error("Skipping a malformed auction claim owner: {}", key);
                }
            }
            return store;
        }
    }

    private AuctionService() {
    }

    public static Store store(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Store::load, Store::new, Rotasutils.MOD_ID + "_auction");
    }

    private static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    private static SeasonRules.NpcServiceRules rules(RotasData data) {
        return SeasonService.rules(data).npcServices;
    }

    /** Moves expired listings into their sellers' claim boxes. */
    static void sweep(Store store) {
        long now = now();
        var iterator = store.listings.values().iterator();
        while (iterator.hasNext()) {
            Listing listing = iterator.next();
            if (listing.expires > now) continue;
            store.claims.computeIfAbsent(listing.seller, key -> new ArrayList<>()).add(listing.item);
            iterator.remove();
            store.setDirty();
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    public static void open(ServerPlayer player, RotasData data, NpcDef npc, String tab) {
        Store store = store(player.server);
        sweep(store);
        SeasonRules.NpcServiceRules rules = rules(data);
        CompoundTag payload = new CompoundTag();
        payload.putString("npc", npc.id());
        payload.putString("name", npc.name());
        payload.putString("tab", tab == null ? "" : tab);
        payload.putLong("balance", data.progress(player.getUUID()).rpg().currency(GoldCoinService.CURRENCY));
        payload.putDouble("fee", rules.auctionFee);
        payload.putInt("max_listings", rules.auctionMaxListings);
        payload.putInt("hours", rules.auctionHours);
        payload.putLong("max_price", rules.auctionMaxPrice);
        ListTag listings = new ListTag();
        long now = now();
        for (Listing listing : store.listings.values()) {
            CompoundTag row = new CompoundTag();
            row.putString("id", listing.id);
            row.putString("seller", listing.sellerName);
            row.putBoolean("mine", listing.seller.equals(player.getUUID()));
            row.put("item", listing.item.save(new CompoundTag()));
            row.putLong("price", listing.price);
            row.putLong("left", Math.max(0, listing.expires - now));
            listings.add(row);
        }
        payload.put("listings", listings);
        payload.putInt("claims", store.claims.getOrDefault(player.getUUID(), List.of()).size());
        RotasNetwork.openScreen(player, "auction", payload);
    }

    public static void handle(ServerPlayer player, RotasData data, NpcDef npc, String action, CompoundTag payload) {
        if (npc.role() != NpcDef.Role.AUCTIONEER) return;
        String result = switch (action) {
            case "auction_list" -> list(player, data, payload.getLong("price"));
            case "auction_buy" -> buy(player, data, payload.getString("id"), payload.getLong("price"));
            case "auction_cancel" -> cancel(player, payload.getString("id"));
            case "auction_claim" -> claim(player);
            default -> "?";
        };
        boolean ok = result.startsWith("+");
        RotasNetwork.feedback(player, ok, ok ? result.substring(1) : result);
        RotasNetwork.syncProgress(player);
        open(player, data, npc, payload.getString("tab"));
    }

    static String list(ServerPlayer player, RotasData data, long price) {
        SeasonRules.NpcServiceRules rules = rules(data);
        Store store = store(player.server);
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return "ถือของที่จะขายไว้ในมือ";
        if (price < 1 || price > rules.auctionMaxPrice) return "ราคาต้องอยู่ระหว่าง 1 ถึง " + rules.auctionMaxPrice;
        long mine = store.listings.values().stream().filter(listing -> listing.seller.equals(player.getUUID())).count();
        if (mine >= rules.auctionMaxListings) return "ลงขายได้สูงสุด " + rules.auctionMaxListings + " ชิ้น";
        Listing listing = new Listing();
        do {
            listing.id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        } while (store.listings.containsKey(listing.id));
        listing.seller = player.getUUID();
        listing.sellerName = player.getGameProfile().getName();
        listing.item = held.copy();
        listing.price = price;
        listing.expires = now() + rules.auctionHours * 3600L;
        player.getInventory().setItem(player.getInventory().selected, ItemStack.EMPTY);
        store.listings.put(listing.id, listing);
        store.setDirty();
        data.audit(listing.sellerName + " listed " + listing.item.getCount() + "x " + listing.item.getItem() + " for " + price);
        return "+ลงขายแล้ว";
    }

    static String buy(ServerPlayer player, RotasData data, String id, long expectedPrice) {
        Store store = store(player.server);
        sweep(store);
        Listing listing = store.listings.get(id);
        if (listing == null) return "ของชิ้นนี้ขายไปแล้วหรือหมดเวลา";
        if (listing.seller.equals(player.getUUID())) return "ซื้อของตัวเองไม่ได้";
        if (listing.price != expectedPrice) return "ราคาเปลี่ยนไปแล้ว";
        PlayerProgress buyer = data.progress(player.getUUID());
        if (buyer.rpg().currency(GoldCoinService.CURRENCY) < listing.price) return "เงินไม่พอ";
        buyer.rpg().currency(GoldCoinService.CURRENCY, -listing.price);
        long fee = Math.max(0, Math.min(listing.price, Math.round(listing.price * rules(data).auctionFee)));
        data.progress(listing.seller).rpg().currency(GoldCoinService.CURRENCY, listing.price - fee);
        store.listings.remove(id);
        store.setDirty();
        data.setDirty();
        give(player, listing.item.copy());
        ServerPlayer seller = player.server.getPlayerList().getPlayer(listing.seller);
        if (seller != null) {
            seller.sendSystemMessage(Component.literal("โรงประมูล: " + listing.item.getHoverName().getString() + " ขายได้แล้ว +"
                    + (listing.price - fee) + " ทอง").withStyle(ChatFormatting.GOLD));
            RotasNetwork.syncProgress(seller);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8f, 1f);
        data.audit(player.getGameProfile().getName() + " bought auction " + id + " from " + listing.sellerName + " for " + listing.price + " fee=" + fee);
        return "+ซื้อสำเร็จ";
    }

    static String cancel(ServerPlayer player, String id) {
        Store store = store(player.server);
        Listing listing = store.listings.get(id);
        if (listing == null || !listing.seller.equals(player.getUUID())) return "ไม่พบรายการของท่าน";
        store.listings.remove(id);
        store.setDirty();
        give(player, listing.item);
        return "+ถอนของคืนแล้ว";
    }

    static String claim(ServerPlayer player) {
        Store store = store(player.server);
        sweep(store);
        List<ItemStack> items = store.claims.remove(player.getUUID());
        if (items == null || items.isEmpty()) return "ไม่มีของค้างรับ";
        store.setDirty();
        items.forEach(stack -> give(player, stack));
        return "+รับของคืน " + items.size() + " รายการ";
    }
}
