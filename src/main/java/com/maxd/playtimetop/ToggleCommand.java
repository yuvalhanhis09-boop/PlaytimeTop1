package com.maxd.playtimetop;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ToggleCommand implements CommandExecutor {
    private final PlayTimeTopPlugin plugin;
    private final ScoreboardService scoreboardService;

    public ToggleCommand(PlayTimeTopPlugin plugin, ScoreboardService scoreboardService) {
        this.plugin = plugin;
        this.scoreboardService = scoreboardService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        Player p = (Player) sender;
        if (!p.hasPermission("playtimetop.toggle")) {
            p.sendMessage("No permission.");
            return true;
        }
        scoreboardService.toggle(p);
        return true;
    }
}
