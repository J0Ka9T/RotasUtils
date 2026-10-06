package net.schwarz.rotasutils.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.core.PerkRules;
import net.schwarz.rotasutils.core.StarRules;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.job.JobDef;
import net.schwarz.rotasutils.progress.PlayerProgress;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class PerkService {
    private static final TagKey<net.minecraft.world.level.block.Block> ORES =
            TagKey.create(Registries.BLOCK, new ResourceLocation("forge", "ores"));
    private static final TagKey<net.minecraft.world.level.block.Block> CROPS =
            TagKey.create(Registries.BLOCK, new ResourceLocation("minecraft", "crops"));
    private static final UUID LUCK = UUID.fromString("c4d1a2f0-0b1e-4e0a-9a51-5b7a10c0ffee");
    private static final UUID MANA = UUID.fromString("c4d1a2f0-0b1e-4e0a-9a51-5b7a10c0fe01");
    private static final UUID MANA_REGEN = UUID.fromString("c4d1a2f0-0b1e-4e0a-9a51-5b7a10c0fe02");
    private static final int MAX_ORES = 24;
    private static long seconds;

    private PerkService() {
    }

    public static void tick(MinecraftServer server, RotasData data) {
        seconds++;
        PerkRules rules = TradeConfig.perks();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                PlayerProgress progress = data.progress(player.getUUID());
                String role = progress.subJob();
                JobDef job = role.isEmpty() ? null : data.job(role);
                int level = job == null ? 0 : ProductionService.subLevel(progress, job);
                List<PerkRules.Perk> mine = job == null ? List.of() : rules.forRole(role);
                setModifier(player, Attributes.LUCK, LUCK, "rotasutils.perk/luck", amount(mine, "lucky_waters", level), AttributeModifier.Operation.ADDITION);
                Attribute maxMana = attribute("irons_spellbooks:max_mana");
                double mana = amount(mine, "arcane_mind", level);
                setModifier(player, maxMana, MANA, "rotasutils.perk/mana", mana, AttributeModifier.Operation.ADDITION);
                setModifier(player, attribute("irons_spellbooks:mana_regen"), MANA_REGEN, "rotasutils.perk/regen", mana / 400.0,
                        AttributeModifier.Operation.ADDITION);
                for (PerkRules.Perk perk : mine) {
                    switch (perk.type()) {
                        case "prospect" -> prospect(player, perk.value(level));
                        case "swift_pick" -> swiftPick(player, perk.value(level));
                        case "green_thumb" -> greenThumb(player, perk.value(level), perk.radius());
                        case "animal_whisperer" -> animals(player, perk.value(level), perk.radius());
                        default -> { }
                    }
                }
            } catch (RuntimeException failure) {
                Rotasutils.LOG.error("Role perk failed for {}: {}", player.getGameProfile().getName(), failure.toString());
            }
        }
    }

    private static double amount(List<PerkRules.Perk> perks, String type, int level) {
        for (PerkRules.Perk perk : perks) {
            if (perk.type().equals(type)) return perk.value(level);
        }
        return 0;
    }

    private static Attribute attribute(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location != null && BuiltInRegistries.ATTRIBUTE.containsKey(location) ? BuiltInRegistries.ATTRIBUTE.get(location) : null;
    }

    private static void setModifier(ServerPlayer player, Attribute attribute, UUID id, String name, double amount,
                                    AttributeModifier.Operation operation) {
        AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (amount <= 0) {
            if (current != null) instance.removeModifier(id);
            return;
        }
        if (current != null && current.getAmount() == amount) {
            return;
        }
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
    }

private static void prospect(ServerPlayer player, double radius) {
        if (!player.isShiftKeyDown() || !player.getMainHandItem().is(ItemTags.PICKAXES)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        int r = (int) radius;
        BlockPos center = player.blockPosition();
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (level.isLoaded(cursor) && level.getBlockState(cursor).is(ORES)) {
                        found.add(cursor.immutable());
                    }
                }
            }
        }
        if (found.isEmpty()) {
            return;
        }
        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        for (BlockPos pos : found.subList(0, Math.min(MAX_ORES, found.size()))) {
            level.sendParticles(player, ParticleTypes.END_ROD, true, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    3, 0.25, 0.25, 0.25, 0.01);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.4f, 1.6f);
    }

    private static void swiftPick(ServerPlayer player, double haste) {
        if (haste >= 1 && player.getMainHandItem().is(ItemTags.PICKAXES)) {
            player.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.DIG_SPEED, 60, (int) haste - 1, true, false, false));
        }
    }

    public static List<ItemStack> richDrops(ServerPlayer player, BlockState state, List<ItemStack> drops) {
        if (drops == null || drops.isEmpty() || !state.is(ORES)) {
            return null;
        }
        RotasData data = RotasData.get(player.server);
        PlayerProgress progress = data.progress(player.getUUID());
        JobDef job = progress.subJob().isEmpty() ? null : data.job(progress.subJob());
        if (job == null) {
            return null;
        }
        for (PerkRules.Perk perk : TradeConfig.perks().forRole(progress.subJob())) {
            if (perk.type().equals("rich_veins")
                    && player.getRandom().nextDouble() * 100 < perk.value(ProductionService.subLevel(progress, job))) {
                List<ItemStack> doubled = new ArrayList<>(drops.size());
                for (ItemStack stack : drops) {
                    doubled.add(stack.isEmpty() ? stack : stack.copyWithCount(Math.min(stack.getMaxStackSize() * 2, stack.getCount() * 2)));
                }
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("rotasutils.perk.rich_veins.hit"), true);
                TitleService.stat(player, data, "rich_veins", 1);
                return doubled;
            }
        }
        return null;
    }

private static void greenThumb(ServerPlayer player, double strength, int radius) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        int samples = Math.max(1, (int) Math.round(strength));
        int r = Math.max(2, radius);
        for (int i = 0; i < samples * 4; i++) {
            BlockPos pos = center.offset(level.random.nextInt(2 * r + 1) - r, level.random.nextInt(5) - 2, level.random.nextInt(2 * r + 1) - r);
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if ((state.getBlock() instanceof CropBlock || state.is(CROPS)) && state.isRandomlyTicking()) {
                state.randomTick(level, pos, level.random);
                BlockState after = level.getBlockState(pos);
                if (after != state) {
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0);
                }
                if (--samples <= 0) {
                    break;
                }
            }
        }
    }

private static void animals(ServerPlayer player, double strength, int radius) {
        int boost = (int) (strength * 20);
        for (Animal animal : player.serverLevel().getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(radius))) {
            int age = animal.getAge();
            if (age < 0) {
                animal.setAge(Math.min(0, age + boost));
            } else if (age > 0) {
                animal.setAge(Math.max(0, age - boost));
            }
        }
    }

public static void banquet(ServerPlayer eater, int star) {
        RotasData data = RotasData.get(eater.server);
        PlayerProgress progress = data.progress(eater.getUUID());
        JobDef job = data.job(progress.subJob());
        if (job == null || star <= 0) {
            return;
        }
        for (PerkRules.Perk perk : TradeConfig.perks().forRole(progress.subJob())) {
            if (!perk.type().equals("banquet")) {
                continue;
            }
            double reach = perk.value(ProductionService.subLevel(progress, job));
            int shared = 0;
            for (ServerPlayer other : eater.serverLevel().players()) {
                if (other != eater && other.distanceToSqr(eater) <= reach * reach) {
                    for (StarRules.Buff buff : TradeConfig.stars().meal(star)) {
                        ResourceLocation id = ResourceLocation.tryParse(buff.effect());
                        if (id != null && BuiltInRegistries.MOB_EFFECT.containsKey(id)) {
                            other.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.get(id),
                                    (int) (buff.seconds() * 20 * 0.5), buff.amplifier()));
                        }
                    }
                    shared++;
                }
            }
            if (shared > 0) {
                eater.displayClientMessage(net.minecraft.network.chat.Component.translatable("rotasutils.perk.banquet.shared", shared), true);
            }
        }
    }

    public static void masterBrew(ServerPlayer drinker, ItemStack potion) {
        if (!potion.is(Items.POTION)) {
            return;
        }
        RotasData data = RotasData.get(drinker.server);
        PlayerProgress progress = data.progress(drinker.getUUID());
        JobDef job = data.job(progress.subJob());
        if (job == null) {
            return;
        }
        for (PerkRules.Perk perk : TradeConfig.perks().forRole(progress.subJob())) {
            if (!perk.type().equals("master_brew")) {
                continue;
            }
            double bonus = 1 + perk.value(ProductionService.subLevel(progress, job)) / 100.0;
            for (MobEffectInstance effect : PotionUtils.getMobEffects(potion)) {
                if (!effect.getEffect().isInstantenous()) {
                    drinker.addEffect(new MobEffectInstance(effect.getEffect(), (int) (effect.getDuration() * bonus),
                            effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon()));
                }
            }
        }
    }

    public static List<PerkRules.Perk> perksOf(String role) {
        return TradeConfig.perks().forRole(role);
    }
}
