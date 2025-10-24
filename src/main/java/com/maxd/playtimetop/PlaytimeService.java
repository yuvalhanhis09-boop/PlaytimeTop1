package com.maxd.playtimetop;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlaytimeService {
    private final PlayTimeTopPlugin plugin;
    private final FileConfiguration dataCfg;

    // seconds accumulated per player (in-memory)
    private final Map<UUID, Long> sessionSeconds = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTick = new ConcurrentHashMap<>();

    public PlaytimeService(PlayTimeTopPlugin plugin, FileConfiguration dataCfg) {
        this.plugin = plugin;
        this.dataCfg = dataCfg;
    }

    public void startTickTask() {
        // Tick every second; accumulate session seconds for online players
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            Bukkit.getOnlinePlayers().forEach(p -> {
                UUID id = p.getUniqueId();
                Long prev = lastTick.getOrDefault(id, now);
                long delta = Math.max(0, (now - prev) / 1000);
                sessionSeconds.merge(id, delta, Long::sum);
                lastTick.put(id, now);
            });
        }, 20L, 20L);

        // Persist every 60 seconds
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flush, 20L * 60, 20L * 60);
    }

    public void touch(UUID id, String name) {
        // ensure there's a node for player
        String key = id.toString();
        if (!dataCfg.isConfigurationSection("players." + key)) {
            dataCfg.set("players." + key + ".name", name);
            dataCfg.set("players." + key + ".seconds", 0L);
            plugin.saveDataAsync();
        } else {
            dataCfg.set("players." + key + ".name", name); // keep last name
        }
        lastTick.put(id, System.currentTimeMillis());
    }

    public void onQuit(UUID id) {
        flush(id);
        lastTick.remove(id);
        sessionSeconds.remove(id);
    }

    public void flushAndSave() {
        flush();
        try { plugin.getDataCfg().save(plugin.getDataFolder() + "/data.yml"); } catch (Exception ignored) {}
    }

    private void flush(UUID id) {
        long add = sessionSeconds.getOrDefault(id, 0L);
        if (add <= 0) return;
        String key = id.toString();
        long current = dataCfg.getLong("players." + key + ".seconds", 0L);
        dataCfg.set("players." + key + ".seconds", current + add);
        sessionSeconds.put(id, 0L);
        plugin.saveDataAsync();
    }

    private void flush() {
        for (UUID id : new ArrayList<>(sessionSeconds.keySet())) {
            flush(id);
        }
    }

    public List<PlayerEntry> top(int limit) {
        Map<String, Object> map = dataCfg.getConfigurationSection("players") == null
                ? Collections.emptyMap()
                : dataCfg.getConfigurationSection("players").getValues(false);
        List<PlayerEntry> list = new ArrayList<>();
        for (String key : map.keySet()) {
            String base = "players." + key + ".";
            String name = dataCfg.getString(base + "name", "?");
            long seconds = dataCfg.getLong(base + "seconds", 0L);
            // add in-memory session time if online
            try {
                UUID id = UUID.fromString(key);
                seconds += sessionSeconds.getOrDefault(id, 0L);
            } catch (IllegalArgumentException ignored) {}
            list.add(new PlayerEntry(name, seconds));
        }
        list.sort((a, b) -> Long.compare(b.seconds, a.seconds));
        return list.size() > limit ? list.subList(0, limit) : list;
    }

    public static class PlayerEntry {
        public final String name;
        public final long seconds;
        public PlayerEntry(String name, long seconds) { this.name = name; this.seconds = seconds; }
    }
}
