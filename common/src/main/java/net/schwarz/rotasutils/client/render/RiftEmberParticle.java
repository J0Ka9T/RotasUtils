package net.schwarz.rotasutils.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public final class RiftEmberParticle extends TextureSheetParticle {
    private final float swayPhase;
    private final float swaySpeed;
    private final float baseSize;
    private final float flickerPhase;

    RiftEmberParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = 0.0012f + random.nextFloat() * 0.0012f;
        this.friction = 0.985f;
        this.lifetime = 90 + random.nextInt(80);
        this.baseSize = 0.05f + random.nextFloat() * 0.09f;
        this.quadSize = baseSize;
        this.swayPhase = random.nextFloat() * Mth.TWO_PI;
        this.swaySpeed = 0.05f + random.nextFloat() * 0.07f;
        this.flickerPhase = random.nextFloat() * Mth.TWO_PI;
        this.hasPhysics = true;
        pickSprite(sprites);
        tint(0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        float swirl = Mth.sin(age * swaySpeed + swayPhase);
        xd += swirl * 0.0022;
        zd += Mth.cos(age * swaySpeed * 0.8f + swayPhase) * 0.0022;
        if (onGround && age < lifetime - 12) {
            age = lifetime - 12;
        }
        tint(age / (float) lifetime);
    }

    private void tint(float life) {
        float cool = Mth.clamp(life * 1.6f, 0f, 1f);
        float late = Mth.clamp((life - 0.45f) / 0.55f, 0f, 1f);
        float red = Mth.lerp(late, Mth.lerp(cool, 0.85f, 0.32f), 0.62f);
        float green = Mth.lerp(late, Mth.lerp(cool, 0.96f, 0.55f), 0.36f);
        setColor(EldritchSkyPalette.r(red, green, 1.0f), EldritchSkyPalette.g(red, green, 1.0f),
                EldritchSkyPalette.b(red, green, 1.0f));
        float flicker = 0.8f + 0.2f * Mth.sin(age * 0.9f + flickerPhase);
        float fade = 1f - Mth.clamp((life - 0.7f) / 0.3f, 0f, 1f);
        float appear = Mth.clamp(age / 6f, 0f, 1f);
        setAlpha(flicker * fade * appear);
        quadSize = baseSize * (1f - 0.45f * life);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new RiftEmberParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
