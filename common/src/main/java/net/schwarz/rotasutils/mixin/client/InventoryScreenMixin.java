package net.schwarz.rotasutils.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.inventory.Slot;
import net.schwarz.rotasutils.client.compat.CuriosClientCompat;
import net.schwarz.rotasutils.client.inventory.CharacterHubTab;
import net.schwarz.rotasutils.client.inventory.RotasInventoryRenderer;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.player.PartyScreen;
import net.schwarz.rotasutils.client.screen.player.QuestDetailScreen;
import net.schwarz.rotasutils.client.screen.player.StatsScreen;
import net.schwarz.rotasutils.client.screen.player.TitleScreen;
import net.schwarz.rotasutils.network.RotasNetwork;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    private static final int HIDDEN_SLOT = -10000;

    @Shadow @Final private RecipeBookComponent recipeBookComponent;

    @Unique private CharacterHubTab rotasutils$tab = CharacterHubTab.BAG;
    @Unique private String rotasutils$selectedQuestId = "";
    @Unique private RotasInventoryRenderer.Layout rotasutils$appliedLayout;

    @Inject(method = "init", at = @At("HEAD"))
    private void rotasutils$prepareCharacterHub(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode == null || minecraft.gameMode.hasInfiniteItems()) return;
        AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) this;
        screen.rotasutils$setImageWidth(RotasInventoryRenderer.targetWidth(minecraft.getWindow().getGuiScaledWidth()));
        screen.rotasutils$setImageHeight(RotasInventoryRenderer.targetHeight(minecraft.getWindow().getGuiScaledHeight()));
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void rotasutils$finishCharacterHub(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode == null || minecraft.gameMode.hasInfiniteItems()) return;
        AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) this;
        screen.rotasutils$setImageWidth(RotasInventoryRenderer.targetWidth(minecraft.getWindow().getGuiScaledWidth()));
        screen.rotasutils$setImageHeight(RotasInventoryRenderer.targetHeight(minecraft.getWindow().getGuiScaledHeight()));
        RotasInventoryRenderer.resetScrolls();
        rotasutils$applySlotLayout();
        RotasNetwork.sendAction("request_sync");

        if (recipeBookComponent.isVisible()) recipeBookComponent.toggleVisibility();
        rotasutils$hideForeignWidgets();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void rotasutils$characterHubClick(double mouseX, double mouseY, int button,
                                               CallbackInfoReturnable<Boolean> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        if ((button != 0 && button != 1) || minecraft.gameMode == null || minecraft.gameMode.hasInfiniteItems()) return;
        AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) this;

        var curio = RotasInventoryRenderer.curioAt(
                screen.rotasutils$getLeftPos(), screen.rotasutils$getTopPos(),
                screen.rotasutils$getImageWidth(), screen.rotasutils$getImageHeight(), mouseX, mouseY);
        if (curio != null) {
            CuriosClientCompat.click(curio.slotType(), curio.slotIndex(), button);
            cir.setReturnValue(true);
            return;
        }

        RotasInventoryRenderer.Hit hit = RotasInventoryRenderer.hitTest(
                screen.rotasutils$getLeftPos(), screen.rotasutils$getTopPos(),
                screen.rotasutils$getImageWidth(), screen.rotasutils$getImageHeight(),
                rotasutils$tab, rotasutils$selectedQuestId, mouseX, mouseY);
        if (hit.type() == RotasInventoryRenderer.ActionType.NONE) return;

        switch (hit.type()) {
            case SELECT_TAB -> {
                rotasutils$tab = hit.tab();
                rotasutils$applySlotLayout();
                Sfx.page();
            }
            case OPEN_SKILLS -> {
                RotasNetwork.sendAction("open_skills");
                Sfx.page();
            }
            case OPEN_PARTY -> {
                minecraft.setScreen(new PartyScreen((InventoryScreen) (Object) this));
                Sfx.page();
            }
            case OPEN_STATS -> {
                minecraft.setScreen(new StatsScreen((InventoryScreen) (Object) this));
                Sfx.page();
            }
            case WITHDRAW_COINS -> {
                net.minecraft.nbt.CompoundTag payload = new net.minecraft.nbt.CompoundTag();
                payload.putInt("amount", Integer.parseInt(hit.value()));
                RotasNetwork.sendAction("wallet_withdraw", payload);
                Sfx.select();
            }
            case SELECT_QUEST -> {
                rotasutils$selectedQuestId = hit.value();
                Sfx.select();
            }
            case OPEN_QUEST -> {
                minecraft.setScreen(new QuestDetailScreen(hit.value(), "", (InventoryScreen) (Object) this));
                Sfx.page();
            }
            case OPEN_TITLES -> {
                minecraft.setScreen(new TitleScreen((InventoryScreen) (Object) this));
                Sfx.page();
            }
            default -> {
            }
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "renderBg", at = @At("HEAD"), cancellable = true)
    private void rotasutils$renderCharacterHub(GuiGraphics graphics, float partialTick,
                                               int mouseX, int mouseY, CallbackInfo ci) {
        rotasutils$hideForeignWidgets();
        AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) this;
        if (RotasInventoryRenderer.currentLayout(screen.rotasutils$getImageWidth(),
                screen.rotasutils$getImageHeight()) != rotasutils$appliedLayout) {
            rotasutils$applySlotLayout();
        }
        RotasInventoryRenderer.renderBackground(graphics,
                screen.rotasutils$getLeftPos(), screen.rotasutils$getTopPos(),
                screen.rotasutils$getImageWidth(), screen.rotasutils$getImageHeight(),
                mouseX, mouseY, rotasutils$tab, rotasutils$selectedQuestId);
        ci.cancel();
    }

    @Inject(method = "renderLabels", at = @At("HEAD"), cancellable = true)
    private void rotasutils$renderCharacterHubLabels(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        RotasInventoryRenderer.renderLabels(graphics);
        ci.cancel();
    }

@Unique
    private void rotasutils$hideForeignWidgets() {
        for (var child : ((InventoryScreen) (Object) this).children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                widget.visible = false;
                widget.active = false;
            }
        }
    }

    @Unique
    private void rotasutils$applySlotLayout() {
        InventoryScreen inventory = (InventoryScreen) (Object) this;
        AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) this;
        RotasInventoryRenderer.Layout l = RotasInventoryRenderer.currentLayout(
                screen.rotasutils$getImageWidth(), screen.rotasutils$getImageHeight());
        rotasutils$appliedLayout = l;
        List<Slot> slots = inventory.getMenu().slots;
        if (slots.size() < 46) return;

        setSlot(slots.get(5), l.headX() - 8, l.headY() - 8);
        setSlot(slots.get(6), l.chestX() - 8, l.chestY() - 8);
        setSlot(slots.get(7), l.legsX() - 8, l.legsY() - 8);
        setSlot(slots.get(8), l.feetX() - 8, l.feetY() - 8);
        setSlot(slots.get(45), l.offhandX() - 8, l.offhandY() - 8);

        if (RotasInventoryRenderer.showsCrafting(rotasutils$tab)) {
            int craftInset = RotasInventoryRenderer.itemInset(l.craftBox());
            int craftPitch = l.spacing();
            setSlot(slots.get(0), l.resultX() + craftInset, l.resultY() + craftInset);
            for (int row = 0; row < 2; row++) {
                for (int col = 0; col < 2; col++) {
                    setSlot(slots.get(1 + row * 2 + col),
                            l.craftX() + col * craftPitch + craftInset, l.craftY() + row * craftPitch + craftInset);
                }
            }
        } else {
            for (int i = 0; i <= 4; i++) hideSlot(slots.get(i));
        }

        int inset = RotasInventoryRenderer.itemInset(l.slotBox());
        if (RotasInventoryRenderer.showsMainInventory(rotasutils$tab)) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    setSlot(slots.get(9 + row * 9 + col),
                            l.gridX() + col * l.spacing() + inset, l.gridY() + row * l.spacing() + inset);
                }
            }
        } else {
            for (int i = 9; i <= 35; i++) hideSlot(slots.get(i));
        }

        if (RotasInventoryRenderer.showsHotbar(rotasutils$tab)) {
            for (int col = 0; col < 9; col++) {
                setSlot(slots.get(36 + col), l.gridX() + col * l.spacing() + inset, l.hotbarY() + inset);
            }
        } else {
            for (int i = 36; i <= 44; i++) hideSlot(slots.get(i));
        }
    }

    @Unique
    private static void hideSlot(Slot slot) {
        setSlot(slot, HIDDEN_SLOT, HIDDEN_SLOT);
    }

    @Unique
    private static void setSlot(Slot slot, int x, int y) {
        SlotAccessor accessor = (SlotAccessor) slot;
        accessor.rotasutils$setX(x);
        accessor.rotasutils$setY(y);
    }
}
