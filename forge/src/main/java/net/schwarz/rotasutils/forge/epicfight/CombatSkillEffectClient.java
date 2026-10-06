package net.schwarz.rotasutils.forge.epicfight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.schwarz.rotasutils.client.cinematic.CastPostFx;
import net.schwarz.rotasutils.client.render.CameraQuake;
import net.schwarz.rotasutils.client.render.VfxRenderTypes;
import net.schwarz.rotasutils.client.render.WorldVfxOverlay;
import net.schwarz.rotasutils.core.CombatSkillVisuals;
import net.schwarz.rotasutils.core.DharmakayaRules;
import net.schwarz.rotasutils.core.SwordConvergenceTimeline;
import net.schwarz.rotasutils.core.WanJianTimeline;
import org.joml.Matrix4f;
import yesman.epicfight.api.client.model.Mesh;
import yesman.epicfight.api.utils.EntitySnapshot;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.world.capabilites.entitypatch.player.AbstractClientPlayerPatch;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CombatSkillEffectClient {
    private static final int MAX_EFFECTS = 96;
    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static final MultiBufferSource.BufferSource BUFFERS = buildBuffers();

    private static MultiBufferSource.BufferSource buildBuffers() {
        Map<RenderType, BufferBuilder> fixed = new LinkedHashMap<>();
        fixed.put(VfxRenderTypes.ADDITIVE, new BufferBuilder(1 << 20));
        fixed.put(VfxRenderTypes.TRANSLUCENT, new BufferBuilder(1 << 16));
        fixed.put(VfxRenderTypes.lightTextured(MyriadSwordsRenderer.BLADE), new BufferBuilder(1 << 18));
        fixed.put(VfxRenderTypes.lightTextured(MyriadSwordsRenderer.SEAL), new BufferBuilder(1 << 16));
        fixed.put(VfxRenderTypes.lightTextured(DharmakayaRenderer.SEAL), new BufferBuilder(1 << 16));
        fixed.put(VfxRenderTypes.lightTextured(DharmakayaRenderer.EMBLEM), new BufferBuilder(1 << 16));
        fixed.put(VfxRenderTypes.lightTextured(DharmakayaRenderer.ENSO), new BufferBuilder(1 << 16));
        fixed.put(VfxRenderTypes.sprite(DharmakayaRenderer.TALISMAN), new BufferBuilder(1 << 14));
        fixed.put(VfxRenderTypes.lightTextured(HitVfx.FLARE), new BufferBuilder(1 << 16));
        fixed.put(VfxRenderTypes.lightTextured(MyriadArcana.GLYPHS), new BufferBuilder(1 << 16));
        fixed.put(VfxRenderTypes.lightTextured(MyriadArcana.NEBULA), new BufferBuilder(1 << 15));
        fixed.put(VfxRenderTypes.sprite(SwordSprite.SWORD), new BufferBuilder(1 << 19));
        fixed.put(VfxRenderTypes.lightTextured(SwordSprite.GLOW), new BufferBuilder(1 << 19));
        fixed.put(VfxRenderTypes.lightTextured(DharmakayaRenderer.HALO), new BufferBuilder(1 << 14));
        fixed.put(VfxRenderTypes.lightTextured(DharmakayaRenderer.STRIKE), new BufferBuilder(1 << 14));
        fixed.put(VfxRenderTypes.lightTextured(DharmakayaRenderer.GLOW), new BufferBuilder(1 << 14));
        return MultiBufferSource.immediateWithBuffers(fixed, new BufferBuilder(1 << 16));
    }
    private static ClientLevel lastLevel;
    private static boolean installed;
    private CombatSkillEffectClient() { }

    public static void init() {
        if (installed) return;
        installed = true;
        MinecraftForge.EVENT_BUS.addListener(CombatSkillEffectClient::tick);
        MinecraftForge.EVENT_BUS.addListener(CombatSkillEffectClient::render);
    }

    public static void accept(CombatSkillEffects.Cue cue) {
        Minecraft mc = Minecraft.getInstance();
        syncWorld(mc);
        CombatSkillVisuals.Skill skill = CombatSkillVisuals.skill(cue.skill());
        if (mc.level == null || skill == null || !mc.level.dimension().location().toString().equals(cue.dimension())
                || !finite(cue.source()) || !finite(cue.target()) || !Float.isFinite(cue.yaw())
                || !Float.isFinite(cue.height())) return;
        boolean strike = cue.stage() > 0 && (skill == CombatSkillVisuals.Skill.DHARMAKAYA
                || skill == CombatSkillVisuals.Skill.WANJIAN || skill == CombatSkillVisuals.Skill.MYRIAD_SWORDS_RETURN);
        EFFECTS.removeIf(fx -> {
            boolean replace = !strike && fx.cue.sourceId() == cue.sourceId() && fx.skill == skill;
            if (replace) fx.dispose();
            return replace;
        });
        if (cue.stage() < 0) return;
        if (EFFECTS.size() >= MAX_EFFECTS) EFFECTS.remove(0).dispose();
        Effect effect = new Effect(cue, skill, mc.level.getGameTime());
        Entity entity = mc.level.getEntity(cue.sourceId());
        if ((skill == CombatSkillVisuals.Skill.AFTERIMAGE && cue.stage() == 0
                || skill == CombatSkillVisuals.Skill.PREDATORS_MARK && cue.stage() == 1)
                && entity instanceof AbstractClientPlayer player
                && mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer) {
            var patch = EpicFightCapabilities.getEntityPatch(player, AbstractClientPlayerPatch.class);
            if (patch != null && patch.overrideRender()) {
                effect.epicGhost = patch.captureEntitySnapshot();
            }
            if (effect.epicGhost == null) {
            boolean slim = player.getModelName().equals("slim");
            effect.ghost = new PlayerModel<>(mc.getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
            PlayerModel<AbstractClientPlayer> live = renderer.getModel();
            live.copyPropertiesTo(effect.ghost);
            effect.ghost.head.copyFrom(live.head);
            effect.ghost.hat.copyFrom(live.hat);
            effect.ghost.body.copyFrom(live.body);
            effect.ghost.leftArm.copyFrom(live.leftArm);
            effect.ghost.rightArm.copyFrom(live.rightArm);
            effect.ghost.leftLeg.copyFrom(live.leftLeg);
            effect.ghost.rightLeg.copyFrom(live.rightLeg);
            effect.ghost.leftSleeve.copyFrom(live.leftSleeve);
            effect.ghost.rightSleeve.copyFrom(live.rightSleeve);
            effect.ghost.leftPants.copyFrom(live.leftPants);
            effect.ghost.rightPants.copyFrom(live.rightPants);
            effect.ghost.jacket.copyFrom(live.jacket);
            effect.skin = player.getSkinTextureLocation();
            effect.ghostYaw = player.yBodyRot;
            }
        }
        EFFECTS.add(effect);
        if (skill == CombatSkillVisuals.Skill.LAST_BASTION || skill == CombatSkillVisuals.Skill.MOMENTUM && cue.stage() == 6) {
            float setting = mc.options.screenEffectScale().get().floatValue();
            CameraQuake.impulse(.45f * setting, mc.gameRenderer.getMainCamera().getPosition().distanceTo(cue.target()), 12);
        }
    }

    private static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static void syncWorld(Minecraft mc) {
        if (mc.level != lastLevel) { EFFECTS.forEach(Effect::dispose); EFFECTS.clear(); lastLevel = mc.level; }
    }
    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        syncWorld(mc);
        if (mc.level == null || mc.isPaused()) return;
        for (Effect fx : EFFECTS) {
            long age = mc.level.getGameTime() - fx.born;
            if (fx.sound != null) fx.sound.tick(age);
            if (fx.domain != null) fx.domain.tick(age);
            if (fx.skill == CombatSkillVisuals.Skill.DHARMAKAYA && fx.cue.stage() == 0
                    && mc.level.getEntity(fx.cue.sourceId()) instanceof AbstractClientPlayer player) {
                DharmakayaRenderer.tick(player);
            }
        }
        EFFECTS.removeIf(fx -> {
            boolean expired = mc.level.getGameTime() - fx.born >= fx.life;
            if (expired) fx.dispose();
            return expired;
        });
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        syncWorld(mc);
        if (mc.level == null || EFFECTS.isEmpty()) return;
        Vec3 camera = event.getCamera().getPosition();
        for (Effect fx : EFFECTS) {
            float age = mc.level.getGameTime() - fx.born + event.getPartialTick();
            if (age < 0 || age >= fx.life) continue;
            if (fx.cue.stage() > 0 && (fx.skill == CombatSkillVisuals.Skill.WANJIAN
                    || fx.skill == CombatSkillVisuals.Skill.MYRIAD_SWORDS_RETURN)) {
                Vec3 at = fx.cue.target();
                if (camera.distanceToSqr(at) > 64 * 64) continue;
                int core = fx.skill == CombatSkillVisuals.Skill.WANJIAN ? 0xFFF1D0 : 0xF2FDFF;
                int accent = fx.skill == CombatSkillVisuals.Skill.WANJIAN ? 0xE0B25C : 0x5FD0FF;
                PoseStack hitPose = event.getPoseStack();
                hitPose.pushPose();
                hitPose.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
                final float hitAge = age;
                if (!WorldVfxOverlay.defer(hitPose, (deferred, output) -> {
                    HitVfx.draw(deferred, BUFFERS, fx.cue, hitAge, camera, core, accent);
                    BUFFERS.endBatch();
                })) HitVfx.draw(hitPose, BUFFERS, fx.cue, age, camera, core, accent);
                hitPose.popPose();
                continue;
            }
            AbstractClientPlayer domainCaster = null;
            Vec3 anchor = fx.cue.target();
            if (fx.skill == CombatSkillVisuals.Skill.DHARMAKAYA && fx.cue.stage() > 0) {
                feelDharmaStrike(fx, camera, anchor);
            } else if (fx.skill == CombatSkillVisuals.Skill.DHARMAKAYA) {
                if (!(mc.level.getEntity(fx.cue.sourceId()) instanceof AbstractClientPlayer player)) continue;
                domainCaster = player;
                anchor = player.getPosition(event.getPartialTick());
                dharmaScreen(event, age, fx.life, camera, anchor);
            } else if (fx.skill == CombatSkillVisuals.Skill.WANJIAN) {
                if (mc.level.getEntity(fx.cue.sourceId()) instanceof AbstractClientPlayer player) domainCaster = player;
                anchor = fx.cue.source();
            } else if (fx.follow()) {
                Entity target = mc.level.getEntity(fx.cue.targetId());
                if (target == null || !target.isAlive()) continue;
                anchor = target.getPosition(event.getPartialTick());
            }
            double cull = fx.skill == CombatSkillVisuals.Skill.WANJIAN ? 200 : domainCaster == null ? 48 : 112;
            if (camera.distanceToSqr(anchor) > cull * cull) continue;
            if (fx.skill == CombatSkillVisuals.Skill.MYRIAD_SWORDS_RETURN) {
                feelMyriad(fx, age, camera, anchor);
                myriadScreen(event, age, camera, anchor);
            } else if (fx.skill == CombatSkillVisuals.Skill.WANJIAN) {
                feelWanjian(fx, age, camera, anchor);
                wanjianScreen(event, age, camera, anchor);
            }
            final AbstractClientPlayer caster = domainCaster;
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            pose.translate(anchor.x - camera.x, anchor.y - camera.y, anchor.z - camera.z);
            boolean dharma = fx.skill == CombatSkillVisuals.Skill.DHARMAKAYA;
            boolean wanjian = fx.skill == CombatSkillVisuals.Skill.WANJIAN;
            if (!WorldVfxOverlay.defer(pose, (deferred, output) -> {
                if (dharma) DharmakayaRenderer.draw(deferred, BUFFERS, fx.cue, age, caster);
                else if (wanjian) WanJianRenderer.draw(deferred, BUFFERS, fx.cue, age, caster);
                else fx.draw(deferred, BUFFERS, age);
                BUFFERS.endBatch();
            })) {
                if (dharma) DharmakayaRenderer.draw(pose, BUFFERS, fx.cue, age, caster);
                else if (wanjian) WanJianRenderer.draw(pose, BUFFERS, fx.cue, age, caster);
                else fx.draw(pose, BUFFERS, age);
            }
            pose.popPose();
        }
        BUFFERS.endBatch();
    }

    private static final class Effect {
        final CombatSkillEffects.Cue cue;
        final CombatSkillVisuals.Skill skill;
        final long born;
        final int life;
        PlayerModel<AbstractClientPlayer> ghost;
        EntitySnapshot<?> epicGhost;
        final MyriadSwordsSoundTrack sound;
        final DharmakayaSoundTrack domain;
        ResourceLocation skin;
        float ghostYaw;
        int volleys;
        int pulses;
        boolean punched;
        Effect(CombatSkillEffects.Cue cue, CombatSkillVisuals.Skill skill, long born) {
            this.cue = cue; this.skill = skill; this.born = born;
            life = CombatSkillVisuals.duration(cue.life());
            sound = skill == CombatSkillVisuals.Skill.MYRIAD_SWORDS_RETURN && cue.stage() == 0 ? new MyriadSwordsSoundTrack(cue) : null;
            domain = skill == CombatSkillVisuals.Skill.DHARMAKAYA && cue.stage() == 0 ? new DharmakayaSoundTrack(cue) : null;
        }
        void dispose() {
            if (sound != null) sound.stop();
            if (domain != null) domain.stop();
            if (skill == CombatSkillVisuals.Skill.DHARMAKAYA) DharmakayaRenderer.clear(cue.sourceId());
        }
        boolean follow() {
            return skill == CombatSkillVisuals.Skill.PREDATORS_MARK && cue.stage() == 0
                    || skill == CombatSkillVisuals.Skill.LAST_BASTION
                    || skill == CombatSkillVisuals.Skill.HUNTERS_PATIENCE && cue.stage() == 0
                    || skill == CombatSkillVisuals.Skill.FROSTBIND || skill == CombatSkillVisuals.Skill.SUNFIRE_BRAND;
        }
        void draw(PoseStack pose, MultiBufferSource output, float age) {
            float alpha = CombatSkillVisuals.alpha(age, life);
            float t = Math.min(1, age / life), kick = (float) Math.sin(t * Math.PI * .5);
            float bloom = Math.min(1, age / 7), release = Math.max(0, (t - .65f) / .35f);
            float breath = (float) Math.sin(age * .17);
            float scale = CombatSkillVisuals.scale(cue.scale());
            float height = Math.max(.5f, Math.min(4, cue.height()));
            VertexConsumer light = output.getBuffer(VfxRenderTypes.ADDITIVE);
            VertexConsumer solid = output.getBuffer(VfxRenderTypes.TRANSLUCENT);
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(-cue.yaw()));
            switch (skill) {
                case MYRIAD_SWORDS_RETURN -> {
                    pose.mulPose(Axis.YP.rotationDegrees(cue.yaw()));
                    MyriadSwordsRenderer.draw(pose, output, cue, age);
                }
                case MOMENTUM -> {
                    pose.translate(0, height * .55, 0);
                    for (int i = 0; i < Math.min(5, Math.max(1, cue.stage())); i++)
                        arc(pose, light, .65 + .10 * i, .035, age * .09 + i * 1.25, 1.0, skill.colour, alpha);
                    if (cue.stage() == 6) {
                        ring(pose, light, .3 + 2.2 * kick, .24, skill.colour, alpha * .25f);
                        ring(pose, light, .3 + 2.2 * kick, .045, 0xFFF1B8, alpha);
                        crossCut(pose, light, 1.3, skill.colour, alpha);
                    }
                }
                case LAST_BASTION, IRON_REBOUND -> {
                    pose.translate(0, height * .55, 0);
                    for (int i = 0; i < 6; i++) {
                        pose.pushPose(); pose.mulPose(Axis.YP.rotationDegrees(i * 60 + age * 2));
                        pose.translate(0, 0, .65 + bloom * .30 + breath * .035 + release * .18);
                        plate(pose, solid, .30 * (1 - release * .3), .6 * bloom, skill.colour, alpha * .25f);
                        diamond(pose, light, .34 * (1 - release * .3), .65 * bloom, skill.colour, alpha);
                        pose.popPose();
                    }
                    ring(pose, light, .9 + kick * .35, .05, skill.colour, alpha);
                    if (cue.stage() == 1) crossCut(pose, light, 1.1, skill.colour, alpha);
                }
                case PREDATORS_MARK -> {
                    if (cue.stage() == 0) {
                        pose.translate(0, height + .3 + Math.sin(age * .13) * .10, 0);
                        pose.mulPose(Axis.YP.rotationDegrees(age * 3));
                        diamond(pose, light, .22 + .015 * breath, .36 + .025 * breath, skill.colour, alpha);
                        crossCut(pose, light, .28, skill.colour, alpha * .6f);
                        pose.mulPose(Axis.XP.rotationDegrees(90)); ring(pose, light, .34, .025, skill.colour, alpha);
                    } else { ring(pose, solid, 1.4 * (1 - kick) + .1, .13, 0x341446, alpha * .7f); ghost(pose, output, age, alpha); }
                }
                case AFTERIMAGE -> {
                    if (cue.stage() == 0) { ghost(pose, output, age, alpha); ring(pose, light, .5 + kick * .4, .035, skill.colour, alpha * .4f); }
                    else {
                        pose.translate(0, height * .6, 0); pose.mulPose(Axis.XP.rotationDegrees(65));
                        arc(pose, light, .7 + kick * .6, .22, age * .09, 2.7, skill.colour, alpha * .75f);
                        arc(pose, light, .7 + kick * .6, .04, age * .09, 2.7, 0xE2FAFF, alpha);
                    }
                }
                case ARCANE_EDGE -> {
                    pose.translate(0, .09, 0); seal(pose, light, .5 + kick * scale, age * .035, skill.colour, alpha);
                    pose.translate(0, height * .5, 0); pose.mulPose(Axis.XP.rotationDegrees(90));
                    ring(pose, light, .2 + kick * 1.2, .065, 0xF6DEFF, alpha);
                }
                case HUNTERS_PATIENCE -> {
                    pose.translate(0, height * .6, 0); pose.mulPose(Axis.XP.rotationDegrees(90));
                    for (int i = 0; i < 4; i++) arc(pose, light, .45 + .2 * (1 - bloom) + .025 * breath, .035, i * Math.PI / 2 + age * .035, .5, skill.colour, alpha);
                    if (cue.stage() == 1) { crossCut(pose, light, .8, 0xEDFFF7, alpha); pose.mulPose(Axis.XP.rotationDegrees(-90)); spear(pose, light, 1.3, skill.colour, alpha); }
                }
                case STORM_STEP -> {
                    pose.translate(0, .15, 0);
                    for (int i = 0; i < 3; i++) { pose.pushPose(); pose.translate(0, i * .28 + kick * .45, 0);
                        arc(pose, light, .4 + kick * 1.3, .06, age * .22 + i * 2, 2.5, skill.colour, alpha); pose.popPose(); }
                }
                case EXECUTIONERS_OATH -> {
                    pose.translate(0, height * .6, 0);
                    pose.mulPose(Axis.ZP.rotationDegrees(-18 + 30 * kick));
                    crossCut(pose, solid, .65 + .65 * bloom, 0x420D1C, alpha * .8f);
                    crossCut(pose, light, .55 + .60 * bloom, skill.colour, alpha);
                    pose.translate(0, 1.4 * (1 - kick), 0); spear(pose, light, .9, 0xFFE4E8, alpha);
                }
                case BLOOD_TITHE -> {
                    for (int i = 0; i < 3; i++) { pose.pushPose(); pose.translate(0, .25 + t * 1.5 + i * .15, 0);
                        arc(pose, light, .55 - t * .25, .06, age * .18 + i * 2.1, 1.7, skill.colour, alpha); pose.popPose(); }
                    pose.translate(0, height * .6, 0); diamond(pose, solid, .16, .3, skill.colour, alpha * .5f);
                }
                case FROSTBIND -> {
                    ring(pose, light, .5 + kick * .5, .06, skill.colour, alpha);
                    for (int i = 0; i < 6; i++) { pose.pushPose(); pose.mulPose(Axis.YP.rotationDegrees(i * 60 + age * .65f));
                        float growth = Math.min(1, Math.max(0, (age - i * .65f) / 7));
                        pose.translate(0, .25 * growth + release * .3, .6 + release * .15); pose.mulPose(Axis.ZP.rotationDegrees(15 + release * 18));
                        crystal(pose, solid, .16, .2 + .8 * growth, 0x6AABDA, alpha * .7f);
                        crystal(pose, light, .06, .25 + .85 * growth, 0xDEFFFF, alpha); pose.popPose(); }
                }
                case GRAVITY_WELL -> {
                    pose.translate(0, .15, 0); seal(pose, solid, scale * (1 - kick) + .3, -age * .08, 0x321352, alpha * .5f);
                    for (int i = 0; i < 3; i++) arc(pose, light, .25 + scale * (1 - kick), .045, age * .20 + i * 2.1, 1.8, skill.colour, alpha);
                    pose.translate(0, height * .5 + .12 * breath, 0);
                    pose.mulPose(Axis.YP.rotationDegrees(age * 5)); crystal(pose, light, .15, .4 + .04 * breath, skill.colour, alpha);
                }
                case SUNFIRE_BRAND -> {
                    pose.translate(0, height * .6, 0); pose.mulPose(Axis.XP.rotationDegrees(90));
                    ring(pose, light, .4 + .025 * breath, .065, 0xFFE7A1, alpha);
                    for (int i = 0; i < 8; i++) { pose.pushPose(); pose.mulPose(Axis.YP.rotationDegrees(i * 45 + age * 2));
                        pose.translate(0, 0, .58 + .12 * Math.sin(age * .2)); spear(pose, light, .22, skill.colour, alpha); pose.popPose(); }
                }
                case ECHO_STRIKE -> {
                    pose.translate(0, height * .6, 0);
                    for (int i = 0; i < 3; i++) { pose.pushPose(); float echoAge = age - i * 2.5f;
                        float echoAlpha = CombatSkillVisuals.alpha(echoAge, life - i * 2.5f);
                        pose.translate((i - 1) * .25 + kick * .22, kick * .12, (i - 1) * .18);
                        pose.mulPose(Axis.ZP.rotationDegrees(25 + i * 20)); pose.mulPose(Axis.XP.rotationDegrees(70));
                        arc(pose, light, .8 + kick * .35, .16, echoAge * .18 + i * .5, 2.4, skill.colour, echoAlpha * (1 - i * .2f)); pose.popPose(); }
                }
            }
            pose.popPose();
        }
        void ghost(PoseStack pose, MultiBufferSource output, float age, float alpha) {
            if (age >= 20) return;
            float fade = alpha * .48f * (1 - age / 20);
            if (epicGhost != null) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(cue.yaw() + 180));
                pose.mulPoseMatrix(OpenMatrix4f.exportToMojangMatrix(epicGhost.getModelMatrix()));
                epicGhost.renderTextured(pose, output, RenderType::entityTranslucent,
                        Mesh.DrawingFunction.NEW_ENTITY, 0xF000F0, .40f, .85f, 1, fade);
                epicGhost.renderItems(pose, output, RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS),
                        Mesh.DrawingFunction.NEW_ENTITY, 0xF000F0, fade);
                pose.popPose();
                return;
            }
            if (ghost == null) return;
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(cue.yaw() + 180 - ghostYaw));
            pose.scale(-1, -1, 1); pose.translate(0, -1.501, 0);
            ghost.renderToBuffer(pose, output.getBuffer(RenderType.entityTranslucent(skin)), 0xF000F0,
                    OverlayTexture.NO_OVERLAY, .40f, .85f, 1, fade);
            pose.popPose();
        }
    }

    private static void myriadScreen(RenderLevelStageEvent event, float age, Vec3 camera, Vec3 anchor) {
        float freeze = SwordConvergenceTimeline.FREEZE, release = SwordConvergenceTimeline.RELEASE;
        float life = SwordConvergenceTimeline.LIFE;
        float distortion, chroma, vignette, dim, bloom, rays, streak;
        if (age < freeze) {
            float u = age / freeze;
            distortion = 0.06f + 0.14f * u;
            chroma = 0.04f + 0.06f * u;
            vignette = 0.10f + 0.40f * u;
            dim = 0.05f + 0.22f * u;
            bloom = 0.55f + 0.40f * u;
            rays = 0.25f + 0.60f * u;
            streak = 0.30f + 0.25f * u;
        } else if (age < release) {
            distortion = 0.22f; chroma = 0.10f; vignette = 0.55f; dim = 0.30f;
            bloom = 1.0f; rays = 0.95f; streak = 0.55f;
        } else {
            float k = Math.max(0, 1 - (age - release) / (life - release));
            distortion = 0.55f * k; chroma = 0.40f * k; vignette = 0.55f * k; dim = 0.35f * k;
            bloom = 1.0f * k; rays = 0.90f * k; streak = 0.60f * k;
        }
        float flash = age >= release && age < release + 4f ? 1 - (age - release) / 4f : 0;
        for (int impact : SwordConvergenceTimeline.IMPACTS) {
            float d = Math.abs(age - impact);
            if (d < 7f) flash = Math.max(flash, 1 - d / 7f);
        }
        CastPostFx.request(anchor, camera, event.getPoseStack().last().pose(), event.getProjectionMatrix(),
                distortion, chroma, vignette, flash, 7f, 0.62f, 0.86f, 1.0f, dim);
        CastPostFx.glow(bloom, rays, streak);
    }

    private static void feelWanjian(Effect fx, float age, Vec3 camera, Vec3 anchor) {
        double reach = Math.max(48, fx.cue.radius() + 24);
        if (!fx.punched && age >= WanJianTimeline.COMMAND) {
            fx.punched = true;
            CameraQuake.lensPunch(.45f);
            CameraQuake.impulse(2.4f, camera.distanceTo(anchor), reach);
        }
        while (fx.pulses < WanJianTimeline.RAIN_PULSES && age >= WanJianTimeline.damageTick(fx.pulses)) {
            int pulse = fx.pulses++;
            CameraQuake.impulse(pulse == WanJianTimeline.RAIN_PULSES - 1 ? 1.5f : .42f,
                    camera.distanceTo(anchor), reach);
        }
    }

    private static void wanjianScreen(RenderLevelStageEvent event, float age, Vec3 camera, Vec3 anchor) {
        float life = WanJianTimeline.LIFE;
        float gather = Mth.clamp(age / WanJianTimeline.HOMAGE_START, 0f, 1f);
        float fall = Mth.clamp((life - age) / 16f, 0f, 1f);
        float bow = Mth.clamp((age - WanJianTimeline.HOMAGE_START) / 22f, 0f, 1f);
        float storm = Mth.clamp((age - WanJianTimeline.COMMAND) / 10f, 0f, 1f);
        float k = Math.max(gather * .5f, storm) * fall;
        if (k <= .001f) {
            return;
        }
        float flash = 0;
        if (age >= WanJianTimeline.COMMAND && age < WanJianTimeline.COMMAND + 5f) {
            flash = 1 - (age - WanJianTimeline.COMMAND) / 5f;
        }
        float last = WanJianTimeline.damageTick(WanJianTimeline.RAIN_PULSES - 1);
        if (age >= last && age < last + 7f) {
            flash = Math.max(flash, 1 - (age - last) / 7f);
        }
        float distortion = .04f * k + .05f * storm + .03f * (1 - bow) * gather;
        CastPostFx.request(anchor, camera, event.getPoseStack().last().pose(), event.getProjectionMatrix(),
                distortion, .02f * k, .34f * k + .14f * gather, flash * .85f, 12f, .96f, .80f, .48f, .42f * storm);
        CastPostFx.glow(.45f * k + .35f * gather * (1 - bow), .18f * storm, .30f * storm);
    }

    private static void feelDharmaStrike(Effect fx, Vec3 camera, Vec3 anchor) {
        if (fx.punched) return;
        fx.punched = true;
        CameraQuake.impulse(2.2f, camera.distanceTo(anchor), 32);
        CameraQuake.lensPunch(.3f);
    }

    private static void dharmaScreen(RenderLevelStageEvent event, float age, int life, Vec3 camera, Vec3 anchor) {
        float k = (float) (Math.min(1, age / 20f)
                * Math.max(0, Math.min(1, (life - age) / 20f)));
        if (k <= 0.001f) return;
        CastPostFx.request(anchor, camera, event.getPoseStack().last().pose(), event.getProjectionMatrix(),
                0.05f * k, 0.02f * k, 0.42f * k, 0f, 20f, 0.55f, 0.44f, 0.20f, 0.58f * k);
        CastPostFx.glow(0.28f * k, 0.08f * k, 0.12f * k);
    }

    private static void feelMyriad(Effect fx, float age, Vec3 camera, Vec3 anchor) {
        if (!fx.punched && age >= SwordConvergenceTimeline.RELEASE) {
            fx.punched = true;
            CameraQuake.lensPunch(.5f);
        }
        while (fx.volleys < SwordConvergenceTimeline.IMPACTS.length
                && age >= SwordConvergenceTimeline.IMPACTS[fx.volleys]) {
            int volley = fx.volleys++;
            CameraQuake.impulse(1.1f + volley * .6f, camera.distanceTo(anchor), 28);
        }
    }

    private static void ring(PoseStack p, VertexConsumer v, double radius, double width, int c, float a) { arc(p, v, radius, width, 0, Math.PI * 2, c, a); }
    private static void arc(PoseStack p, VertexConsumer v, double radius, double width, double start, double span, int c, float a) {
        int steps = Math.max(8, (int) (Math.abs(span) * 8));
        for (int i = 0; i < steps; i++) {
            double u = start + span * i / steps, w = start + span * (i + 1) / steps;
            double taperA = Math.abs(span) < 6 ? Math.sin(Math.PI * i / steps) : 1;
            double taperB = Math.abs(span) < 6 ? Math.sin(Math.PI * (i + 1) / steps) : 1;
            quad(p, v, Math.cos(u) * (radius - width * taperA), 0, Math.sin(u) * (radius - width * taperA),
                    Math.cos(u) * radius, 0, Math.sin(u) * radius,
                    Math.cos(w) * radius, 0, Math.sin(w) * radius,
                    Math.cos(w) * (radius - width * taperB), 0, Math.sin(w) * (radius - width * taperB), c, a);
        }
    }
    private static void seal(PoseStack p, VertexConsumer v, double r, double rotation, int c, float a) {
        ring(p, v, r, .05, c, a); ring(p, v, r * .78, .02, c, a * .6f);
        for (int i = 0; i < 8; i++) { p.pushPose(); p.mulPose(Axis.YP.rotation((float) (rotation + i * Math.PI / 4)));
            quad(p, v, -.025, 0, r * .83, .025, 0, r * .83, .025, 0, r * 1.12, -.025, 0, r * 1.12, c, a);
            p.translate(0, .008, r * .6); p.mulPose(Axis.XP.rotationDegrees(90));
            diamond(p,v,r*.06,r*.12,c,a*.7f); p.popPose(); }
    }
    private static void plate(PoseStack p, VertexConsumer v, double x, double y, int c, float a) {
        quad(p, v, -x, -y, 0, x, -y, 0, x, y, 0, -x, y, 0, c, a);
    }
    private static void diamond(PoseStack p, VertexConsumer v, double x, double y, int c, float a) {
        double[][] points = {{0,y},{x,0},{0,-y},{-x,0}};
        for (int i = 0; i < 4; i++) { double[] u = points[i], w = points[(i+1)%4];
            quad(p,v,u[0],u[1],0,w[0],w[1],0,w[0]*.85,w[1]*.85,0,u[0]*.85,u[1]*.85,0,c,a); }
    }
    private static void crystal(PoseStack p, VertexConsumer v, double r, double h, int c, float a) {
        for (int i=0;i<4;i++) { double u=i*Math.PI/2,w=(i+1)*Math.PI/2;
            quad(p,v,0,h,0,Math.cos(u)*r,0,Math.sin(u)*r,0,-h*.25,0,Math.cos(w)*r,0,Math.sin(w)*r,c,a); }
    }
    private static void crossCut(PoseStack p, VertexConsumer v, double r, int c, float a) {
        for (int i=0;i<2;i++) { p.pushPose(); p.mulPose(Axis.ZP.rotationDegrees(i==0?40:-40));
            quad(p,v,-r,-.025,0,r,-.025,0,r*.8,.045,0,-r*.8,.045,0,c,a); p.popPose(); }
    }
    private static void spear(PoseStack p, VertexConsumer v, double h, int c, float a) {
        quad(p,v,0,h,0,.075,0,0,0,-h*.4,0,-.075,0,0,c,a);
    }
    private static void quad(PoseStack p, VertexConsumer v, double ax,double ay,double az,double bx,double by,double bz,
                             double cx,double cy,double cz,double dx,double dy,double dz,int c,float a) {
        vertex(p,v,ax,ay,az,c,a); vertex(p,v,bx,by,bz,c,a); vertex(p,v,cx,cy,cz,c,a); vertex(p,v,dx,dy,dz,c,a);
    }
    private static void vertex(PoseStack p, VertexConsumer v, double x,double y,double z,int c,float a) {
        Matrix4f matrix=p.last().pose();
        v.vertex(matrix,(float)x,(float)y,(float)z).color((c>>16)&255,(c>>8)&255,c&255,(int)(Math.max(0,Math.min(1,a))*255)).endVertex();
    }
}
