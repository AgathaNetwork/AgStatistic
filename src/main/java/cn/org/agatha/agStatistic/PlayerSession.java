package cn.org.agatha.agStatistic;

import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class PlayerSession {
    private final AgStatistic plugin;
    private final DatabaseManager databaseManager;

    public PlayerSession(AgStatistic plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void recordPlayerJoin(Player player) {
        long joinTime = System.currentTimeMillis();
        String username = player.getName();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                insertSessionRecord(username, joinTime, "join");
            } catch (SQLException e) {
                plugin.getLogger().severe("记录玩家登录时出错: " + e.getMessage());
            }
        });
    }

    public void recordPlayerQuit(Player player) {
        long exitTime = System.currentTimeMillis();
        String username = player.getName();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                insertSessionRecord(username, exitTime, "quit");
            } catch (SQLException e) {
                plugin.getLogger().severe("记录玩家退出时出错: " + e.getMessage());
            }
        });
    }

    private void insertSessionRecord(String username, long timestamp, String eventType) throws SQLException {
        Connection connection = databaseManager.getConnection();
        String insert = "INSERT INTO player_sessions (username, timestamp, event_type) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(insert)) {
            stmt.setString(1, username);
            stmt.setLong(2, timestamp);
            stmt.setString(3, eventType);
            stmt.executeUpdate();
        }

        plugin.getLogger().info("为玩家 " + username + " 记录了" + ("join".equals(eventType) ? "加入" : "退出") + "事件");
    }
}