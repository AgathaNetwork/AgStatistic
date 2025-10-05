package cn.org.agatha.agStatistic;

import com.mysql.cj.jdbc.Driver;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Logger;

public class DatabaseManager {
    private final AgStatistic plugin;
    private Connection connection;
    private BukkitTask connectionCheckTask;
    private String host;
    private int port;
    private String database;
    private String username;
    private String password;

    public DatabaseManager(AgStatistic plugin) {
        this.plugin = plugin;
        loadConfiguration();
    }

    private void loadConfiguration() {
        FileConfiguration config = plugin.getConfig();
        host = config.getString("database.ip", "localhost");
        port = config.getInt("database.port", 3306);
        database = config.getString("database.database", "agatha");
        username = config.getString("database.username", "root");
        password = config.getString("database.password", "password");
    }

    public void connect() throws SQLException {
        try {
            DriverManager.registerDriver(new Driver());
            Properties props = new Properties();
            props.setProperty("user", username);
            props.setProperty("password", password);
            props.setProperty("useSSL", "false");
            props.setProperty("verifyServerCertificate", "false");
            props.setProperty("allowPublicKeyRetrieval", "true");

            connection = DriverManager.getConnection(
                    "jdbc:mysql://" + host + ":" + port + "/" + database, props);

            Logger.getLogger("Minecraft").info("[AgStatistic] 数据库连接成功建立");
        } catch (SQLException e) {
            Logger.getLogger("Minecraft").severe("[AgStatistic] 数据库连接失败: " + e.getMessage());
            throw e;
        }
    }

    public void startConnectionCheck() {
        connectionCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (connection == null) {
                    try {
                        connect();
                    } catch (SQLException e) {
                        Logger.getLogger("Minecraft").severe("[AgStatistic] 数据库重连失败: " + e.getMessage());
                    }
                } else {
                    try {
                        if (connection.isClosed() || !connection.isValid(5)) {
                            Logger.getLogger("Minecraft").warning("[AgStatistic] 检测到数据库连接已断开，正在尝试重新连接...");
                            try {
                                connection.close();
                            } catch (SQLException ignored) {}
                            connect();
                        }
                    } catch (SQLException e) {
                        Logger.getLogger("Minecraft").severe("[AgStatistic] 数据库连接检测失败: " + e.getMessage());
                    }
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 1200L); // 每60秒检测一次 (1200 ticks = 60 seconds)
    }

    public void stopConnectionCheck() {
        if (connectionCheckTask != null) {
            connectionCheckTask.cancel();
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                Logger.getLogger("Minecraft").info("[AgStatistic] 数据库连接已关闭");
            }
        } catch (SQLException e) {
            Logger.getLogger("Minecraft").severe("[AgStatistic] 关闭数据库连接时出错: " + e.getMessage());
        }
    }

    public Connection getConnection() {
        return connection;
    }
}