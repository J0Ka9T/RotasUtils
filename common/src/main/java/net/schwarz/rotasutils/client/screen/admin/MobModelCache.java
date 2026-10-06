package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.schwarz.rotasutils.client.screen.Ui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public final class MobModelCache {
    private final Map<EntityType<?>, Entity> models = new HashMap<>();
    private final Set<EntityType<?>> broken = new HashSet<>();
    private final int perFrame;
    private int createdThisFrame;

    public MobModelCache(int perFrame) {
        this.perFrame = perFrame;
    }

    public void beginFrame() {
        createdThisFrame = 0;
    }

    public Entity model(EntityType<?> type) {
        Minecraft minecraft = Minecraft.getInstance();
        if (type == null || broken.contains(type)) {
            return null;
        }
        Entity cached = models.get(type);
        if (cached != null || minecraft.level == null || createdThisFrame >= perFrame) {
            return cached;
        }
        createdThisFrame++;
        try {
            Entity created = type.create(minecraft.level);
            if (created == null) {
                broken.add(type);
            } else {
                models.put(type, created);
            }
            return created;
        } catch (RuntimeException | LinkageError failure) {
            broken.add(type);
            return null;
        }
    }

    public LivingEntity living(EntityType<?> type) {
        return model(type) instanceof LivingEntity living ? living : null;
    }

    public void draw(GuiGraphics graphics, EntityType<?> type, int centerX, int feetY, int box, float lookX, float lookY) {
        LivingEntity living = living(type);
        if (living != null) {
            float size = Math.max(living.getBbHeight(), living.getBbWidth());
            int scale = (int) Math.max(2, Math.min(box, box * 0.85f / Math.max(0.3f, size)));
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, centerX, feetY, scale, lookX, lookY, living);
                return;
            } catch (RuntimeException | LinkageError failure) {
                broken.add(type);
                models.remove(type);
            }
        }
        ItemStack egg = egg(type);
        if (!egg.isEmpty()) {
            graphics.renderFakeItem(egg, centerX - 8, feetY - 26);
        } else {
            Ui.labelCentered(graphics, "?", centerX, feetY - 22, Ui.TEXT_MUTED);
        }
    }

    public void clear() {
        models.clear();
    }

    public static EntityType<?> type(String id) {
        ResourceLocation location = id == null ? null : ResourceLocation.tryParse(id);
        return location != null && BuiltInRegistries.ENTITY_TYPE.containsKey(location)
                ? BuiltInRegistries.ENTITY_TYPE.get(location) : null;
    }

    public static String displayName(String id) {
        EntityType<?> type = type(id);
        return type == null ? id : type.getDescription().getString();
    }

    public static ItemStack egg(EntityType<?> type) {
        SpawnEggItem egg = type == null ? null : SpawnEggItem.byId(type);
        return egg == null ? ItemStack.EMPTY : new ItemStack(egg);
    }

    public static ItemStack egg(String id) {
        return egg(type(id));
    }
}
