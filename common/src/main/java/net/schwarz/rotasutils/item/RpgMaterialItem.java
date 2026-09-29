package net.schwarz.rotasutils.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.List;

/**
 * A crafting-material item that explains itself in the tooltip.
 *
 * <p>Refine ores and scrolls are only worth picking up if the player can tell what they are for, and a
 * name alone does not say it. Each one carries a translation key ending in {@code .desc}; the line is
 * shown in Thai like the rest of this mod, and an item with no such key simply shows nothing extra.</p>
 */
public class RpgMaterialItem extends Item {
    private final String descriptionKey;
    private final ChatFormatting style;
    private final boolean glint;
    private final Opens opens;

    /** The bench this material opens when it is used, so a player never has to be told a command. */
    public enum Opens { NOTHING, REFINE, SOCKETS, RUNES }

    public RpgMaterialItem(Properties properties, String descriptionKey, ChatFormatting style, boolean glint) {
        this(properties, descriptionKey, style, glint, Opens.NOTHING);
    }

    public RpgMaterialItem(Properties properties, String descriptionKey, ChatFormatting style, boolean glint,
                           Opens opens) {
        super(properties);
        this.descriptionKey = descriptionKey;
        this.style = style;
        this.glint = glint;
        this.opens = opens == null ? Opens.NOTHING : opens;
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level,
                                                                     net.minecraft.world.entity.player.Player player,
                                                                     net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (opens == Opens.NOTHING) {
            return super.use(level, player, hand);
        }
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer server) {
            if (opens == Opens.SOCKETS) {
                net.schwarz.rotasutils.network.RotasNetwork.openSockets(server);
            } else if (opens == Opens.RUNES) {
                net.schwarz.rotasutils.network.RotasNetwork.openRunes(server);
            } else {
                net.schwarz.rotasutils.network.RotasNetwork.openRefine(server);
            }
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** A tiered rune shows its tier after its name. */
    @Override
    public Component getName(ItemStack stack) {
        int tier = ItemRunes.tierOf(stack);
        return tier > 1 && stack.hasTag() ? Component.empty().append(super.getName(stack)).append(" " + ItemRunes.numeral(tier))
                : super.getName(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return glint || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        int tier = ItemRunes.tierOf(stack);
        if (tier > 1 && stack.hasTag()) {
            lines.add(Component.literal(ThaiText.t("rotasutils.rune.tier_line", ItemRunes.numeral(tier),
                    ItemRunes.power(tier))).withStyle(style));
        }
        if (ThaiText.has(descriptionKey)) {
            for (String line : ThaiText.t(descriptionKey).split("\\|")) {
                lines.add(Component.literal(line).withStyle(style));
            }
        }
    }
}
