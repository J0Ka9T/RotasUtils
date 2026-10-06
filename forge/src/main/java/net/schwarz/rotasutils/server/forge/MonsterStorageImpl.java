package net.schwarz.rotasutils.server.forge;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.server.MonsterStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MonsterStorageImpl {
    private static final String LEGACY_KEY = "rotasutils_monster";
    private static final String STATE_KEY = "state";
    private static final String HEALTH_RATIO_KEY = "health_ratio";
    private static final ResourceLocation CAPABILITY_KEY = Rotasutils.id("monster");
    private static final Capability<MonsterData> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() { });
    private static boolean initialized;

    private MonsterStorageImpl() { }

    public static synchronized void init(IEventBus modEventBus) {
        if (initialized) { return; }
        initialized = true;
        modEventBus.addListener(MonsterStorageImpl::registerCapabilities);
        MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, MonsterStorageImpl::attachCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(MonsterData.class);
    }

    private static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof LivingEntity living)) { return; }
        MonsterData data = new MonsterData(living);
        event.addCapability(CAPABILITY_KEY, data);
        event.addListener(data::invalidate);
    }

    public static CompoundTag read(LivingEntity entity) {
        MonsterData data = data(entity);
        CompoundTag legacy = entity.getPersistentData().getCompound(LEGACY_KEY);
        if (data.state().isEmpty() && !legacy.isEmpty()) {
            data.write(legacy);
            entity.getPersistentData().remove(LEGACY_KEY);
        }
        return data.readAndPrimeHealth();
    }

    public static void write(LivingEntity entity, CompoundTag state) {
        data(entity).write(state);
        entity.getPersistentData().remove(LEGACY_KEY);
    }

    private static MonsterData data(LivingEntity entity) {
        return entity.getCapability(CAPABILITY).resolve().orElseThrow(
                () -> new IllegalStateException("Monster capability is unavailable; call MonsterStorageImpl.init during Forge bootstrap"));
    }

    private static final class MonsterData implements ICapabilitySerializable<CompoundTag> {
        private final LivingEntity entity;
        private final LazyOptional<MonsterData> optional = LazyOptional.of(() -> this);
        private CompoundTag state = new CompoundTag();
        private double loadedHealthRatio;
        private boolean primeHealth;

        private MonsterData(LivingEntity entity) {
            this.entity = entity;
        }

        private CompoundTag state() {
            return state;
        }

        private CompoundTag readAndPrimeHealth() {
            if (primeHealth) {
                entity.setHealth(MonsterStorage.healthAtRatio(entity.getMaxHealth(), loadedHealthRatio));
                primeHealth = false;
            }
            return state.copy();
        }

        private void write(CompoundTag value) {
            state = value.copy();
            primeHealth = false;
        }

        private void invalidate() {
            optional.invalidate();
        }

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
            return CAPABILITY.orEmpty(capability, optional);
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag result = new CompoundTag();
            if (!state.isEmpty()) {
                result.put(STATE_KEY, state.copy());
                result.putDouble(HEALTH_RATIO_KEY, MonsterStorage.healthRatio(entity.getHealth(), entity.getMaxHealth()));
            }
            return result;
        }

        @Override
        public void deserializeNBT(CompoundTag serialized) {
            state = serialized.contains(STATE_KEY, Tag.TAG_COMPOUND)
                    ? serialized.getCompound(STATE_KEY).copy()
                    : new CompoundTag();
            double ratio = serialized.getDouble(HEALTH_RATIO_KEY);
            primeHealth = !state.isEmpty() && serialized.contains(HEALTH_RATIO_KEY, Tag.TAG_ANY_NUMERIC)
                    && Double.isFinite(ratio) && ratio >= 0.0D && ratio <= 1.0D;
            loadedHealthRatio = primeHealth ? ratio : 0.0D;
        }
    }
}
