package cn.org.agatha.agStatistic;

import org.bukkit.plugin.java.JavaPlugin;

public final class AgStatistic extends JavaPlugin {
    private DatabaseManager databaseManager;
    private PlayerSession playerSession;

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();

        // 初始化数据库管理器
        databaseManager = new DatabaseManager(this);
        
        // 建立数据库连接
        try {
            databaseManager.connect();
        } catch (Exception e) {
            getLogger().severe("无法连接到数据库: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // 启动连接检测任务
        databaseManager.startConnectionCheck();
        
        // 初始化玩家会话管理器
        playerSession = new PlayerSession(this, databaseManager);
        
        // 注册玩家监听器
        getServer().getPluginManager().registerEvents(new PlayerListener(this, playerSession), this);
        
        getLogger().info("AgStatistic 插件已启用!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (databaseManager != null) {
            databaseManager.stopConnectionCheck();
            databaseManager.disconnect();
        }
        
        getLogger().info("AgStatistic 插件已禁用!");
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public PlayerSession getPlayerSession() {
        return playerSession;
    }
}