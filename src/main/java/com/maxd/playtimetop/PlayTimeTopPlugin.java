package com.maxd.playtimetop;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class PlayTimeTopPlugin extends JavaPlugin implements Listener {
    private PlaytimeService playtimeService;
    private ScoreboardService scoreboardService;
    private File dataFile;
    private File toggleFile;
    private FileConfiguration dataCfg;
    private FileConfiguration toggleCfg;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadDataFiles();

        this.playtimeService = new PlaytimeService(this, dataCfg);
        this.scoreboardService = new ScoreboardService(this, playtimeService, toggleCfg);

        // Commands
        getCommand("pttoggle").setExecutor(new ToggleCommand(this, scoreboardService));

        // Events
        Bukkit.getPluginManager().registerEvents(this, this);

        // Start periodic tasks
        playtimeService.startTickTask();
        scoreboardService.startUpdateTask();

        // Attach scoreboard on reload to online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            scoreboardService.applyIfNotHidden(p);
        }

        getLogger().info("PlayTimeTop enabled.");
    }

    @Override
    public void onDisable() {
        try {
            playtimeService.flushAndSave();
            scoreboardService.saveToggles();
        } catch (Exception e) {
            getLogger().warning("Failed saving data: " + e.getMessage());
        }
    }

    private void loadDataFiles() {
        // playtime data
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try { dataFile.createNewFile(); } catch (IOException ignored) {}
        }
        dataCfg = YamlConfiguration.loadConfiguration(dataFile);

        // toggles
        toggleFile = new File(getDataFolder(), "toggles.yml");
        if (!toggleFile.exists()) {
            toggleFile.getParentFile().mkdirs();
            try { toggleFile.createNewFile(); } catch (IOException ignored) {}
        }
        toggleCfg = YamlConfiguration.loadConfiguration(toggleFile);
    }

    public void saveDataAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                dataCfg.save(dataFile);
            } catch (IOException e) {
                getLogger().warning("Error saving data.yml: " + e.getMessage());
            }
        });
    }

    public void saveTogglesAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                toggleCfg.save(toggleFile);
            } catch (IOException e) {
                getLogger().warning("Error saving toggles.yml: " + e.getMessage());
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        playtimeService.touch(p.getUniqueId(), p.getName());
        scoreboardService.applyIfNotHidden(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        playtimeService.onQuit(p.getUniqueId());
    }

    public FileConfiguration getDataCfg() { return dataCfg; }
    public FileConfiguration getToggleCfg() { return toggleCfg; }
}
