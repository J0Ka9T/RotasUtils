package net.schwarz.rotasutils.npc;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.job.JobSlot;
import net.schwarz.rotasutils.util.Nbt;

public record NpcServiceDef(Type type, String jobId, String targetId, JobSlot slot, int level) {
    public enum Type { TRAINER, JOB_MASTER, RESPEC, STAT_TRAINER, CRAFTER }
    public NpcServiceDef {
        type=type==null?Type.TRAINER:type; jobId=clean(jobId,128); targetId=clean(targetId,128); slot=slot==null?JobSlot.MAIN:slot; level=Math.max(0,Math.min(10000,level));
    }
    public NpcServiceDef(Type type,String jobId,String targetId,JobSlot slot) { this(type,jobId,targetId,slot,0); }
    private static String clean(String value,int max) { value=value==null?"":value.trim(); if(value.length()>max) throw new IllegalArgumentException("NPC service id too long"); return value; }
    public CompoundTag save() { CompoundTag tag=new CompoundTag(); tag.putString("type",type.name()); tag.putString("job",jobId); tag.putString("target",targetId); tag.putString("slot",slot.name()); tag.putInt("level",level); return tag; }
    public static NpcServiceDef load(CompoundTag tag) { return new NpcServiceDef(Nbt.readEnum(tag,"type",Type.class,Type.TRAINER),tag.getString("job"),tag.getString("target"),Nbt.readEnum(tag,"slot",JobSlot.class,JobSlot.MAIN),tag.getInt("level")); }
}
