package net.schwarz.rotasutils.ability;

import dev.architectury.platform.Platform;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class StargunDissolver {
    public static final class Settings {
        public boolean destroyBlocks = true;
        public boolean affectEntities = true;
        public boolean affectPlayers = false;
        public double safeRadius = 4.5;
        public int budgetMillis = 12;
        public boolean erodeEdge = true;
    }

    public static final TagKey<EntityType<?>> IMMUNE = TagKey.create(Registries.ENTITY_TYPE, Rotasutils.id("stargun_immune"));
    private static final int UPDATE = 2 | 16 | 32;

    public static volatile Settings settings = load();

    private static Settings load() {
        try {
            Path file = Platform.getConfigFolder().resolve("rotasutils").resolve("stargun.json");
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    Settings read = new com.google.gson.Gson().fromJson(reader, Settings.class);
                    if (read != null) {
                        return read;
                    }
                }
            }
        } catch (Exception bad) {
            Rotasutils.LOG.warn("Could not read stargun.json, using defaults: {}", bad.toString());
        }
        return new Settings();
    }

    private final ServerLevel level;
    private final BlockPos centre;
    private final Vec3 centreVec;
    private final ServerPlayer caster;
    private final Vec3 casterFeet;
    private final long seed;
    private final Settings rules = settings;

    private long scanned;
    private final Int2ObjectOpenHashMap<LongArrayList> bins = new Int2ObjectOpenHashMap<>();
    private int nextBin;
    private int cursor;
    private final List<Mob> held = new ArrayList<>();
    private long removed;
    private long eroded = -1;

    public StargunDissolver(ServerLevel level, BlockPos centre, ServerPlayer caster, long seed) {
        this.level = level;
        this.centre = centre;
        this.centreVec = Vec3.atCenterOf(centre);
        this.caster = caster;
        this.casterFeet = caster.position();
        this.seed = seed;
    }

    public long removed() {
        return removed;
    }

    public void tick(double t) {
        if (t < StargunTimings.IMPACT) {
            return;
        }
        if (rules.destroyBlocks) {
            scan(t);
            remove(t, rules.budgetMillis * 1_000_000L);
        }
        if (rules.destroyBlocks && rules.erodeEdge) {
            erode(t);
        }
        if ((level.getGameTime() & 3) == 0) {
            entities(t);
        }
    }

    private boolean safe(BlockPos pos) {
        double dx = pos.getX() + 0.5 - casterFeet.x, dz = pos.getZ() + 0.5 - casterFeet.z;
        return dx * dx + dz * dz < rules.safeRadius * rules.safeRadius && pos.getY() >= casterFeet.y - 3 && pos.getY() <= casterFeet.y + 4;
    }

    private boolean takeable(BlockState state, BlockPos pos) {
        return !state.isAir() && state.getDestroySpeed(level, pos) >= 0;
    }

    private void scan(double t) {
        double reach = Math.min(StargunTimings.RADIUS + StargunTimings.BAND, StargunTimings.front(t) + StargunTimings.BAND);
        long to = (long) Math.floor(reach * reach) + 1;
        if (to <= scanned) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        StargunShell.forEach(scanned, to, (dx, dy, dz) -> {
            pos.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
            if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
                return;
            }
            if (!takeable(level.getBlockState(pos), pos) || safe(pos)) {
                return;
            }
            int bin = (int) (StargunShell.removeRadius(dx, dy, dz, seed) * 8);
            bins.computeIfAbsent(bin, k -> new LongArrayList()).add(pos.asLong());
        });
        scanned = to;
    }

    private void remove(double t, long nanos) {
        int last = (int) (StargunTimings.front(t) * 8);
        long deadline = System.nanoTime() + nanos;
        while (nextBin <= last) {
            LongArrayList list = bins.get(nextBin);
            if (list != null) {
                while (cursor < list.size()) {
                    take(BlockPos.of(list.getLong(cursor++)));
                    if ((cursor & 63) == 0 && System.nanoTime() > deadline) {
                        return;
                    }
                }
                bins.remove(nextBin);
            }
            nextBin++;
            cursor = 0;
        }
    }

    private static BlockState crackedForm(BlockState s) {
        if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM)) {
            return Blocks.COARSE_DIRT.defaultBlockState();
        } else if (s.is(Blocks.STONE)) {
            return Blocks.COBBLESTONE.defaultBlockState();
        } else if (s.is(Blocks.COBBLESTONE) || s.is(Blocks.ANDESITE) || s.is(Blocks.DIORITE) || s.is(Blocks.GRANITE)) {
            return Blocks.GRAVEL.defaultBlockState();
        } else if (s.is(Blocks.STONE_BRICKS)) {
            return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        } else if (s.is(Blocks.DEEPSLATE_BRICKS)) {
            return Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
        } else if (s.is(Blocks.DEEPSLATE)) {
            return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        } else if (s.is(Blocks.SANDSTONE)) {
            return Blocks.SAND.defaultBlockState();
        }
        return null;
    }

    private void erode(double t) {
        double open = Mth.clamp((t - (StargunTimings.FRONT_END - 4.0)) / 6.0, 0, 1);
        if (open <= 0) {
            return;
        }
        if (eroded < 0) {
            eroded = (long) Math.floor((StargunTimings.RADIUS - StargunTimings.BAND) * (StargunTimings.RADIUS - StargunTimings.BAND));
        }
        double reach = StargunTimings.RADIUS + StargunTimings.ERODE_BAND * open;
        long to = (long) Math.floor(reach * reach) + 1;
        if (to <= eroded) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(), near = new BlockPos.MutableBlockPos();
        StargunShell.forEach(eroded, to, (dx, dy, dz) -> {
            double dist = Math.sqrt((double) dx * dx + dy * dy + dz * dz);
            if (StargunShell.removeRadius(dx, dy, dz, seed) <= StargunTimings.RADIUS) {
                return;
            }
            double chance = StargunTimings.erodeChance(dist);
            if (chance <= 0 || StargunShell.hash01(dx, dy, dz, seed ^ 0x5DEECE66DL) > chance) {
                return;
            }
            pos.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
            if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos) || safe(pos)) {
                return;
            }
            BlockState state = level.getBlockState(pos);
            if (!takeable(state, pos)) {
                return;
            }
            boolean exposed = false;
            for (Direction d : Direction.values()) {
                near.set(pos.getX() + d.getStepX(), pos.getY() + d.getStepY(), pos.getZ() + d.getStepZ());
                exposed |= level.getBlockState(near).isAir();
            }
            if (!exposed) {
                return;
            }
            double roll = StargunShell.hash01(dx, dy, dz, seed ^ 0xBB67AE85L);
            BlockState cracked = crackedForm(state);
            if (roll < 0.45 || cracked == null) {
                if (roll < 0.45 || StargunShell.hash01(dx, dy, dz, seed ^ 0x1F83D9ABL) < 0.35) {
                    take(pos);
                }
            } else {
                level.setBlock(pos, cracked, UPDATE);
            }
        });
        eroded = to;
    }

    private void take(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!takeable(state, pos)) {
            return;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof Clearable container) {
            container.clearContent();
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE);
        removed++;
    }

    private void entities(double t) {
        if (!rules.affectEntities && !rules.affectPlayers) {
            return;
        }
        double reach = StargunTimings.front(t);
        if (reach <= 0) {
            return;
        }
        AABB box = new AABB(centreVec, centreVec).inflate(Math.min(StargunTimings.RADIUS, reach) + 2);
        for (Entity entity : level.getEntities((Entity) null, box, e -> e != caster && !e.isRemoved() && !e.getType().is(IMMUNE))) {
            boolean player = entity instanceof Player;
            if (player ? !rules.affectPlayers : !rules.affectEntities) {
                continue;
            }
            double progress = StargunTimings.entityProgress(entity.getBoundingBox().getCenter().distanceTo(centreVec), t);
            if (progress <= 0) {
                continue;
            }
            if (progress >= 1) {
                if (player) {
                    entity.setInvulnerable(false);
                    entity.kill();
                } else {
                    entity.discard();
                }
                continue;
            }
            if (entity instanceof Mob mob && !mob.isNoAi()) {
                mob.setNoAi(true);
                mob.setInvulnerable(true);
                held.add(mob);
            }
        }
    }

    public void finish(boolean completed) {
        if (completed && rules.destroyBlocks) {
            remove(StargunTimings.END, 80_000_000L);
        }
        for (Mob mob : held) {
            if (!mob.isRemoved()) {
                mob.setNoAi(false);
                mob.setInvulnerable(false);
            }
        }
        held.clear();
        bins.clear();
    }
}
