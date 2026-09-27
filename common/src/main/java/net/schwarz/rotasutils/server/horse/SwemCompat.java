package net.schwarz.rotasutils.server.horse;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.Rotasutils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Reflection bridge to SWEM (Star Worm Equestrian Mod, Forge only). RotasUtils never links against SWEM, so
 * the mod builds and runs without it; every call fails soft and the horse system reports itself unavailable.
 *
 * <p>Verified against swem-1.20.1-1.6.6: {@code SWEMHorseEntityBase.progressionManager} holds Speed, Jump,
 * Health and Affinity levelings with 0-based {@code getLevel/setLevel} and {@code setXp}; the NBT keys are
 * {@code SpeedLevel}, {@code JumpLevel}, {@code HealthLevel} and {@code AffinityLevel}; coats come from
 * {@code CoatManager} and are set through {@code getCoatBehavior().set(HorseCoat)}.</p>
 */
public final class SwemCompat {
    public static final ResourceLocation HORSE = new ResourceLocation("swem", "swem_horse");
    private static final String BASE = "com.alaharranhonor.swem.forge.entities.horse.SWEMHorseEntityBase";
    private static final String COAT_MANAGER = "com.alaharranhonor.swem.forge.entities.horse.coats.CoatManager";
    private static final String XP_POTION = "com.alaharranhonor.swem.forge.items.HorseXPPotion";
    private static final String[] LEVELINGS = {"getSpeedLeveling", "getJumpLeveling", "getHealthLeveling", "getAffinityLeveling"};
    public static final String[] LEVEL_KEYS = {"SpeedLevel", "JumpLevel", "HealthLevel", "AffinityLevel"};
    public static final int[] MAX_LEVEL = {5, 5, 5, 11};

    public record Coat(String id, boolean breedable, boolean blacklisted) {
    }

    private static Boolean available;
    private static Class<?> baseClass;

    private SwemCompat() {
    }

    public static boolean available() {
        if (available == null) {
            try {
                baseClass = Class.forName(BASE);
                available = BuiltInRegistries.ENTITY_TYPE.containsKey(HORSE);
            } catch (ReflectiveOperationException | LinkageError missing) {
                available = false;
            }
        }
        return available;
    }

    public static boolean isHorse(Entity entity) {
        return entity != null && available() && baseClass.isInstance(entity);
    }

    public static EntityType<?> horseType() {
        return available() ? BuiltInRegistries.ENTITY_TYPE.get(HORSE) : null;
    }

    public static boolean isXpPotion(ItemStack stack) {
        for (Class<?> type = stack.getItem().getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals(XP_POTION)) {
                return true;
            }
        }
        return false;
    }

    /** Display levels (1-based) for Speed, Jump, Health and Affinity; all 1 when SWEM cannot be read. */
    public static int[] levels(Entity horse) {
        int[] levels = {1, 1, 1, 1};
        if (!isHorse(horse)) {
            return levels;
        }
        try {
            Object manager = baseClass.getField("progressionManager").get(horse);
            for (int i = 0; i < 4; i++) {
                Object leveling = manager.getClass().getMethod(LEVELINGS[i]).invoke(manager);
                levels[i] = (int) leveling.getClass().getMethod("getLevel").invoke(leveling) + 1;
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            warn("read levels", failure);
        }
        return levels;
    }

    /** Levels read from a saved horse, for stored horses that are not in the world. */
    public static int[] levels(CompoundTag snapshot) {
        int[] levels = new int[4];
        for (int i = 0; i < 4; i++) {
            levels[i] = Math.max(1, Math.min(MAX_LEVEL[i] + 1, snapshot.getInt(LEVEL_KEYS[i]) + 1));
        }
        return levels;
    }

    /** Sets display levels (1-based) and clears the XP inside each level, so nothing overflows on load. */
    public static boolean setLevels(Entity horse, int[] levels) {
        if (!isHorse(horse)) {
            return false;
        }
        try {
            Object manager = baseClass.getField("progressionManager").get(horse);
            for (int i = 0; i < 4; i++) {
                Object leveling = manager.getClass().getMethod(LEVELINGS[i]).invoke(manager);
                leveling.getClass().getMethod("setLevel", int.class).invoke(leveling, Math.max(0, levels[i] - 1));
                leveling.getClass().getMethod("setXp", float.class).invoke(leveling, 0f);
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            warn("set levels", failure);
            return false;
        }
    }

    public static String coat(Entity horse) {
        if (!isHorse(horse)) {
            return "";
        }
        try {
            Object behavior = baseClass.getMethod("getCoatBehavior").invoke(horse);
            Object coat = behavior.getClass().getMethod("coat").invoke(behavior);
            return coat == null ? "" : String.valueOf(coat.getClass().getMethod("id").invoke(coat));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            warn("read coat", failure);
            return "";
        }
    }

    public static boolean setCoat(Entity horse, String coatId) {
        ResourceLocation id = ResourceLocation.tryParse(coatId);
        if (!isHorse(horse) || id == null) {
            return false;
        }
        try {
            Class<?> manager = Class.forName(COAT_MANAGER);
            Object coat = manager.getMethod("get", ResourceLocation.class).invoke(null, id);
            if (coat == null) {
                return false;
            }
            Object behavior = baseClass.getMethod("getCoatBehavior").invoke(horse);
            for (Method method : behavior.getClass().getMethods()) {
                if (method.getName().equals("set") && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isInstance(coat)) {
                    method.invoke(behavior, coat);
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            warn("set coat", failure);
        }
        return false;
    }

    /** Every coat SWEM knows, with whether foals can inherit it. */
    @SuppressWarnings("unchecked")
    public static List<Coat> coats() {
        if (!available()) {
            return List.of();
        }
        try {
            Class<?> manager = Class.forName(COAT_MANAGER);
            Field field = manager.getDeclaredField("ALL_COATS");
            field.setAccessible(true);
            List<Coat> coats = new ArrayList<>();
            for (Object coat : ((Map<Object, Object>) field.get(null)).values()) {
                Class<?> type = coat.getClass();
                coats.add(new Coat(String.valueOf(type.getMethod("id").invoke(coat)),
                        (boolean) type.getMethod("obtainableByBreeding").invoke(coat),
                        (boolean) type.getMethod("isBlacklisted").invoke(coat)));
            }
            return Collections.unmodifiableList(coats);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            warn("list coats", failure);
            return List.of();
        }
    }

    public static boolean tame(Entity horse, ServerPlayer owner) {
        if (!isHorse(horse)) {
            return false;
        }
        try {
            baseClass.getMethod("tameWithName", com.mojang.authlib.GameProfile.class).invoke(horse, owner.getGameProfile());
            return true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            warn("tame", failure);
            return false;
        }
    }

    public static boolean ownedBy(Entity horse, ServerPlayer player) {
        if (!isHorse(horse)) {
            return false;
        }
        try {
            return (boolean) baseClass.getMethod("isOwner", net.minecraft.world.entity.player.Player.class).invoke(horse, player);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            warn("owner check", failure);
            return false;
        }
    }

    /** True for a foal of two recorded parents, which is how SWEM marks a bred horse. */
    public static boolean bred(Entity horse) {
        if (!isHorse(horse)) {
            return false;
        }
        try {
            return baseClass.getMethod("getSire").invoke(horse) != null || baseClass.getMethod("getDam").invoke(horse) != null;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return false;
        }
    }

    private static int warnings;

    private static void warn(String action, Throwable failure) {
        if (warnings++ < 16) {
            Rotasutils.LOG.warn("SWEM horse bridge could not {}: {}", action, failure.toString());
        }
    }
}
