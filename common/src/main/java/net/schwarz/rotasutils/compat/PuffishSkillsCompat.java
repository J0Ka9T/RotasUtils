package net.schwarz.rotasutils.compat;

import dev.architectury.platform.Platform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.Rotasutils;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.stream.Stream;

public final class PuffishSkillsCompat {
    public static final String MOD_ID = "puffish_skills";

    private static boolean reflectionAttempted;
    private static boolean reflectionReady;
    private static boolean loggedFailure;
    private static Method openScreen;
    private static Method streamUnlockedCategories;
    private static Method getCategory;
    private static Method addExtraPoints;
    private static Method categoryIsUnlocked;
    private static Method categoryUnlock;
    private static Method categoryGetSkill;
    private static Method categoryGetId;
    private static Method categoryGetPointsLeft;
    private static Method categoryGetPointsTotal;
    private static Method categoryGetSpentPoints;
    private static Method categoryStreamSkills;
    private static Method categoryStreamUnlockedSkills;
    private static Method skillUnlock;

    private PuffishSkillsCompat() {
    }

    public static boolean isPresent() {
        return Platform.isModLoaded(MOD_ID);
    }

    public static boolean active(net.schwarz.rotasutils.data.RotasData data) {
        return isPresent() && data.levelConfig().pufferfishSkills();
    }

    public static boolean openScreen(ServerPlayer player) {
        if (!prepare()) {
            return false;
        }
        try {
            openScreen.invoke(null, player);
            return true;
        } catch (ReflectiveOperationException e) {
            fail(e);
            return false;
        }
    }

    public static void writeClientSummary(ServerPlayer player, CompoundTag target) {
        ListTag categories = new ListTag();
        if (prepare()) {
            try (Stream<?> stream = (Stream<?>) streamUnlockedCategories.invoke(null, player)) {
                for (Object category : stream.toList()) {
                    CompoundTag entry = new CompoundTag();
                    Object rawId = categoryGetId.invoke(category);
                    entry.putString("id", rawId instanceof ResourceLocation id ? id.toString() : String.valueOf(rawId));
                    entry.putInt("points_left", (Integer) categoryGetPointsLeft.invoke(category, player));
                    entry.putInt("points_total", (Integer) categoryGetPointsTotal.invoke(category, player));
                    entry.putInt("points_spent", (Integer) categoryGetSpentPoints.invoke(category, player));
                    try (Stream<?> allSkills = (Stream<?>) categoryStreamSkills.invoke(category);
                         Stream<?> unlockedSkills = (Stream<?>) categoryStreamUnlockedSkills.invoke(category, player)) {
                        entry.putInt("skills_total", Math.toIntExact(allSkills.count()));
                        entry.putInt("skills_unlocked", Math.toIntExact(unlockedSkills.count()));
                    }
                    categories.add(entry);
                }
            } catch (ReflectiveOperationException | ArithmeticException e) {
                fail(e);
            }
        }
        target.put("puffish_categories", categories);
        target.putBoolean("puffish_available", reflectionReady);
    }

    public static int addPointsToUnlockedCategories(ServerPlayer player, int amount) {
        if (amount <= 0 || !prepare()) {
            return 0;
        }
        int changed = 0;
        try (Stream<?> stream = (Stream<?>) streamUnlockedCategories.invoke(null, player)) {
            for (Object category : stream.toList()) {
                addExtraPoints.invoke(category, player, amount);
                changed++;
            }
        } catch (ReflectiveOperationException e) {
            fail(e);
            return 0;
        }
        return changed;
    }

    public static boolean addPointsToCategory(ServerPlayer player, String categoryId, int amount) {
        if (amount <= 0 || categoryId == null || categoryId.isBlank() || !prepare()) {
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(categoryId);
        if (id == null) {
            return false;
        }
        try {
            Object result = getCategory.invoke(null, id);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) {
                return false;
            }
            Object category = optional.get();
            if (categoryIsUnlocked != null
                    && categoryIsUnlocked.invoke(category, player) instanceof Boolean unlocked
                    && !unlocked) {
                return false;
            }
            addExtraPoints.invoke(category, player, amount);
            return true;
        } catch (ReflectiveOperationException e) {
            fail(e);
            return false;
        }
    }

    public static boolean unlockCategory(ServerPlayer player, String categoryId) {
        Object category = findCategory(categoryId);
        if (category == null) {
            return false;
        }
        try {
            categoryUnlock.invoke(category, player);
            return true;
        } catch (ReflectiveOperationException e) {
            fail(e);
            return false;
        }
    }

    public static boolean unlockSkill(ServerPlayer player, String categoryId, String skillId) {
        Object category = findCategory(categoryId);
        if (category == null || skillId == null || skillId.isBlank()) {
            return false;
        }
        try {
            Object result = categoryGetSkill.invoke(category, skillId);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) {
                return false;
            }
            skillUnlock.invoke(optional.get(), player);
            return true;
        } catch (ReflectiveOperationException e) {
            fail(e);
            return false;
        }
    }

    private static Object findCategory(String categoryId) {
        if (categoryId == null || categoryId.isBlank() || !prepare()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(categoryId);
        if (id == null) {
            return null;
        }
        try {
            Object result = getCategory.invoke(null, id);
            return result instanceof Optional<?> optional && optional.isPresent() ? optional.get() : null;
        } catch (ReflectiveOperationException e) {
            fail(e);
            return null;
        }
    }

    private static boolean prepare() {
        if (!isPresent()) {
            return false;
        }
        if (reflectionAttempted) {
            return reflectionReady;
        }
        reflectionAttempted = true;
        try {
            Class<?> api = Class.forName("net.puffish.skillsmod.api.SkillsAPI");
            Class<?> category = Class.forName("net.puffish.skillsmod.api.Category");
            Class<?> skill = Class.forName("net.puffish.skillsmod.api.Skill");
            openScreen = api.getMethod("openScreen", ServerPlayer.class);
            streamUnlockedCategories = api.getMethod("streamUnlockedCategories", ServerPlayer.class);
            getCategory = api.getMethod("getCategory", ResourceLocation.class);
            addExtraPoints = category.getMethod("addExtraPoints", ServerPlayer.class, int.class);
            categoryIsUnlocked = category.getMethod("isUnlocked", ServerPlayer.class);
            categoryUnlock = category.getMethod("unlock", ServerPlayer.class);
            categoryGetSkill = category.getMethod("getSkill", String.class);
            categoryGetId = category.getMethod("getId");
            categoryGetPointsLeft = category.getMethod("getPointsLeft", ServerPlayer.class);
            categoryGetPointsTotal = category.getMethod("getPointsTotal", ServerPlayer.class);
            categoryGetSpentPoints = category.getMethod("getSpentPoints", ServerPlayer.class);
            categoryStreamSkills = category.getMethod("streamSkills");
            categoryStreamUnlockedSkills = category.getMethod("streamUnlockedSkills", ServerPlayer.class);
            skillUnlock = skill.getMethod("unlock", ServerPlayer.class);
            reflectionReady = true;
            Rotasutils.LOG.info("Pufferfish Skills detected; Rotas level points will use its skill categories");
        } catch (ReflectiveOperationException e) {
            fail(e);
        }
        return reflectionReady;
    }

    private static void fail(Exception e) {
        reflectionReady = false;
        if (!loggedFailure) {
            loggedFailure = true;
            Rotasutils.LOG.error("Pufferfish Skills is installed but its API could not be linked; using Rotas legacy points", e);
        }
    }
}
