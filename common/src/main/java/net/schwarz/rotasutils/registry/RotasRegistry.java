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

    public static final RegistrySupplier<net.minecraft.world.entity.EntityType<net.schwarz.rotasutils.entity.RiftPortalEntity>>
            RIFT_PORTAL = ENTITIES.register("rift_portal", () -> net.minecraft.world.entity.EntityType.Builder
                    .<net.schwarz.rotasutils.entity.RiftPortalEntity>of(net.schwarz.rotasutils.entity.RiftPortalEntity::new,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(2.2f, 3.4f).clientTrackingRange(10).updateInterval(1).noSave()
                    .build("rift_portal"));

    public static final RegistrySupplier<net.minecraft.world.entity.EntityType<net.schwarz.rotasutils.entity.RiftConvergenceEntity>>
            RIFT_CONVERGENCE = ENTITIES.register("rift_convergence", () -> net.minecraft.world.entity.EntityType.Builder
                    .<net.schwarz.rotasutils.entity.RiftConvergenceEntity>of(net.schwarz.rotasutils.entity.RiftConvergenceEntity::new,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(1f, 1f).clientTrackingRange(12).updateInterval(1).noSave()
                    .build("rift_convergence"));

    public static final RegistrySupplier<net.minecraft.world.entity.EntityType<net.schwarz.rotasutils.entity.TetrarchEntity>>
            TETRARCH = ENTITIES.register("tetrarch", () -> net.minecraft.world.entity.EntityType.Builder
                    .<net.schwarz.rotasutils.entity.TetrarchEntity>of(net.schwarz.rotasutils.entity.TetrarchEntity::new,
                            net.minecraft.world.entity.MobCategory.MONSTER)
                    .sized(0.9f, 2.9f).clientTrackingRange(12).fireImmune()
                    .build("tetrarch"));

    public static final RegistrySupplier<net.minecraft.world.entity.EntityType<net.schwarz.rotasutils.entity.VoidGraspEntity>>
            VOID_GRASP = ENTITIES.register("void_grasp", () -> net.minecraft.world.entity.EntityType.Builder
                    .<net.schwarz.rotasutils.entity.VoidGraspEntity>of(net.schwarz.rotasutils.entity.VoidGraspEntity::new,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(4.4f, 1f).clientTrackingRange(8).updateInterval(1).noSave()
                    .build("void_grasp"));

    public static final RegistrySupplier<net.minecraft.world.entity.EntityType<net.schwarz.rotasutils.entity.ZenithBladeEntity>>
            ZENITH_BLADE = ENTITIES.register("zenith_blade", () -> net.minecraft.world.entity.EntityType.Builder
                    .<net.schwarz.rotasutils.entity.ZenithBladeEntity>of(net.schwarz.rotasutils.entity.ZenithBladeEntity::new,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(8).updateInterval(2).noSave().fireImmune()
                    .build("zenith_blade"));

    public static final RegistrySupplier<net.minecraft.world.entity.EntityType<net.schwarz.rotasutils.entity.ExcaliburSlashEntity>>
            EXCALIBUR_SLASH = ENTITIES.register("excalibur_slash", () -> net.minecraft.world.entity.EntityType.Builder
                    .<net.schwarz.rotasutils.entity.ExcaliburSlashEntity>of(net.schwarz.rotasutils.entity.ExcaliburSlashEntity::new,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(12).updateInterval(20).noSave().fireImmune()
                    .build("excalibur_slash"));

    public static final RegistrySupplier<net.minecraft.world.entity.EntityType<net.schwarz.rotasutils.entity.ExoBeamEntity>>
            EXO_BEAM = ENTITIES.register("exo_beam", () -> net.minecraft.world.entity.EntityType.Builder
                    .<net.schwarz.rotasutils.entity.ExoBeamEntity>of(net.schwarz.rotasutils.entity.ExoBeamEntity::new,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(10).updateInterval(1).noSave().fireImmune()
                    .build("exo_beam"));

    public static final RegistrySupplier<net.minecraft.world.entity.EntityType<net.schwarz.rotasutils.entity.ExoCeroMuzzleEntity>>
            EXO_CERO_MUZZLE = ENTITIES.register("exo_cero_muzzle",
                    () -> net.minecraft.world.entity.EntityType.Builder
                    .<net.schwarz.rotasutils.entity.ExoCeroMuzzleEntity>of(
                            net.schwarz.rotasutils.entity.ExoCeroMuzzleEntity::new,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(10).updateInterval(3).noSave().fireImmune()
                    .build("exo_cero_muzzle"));

    public static final RegistrySupplier<net.minecraft.world.entity.EntityType<net.schwarz.rotasutils.entity.CelestialFxEntity>>
            CELESTIAL_FX = ENTITIES.register("celestial_fx", () -> net.minecraft.world.entity.EntityType.Builder
                    .<net.schwarz.rotasutils.entity.CelestialFxEntity>of(net.schwarz.rotasutils.entity.CelestialFxEntity::new,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(10).updateInterval(1).noSave().fireImmune()
                    .build("celestial_fx"));

    public static final RegistrySupplier<net.minecraft.world.entity.EntityType<net.schwarz.rotasutils.entity.RiftWandererEntity>>
            RIFT_WANDERER = ENTITIES.register("rift_wanderer", () -> net.minecraft.world.entity.EntityType.Builder
                    .<net.schwarz.rotasutils.entity.RiftWandererEntity>of(net.schwarz.rotasutils.entity.RiftWandererEntity::new,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.6f, 1.95f).clientTrackingRange(10)
                    .build("rift_wanderer"));

    public static final RegistrySupplier<net.minecraft.core.particles.SimpleParticleType> RIFT_EMBER =
            PARTICLES.register("rift_ember", () -> new net.minecraft.core.particles.SimpleParticleType(false) { });

public static final RegistrySupplier<Block> QUEST_BOARD = BLOCKS.register("quest_board",
            () -> new QuestBoardBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

    public static final RegistrySupplier<Block> QUEST_BOARD_PART = BLOCKS.register("quest_board_part",
            () -> new net.schwarz.rotasutils.block.QuestBoardPartBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .noLootTable()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

    public static final RegistrySupplier<Block> WAYSTONE = BLOCKS.register("waystone",
            () -> new net.schwarz.rotasutils.block.WaystoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.0f, 1200.0f)
                    .sound(SoundType.DEEPSLATE)
                    .lightLevel(state -> 9)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

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

    private static RegistrySupplier<Block> workstation(String id, MapColor color, SoundType sound, int light,
                                                        net.schwarz.rotasutils.block.StationBlock.Kind kind, String trade) {
        return BLOCKS.register(id, () -> new net.schwarz.rotasutils.block.StationBlock(BlockBehaviour.Properties.of()
                .mapColor(color).strength(3.5f, 6.0f).sound(sound)
                .lightLevel(state -> state.hasProperty(net.schwarz.rotasutils.block.StationBlock.LIT) && state.getValue(net.schwarz.rotasutils.block.StationBlock.LIT)
                        ? Math.max(light, 13) : (light > 8 ? light - 5 : 0))
                .requiresCorrectToolForDrops(),
                kind, Block.box(0, 0, 0, 16, 16, 16),
                player -> net.schwarz.rotasutils.server.TradeService.openStation(player, trade)));
    }

    private static RegistrySupplier<Item> stationItem(String id, RegistrySupplier<Block> block) {
        return ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static final RegistrySupplier<Block> COOKING_STATION = workstation("cooking_station", MapColor.COLOR_ORANGE,
            SoundType.METAL, 8, net.schwarz.rotasutils.block.StationBlock.Kind.STOVE, "chef");
    public static final RegistrySupplier<Block> SMELTERY = workstation("smeltery", MapColor.STONE,
            SoundType.STONE, 12, net.schwarz.rotasutils.block.StationBlock.Kind.SMELTER, "miner");
    public static final RegistrySupplier<Block> SMITHING_BENCH = workstation("smithing_bench", MapColor.METAL,
            SoundType.ANVIL, 6, net.schwarz.rotasutils.block.StationBlock.Kind.BENCH, "blacksmith");
    public static final RegistrySupplier<Block> ALCHEMY_TABLE = workstation("alchemy_table", MapColor.COLOR_PURPLE,
            SoundType.GLASS, 9, net.schwarz.rotasutils.block.StationBlock.Kind.TABLE, "alchemy");
    public static final RegistrySupplier<Block> TANNERY = workstation("tannery", MapColor.WOOD,
            SoundType.WOOD, 3, net.schwarz.rotasutils.block.StationBlock.Kind.TANNERY, "rancher");
    public static final RegistrySupplier<Block> FISHMONGER = workstation("fishmonger", MapColor.COLOR_LIGHT_BLUE,
            SoundType.WET_GRASS, 5, net.schwarz.rotasutils.block.StationBlock.Kind.FISH, "fisher");
    public static final RegistrySupplier<Block> MILL = workstation("mill", MapColor.COLOR_YELLOW,
            SoundType.WOOD, 3, net.schwarz.rotasutils.block.StationBlock.Kind.MILL, "farmer");

    public static final RegistrySupplier<Item> COOKING_STATION_ITEM = stationItem("cooking_station", COOKING_STATION);
    public static final RegistrySupplier<Item> SMELTERY_ITEM = stationItem("smeltery", SMELTERY);
    public static final RegistrySupplier<Item> SMITHING_BENCH_ITEM = stationItem("smithing_bench", SMITHING_BENCH);
    public static final RegistrySupplier<Item> ALCHEMY_TABLE_ITEM = stationItem("alchemy_table", ALCHEMY_TABLE);
    public static final RegistrySupplier<Item> TANNERY_ITEM = stationItem("tannery", TANNERY);
    public static final RegistrySupplier<Item> FISHMONGER_ITEM = stationItem("fishmonger", FISHMONGER);
    public static final RegistrySupplier<Item> MILL_ITEM = stationItem("mill", MILL);

    public static final RegistrySupplier<Item> RECIPE_SCROLL = ITEMS.register("recipe_scroll",
            () -> new net.schwarz.rotasutils.item.RecipeScrollItem(new Item.Properties().stacksTo(16)));

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

    public static final RegistrySupplier<Item> ZONE_WAND = ITEMS.register("zone_wand",
            () -> new net.schwarz.rotasutils.item.ZoneWandItem(new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<Item> HOUSE_WAND = ITEMS.register("house_wand",
            () -> new net.schwarz.rotasutils.item.HouseWandItem(new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<Item> ELDRITCH_SIGIL = ITEMS.register("eldritch_sigil",
            () -> new net.schwarz.rotasutils.item.EldritchSigilItem(new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<Item> HERALD_SIGIL = ITEMS.register("herald_sigil",
            () -> new net.schwarz.rotasutils.item.EldritchSigilItem(new Item.Properties().stacksTo(1),
                    net.schwarz.rotasutils.sky.EldritchSkyTransition.VARIANT_HERALD));

    public static final RegistrySupplier<Item> CRIMSON_SIGIL = ITEMS.register("crimson_sigil",
            () -> new net.schwarz.rotasutils.item.EldritchSigilItem(new Item.Properties().stacksTo(1),
                    net.schwarz.rotasutils.sky.EldritchSkyTransition.VARIANT_SKY_RED));

    public static final RegistrySupplier<Item> CRIMSON_HERALD_SIGIL = ITEMS.register("crimson_herald_sigil",
            () -> new net.schwarz.rotasutils.item.EldritchSigilItem(new Item.Properties().stacksTo(1),
                    net.schwarz.rotasutils.sky.EldritchSkyTransition.VARIANT_HERALD_RED));

    public static final RegistrySupplier<Item> GOLD_SIGIL = ITEMS.register("gold_sigil",
            () -> new net.schwarz.rotasutils.item.EldritchSigilItem(new Item.Properties().stacksTo(1),
                    net.schwarz.rotasutils.sky.EldritchSkyTransition.VARIANT_SKY_GOLD));

    public static final RegistrySupplier<Item> GOLD_HERALD_SIGIL = ITEMS.register("gold_herald_sigil",
            () -> new net.schwarz.rotasutils.item.EldritchSigilItem(new Item.Properties().stacksTo(1),
                    net.schwarz.rotasutils.sky.EldritchSkyTransition.VARIANT_HERALD_GOLD));

    public static final RegistrySupplier<Item> VOID_SIGIL = ITEMS.register("void_sigil",
            () -> new net.schwarz.rotasutils.item.EldritchSigilItem(new Item.Properties().stacksTo(1),
                    net.schwarz.rotasutils.sky.EldritchSkyTransition.VARIANT_VOID));

    public static final RegistrySupplier<Item> PRISM_SIGIL = ITEMS.register("prism_sigil",
            () -> new net.schwarz.rotasutils.item.EldritchSigilItem(new Item.Properties().stacksTo(1),
                    net.schwarz.rotasutils.sky.EldritchSkyTransition.VARIANT_SKY_RAINBOW));

    public static final RegistrySupplier<Item> FOUR_SKIES_SIGIL = ITEMS.register("four_skies_sigil",
            () -> new net.schwarz.rotasutils.item.EldritchSigilItem(new Item.Properties().stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.EPIC),
                    net.schwarz.rotasutils.sky.EldritchSkyTransition.VARIANT_FOUR_SKIES));

    public static final RegistrySupplier<Item> RIFT_KEY = ITEMS.register("rift_key",
            () -> new net.schwarz.rotasutils.item.RiftSigilItem(new Item.Properties().stacksTo(1),
                    net.schwarz.rotasutils.entity.RiftPortalEntity.Kind.TRAVEL));

    public static final RegistrySupplier<Item> VOID_MAW = ITEMS.register("void_maw",
            () -> new net.schwarz.rotasutils.item.RiftSigilItem(new Item.Properties().stacksTo(1),
                    net.schwarz.rotasutils.entity.RiftPortalEntity.Kind.MAW));

    public static final RegistrySupplier<Item> TENTACLE_RIFT = ITEMS.register("tentacle_rift",
            () -> new net.schwarz.rotasutils.item.RiftSigilItem(new Item.Properties().stacksTo(1),
                    net.schwarz.rotasutils.entity.RiftPortalEntity.Kind.TENTACLE));

    public static final RegistrySupplier<Item> RADIANT_RIFT = ITEMS.register("radiant_rift",
            () -> new net.schwarz.rotasutils.item.RiftSigilItem(new Item.Properties().stacksTo(1),
                    net.schwarz.rotasutils.entity.RiftPortalEntity.Kind.RADIANT));

    public static final RegistrySupplier<Item> CONVERGENCE_SIGIL = ITEMS.register("convergence_sigil",
            () -> new net.schwarz.rotasutils.item.ConvergenceSigilItem(new Item.Properties().stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.EPIC)));

    public static final RegistrySupplier<Item> SKY_CLASH_SIGIL = ITEMS.register("sky_clash_sigil",
            () -> new net.schwarz.rotasutils.item.SkyClashSigilItem(new Item.Properties().stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.EPIC)));

    public static final RegistrySupplier<Item> SUNDERING_SIGIL = ITEMS.register("sundering_sigil",
            () -> new net.schwarz.rotasutils.item.SunderingSigilItem(new Item.Properties().stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.EPIC)));

    public static final RegistrySupplier<Item> RIFT_SIGIL = ITEMS.register("rift_sigil",
            () -> new net.schwarz.rotasutils.item.RiftSigilItem(new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<Item> ZENITH = ITEMS.register("zenith",
            () -> new net.schwarz.rotasutils.item.ZenithItem(new Item.Properties().stacksTo(1).fireResistant()
                    .rarity(net.minecraft.world.item.Rarity.EPIC)));

    public static final RegistrySupplier<Item> RED_REVERSAL = ITEMS.register("red_reversal",
            () -> new net.schwarz.rotasutils.item.RedReversalItem(new Item.Properties().stacksTo(1).fireResistant()
                    .rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistrySupplier<Item> RED_REVERSAL_MAX = ITEMS.register("red_reversal_max",
            () -> new net.schwarz.rotasutils.item.RedReversalItem(new Item.Properties().stacksTo(1).fireResistant()
                    .rarity(net.minecraft.world.item.Rarity.EPIC), net.schwarz.rotasutils.ability.RedReversalAbility.MAX,
                    "item.rotasutils.red_reversal_max.desc"));
    public static final RegistrySupplier<Item> HOLLOW_PURPLE = ITEMS.register("hollow_purple",
            () -> new net.schwarz.rotasutils.item.RedReversalItem(new Item.Properties().stacksTo(1).fireResistant()
                    .rarity(net.minecraft.world.item.Rarity.EPIC), net.schwarz.rotasutils.ability.RedReversalAbility.PURPLE,
                    "item.rotasutils.hollow_purple.desc"));
    public static final RegistrySupplier<Item> ANNIHILATOR_STARGUN = ITEMS.register("annihilator_stargun",
            () -> new net.schwarz.rotasutils.item.RedReversalItem(new Item.Properties().stacksTo(1).fireResistant()
                    .rarity(net.minecraft.world.item.Rarity.EPIC), net.schwarz.rotasutils.ability.StargunAbility.INSTANCE,
                    "item.rotasutils.annihilator_stargun.desc"));
    public static final RegistrySupplier<Item> PROJECTION_SORCERY = ITEMS.register("projection_sorcery",
            () -> new net.schwarz.rotasutils.item.RedReversalItem(new Item.Properties().stacksTo(1).fireResistant()
                    .rarity(net.minecraft.world.item.Rarity.EPIC), net.schwarz.rotasutils.ability.ProjectionAbility.INSTANCE,
                    "item.rotasutils.projection_sorcery.desc"));

    public static final RegistrySupplier<Item> EXO_DISINTEGRATOR = ITEMS.register("exo_disintegrator",
            () -> new net.schwarz.rotasutils.item.ExoDisintegratorItem(new Item.Properties().stacksTo(1).fireResistant()
                    .rarity(net.minecraft.world.item.Rarity.EPIC)));

    public static final RegistrySupplier<Item> CAPOEIRA_WRAPS = ITEMS.register("capoeira_wraps",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));

    public static final RegistrySupplier<Item> HORSE_WHISTLE = ITEMS.register("horse_whistle",
            () -> new net.schwarz.rotasutils.item.HorseWhistleItem(new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<Item> GOLD_COIN = ITEMS.register("gold_coin",
            () -> new net.schwarz.rotasutils.item.GoldCoinItem(new Item.Properties().stacksTo(1)));

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

    public static final RegistrySupplier<Item> CARD = ITEMS.register("card",
            () -> new net.schwarz.rotasutils.item.CardItem(new Item.Properties().stacksTo(16)));

    public static final RegistrySupplier<Item> SOCKET_PUNCH = ITEMS.register("socket_punch",
            () -> new net.schwarz.rotasutils.item.RpgMaterialItem(new Item.Properties(),
                    "item.rotasutils.socket_punch.desc", net.minecraft.ChatFormatting.AQUA, true,
                    net.schwarz.rotasutils.item.RpgMaterialItem.Opens.SOCKETS));

    public static final RegistrySupplier<BlockEntityType<net.schwarz.rotasutils.block.WaystoneBlockEntity>>
            WAYSTONE_BLOCK_ENTITY = BLOCK_ENTITIES.register("waystone", () -> BlockEntityType.Builder
                    .of(net.schwarz.rotasutils.block.WaystoneBlockEntity::new, WAYSTONE.get())
                    .build(null));

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
        CreativeTabRegistry.append(TAB, QUEST_BOARD_ITEM, WAYSTONE_ITEM, HORSE_WHISTLE, ZENITH, RED_REVERSAL, RED_REVERSAL_MAX, HOLLOW_PURPLE, PROJECTION_SORCERY, ANNIHILATOR_STARGUN, EXO_DISINTEGRATOR, CAPOEIRA_WRAPS, GOLD_COIN,
                ORIDECON, ELUNIUM, ENRICHED_ORIDECON, ENRICHED_ELUNIUM,
                PROTECTION_SCROLL, BLESSING_SCROLL, CERTIFICATE_SCROLL, SOCKET_PUNCH, CARD,
                REFINE_FORGE_ITEM, RUNE_ALTAR_ITEM, COOKING_STATION_ITEM, SMELTERY_ITEM, SMITHING_BENCH_ITEM,
                ALCHEMY_TABLE_ITEM, TANNERY_ITEM, FISHMONGER_ITEM, MILL_ITEM, RECIPE_SCROLL);
        for (RegistrySupplier<Item> rune : RUNES.values()) {
            CreativeTabRegistry.append(TAB, rune);
        }
    }
}
