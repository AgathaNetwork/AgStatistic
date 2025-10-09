package cn.org.agatha.agStatistic;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StatCommand implements CommandExecutor {
    private final AgStatistic plugin;

    public StatCommand(AgStatistic plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("该命令只能由玩家执行");
            return true;
        }

        Player player = (Player) sender;
        StatGUI.openGUI(player, plugin);
        return true;
    }
}