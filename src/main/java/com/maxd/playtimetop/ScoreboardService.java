package com.maxd.playtimetop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ScoreboardService {
    private final PlayTimeTopPlugin plugin;
    private final PlaytimeService playtimeService;
    private final FileConfiguration toggleCfg;

    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    private final String titleHeb = ChatColor.AQUA + "\u23F1 \u05D6\u05DE\u05DF \u05DE\u05E9\u05D7\u05E7"; // ⏱ זמן משחק
    private final String lineEmpty = ChatColor.GRAY + "—";

    public ScoreboardService(PlayTimeTopPlugin plugin, PlaytimeService service, FileConfiguration toggleCfg) {
        this.plugin = plugin;
        this.playtimeService = service;
        this.toggleCfg = toggleCfg;
    }

    public void startUpdateTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 20L * 5); // update every 5s
    }

    public void updateAll() {
        List<PlaytimeService.PlayerEntry> top = playtimeService.top(10);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isHidden(p.getUniqueId())) continue;
            updateFor(p, top);
        }
    }

    public void applyIfNotHidden(Player p) {
        if (!isHidden(p.getUniqueId())) {
            attach(p);
        } else {
            p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }
    }

    public void toggle(Player p) {
        UUID id = p.getUniqueId();
        boolean hide = !isHidden(id);
        setHidden(id, hide);
        if (hide) {
            p.sendMessage(ChatColor.YELLOW + "הטבלה הוסתרה. כדי להחזיר: /pttoggle");
            p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        } else {
            p.sendMessage(ChatColor.GREEN + "הטבלה הופעלה");
            attach(p);
            updateAll();
        }
        saveToggles();
    }

    private void attach(Player p) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("ptop", "dummy", titleHeb);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        boards.put(p.getUniqueId(), board);
        p.setScoreboard(board);
    }

    private void updateFor(Player p, List<PlaytimeService.PlayerEntry> top) {
        Scoreboard board = boards.get(p.getUniqueId());
        if (board == null) {
            attach(p);
            board = boards.get(p.getUniqueId());
        }
        Objective obj = board.getObjective(DisplaySlot.SIDEBAR);
        if (obj == null) {
            attach(p);
            obj = board.getObjective(DisplaySlot.SIDEBAR);
        }

        // Clear existing lines
        for (String entry : new ArrayList<>(board.getEntries())) {
            board.resetScores(entry);
        }

        if (top.isEmpty()) {
            obj.getScore(lineEmpty).setScore(1);
            return;
        }

        int score = Math.min(15, top.size()); // sidebar supports up to 15 lines
        int rank = 1;
        for (PlaytimeService.PlayerEntry e : top) {
            String time = Util.formatHMS(e.seconds);
            String name = e.name.length() > 12 ? e.name.substring(0, 12) : e.name;
            String line = ChatColor.WHITE + (rank + ". ") + ChatColor.GOLD + name + ChatColor.GRAY + " - " + ChatColor.GREEN + time;
            while (board.getEntries().contains(line)) line += ChatColor.RESET;
            Score s = obj.getScore(line);
            s.setScore(score);
            score--; rank++;
        }
    }

    private boolean isHidden(UUID id) {
        return toggleCfg.getBoolean("hidden." + id.toString(), false);
    }

    private void setHidden(UUID id, boolean hidden) {
        toggleCfg.set("hidden." + id.toString(), hidden);
    }

    public void saveToggles() {
        try {
            toggleCfg.save(new File(plugin.getDataFolder(), "toggles.yml"));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save toggles.yml: " + e.getMessage());
        }
    }
}
