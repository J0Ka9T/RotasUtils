package net.schwarz.rotasutils.forge.epicfight;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.core.SwordConvergenceTimeline;
import yesman.epicfight.api.forgeevent.SkillBuildEvent;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.skill.*;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.damagesource.StunType;
import java.util.*;

public final class MyriadSwordsSkill extends Skill {
    public static final String ID="myriad_swords_return";
    private static final String READY="rotasutils.myriadSwordsReady";
    private final Map<UUID,Cast> casts=new HashMap<>();
    private final Set<UUID> initializing=new HashSet<>();
    private float damage=16, stamina=6;
    private int staggerTicks=20;
    private float staggerKnockback=.4f;
    private float aoeRadius=6f;
    private float aoeShare=.6f;
    private static final class Cast {
        final UUID target; final long born; final Vec3 origin; int pulse;
        Cast(LivingEntity target,long born){this.target=target.getUUID();this.born=born;this.origin=target.position();}
    }
    public MyriadSwordsSkill(SkillBuilder<? extends Skill> builder){
        super(builder);consumption=30;maxDuration=SwordConvergenceTimeline.LIFE;maxStackSize=1;
    }
    static void build(SkillBuildEvent.ModRegistryWorker worker){
        worker.build(ID,MyriadSwordsSkill::new,Skill.createIdentityBuilder()
            .setActivateType(ActivateType.DURATION).setResource(Resource.COOLDOWN));
    }
    @Override public void setParams(CompoundTag tag){
        super.setParams(tag);consumption=Math.max(10,Math.min(120,Float.isFinite(consumption)?consumption:30));
        maxDuration=SwordConvergenceTimeline.LIFE;maxStackSize=1;
        if(tag.contains("damage")){float n=tag.getFloat("damage");damage=Float.isFinite(n)?Math.max(1,Math.min(80,n)):16;}
        if(tag.contains("stamina")){float n=tag.getFloat("stamina");stamina=Float.isFinite(n)?Math.max(0,Math.min(12,n)):6;}
        if(tag.contains("stagger_ticks")){int n=tag.getInt("stagger_ticks");staggerTicks=Math.max(0,Math.min(200,n));}
        if(tag.contains("stagger_knockback")){float n=tag.getFloat("stagger_knockback");staggerKnockback=Float.isFinite(n)?Math.max(0,Math.min(2,n)):.4f;}
        if(tag.contains("aoe_radius")){float n=tag.getFloat("aoe_radius");aoeRadius=Float.isFinite(n)?Math.max(0,Math.min(16,n)):6f;}
        if(tag.contains("aoe_share")){float n=tag.getFloat("aoe_share");aoeShare=Float.isFinite(n)?Math.max(0,Math.min(2,n)):.6f;}
    }
    @Override public void onInitiate(SkillContainer c){
        super.onInitiate(c);
        if(!c.getExecutor().isLogicalClient()){
            UUID id=c.getExecutor().getOriginal().getUUID();casts.remove(id);initializing.add(id);
        }
    }
    @Override public boolean isExecutableState(PlayerPatch<?> p){
        return p.isEpicFightMode()&&super.isExecutableState(p)&&p.getOriginal().isAlive()
            &&p.getOriginal().getMainHandItem().getItem() instanceof SwordItem;
    }
    @Override public boolean checkExecuteCondition(SkillContainer c){
        var p=c.getExecutor();
        if(c.isActivated()||!p.hasStamina(p.getModifiedStaminaConsume(stamina)))return false;
        if(p.isLogicalClient())return true;
        var player=(ServerPlayer)p.getOriginal();
        if(casts.containsKey(player.getUUID())||player.level().getGameTime()<player.getPersistentData().getLong(READY))return false;
        if(aimTarget(player)==null){player.displayClientMessage(Component.translatable("skill.rotasutils.myriad_swords_return.no_target"),true);return false;}
        return true;
    }
    @Override public void executeOnServer(SkillContainer c,FriendlyByteBuf args){
        var patch=c.getServerExecutor();var player=patch.getOriginal();
        if(!isExecutableState(patch))return;
        if(c.isActivated()||!patch.hasStamina(patch.getModifiedStaminaConsume(stamina)))return;
        long now=player.level().getGameTime();
        if(casts.containsKey(player.getUUID())||now<player.getPersistentData().getLong(READY))return;
        LivingEntity target=aimTarget(player);
        if(target==null){player.displayClientMessage(Component.translatable("skill.rotasutils.myriad_swords_return.no_target"),true);return;}
        casts.put(player.getUUID(),new Cast(target,now));
        player.getPersistentData().putLong(READY,now+(long)(consumption*20));
        patch.setStamina(Math.max(0,patch.getStamina()-patch.getModifiedStaminaConsume(stamina)));
        setStackSynchronize(c,0);setConsumptionSynchronize(c,0);
        super.executeOnServer(c,args);
        patch.playAnimationSynchronized(Animations.SWORD_GUARD,.2f);
        if(player.level() instanceof ServerLevel level){
            Vec3 at=target.position().add(0,target.getBbHeight()*.5,0);
            level.playSound(null,target.blockPosition(),SoundEvents.BEACON_POWER_SELECT,SoundSource.PLAYERS,.7f,.8f);
        }
        CombatSkillEffects.emit(player,target,ID,0,1,SwordConvergenceTimeline.LIFE);
    }
    static LivingEntity aimTarget(ServerPlayer player){
        Vec3 start=player.getEyePosition(),end=start.add(player.getLookAngle().scale(28));
        var block=player.level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        end=block.getLocation();double nearest=start.distanceToSqr(end);LivingEntity result=null;
        for(var target:player.level().getEntitiesOfClass(LivingEntity.class,new AABB(start,end).inflate(1.2))){
            if(!legal(player,target)||!player.hasLineOfSight(target))continue;
            var hit=target.getBoundingBox().inflate(.35).clip(start,end);
            if(hit.isPresent()&&start.distanceToSqr(hit.get())<nearest){nearest=start.distanceToSqr(hit.get());result=target;}
        }
        return result;
    }
    static boolean legal(ServerPlayer player,LivingEntity target){return target.isAlive()&&AdditionalEpicFightSkills.legalTarget(player,target);}

    static LivingEntity visibleTarget(ServerPlayer player,LivingEntity target){
        if(player.hasLineOfSight(target))return target;
        LivingEntity best=null;double nearest=Double.MAX_VALUE;
        for(var other:player.level().getEntitiesOfClass(LivingEntity.class,target.getBoundingBox().inflate(3.5))){
            if(other==target||!legal(player,other)||!player.hasLineOfSight(other)||!hostile(other,player))continue;
            double distance=other.distanceToSqr(target);
            if(distance<nearest){nearest=distance;best=other;}
        }
        return best;
    }

    static LivingEntity nearestAt(ServerPlayer player,Vec3 at){
        LivingEntity best=null;double nearest=3.5*3.5;
        for(var other:player.level().getEntitiesOfClass(LivingEntity.class,new AABB(at,at).inflate(3.5))){
            if(!legal(player,other)||!hostile(other,player))continue;
            double d=other.distanceToSqr(at);
            if(d<=nearest){nearest=d;best=other;}
        }
        return best;
    }

    static boolean hostile(LivingEntity entity,ServerPlayer player){
        return entity instanceof Enemy||entity instanceof Player||entity instanceof Mob mob&&mob.getTarget()==player;
    }

    private static void crown(LivingEntity victim,int volley){
        if(!(victim.level() instanceof ServerLevel level))return;
        boolean finale=volley==SwordConvergenceTimeline.IMPACTS.length-1;
        level.playSound(null,victim.blockPosition(),SoundEvents.TRIDENT_HIT,SoundSource.PLAYERS,1f,.7f+volley*.05f);
        if(finale){
            level.playSound(null,victim.blockPosition(),SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,1.2f,.75f);
            level.playSound(null,victim.blockPosition(),SoundEvents.TRIDENT_THUNDER,SoundSource.PLAYERS,.9f,.7f);
        }
    }

    private void stagger(ServerPlayer player,LivingEntity victim){
        if(staggerTicks>0)victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,staggerTicks,1,false,true,true));
        Vec3 away=victim.position().subtract(player.position());
        if(staggerKnockback>0&&away.lengthSqr()>1e-6)victim.knockback(staggerKnockback,away.x,away.z);
        if(victim.level() instanceof ServerLevel level){
            level.playSound(null,victim.blockPosition(),SoundEvents.PLAYER_ATTACK_CRIT,SoundSource.PLAYERS,.9f,.7f);
        }
    }
    @Override public void updateContainer(SkillContainer c){
        if(!c.getExecutor().isLogicalClient()&&initializing.remove(c.getExecutor().getOriginal().getUUID())){
            var player=c.getServerExecutor().getOriginal();
            long remaining=Math.max(0,player.getPersistentData().getLong(READY)-player.level().getGameTime());
            setConsumptionSynchronize(c,remaining==0?0:Math.max(0,consumption-remaining/20f));
            setStackSynchronize(c,remaining==0?1:0);
        }
        super.updateContainer(c);if(c.getExecutor().isLogicalClient())return;
        var player=c.getServerExecutor().getOriginal();var cast=casts.get(player.getUUID());if(cast==null)return;
        int age=(int)(player.level().getGameTime()-cast.born);
        var entity=player.serverLevel().getEntity(cast.target);
        if(!player.isAlive()||player.distanceToSqr(cast.origin)>48*48){stop(c);return;}
        LivingEntity target=entity instanceof LivingEntity t&&legal(player,t)&&t.position().distanceToSqr(cast.origin)<=3.5*3.5
                ?t:nearestAt(player,cast.origin);

while(cast.pulse<SwordConvergenceTimeline.DAMAGE_PULSES&&age>=SwordConvergenceTimeline.damageTick(cast.pulse)){
            int index=cast.pulse++;
            LivingEntity victim=target==null?null:visibleTarget(player,target);
            if(victim==null)continue;
            var source=c.getServerExecutor().getDamageSource(Animations.SWORD_AUTO1,InteractionHand.MAIN_HAND).setStunType(StunType.NONE)
                .addRuntimeTag(net.minecraft.tags.DamageTypeTags.BYPASSES_COOLDOWN);
            CombatSkillEffects.emit(player,victim,ID,index==SwordConvergenceTimeline.DAMAGE_PULSES-1?HitVfx.HEAVY:HitVfx.HIT,1,HitVfx.HIT_LIFE+6);
            victim.hurt(source,damage);
            if(index==SwordConvergenceTimeline.DAMAGE_PULSES-1&&victim==target)stagger(player,victim);
            int crown=SwordConvergenceTimeline.impactAt(SwordConvergenceTimeline.damageTick(index));
            if(crown>=0){
                boolean finale=crown==SwordConvergenceTimeline.IMPACTS.length-1;
                double reach=aoeRadius*(finale?1.7:1.0);
                float splash=damage*aoeShare*(1+crown*.15f);
                for(var other:player.level().getEntitiesOfClass(LivingEntity.class,victim.getBoundingBox().inflate(reach))){
                    if(other==victim||other==target||!legal(player,other)||!hostile(other,player)
                            ||!player.hasLineOfSight(other))continue;
                    var aoe=c.getServerExecutor().getDamageSource(Animations.SWORD_AUTO1,InteractionHand.MAIN_HAND)
                        .setStunType(StunType.NONE).addRuntimeTag(net.minecraft.tags.DamageTypeTags.BYPASSES_COOLDOWN);
                    other.hurt(aoe,splash);
                    CombatSkillEffects.emit(player,other,ID,finale?HitVfx.HEAVY:HitVfx.HIT,1,HitVfx.HIT_LIFE+6);
                    if(finale)stagger(player,other);
                }
                crown(victim,crown);
            }
        }
        if(age>=SwordConvergenceTimeline.LIFE)stop(c);
    }
    static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){
        var skill=yesman.epicfight.api.data.reloader.SkillManager.getSkill("rotasutils:"+ID);
        if(skill instanceof MyriadSwordsSkill swords){
            UUID id=event.getEntity().getUUID();swords.casts.remove(id);swords.initializing.remove(id);
            CombatSkillEffects.clear(event.getEntity(),ID);
        }
    }
    private void stop(SkillContainer c){
        var player=c.getServerExecutor().getOriginal();casts.remove(player.getUUID());initializing.remove(player.getUUID());
        CombatSkillEffects.clear(player,ID);c.deactivate();
    }
    @Override public void cancelOnServer(SkillContainer c,FriendlyByteBuf args){stop(c);super.cancelOnServer(c,args);}
    @Override public void onRemoved(SkillContainer c){if(!c.getExecutor().isLogicalClient())stop(c);super.onRemoved(c);}
    @Override public List<Object> getTooltipArgsOfScreen(List<Object> list){list.add(stamina);list.add(consumption);list.add(damage*SwordConvergenceTimeline.DAMAGE_PULSES);return list;}
}
