package cn.org.agatha.agStatistic;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {
    private final AgStatistic plugin;
    private final PlayerSession playerSession;

    public PlayerListener(AgStatistic plugin, PlayerSession playerSession) {
        this.plugin = plugin;
        this.playerSession = playerSession;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        playerSession.recordPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerSession.recordPlayerQuit(event.getPlayer());
    }
}