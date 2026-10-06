package net.schwarz.rotasutils.forge.magic;

import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
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

public final class AbyssMagic {
    public static final ResourceLocation SCHOOL_ID = new ResourceLocation(Rotasutils.MOD_ID, "abyss");
    public static final ResourceKey<DamageType> DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Rotasutils.MOD_ID, "abyss_magic"));
    public static final TagKey<Item> FOCUS = ItemTags.create(new ResourceLocation(Rotasutils.MOD_ID, "abyss_focus"));
    public static final int COLOR = 0x9A4DFF;

    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ForgeRegistries.ATTRIBUTES, Rotasutils.MOD_ID);
    private static final DeferredRegister<SchoolType> SCHOOLS = DeferredRegister.create(SchoolRegistry.SCHOOL_REGISTRY_KEY, Rotasutils.MOD_ID);
    private static final DeferredRegister<AbstractSpell> SPELLS = DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, Rotasutils.MOD_ID);

    public static final RegistryObject<Attribute> POWER = ATTRIBUTES.register("abyss_spell_power", () ->
            new RangedAttribute("attribute.rotasutils.abyss_spell_power", 1.0, 0, 10).setSyncable(true));
    public static final RegistryObject<Attribute> RESIST = ATTRIBUTES.register("abyss_magic_resist", () ->
            new RangedAttribute("attribute.rotasutils.abyss_magic_resist", 1.0, -100, 100).setSyncable(true));

    public static final RegistryObject<SchoolType> SCHOOL = SCHOOLS.register("abyss", () -> new SchoolType(
            SCHOOL_ID, FOCUS,
            Component.translatable("school.rotasutils.abyss").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(COLOR))),
            POWER::get, RESIST::get, () -> SoundEvents.SCULK_SHRIEKER_SHRIEK, DAMAGE_TYPE));

    public static final RegistryObject<AbstractSpell> SHADOW_BOLT = SPELLS.register("shadow_bolt", AbyssSpells.ShadowBolt::new);
    public static final RegistryObject<AbstractSpell> UMBRAL_GRASP = SPELLS.register("umbral_grasp", AbyssSpells.UmbralGrasp::new);
    public static final RegistryObject<AbstractSpell> VOID_RAY = SPELLS.register("void_ray", AbyssSpells.VoidRay::new);
    public static final RegistryObject<AbstractSpell> ECLIPSE_NOVA = SPELLS.register("eclipse_nova", AbyssSpells.EclipseNova::new);
    public static final RegistryObject<AbstractSpell> SHADE_STEP = SPELLS.register("shade_step", AbyssSpells.ShadeStep::new);
    public static final RegistryObject<AbstractSpell> ABYSSAL_PIT = SPELLS.register("abyssal_pit", AbyssSpells.AbyssalPit::new);
    public static final RegistryObject<AbstractSpell> NIGHT_VEIL = SPELLS.register("night_veil", AbyssSpells.NightVeil::new);
    public static final RegistryObject<AbstractSpell> OBLIVION = SPELLS.register("oblivion", AbyssSpells.Oblivion::new);

    private AbyssMagic() {
    }

    public static void init(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
        SCHOOLS.register(modBus);
        SPELLS.register(modBus);
        modBus.addListener(AbyssMagic::addAttributes);
        CelestialFxEntity.abyssBehaviour = new AbyssBehaviour();
    }

    private static void addAttributes(EntityAttributeModificationEvent event) {
        for (var type : event.getTypes()) {
            event.add(type, POWER.get());
            event.add(type, RESIST.get());
        }
    }
}
