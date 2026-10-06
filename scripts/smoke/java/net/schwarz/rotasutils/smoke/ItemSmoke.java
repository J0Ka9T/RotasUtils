package net.schwarz.rotasutils.smoke;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.ContentPacks;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.server.ItemFactory;
import net.schwarz.rotasutils.server.LootService;
import java.util.List;
import java.util.UUID;

public final class ItemSmoke {
    private static final UUID ID = UUID.fromString("2f9a1c34-2b6e-4f27-9a51-6d0b9c8e77a2");
    private static final String ACTOR = "smoke-item";
    private static final ContentId SWORD = new ContentId("rotas:item/smoke_sword");
    private static final ContentId LOCKED = new ContentId("rotas:item/smoke_locked");
    private static final ContentId TABLE = new ContentId("rotas:loot/smoke");
    private static final List<String> DEFINITIONS = List.of(
            "{\"schema\":1,\"id\":\"rotas:rarity/smoke_rare\",\"kind\":\"rarity\",\"body\":{\"label\":\"Rare\",\"color\":\"blue\",\"rank\":2,\"modifier_multiplier\":2}}",
            "{\"schema\":1,\"id\":\"rotas:item/smoke_sword\",\"kind\":\"item\",\"body\":{\"item\":\"minecraft:iron_sword\",\"slot\":\"MAINHAND\","
                    + "\"min_level\":1,\"max_level\":50,\"name\":\"{rarity} Blade Lv{level}\",\"rarities\":{\"rotas:rarity/smoke_rare\":1},"
                    + "\"modifiers\":[{\"attribute\":\"minecraft:generic.attack_damage\",\"base\":2,\"per_level\":0.5}]}}",
            "{\"schema\":1,\"id\":\"rotas:item/smoke_locked\",\"kind\":\"item\",\"body\":{\"item\":\"minecraft:iron_chestplate\",\"slot\":\"CHEST\","
                    + "\"rarities\":{\"rotas:rarity/smoke_rare\":1},\"requirement\":{\"min_level\":50}}}",
            "{\"schema\":1,\"id\":\"rotas:item/smoke_helm\",\"kind\":\"item\",\"body\":{\"item\":\"minecraft:iron_helmet\",\"slot\":\"HEAD\","
                    + "\"set\":\"rotas:set/smoke\",\"rarities\":{\"rotas:rarity/smoke_rare\":1}}}",
            "{\"schema\":1,\"id\":\"rotas:item/smoke_boots\",\"kind\":\"item\",\"body\":{\"item\":\"minecraft:iron_boots\",\"slot\":\"FEET\","
                    + "\"set\":\"rotas:set/smoke\",\"rarities\":{\"rotas:rarity/smoke_rare\":1}}}",
            "{\"schema\":1,\"id\":\"rotas:set/smoke\",\"kind\":\"set\",\"body\":{\"label\":\"Smoke\",\"pieces\":[\"rotas:item/smoke_helm\",\"rotas:item/smoke_boots\"],"
                    + "\"bonuses\":[{\"pieces\":2,\"modifiers\":[{\"attribute\":\"minecraft:generic.armor\",\"base\":6}]}]}}",
            "{\"schema\":1,\"id\":\"rotas:loot/smoke\",\"kind\":\"loot\",\"body\":{\"min_rolls\":2,\"max_rolls\":2,\"entries\":["
                    + "{\"weight\":3,\"item\":\"minecraft:diamond\",\"min_count\":1,\"max_count\":3},"
                    + "{\"weight\":1,\"profile\":\"rotas:item/smoke_sword\"}]}}");

    private ItemSmoke() { }

    static void attach(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rotas_item_smoke").requires(source -> source.hasPermission(4))
                .executes(context -> {
                    MinecraftServer server = context.getSource().getServer();
                    var kernel = RotasData.get(server).kernel();
                    if (kernel.content().items().profiles().containsKey(SWORD)) { return report(server); }
                    if (kernel.history().draft(ACTOR) != null) { kernel.history().discard(ACTOR); }
                    kernel.history().begin(ACTOR);
                    try {
                        for (String definition : DEFINITIONS) { kernel.history().put(ACTOR, ContentPacks.parse(definition)); }
                    } catch (java.io.IOException error) { throw new IllegalArgumentException(error); }
                    require(kernel.validateDraft(ACTOR, true, () -> true, result -> {
                        if (!result.valid()) {
                            net.schwarz.rotasutils.Rotasutils.LOG.error("ROTAS_ITEM_SMOKE_FAIL {}", result.issues());
                            return;
                        }
                        report(server);
                    }), "item definition publication scheduled");
                    return 1;
                }));
    }

    private static int report(MinecraftServer server) {
        try {
            checks(server);
            net.schwarz.rotasutils.Rotasutils.LOG.info("ROTAS_ITEM_SMOKE_PASS");
            return 1;
        } catch (Exception | AssertionError error) {
            net.schwarz.rotasutils.Rotasutils.LOG.error("ROTAS_ITEM_SMOKE_FAIL", error);
            return 0;
        }
    }

    private static void checks(MinecraftServer server) {
        var data = RotasData.get(server);
        var catalog = data.kernel().content().items();
        var player = new FakePlayer(server.overworld(), new GameProfile(ID, "ItemSmoke"));
        player.getInventory().clearContent();
        var progress = data.progress(ID);
        progress.drainMail();
        progress.setLevel(1);

        ItemStack sword = ItemFactory.create(catalog, SWORD, 11, 1, LootService.random("smoke|sword"));
        require(sword.is(Items.IRON_SWORD), "profile base item");
        require(ItemFactory.isRpgItem(sword) && SWORD.equals(ItemFactory.profileId(sword)), "stack carries its profile");
        require(ItemFactory.level(sword) == 11, "item level from source level: " + ItemFactory.level(sword));
        require(ItemFactory.rarity(sword).value().equals("rotas:rarity/smoke_rare"), "rarity rolled from profile weights");
        require(sword.getHoverName().getString().equals("Rare Blade Lv11"), "name template: " + sword.getHoverName().getString());
        var modifiers = sword.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE);
        double added = modifiers.stream().filter(modifier -> modifier.getName().startsWith("rotasutils.item/"))
                .mapToDouble(net.minecraft.world.entity.ai.attributes.AttributeModifier::getAmount).sum();
        require(Math.abs(added - 14.0) < .001, "scaled attack damage modifier: " + added);
        require(ItemFactory.level(ItemFactory.create(catalog, SWORD, 9999, 1, LootService.random("smoke|clamp"))) == 50, "item level clamp");

        List<ItemStack> first = LootService.roll(catalog, TABLE, "seed|a", 10, 1, null);
        List<ItemStack> repeat = LootService.roll(catalog, TABLE, "seed|a", 10, 1, null);
        List<ItemStack> other = LootService.roll(catalog, TABLE, "seed|b", 10, 1, null);
        require(first.size() == 2, "table rolled its declared count: " + first.size());
        require(equal(first, repeat), "identical seed reproduces identical loot");
        require(!equal(first, other) || first.size() != 2, "a different seed can produce different loot");
        require(LootService.roll(catalog, TABLE, "seed|a", 10, 2, null).size() == 4, "tier multiplier scales the roll count");
        require(LootService.roll(catalog, TABLE, "seed|a", 10, 0, null).isEmpty(), "a zero multiplier drops nothing");

        String occurrence = "run-" + UUID.randomUUID();
        require(LootService.grantOnce(player, data, TABLE, occurrence, 10, 1), "first loot grant applies");
        int afterFirst = count(player);
        require(afterFirst > 0, "loot reached the inventory");
        require(!LootService.grantOnce(player, data, TABLE, occurrence, 10, 1), "receipt blocks a duplicate loot grant");
        require(count(player) == afterFirst, "rejected duplicate changed nothing");

        player.getInventory().clearContent();
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            player.getInventory().items.set(slot, new ItemStack(Items.STONE, 64));
        }
        player.getInventory().offhand.set(0, new ItemStack(Items.STONE, 64));
        require(LootService.grantOnce(player, data, TABLE, "run-" + UUID.randomUUID(), 10, 1), "second occurrence grants");
        require(!progress.mailbox().isEmpty(), "undeliverable loot is stored, not lost");
        int stored = progress.mailbox().size();
        require(LootService.recover(player) == 0, "a full inventory recovers nothing");
        require(progress.mailbox().size() == stored, "failed recovery returns the stacks");
        player.getInventory().clearContent();
        require(LootService.recover(player) == stored, "recovery delivers every pending stack");
        require(progress.mailbox().isEmpty(), "mailbox is empty after recovery");

        var equipment = data.kernel().equipment();
        player.getInventory().clearContent();
        ItemStack locked = ItemFactory.create(catalog, LOCKED, 1, 1, LootService.random("smoke|locked"));
        player.setItemSlot(EquipmentSlot.CHEST, locked);
        require(equipment.refresh(player, data) == 1, "unmet requirement removes the equipped item");
        require(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "requirement sweep clears the slot");
        require(count(player) == 1 || !progress.mailbox().isEmpty(), "removed item returns to the player");
        progress.setLevel(60);
        player.getInventory().clearContent();
        player.setItemSlot(EquipmentSlot.CHEST, ItemFactory.create(catalog, LOCKED, 1, 1, LootService.random("smoke|locked2")));
        require(equipment.refresh(player, data) == 0, "a qualified player keeps the item");
        require(!player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "qualified equipment stays equipped");

        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        equipment.forget(ID);
        double armourBefore = player.getAttributeValue(Attributes.ARMOR);
        player.setItemSlot(EquipmentSlot.HEAD, ItemFactory.create(catalog, new ContentId("rotas:item/smoke_helm"), 1, 1, LootService.random("s|h")));
        equipment.refresh(player, data);
        double onePiece = player.getAttributeValue(Attributes.ARMOR);
        player.setItemSlot(EquipmentSlot.FEET, ItemFactory.create(catalog, new ContentId("rotas:item/smoke_boots"), 1, 1, LootService.random("s|b")));
        equipment.refresh(player, data);
        double twoPieces = player.getAttributeValue(Attributes.ARMOR);
        require(Math.abs(twoPieces - onePiece - 6.0) < .001, "two-piece set bonus applied: " + onePiece + " -> " + twoPieces);
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        equipment.refresh(player, data);
        require(Math.abs(player.getAttributeValue(Attributes.ARMOR) - onePiece) < .001, "set bonus removed when the set breaks");
        equipment.forget(ID);
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        require(Math.abs(player.getAttributeValue(Attributes.ARMOR) - armourBefore) < .001, "no owned modifier survives cleanup");
        progress.setLevel(1);
        data.setDirty();
    }

    private static int count(FakePlayer player) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (!player.getInventory().getItem(slot).isEmpty()) { total++; }
        }
        return total;
    }

    private static boolean equal(List<ItemStack> left, List<ItemStack> right) {
        if (left.size() != right.size()) { return false; }
        for (int i = 0; i < left.size(); i++) {
            CompoundTag a = left.get(i).save(new CompoundTag()), b = right.get(i).save(new CompoundTag());
            if (!a.equals(b)) { return false; }
        }
        return true;
    }

    private static void require(boolean value, String message) {
        if (!value) { throw new AssertionError(message); }
    }
}
