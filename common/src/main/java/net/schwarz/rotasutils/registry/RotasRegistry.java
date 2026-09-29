package net.schwarz.rotasutils.registry;

import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.block.QuestBoardBlock;
import net.schwarz.rotasutils.block.QuestBoardBlockEntity;
import net.schwarz.rotasutils.item.AdminToolItem;

/** All blocks, items, block entities and the creative tab. */
public final class RotasRegistry {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Rotasutils.MOD_ID, Registries.BLOCK);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Rotasutils.MOD_ID, Registries.ITEM);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Rotasutils.MOD_ID, Registries.BLOCK_ENTITY_TYPE);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Rotasutils.MOD_ID, Registries.CREATIVE_MODE_TAB);
    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES =
            DeferredRegister.create(Rotasutils.MOD_ID, Registries.PARTICLE_TYPE);
    public static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES =
            DeferredRegister.create(Rotasutils.MOD_ID, Registries.ENTITY_TYPE);

    /** A standing rift that opens, lets a traveller through and seals; lives a few seconds. */
    public static final RegistrySupplier<net.minecraft.world.entity.EntityType<net.schwarz.rotasutils.entity.RiftPortalEntity>>
            RIFT_PORTAL = ENTITIES.register("rift_portal", () -> net.minecraft.world.entity.EntityType.Builder
                    .<net.schwarz.rotasutils.entity.RiftPortalEntity>of(net.schwarz.rotasutils.entity.RiftPortalEntity::new,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(2.2f, 3.4f).clientTrackingRange(10).updateInterval(1).noSave()
                    .build("rift_portal"));

    /** Four rifts clashing; the cinematic that brings the Tetrarch. */
    public static final RegistrySupplier<net.minecraft.world.entity.EntityType<net.schwarz.rotasutils.entity.RiftConvergenceEntity>>
            RIFT_CONVERGENCE = ENTITIES.register("rift_convergence", () -> net.minecraft.world.entity.EntityType.Builder
                    .<net.schwarz.rotasutils.entity.RiftConvergenceEntity>of(net.schwarz.rotasutils.entity.RiftConvergenceEntity::new,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(1f, 1f).clientTrackingRange(12).updateInterval(1).noSave()
                    .build("rift_convergence"));

    /** The Tetrarch of the Four Rifts, and its echoes. */
    public static final RegistrySupplier<net.minecraft.world.entity.EntityType<net.schwarz.rotasutils.entity.TetrarchEntity>>
            TETRARCH = ENTITIES.register("tetrarch", () -> net.minecraft.world.entity.EntityType.Builder
                    .<net.schwarz.rotasutils.entity.TetrarchEntity>of(net.schwarz.rotasutils.entity.TetrarchEntity::new,
                            net.minecraft.world.entity.MobCategory.MONSTER)
                    .sized(0.9f, 2.9f).clientTrackingRange(12).fireImmune()
                    .build("tetrarch"));

    /** Tentacles tearing up from the ground under the Tetrarch's void grasp. */
    public static final RegistrySupplier<net.minecraft.world.entity.EntityType<net.schwarz.rotasutils.entity.VoidGraspEntity>>
            VOID_GRASP = ENTITIES.register("void_grasp", () -> net.minecraft.world.entity.EntityType.Builder
                    .<net.schwarz.rotasutils.entity.VoidGraspEntity>of(net.schwarz.rotasutils.entity.VoidGraspEntity::new,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(4.4f, 1f).clientTrackingRange(8).updateInterval(1).noSave()
                    .build("void_grasp"));

    /** One spectral sword loosed by the Zenith, looping out to the aimed point and back. */
    public static final RegistrySupplier<net.minecraft.world.entity.EntityType<net.schwarz.rotasutils.entity.ZenithBladeEntity>>
            ZENITH_BLADE = ENTITIES.register("zenith_blade", () -> net.minecraft.world.entity.EntityType.Builder
                    .<net.schwarz.rotasutils.entity.ZenithBladeEntity>of(net.schwarz.rotasutils.entity.ZenithBladeEntity::new,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(8).updateInterval(2).noSave().fireImmune()
                    .build("zenith_blade"));

    /** The Zenith's sneak-use finisher: a colossal arch of light torn forward along the ground. */
    public static final RegistrySupplier<net.minecraft.world.entity.EntityType<net.schwarz.rotasutils.entity.ExcaliburSlashEntity>>
            EXCALIBUR_SLASH = ENTITIES.register("excalibur_slash", () -> net.minecraft.world.entity.EntityType.Builder
                    .<net.schwarz.rotasutils.entity.ExcaliburSlashEntity>of(net.schwarz.rotasutils.entity.ExcaliburSlashEntity::new,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(12).updateInterval(20).noSave().fireImmune()
                    .build("excalibur_slash"));

    /** The ExoElectric Disintegrator's channelled ray; lives while its wielder holds use. */
    public static final RegistrySupplier<net.minecraft.world.entity.EntityType<net.schwarz.rotasutils.entity.ExoBeamEntity>>
            EXO_BEAM = ENTITIES.register("exo_beam", () -> net.minecraft.world.entity.EntityType.Builder
                    .<net.schwarz.rotasutils.entity.ExoBeamEntity>of(net.schwarz.rotasutils.entity.ExoBeamEntity::new,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(10).updateInterval(1).noSave().fireImmune()
                    .build("exo_beam"));

    /**
     * The mount the Cero Metralleta pours out of; lives for the barrage. Its bolts are logical
     * shots, not entities, so this is the only entity the whole attack costs.
     */
    public static final RegistrySupplier<net.minecraft.world.entity.EntityType<net.schwarz.rotasutils.entity.ExoCeroMuzzleEntity>>
            EXO_CERO_MUZZLE = ENTITIES.register("exo_cero_muzzle",
                    () -> net.minecraft.world.entity.EntityType.Builder
                    .<net.schwarz.rotasutils.entity.ExoCeroMuzzleEntity>of(
                            net.schwarz.rotasutils.entity.ExoCeroMuzzleEntity::new,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(10).updateInterval(3).noSave().fireImmune()
                    .build("exo_cero_muzzle"));

    /** Every visual of the Celestial spell school (Iron's Spells); see CelestialFxEntity. */
    public static final RegistrySupplier<net.minecraft.world.entity.EntityType<net.schwarz.rotasutils.entity.CelestialFxEntity>>
            CELESTIAL_FX = ENTITIES.register("celestial_fx", () -> net.minecraft.world.entity.EntityType.Builder
                    .<net.schwarz.rotasutils.entity.CelestialFxEntity>of(net.schwarz.rotasutils.entity.CelestialFxEntity::new,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(10).updateInterval(1).noSave().fireImmune()
                    .build("celestial_fx"));

    /** The robed traveller who steps out of a rift portal. */
    public static final RegistrySupplier<net.minecraft.world.entity.EntityType<net.schwarz.rotasutils.entity.RiftWandererEntity>>
            RIFT_WANDERER = ENTITIES.register("rift_wanderer", () -> net.minecraft.world.entity.EntityType.Builder
                    .<net.schwarz.rotasutils.entity.RiftWandererEntity>of(net.schwarz.rotasutils.entity.RiftWandererEntity::new,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.6f, 1.95f).clientTrackingRange(10)
                    .build("rift_wanderer"));

    /** Glowing embers that drift down from the cracked sky while the rift strains open. */
    public static final RegistrySupplier<net.minecraft.core.particles.SimpleParticleType> RIFT_EMBER =
            PARTICLES.register("rift_ember", () -> new net.minecraft.core.particles.SimpleParticleType(false) { });

    /**
     * Who may see the operator tools tab. Servers keep the default (nobody); the client installs a check
     * of its own player's operator permission, so no client class is referenced from common code.
     */

    public static final RegistrySupplier<Block> QUEST_BOARD = BLOCKS.register("quest_board",
            () -> new QuestBoardBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

    /** Invisible filler for the other five cells of a quest board's 3x2 footprint; never an item. */
    public static final RegistrySupplier<Block> QUEST_BOARD_PART = BLOCKS.register("quest_board_part",
            () -> new net.schwarz.rotasutils.block.QuestBoardPartBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .noLootTable()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

    /**
     * Warp pillar: three segments tall, placed and broken as one. Placing it registers the pillar;
     * right-clicking any segment records it and opens the warp list.
     */
    public static final RegistrySupplier<Block> WAYSTONE = BLOCKS.register("waystone",
            () -> new net.schwarz.rotasutils.block.WaystoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.0f, 1200.0f)
                    .sound(SoundType.DEEPSLATE)
                    // Lit enough to find at night without lighting the ground around it like a lamp.
                    .lightLevel(state -> 9)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    /** Where refinement happens: the bench refuses an attempt unless one is within reach. */
    public static final RegistrySupplier<Block> REFINE_FORGE = BLOCKS.register("refine_forge",
            () -> new net.schwarz.rotasutils.block.StationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0f, 1200.0f)
                    .sound(SoundType.ANVIL)
                    .lightLevel(state -> 7)
                    .requiresCorrectToolForDrops(),
                    net.schwarz.rotasutils.block.StationBlock.Kind.FORGE,
                    Block.box(0, 0, 0, 16, 16, 16),
                    net.schwarz.rotasutils.network.RotasNetwork::openRefine));

    /** Where runes are inscribed into a weapon. */
    public static final RegistrySupplier<Block> RUNE_ALTAR = BLOCKS.register("rune_altar",
            () -> new net.schwarz.rotasutils.block.StationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(5.0f, 1200.0f)
                    .sound(SoundType.DEEPSLATE)
                    .lightLevel(state -> 10)
                    .noOcclusion()
                    .requiresCorrectToolForDrops(),
                    net.schwarz.rotasutils.block.StationBlock.Kind.ALTAR,
                    Block.box(0, 0, 0, 16, 16, 16),
                    net.schwarz.rotasutils.network.RotasNetwork::openRunes));

    public static final RegistrySupplier<Item> REFINE_FORGE_ITEM = ITEMS.register("refine_forge",
            () -> new BlockItem(REFINE_FORGE.get(), new Item.Properties()));

    public static final RegistrySupplier<Item> RUNE_ALTAR_ITEM = ITEMS.register("rune_altar",
            () -> new BlockItem(RUNE_ALTAR.get(), new Item.Properties()));

    public static final RegistrySupplier<Item> WAYSTONE_ITEM = ITEMS.register("waystone",
            () -> new BlockItem(WAYSTONE.get(), new Item.Properties()));

    public static final RegistrySupplier<Item> QUEST_BOARD_ITEM = ITEMS.register("quest_board",
            () -> new BlockItem(QUEST_BOARD.get(), new Item.Properties()));

    public static final RegistrySupplier<Item> ADMIN_TOOL = ITEMS.register("admin_tool",
            () -> new AdminToolItem(new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<Item> NPC_WAND = ITEMS.register("npc_wand",
            () -> new net.schwarz.rotasutils.item.NpcWandItem(new Item.Properties().stacksTo(1)));

    /**
     * Admin-only tool. Only listed in the operator-gated {@link #ADMIN_TAB}; it is also handed out from
     * the admin menu, and its use is re-checked server-side.
     */
    public static final RegistrySupplier<Item> ZONE_WAND = ITEMS.register("zone_wand",
            () -> new net.schwarz.rotasutils.item.ZoneWandItem(new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<Item> HOUSE_WAND = ITEMS.register("house_wand",
            () -> new net.schwarz.rotasutils.item.HouseWandItem(new Item.Properties().stacksTo(1)));

    /** Operator-only sky toggle; opens/closes the eldritch rift in the holder's dimension. */
    public static final RegistrySupplier<Item> ELDRITCH_SIGIL = ITEMS.register("eldritch_sigil",
            () -> new net.schwarz.rotasutils.item.EldritchSigilItem(new Item.Properties().stacksTo(1)));

    /** Operator-only sky toggle, like the Eldritch Sigil, but a colossal figure steps out of the tear. */
    public static final RegistrySupplier<Item> HERALD_SIGIL = ITEMS.register("herald_sigil",
            () -> new net.schwarz.rotasutils.item.EldritchSigilItem(new Item.Properties().stacksTo(1),
                    net.schwarz.rotasutils.sky.EldritchSkyTransition.VARIANT_HERALD));

    /** The Eldritch Sigil in crimson: the same invasion under a red sky. */
    public static final RegistrySupplier<Item> CRIMSON_SIGIL = ITEMS.register("crimson_sigil",
            () -> new net.schwarz.rotasutils.item.EldritchSigilItem(new Item.Properties().stacksTo(1),
                    net.schwarz.rotasutils.sky.EldritchSkyTransition.VARIANT_SKY_RED));

    /** The Herald Sigil in crimson: a blood-dark sky and a red herald. */
    public static final RegistrySupplier<Item> CRIMSON_HERALD_SIGIL = ITEMS.register("crimson_herald_sigil",
            () -> new net.schwarz.rotasutils.item.EldritchSigilItem(new Item.Properties().stacksTo(1),
                    net.schwarz.rotasutils.sky.EldritchSkyTransition.VARIANT_HERALD_RED));

    /** The Eldritch Sigil in gold. */
    public static final RegistrySupplier<Item> GOLD_SIGIL = ITEMS.register("gold_sigil",
            () -> new net.schwarz.rotasutils.item.EldritchSigilItem(new Item.Properties().stacksTo(1),
                    net.schwarz.rotasutils.sky.EldritchSkyTransition.VARIANT_SKY_GOLD));

    /** The Herald Sigil in gold. */
    public static final RegistrySupplier<Item> GOLD_HERALD_SIGIL = ITEMS.register("gold_herald_sigil",
            () -> new net.schwarz.rotasutils.item.EldritchSigilItem(new Item.Properties().stacksTo(1),
                    net.schwarz.rotasutils.sky.EldritchSkyTransition.VARIANT_HERALD_GOLD));

    /** A black void sky with tentacles reaching out of the tear. */
    public static final RegistrySupplier<Item> VOID_SIGIL = ITEMS.register("void_sigil",
            () -> new net.schwarz.rotasutils.item.EldritchSigilItem(new Item.Properties().stacksTo(1),
                    net.schwarz.rotasutils.sky.EldritchSkyTransition.VARIANT_VOID));

    /** A rainbow sky: the whole rupture cycles the spectrum. */
    public static final RegistrySupplier<Item> PRISM_SIGIL = ITEMS.register("prism_sigil",
            () -> new net.schwarz.rotasutils.item.EldritchSigilItem(new Item.Properties().stacksTo(1),
                    net.schwarz.rotasutils.sky.EldritchSkyTransition.VARIANT_SKY_RAINBOW));

    public static final RegistrySupplier<Item> FOUR_SKIES_SIGIL = ITEMS.register("four_skies_sigil",
            () -> new net.schwarz.rotasutils.item.EldritchSigilItem(new Item.Properties().stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.EPIC),
                    net.schwarz.rotasutils.sky.EldritchSkyTransition.VARIANT_FOUR_SKIES));

    /** Operator-only travel rift: sneak-use binds a destination, use opens a rift leading there. */
    public static final RegistrySupplier<Item> RIFT_KEY = ITEMS.register("rift_key",
            () -> new net.schwarz.rotasutils.item.RiftSigilItem(new Item.Properties().stacksTo(1),
                    net.schwarz.rotasutils.entity.RiftPortalEntity.Kind.TRAVEL));

    /** Operator-only: a rift that drags nearby monsters in and swallows them. */
    public static final RegistrySupplier<Item> VOID_MAW = ITEMS.register("void_maw",
            () -> new net.schwarz.rotasutils.item.RiftSigilItem(new Item.Properties().stacksTo(1),
                    net.schwarz.rotasutils.entity.RiftPortalEntity.Kind.MAW));

    /** Operator-only: a rift whose tentacles strike nearby monsters. */
    public static final RegistrySupplier<Item> TENTACLE_RIFT = ITEMS.register("tentacle_rift",
            () -> new net.schwarz.rotasutils.item.RiftSigilItem(new Item.Properties().stacksTo(1),
                    net.schwarz.rotasutils.entity.RiftPortalEntity.Kind.TENTACLE));

    /** Operator-only: a rift a colossal prismatic hand reaches out of to strike nearby monsters. */
    public static final RegistrySupplier<Item> RADIANT_RIFT = ITEMS.register("radiant_rift",
            () -> new net.schwarz.rotasutils.item.RiftSigilItem(new Item.Properties().stacksTo(1),
                    net.schwarz.rotasutils.entity.RiftPortalEntity.Kind.RADIANT));

    /** Operator-only: four rifts clash and the Tetrarch steps out. */
    public static final RegistrySupplier<Item> CONVERGENCE_SIGIL = ITEMS.register("convergence_sigil",
            () -> new net.schwarz.rotasutils.item.ConvergenceSigilItem(new Item.Properties().stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.EPIC)));

    /** Operator-only: four rifts tear open in the sky and clash overhead. */
    public static final RegistrySupplier<Item> SKY_CLASH_SIGIL = ITEMS.register("sky_clash_sigil",
            () -> new net.schwarz.rotasutils.item.SkyClashSigilItem(new Item.Properties().stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.EPIC)));

    /** Operator-only: binds the open eldritch rift in a golden seal and shatters it, closing the sky. */
    public static final RegistrySupplier<Item> SUNDERING_SIGIL = ITEMS.register("sundering_sigil",
            () -> new net.schwarz.rotasutils.item.SunderingSigilItem(new Item.Properties().stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.EPIC)));

    /** Operator-only: tears a portal open in front of the holder and a traveller steps out. */
    public static final RegistrySupplier<Item> RIFT_SIGIL = ITEMS.register("rift_sigil",
            () -> new net.schwarz.rotasutils.item.RiftSigilItem(new Item.Properties().stacksTo(1)));

    /** The Zenith: every sword in the world forged into one; holding use looses spectral copies of them all. */
    public static final RegistrySupplier<Item> ZENITH = ITEMS.register("zenith",
            () -> new net.schwarz.rotasutils.item.ZenithItem(new Item.Properties().stacksTo(1).fireResistant()
                    .rarity(net.minecraft.world.item.Rarity.EPIC)));

    /** Red Reversal: a right click begins a cinematic ability, run by the AbilityManager. */
    public static final RegistrySupplier<Item> RED_REVERSAL = ITEMS.register("red_reversal",
            () -> new net.schwarz.rotasutils.item.RedReversalItem(new Item.Properties().stacksTo(1).fireResistant()
                    .rarity(net.minecraft.world.item.Rarity.EPIC)));

    /** ExoElectric Disintegrator: charge, then a continuous piercing ray that unmakes what it kills. */
    public static final RegistrySupplier<Item> EXO_DISINTEGRATOR = ITEMS.register("exo_disintegrator",
            () -> new net.schwarz.rotasutils.item.ExoDisintegratorItem(new Item.Properties().stacksTo(1).fireResistant()
                    .rarity(net.minecraft.world.item.Rarity.EPIC)));

    /** Opens the stable and calls a horse; used on an owned SWEM horse it puts that horse in the stable. */
    public static final RegistrySupplier<Item> HORSE_WHISTLE = ITEMS.register("horse_whistle",
            () -> new net.schwarz.rotasutils.item.HorseWhistleItem(new Item.Properties().stacksTo(1)));

    /** Visual denomination for rotas:gold; wallet balances remain the economic source of truth. */
    public static final RegistrySupplier<Item> GOLD_COIN = ITEMS.register("gold_coin",
            () -> new net.schwarz.rotasutils.item.GoldCoinItem(new Item.Properties().stacksTo(1)));

    // Refinement materials ------------------------------------------------------------------------
    /**
     * Refine ores and scrolls. They are deliberately hard to come by: nothing crafts them, and they only
     * arrive through the rare and epic drop grades, a boss, or whatever a server hands out itself.
     */
    public static final RegistrySupplier<Item> ORIDECON = ITEMS.register("oridecon",
            () -> new net.schwarz.rotasutils.item.RpgMaterialItem(new Item.Properties(),
                    "item.rotasutils.oridecon.desc", net.minecraft.ChatFormatting.GRAY, false,
                    net.schwarz.rotasutils.item.RpgMaterialItem.Opens.REFINE));

    public static final RegistrySupplier<Item> ELUNIUM = ITEMS.register("elunium",
            () -> new net.schwarz.rotasutils.item.RpgMaterialItem(new Item.Properties(),
                    "item.rotasutils.elunium.desc", net.minecraft.ChatFormatting.GRAY, false,
                    net.schwarz.rotasutils.item.RpgMaterialItem.Opens.REFINE));

    public static final RegistrySupplier<Item> ENRICHED_ORIDECON = ITEMS.register("enriched_oridecon",
            () -> new net.schwarz.rotasutils.item.RpgMaterialItem(new Item.Properties(),
                    "item.rotasutils.enriched_oridecon.desc", net.minecraft.ChatFormatting.AQUA, true,
                    net.schwarz.rotasutils.item.RpgMaterialItem.Opens.REFINE));

    public static final RegistrySupplier<Item> ENRICHED_ELUNIUM = ITEMS.register("enriched_elunium",
            () -> new net.schwarz.rotasutils.item.RpgMaterialItem(new Item.Properties(),
                    "item.rotasutils.enriched_elunium.desc", net.minecraft.ChatFormatting.AQUA, true,
                    net.schwarz.rotasutils.item.RpgMaterialItem.Opens.REFINE));

    public static final RegistrySupplier<Item> PROTECTION_SCROLL = ITEMS.register("protection_scroll",
            () -> new net.schwarz.rotasutils.item.RpgMaterialItem(new Item.Properties(),
                    "item.rotasutils.protection_scroll.desc", net.minecraft.ChatFormatting.YELLOW, true,
                    net.schwarz.rotasutils.item.RpgMaterialItem.Opens.REFINE));

    public static final RegistrySupplier<Item> BLESSING_SCROLL = ITEMS.register("blessing_scroll",
            () -> new net.schwarz.rotasutils.item.RpgMaterialItem(new Item.Properties(),
                    "item.rotasutils.blessing_scroll.desc", net.minecraft.ChatFormatting.GREEN, true,
                    net.schwarz.rotasutils.item.RpgMaterialItem.Opens.REFINE));

    public static final RegistrySupplier<Item> CERTIFICATE_SCROLL = ITEMS.register("certificate_scroll",
            () -> new net.schwarz.rotasutils.item.RpgMaterialItem(new Item.Properties().stacksTo(16),
                    "item.rotasutils.certificate_scroll.desc", net.minecraft.ChatFormatting.LIGHT_PURPLE, true,
                    net.schwarz.rotasutils.item.RpgMaterialItem.Opens.REFINE));

    // Runes ---------------------------------------------------------------------------------------
    /** One item per rune; inscribed into a weapon at the Rune Altar. */
    public static final java.util.Map<net.schwarz.rotasutils.core.RuneType, RegistrySupplier<Item>> RUNES = runes();

    private static java.util.Map<net.schwarz.rotasutils.core.RuneType, RegistrySupplier<Item>> runes() {
        var map = new java.util.EnumMap<net.schwarz.rotasutils.core.RuneType, RegistrySupplier<Item>>(
                net.schwarz.rotasutils.core.RuneType.class);
        for (var type : net.schwarz.rotasutils.core.RuneType.values()) {
            map.put(type, ITEMS.register(type.itemPath(),
                    () -> new net.schwarz.rotasutils.item.RpgMaterialItem(new Item.Properties().stacksTo(16),
                            "item.rotasutils." + type.itemPath() + ".desc", type.color(), true,
                            net.schwarz.rotasutils.item.RpgMaterialItem.Opens.RUNES)));
        }
        return java.util.Collections.unmodifiableMap(map);
    }

    // Cards ---------------------------------------------------------------------------------------
    /** Every monster card is this one item; which card it is lives in its NBT. */
    public static final RegistrySupplier<Item> CARD = ITEMS.register("card",
            () -> new net.schwarz.rotasutils.item.CardItem(new Item.Properties().stacksTo(16)));

    /** Punches one socket into a weapon or a piece of armour. */
    public static final RegistrySupplier<Item> SOCKET_PUNCH = ITEMS.register("socket_punch",
            () -> new net.schwarz.rotasutils.item.RpgMaterialItem(new Item.Properties(),
                    "item.rotasutils.socket_punch.desc", net.minecraft.ChatFormatting.AQUA, true,
                    net.schwarz.rotasutils.item.RpgMaterialItem.Opens.SOCKETS));

    /** Carries the floating-core renderer on the lower half of a waystone; stores nothing. */
    public static final RegistrySupplier<BlockEntityType<net.schwarz.rotasutils.block.WaystoneBlockEntity>>
            WAYSTONE_BLOCK_ENTITY = BLOCK_ENTITIES.register("waystone", () -> BlockEntityType.Builder
                    .of(net.schwarz.rotasutils.block.WaystoneBlockEntity::new, WAYSTONE.get())
                    .build(null));

    /** Carries the floating-crystal renderer on the Rune Altar; stores nothing. */
    public static final RegistrySupplier<BlockEntityType<net.schwarz.rotasutils.block.RuneAltarBlockEntity>>
            RUNE_ALTAR_BLOCK_ENTITY = BLOCK_ENTITIES.register("rune_altar", () -> BlockEntityType.Builder
                    .of(net.schwarz.rotasutils.block.RuneAltarBlockEntity::new, RUNE_ALTAR.get())
                    .build(null));

    public static final RegistrySupplier<BlockEntityType<QuestBoardBlockEntity>> QUEST_BOARD_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("quest_board", () -> BlockEntityType.Builder
                    .of(QuestBoardBlockEntity::new, QUEST_BOARD.get())
                    .build(null));

    public static final RegistrySupplier<CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeTabRegistry.create(
                    Component.translatable("itemGroup." + Rotasutils.MOD_ID + ".main"),
                    () -> new ItemStack(QUEST_BOARD_ITEM.get())));

    /**
     * Operator tools: every wand and the admin tool. Always listed: the creative inventory is only
     * reachable in creative mode, which already takes operator rights on the server. A client-side op
     * check here hid the tab from real operators on the live server. Every item still re-checks
     * administrator permission on the server when used.
     */
    public static final RegistrySupplier<CreativeModeTab> ADMIN_TAB = TABS.register("admin",
            () -> CreativeTabRegistry.create(builder -> builder
                    .title(Component.translatable("itemGroup." + Rotasutils.MOD_ID + ".admin"))
                    .icon(() -> new ItemStack(ZONE_WAND.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ADMIN_TOOL.get());
                        output.accept(NPC_WAND.get());
                        output.accept(ZONE_WAND.get());
                        output.accept(HOUSE_WAND.get());
                        output.accept(ELDRITCH_SIGIL.get());
                        output.accept(RIFT_SIGIL.get());
                        output.accept(RIFT_KEY.get());
                        output.accept(VOID_MAW.get());
                        output.accept(TENTACLE_RIFT.get());
                        output.accept(RADIANT_RIFT.get());
                        output.accept(CONVERGENCE_SIGIL.get());
                        output.accept(SKY_CLASH_SIGIL.get());
                        output.accept(SUNDERING_SIGIL.get());
                        output.accept(CRIMSON_SIGIL.get());
                        output.accept(GOLD_SIGIL.get());
                        output.accept(HERALD_SIGIL.get());
                        output.accept(CRIMSON_HERALD_SIGIL.get());
                        output.accept(GOLD_HERALD_SIGIL.get());
                        output.accept(VOID_SIGIL.get());
                        output.accept(PRISM_SIGIL.get());
                        output.accept(FOUR_SKIES_SIGIL.get());
                    })));

    private RotasRegistry() {
    }

    // CreativeTabRegistry.append takes a generic varargs array; no API on Forge/Fabric avoids it.
    @SuppressWarnings("unchecked")
    public static void init() {
        BLOCKS.register();
        ITEMS.register();
        BLOCK_ENTITIES.register();
        ENTITIES.register();
        dev.architectury.registry.level.entity.EntityAttributeRegistry.register(RIFT_WANDERER,
                net.schwarz.rotasutils.entity.RiftWandererEntity::createAttributes);
        dev.architectury.registry.level.entity.EntityAttributeRegistry.register(TETRARCH,
                net.schwarz.rotasutils.entity.TetrarchEntity::createAttributes);
        TABS.register();
        PARTICLES.register();
        CreativeTabRegistry.append(TAB, QUEST_BOARD_ITEM, WAYSTONE_ITEM, HORSE_WHISTLE, ZENITH, RED_REVERSAL, EXO_DISINTEGRATOR, GOLD_COIN,
                ORIDECON, ELUNIUM, ENRICHED_ORIDECON, ENRICHED_ELUNIUM,
                PROTECTION_SCROLL, BLESSING_SCROLL, CERTIFICATE_SCROLL, SOCKET_PUNCH, CARD,
                REFINE_FORGE_ITEM, RUNE_ALTAR_ITEM);
        for (RegistrySupplier<Item> rune : RUNES.values()) {
            CreativeTabRegistry.append(TAB, rune);
        }
    }
}
