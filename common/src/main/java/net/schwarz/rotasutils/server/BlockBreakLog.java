package net.schwarz.rotasutils.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.schwarz.rotasutils.Rotasutils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class BlockBreakLog {
    private static final int MAX = 200_000;
    private static final int SENT = 500;
    private static final int KEEP_DAYS = 30;
    private static final ArrayDeque<Entry> ENTRIES = new ArrayDeque<>();
    private static final Map<String, String> POOL = new HashMap<>();
    private static final ConcurrentLinkedQueue<String> PENDING = new ConcurrentLinkedQueue<>();
    private static ScheduledExecutorService writer;
    private static Path dir;

    private static final java.util.Set<java.util.UUID> INSPECTING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private record Entry(long time, String player, String lower, String block, String dimension, int x, int y, int z, boolean placed) {
        String line() {
            return time + "\t" + player + "\t" + block + "\t" + dimension + "\t" + x + "\t" + y + "\t" + z
                    + "\t" + (placed ? "P" : "B");
        }
    }

    public record Query(String text, String mode, int hours, int radius, String dimension, BlockPos center) {
    }

    private BlockBreakLog() {
    }

    public static void start(MinecraftServer server) {
        dir = server.getWorldPath(LevelResource.ROOT).resolve("rotasutils").resolve("blocklog");
        synchronized (BlockBreakLog.class) {
            ENTRIES.clear();
            POOL.clear();
        }
        writer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "RotasUtils block log");
            thread.setDaemon(true);
            return thread;
        });
        writer.execute(BlockBreakLog::load);
        writer.scheduleWithFixedDelay(BlockBreakLog::flush, 5, 5, TimeUnit.SECONDS);
    }

    public static void stop() {
        if (writer == null) return;
        writer.shutdown();
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        flush();
        writer = null;
    }

    public static void record(ServerPlayer player, Level level, BlockPos pos, BlockState state, boolean placed) {
        Entry entry;
        synchronized (BlockBreakLog.class) {
            entry = new Entry(System.currentTimeMillis(), pool(player.getGameProfile().getName()), lowerOf(player.getGameProfile().getName()),
                    pool(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()),
                    pool(level.dimension().location().toString()), pos.getX(), pos.getY(), pos.getZ(), placed);
            add(entry, true);
        }
        PENDING.add(entry.line());
    }

    private static String lowerOf(String name) {
        return pool(name.toLowerCase(Locale.ROOT));
    }

    private static String pool(String value) {
        return POOL.computeIfAbsent(value, v -> v);
    }

    private static void add(Entry entry, boolean newest) {
        if (ENTRIES.size() >= MAX) {
            if (!newest) return;
            ENTRIES.removeLast();
        }
        if (newest) ENTRIES.addFirst(entry);
        else ENTRIES.addLast(entry);
    }

    private static void flush() {
        if (PENDING.isEmpty() || dir == null) return;
        StringBuilder out = new StringBuilder();
        for (String line; (line = PENDING.poll()) != null; ) out.append(line).append('\n');
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(LocalDate.now() + ".tsv"), out, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            Rotasutils.LOG.error("Block log write failed: {}", failure.getMessage());
        }
    }

    private static void load() {
        if (!Files.isDirectory(dir)) return;
        List<Path> files = new ArrayList<>();
        String oldest = LocalDate.now().minusDays(KEEP_DAYS).toString();
        try (Stream<Path> list = Files.list(dir)) {
            list.filter(p -> p.getFileName().toString().endsWith(".tsv")).forEach(files::add);
        } catch (IOException failure) {
            Rotasutils.LOG.error("Block log read failed: {}", failure.getMessage());
            return;
        }
        files.sort(Collections.reverseOrder());
        for (Path file : files) {
            try {
                if (file.getFileName().toString().compareTo(oldest) < 0) {
                    Files.deleteIfExists(file);
                    continue;
                }
                if (size() >= MAX) continue;
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int start = lines.size() - 1; start >= 0 && size() < MAX; start -= 2000) {
                    synchronized (BlockBreakLog.class) {
                    for (int i = start; i > start - 2000 && i >= 0 && ENTRIES.size() < MAX; i--) {
                        String[] f = lines.get(i).split("\t");
                        if (f.length < 7) continue;
                        try {
                            add(new Entry(Long.parseLong(f[0]), pool(f[1]), lowerOf(f[1]), pool(f[2]), pool(f[3]),
                                    Integer.parseInt(f[4]), Integer.parseInt(f[5]), Integer.parseInt(f[6]),
                                    f.length > 7 && f[7].equals("P")), false);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    }
                }
            } catch (IOException failure) {
                Rotasutils.LOG.error("Block log read failed for {}: {}", file, failure.getMessage());
            }
        }
    }

    public static synchronized ListTag rows(Query query) {
        String needle = query.text() == null ? "" : query.text().trim().toLowerCase(Locale.ROOT);
        long since = query.hours() > 0 ? System.currentTimeMillis() - query.hours() * 3_600_000L : 0;
        long radiusSq = (long) query.radius() * query.radius();
        ListTag rows = new ListTag();
        for (Iterator<Entry> it = ENTRIES.iterator(); it.hasNext() && rows.size() < SENT; ) {
            Entry entry = it.next();
            if (entry.time() < since) break;
            if (query.mode().equals("break") && entry.placed() || query.mode().equals("place") && !entry.placed()) continue;
            if (query.radius() > 0 && (!entry.dimension().equals(query.dimension())
                    || query.center().distToCenterSqr(entry.x() + 0.5, entry.y() + 0.5, entry.z() + 0.5) > radiusSq)) continue;
            if (!needle.isEmpty() && !entry.lower().contains(needle)
                    && !entry.block().contains(needle)) continue;
            rows.add(row(entry));
        }
        return rows;
    }

    private static CompoundTag row(Entry entry) {
        CompoundTag row = new CompoundTag();
        row.putLong("time", entry.time());
        row.putString("player", entry.player());
        row.putString("block", entry.block());
        row.putString("dim", entry.dimension());
        row.putInt("x", entry.x());
        row.putInt("y", entry.y());
        row.putInt("z", entry.z());
        row.putBoolean("placed", entry.placed());
        return row;
    }

    public static boolean toggleInspect(ServerPlayer player) {
        if (INSPECTING.remove(player.getUUID())) return false;
        INSPECTING.add(player.getUUID());
        return true;
    }

    public static boolean inspecting(ServerPlayer player) {
        return INSPECTING.contains(player.getUUID());
    }

    public static dev.architectury.event.EventResult onRightClick(net.minecraft.world.entity.player.Player player,
                                                                 net.minecraft.world.InteractionHand hand,
                                                                 BlockPos pos, net.minecraft.core.Direction face) {
        if (!(player instanceof ServerPlayer admin) || !inspecting(admin)) return dev.architectury.event.EventResult.pass();
        if (!BoardService.isAdmin(admin, net.schwarz.rotasutils.data.RotasData.get(admin.server))) {
            INSPECTING.remove(admin.getUUID());
            return dev.architectury.event.EventResult.pass();
        }
        if (hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
            String dimension = admin.level().dimension().location().toString();
            history(admin, dimension, pos, "Clicked block");
            history(admin, dimension, pos.relative(face), "Space in front");
        }
        return dev.architectury.event.EventResult.interruptFalse();
    }

    private static void history(ServerPlayer admin, String dimension, BlockPos pos, String label) {
        List<Entry> found = new ArrayList<>();
        synchronized (BlockBreakLog.class) {
            for (Iterator<Entry> it = ENTRIES.iterator(); it.hasNext() && found.size() < 8; ) {
                Entry e = it.next();
                if (e.x() == pos.getX() && e.y() == pos.getY() && e.z() == pos.getZ() && e.dimension().equals(dimension)) found.add(e);
            }
        }
        java.text.SimpleDateFormat time = new java.text.SimpleDateFormat("MM-dd HH:mm");
        admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6" + label + " " + pos.toShortString()
                + (found.isEmpty() ? " §7- no history" : "")));
        for (Entry e : found) {
            admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7 " + time.format(new java.util.Date(e.time()))
                    + (e.placed() ? " §a+ " : " §c- ") + "§f" + e.player() + " §7" + e.block()));
        }
    }

    public static synchronized int size() {
        return ENTRIES.size();
    }
}
