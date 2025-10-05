package cn.org.agatha.agStatistic;

import org.bukkit.plugin.java.JavaPlugin;

public final class AgStatistic extends JavaPlugin {
    private DatabaseManager databaseManager;

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
}