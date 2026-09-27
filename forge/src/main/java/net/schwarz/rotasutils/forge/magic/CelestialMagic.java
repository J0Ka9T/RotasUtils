package net.schwarz.rotasutils.forge.magic;

import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.entity.CelestialFxEntity;

/**
 * The Celestial school (ดวงดาว) for Iron's Spells 'n Spellbooks: its own spell power and resistance
 * attributes, a damage type, a focus (amethyst shard) and eight spells whose visuals are drawn by
 * CelestialFxRenderer. Only loaded when Iron's Spells is present - see RotasutilsForge.
 */
public final class CelestialMagic {
    public static final ResourceLocation SCHOOL_ID = new ResourceLocation(Rotasutils.MOD_ID, "celestial");
    public static final ResourceKey<DamageType> DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Rotasutils.MOD_ID, "celestial_magic"));
    public static final TagKey<Item> FOCUS = ItemTags.create(new ResourceLocation(Rotasutils.MOD_ID, "celestial_focus"));
    public static final int COLOR = 0x8FD8FF;

    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ForgeRegistries.ATTRIBUTES, Rotasutils.MOD_ID);
    private static final DeferredRegister<SchoolType> SCHOOLS = DeferredRegister.create(SchoolRegistry.SCHOOL_REGISTRY_KEY, Rotasutils.MOD_ID);
    private static final DeferredRegister<AbstractSpell> SPELLS = DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, Rotasutils.MOD_ID);

    public static final RegistryObject<Attribute> POWER = ATTRIBUTES.register("celestial_spell_power", () ->
            new RangedAttribute("attribute.rotasutils.celestial_spell_power", 1.0, 0, 10).setSyncable(true));
    public static final RegistryObject<Attribute> RESIST = ATTRIBUTES.register("celestial_magic_resist", () ->
            new RangedAttribute("attribute.rotasutils.celestial_magic_resist", 1.0, -100, 100).setSyncable(true));

    public static final RegistryObject<SchoolType> SCHOOL = SCHOOLS.register("celestial", () -> new SchoolType(
            SCHOOL_ID, FOCUS,
            Component.translatable("school.rotasutils.celestial").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(COLOR))),
            POWER::get, RESIST::get, () -> SoundEvents.AMETHYST_BLOCK_RESONATE, DAMAGE_TYPE));

    public static final RegistryObject<AbstractSpell> ASTRAL_BOLT = SPELLS.register("astral_bolt", CelestialSpells.AstralBolt::new);
    public static final RegistryObject<AbstractSpell> NOVA_BURST = SPELLS.register("nova_burst", CelestialSpells.NovaBurst::new);
    public static final RegistryObject<AbstractSpell> PRISM_RAY = SPELLS.register("prism_ray", CelestialSpells.PrismRay::new);
    public static final RegistryObject<AbstractSpell> STARLANCE_VOLLEY = SPELLS.register("starlance_volley", CelestialSpells.StarlanceVolley::new);
    public static final RegistryObject<AbstractSpell> COMET_DASH = SPELLS.register("comet_dash", CelestialSpells.CometDash::new);
    public static final RegistryObject<AbstractSpell> CONSTELLATION_BIND = SPELLS.register("constellation_bind", CelestialSpells.ConstellationBind::new);
    public static final RegistryObject<AbstractSpell> AURORA_WARD = SPELLS.register("aurora_ward", CelestialSpells.AuroraWard::new);
    public static final RegistryObject<AbstractSpell> SUPERNOVA = SPELLS.register("supernova", CelestialSpells.Supernova::new);

    private CelestialMagic() {
    }

    public static void init(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
        SCHOOLS.register(modBus);
        SPELLS.register(modBus);
        modBus.addListener(CelestialMagic::addAttributes);
        CelestialFxEntity.behaviour = new CelestialBehaviour();
    }

    /** Every living entity can resist Celestial magic; everything that casts can have Celestial power. */
    private static void addAttributes(EntityAttributeModificationEvent event) {
        for (var type : event.getTypes()) {
            event.add(type, POWER.get());
            event.add(type, RESIST.get());
        }
    }

    static Component gold(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GOLD);
    }
}
