package net.schwarz.rotasutils.npc;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.util.Nbt;
import java.util.Map;

public record NpcBuild(LevelMode levelMode, int level, double difficultyMultiplier, String faction,
                       int leashBlocks, String equipmentProfile, Map<String,Integer> statAllocations,
                       Map<String,Integer> skillRanks) {
    public static final int ENTRY_LIMIT=64;
    public enum LevelMode { FIXED, ZONE, PLAYER_SCALED }
    public NpcBuild {
        levelMode=levelMode==null?LevelMode.FIXED:levelMode;
        if(level<1||level>10000) throw new IllegalArgumentException("NPC build level outside 1..10000");
        if(!Double.isFinite(difficultyMultiplier)||difficultyMultiplier<0||difficultyMultiplier>100) throw new IllegalArgumentException("NPC difficulty outside 0..100");
        if(leashBlocks<0||leashBlocks>1024) throw new IllegalArgumentException("NPC leash outside 0..1024");
        faction=faction==null?"":faction; equipmentProfile=equipmentProfile==null?"":equipmentProfile;
        statAllocations=bounded(statAllocations,"stat"); skillRanks=bounded(skillRanks,"skill");
    }
    private static Map<String,Integer> bounded(Map<String,Integer> values,String label) {
        values=values==null?Map.of():Map.copyOf(values); if(values.size()>ENTRY_LIMIT) throw new IllegalArgumentException("NPC build "+label+" count exceeds "+ENTRY_LIMIT);
        values.forEach((id,value)->{if(id==null||id.isBlank()||value==null||value<0||value>10000) throw new IllegalArgumentException("Invalid NPC build "+label);}); return values;
    }
    public CompoundTag save() { CompoundTag tag=new CompoundTag(); tag.putString("level_mode",levelMode.name()); tag.putInt("level",level); tag.putDouble("difficulty",difficultyMultiplier); tag.putString("faction",faction); tag.putInt("leash",leashBlocks); tag.putString("equipment",equipmentProfile); tag.put("stats",Nbt.saveStringIntMap(statAllocations)); tag.put("skills",Nbt.saveStringIntMap(skillRanks)); return tag; }
    public static NpcBuild load(CompoundTag tag) { return new NpcBuild(Nbt.readEnum(tag,"level_mode",LevelMode.class,LevelMode.FIXED),tag.contains("level")?tag.getInt("level"):1,tag.contains("difficulty")?tag.getDouble("difficulty"):1,tag.getString("faction"),tag.getInt("leash"),tag.getString("equipment"),Nbt.loadStringIntMap(tag,"stats"),Nbt.loadStringIntMap(tag,"skills")); }
}
