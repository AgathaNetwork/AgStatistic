package cn.org.agatha.agStatistic;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.entity.Player;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class PlayerSession {
    private final AgStatistic plugin;
    private final DatabaseManager databaseManager;
    private final Gson gson;

    public PlayerSession(AgStatistic plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.gson = new Gson();
    }

    public void recordPlayerJoin(Player player) {
        long joinTime = System.currentTimeMillis();
        String username = player.getName();
        String month = getCurrentMonth();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // 查询玩家在当前月份是否已有记录
                String data = getPlayerData(username, month);

                if (data == null) {
                    // 没有记录，创建新记录
                    createNewRecord(username, month, joinTime);
                } else {
                    // 已有记录，更新记录
                    updateExistingRecord(username, month, data, joinTime);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("记录玩家登录时出错: " + e.getMessage());
            }
        });
    }

    public void recordPlayerQuit(Player player) {
        long exitTime = System.currentTimeMillis();
        String username = player.getName();
        String month = getCurrentMonth();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // 查询玩家在当前月份的记录
                String data = getPlayerData(username, month);

                if (data != null) {
                    // 更新现有记录中的最后一条记录，添加退出时间
                    updateExitTime(username, month, data, exitTime);
                }
                // 如果没有记录，则不处理，因为玩家登录时应该已经创建了记录
            } catch (SQLException e) {
                plugin.getLogger().severe("记录玩家退出时出错: " + e.getMessage());
            }
        });
    }

    private String getPlayerData(String username, String month) throws SQLException {
        Connection connection = databaseManager.getConnection();
        String query = "SELECT data FROM statistic WHERE username = ? AND month = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, username);
            stmt.setString(2, month);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("data");
            }
        }
        return null;
    }

    private void createNewRecord(String username, String month, long joinTime) throws SQLException {
        SessionEntry entry = new SessionEntry(joinTime, 0);
        List<SessionEntry> entries = new ArrayList<>();
        entries.add(entry);

        String jsonData = gson.toJson(entries);

        Connection connection = databaseManager.getConnection();
        String insert = "INSERT INTO statistic (username, month, data) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(insert)) {
            stmt.setString(1, username);
            stmt.setString(2, month);
            stmt.setString(3, jsonData);
            stmt.executeUpdate();
        }

        plugin.getLogger().info("为玩家 " + username + " 创建了新的会话记录");
    }

    private void updateExistingRecord(String username, String month, String existingData, long joinTime) throws SQLException {
        Type listType = new TypeToken<ArrayList<SessionEntry>>(){}.getType();
        List<SessionEntry> entries = gson.fromJson(existingData, listType);
        entries.add(new SessionEntry(joinTime, 0));

        String jsonData = gson.toJson(entries);

        Connection connection = databaseManager.getConnection();
        String update = "UPDATE statistic SET data = ? WHERE username = ? AND month = ?";
        try (PreparedStatement stmt = connection.prepareStatement(update)) {
            stmt.setString(1, jsonData);
            stmt.setString(2, username);
            stmt.setString(3, month);
            stmt.executeUpdate();
        }

        plugin.getLogger().info("为玩家 " + username + " 更新了会话记录");
    }

    private void updateExitTime(String username, String month, String existingData, long exitTime) throws SQLException {
        Type listType = new TypeToken<ArrayList<SessionEntry>>(){}.getType();
        List<SessionEntry> entries = gson.fromJson(existingData, listType);

        // 更新最后一条记录的退出时间
        if (!entries.isEmpty()) {
            SessionEntry lastEntry = entries.get(entries.size() - 1);
            lastEntry.exit = exitTime;
        }

        String jsonData = gson.toJson(entries);

        Connection connection = databaseManager.getConnection();
        String update = "UPDATE statistic SET data = ? WHERE username = ? AND month = ?";
        try (PreparedStatement stmt = connection.prepareStatement(update)) {
            stmt.setString(1, jsonData);
            stmt.setString(2, username);
            stmt.setString(3, month);
            stmt.executeUpdate();
        }

        plugin.getLogger().info("为玩家 " + username + " 更新了退出时间");
    }

    private String getCurrentMonth() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        return now.format(formatter);
    }

    private static class SessionEntry {
        private long join;
        private long exit;

        public SessionEntry(long join, long exit) {
            this.join = join;
            this.exit = exit;
        }
    }
}