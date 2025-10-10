package cn.org.agatha.agStatistic;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class StatGUI {
    private static final int GUI_SIZE = 54; // 6行x9列的大箱子GUI

    public static void openGUI(Player player, AgStatistic plugin) {
        openGUI(player, plugin, YearMonth.now());
    }

    public static void openGUI(Player player, AgStatistic plugin, YearMonth yearMonth) {
        StatGUIHolder holder = new StatGUIHolder();
        Inventory gui = Bukkit.createInventory(holder, GUI_SIZE, "玩家在线统计 - " + yearMonth.format(DateTimeFormatter.ofPattern("yyyy年MM月")));
        holder.setInventory(gui);
        holder.setYearMonth(yearMonth);

        // 异步加载数据
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                populateGUI(gui, plugin, player.getName(), yearMonth);
                
                // 切换到主线程打开GUI
                plugin.getServer().getScheduler().runTask(plugin, () -> player.openInventory(gui));
            } catch (Exception e) {
                plugin.getLogger().severe("加载统计GUI时出错: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * 更新现有的GUI内容
     * @param gui 要更新的GUI
     * @param plugin 插件实例
     * @param playerName 玩家名
     * @param yearMonth 要统计的年月
     */
    public static void updateGUI(Inventory gui, AgStatistic plugin, String playerName, YearMonth yearMonth) {
        // 在主线程中先显示加载指示器
        for (int i = 0; i < GUI_SIZE; i++) {
            gui.setItem(i, createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null));
        }
        // 在中间放置指南针表示正在加载
        gui.setItem(22, createItem(Material.COMPASS, "§e正在加载...", null));
        
        // 异步加载数据
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                populateGUI(gui, plugin, playerName, yearMonth);
                
                // 切换到主线程更新GUI
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    // 更新GUI内容
                    gui.getViewers().forEach(viewer -> {
                        if (viewer instanceof Player) {
                            ((Player) viewer).updateInventory();
                        }
                    });
                });
            } catch (Exception e) {
                plugin.getLogger().severe("更新统计GUI时出错: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private static void populateGUI(Inventory gui, AgStatistic plugin, String playerName, YearMonth yearMonth) {
        int daysInMonth = yearMonth.lengthOfMonth();
        LocalDate firstDayOfMonth = yearMonth.atDay(1);
        
        // 创建数组存储每月每天的游玩秒数
        long[] dailyPlaytime = new long[daysInMonth + 1]; // 索引0不使用，从1开始对应每月的第1天
        
        // 填充日历背景
        for (int i = 0; i < GUI_SIZE; i++) {
            gui.setItem(i, createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null));
        }
        
        // 从数据库获取该玩家在该月的所有进出记录
        loadPlayerPlaytimeData(plugin, playerName, yearMonth, dailyPlaytime);
        
        // 在第一行的最左边和最右边放置橡木告示牌用于切换月份
        gui.setItem(0, createItem(Material.OAK_SIGN, "§e上一个月", List.of("§7点击查看上一个月的统计")));
        gui.setItem(8, createItem(Material.OAK_SIGN, "§e下一个月", List.of("§7点击查看下一个月的统计")));
        
        // 计算该月第一天是星期几 (0=星期日, 1=星期一, ..., 6=星期六)
        // getValue() 返回 1=Monday, ..., 7=Sunday，需要转换为 0=Sunday, ..., 6=Saturday
        int firstDayOfWeek = (firstDayOfMonth.getDayOfWeek().getValue() % 7);
        
        // 填充日期 (从第二行开始，位置 9-53)
        // 使用新的算法：找到一个月中的所有周六，然后在每个周六后面空一格
        int position = 1 + firstDayOfWeek; // 第一天的位置
        for (int day = 1; day <= daysInMonth; day++) {
            if (position >= 0 && position < GUI_SIZE) {
                long playtimeSeconds = dailyPlaytime[day];
                Material material = Material.WHITE_STAINED_GLASS_PANE; // 默认白色
                
                String playtimeStr;
                if (playtimeSeconds > 0) {
                    long hours = playtimeSeconds / 3600;
                    long minutes = (playtimeSeconds % 3600) / 60;
                    playtimeStr = String.format("%02d小时%02d分钟", hours, minutes);
                    
                    // 根据在线时间确定使用的材料
                    if (hours >= 1) {
                        material = Material.GREEN_STAINED_GLASS_PANE; // 深绿色
                    } else {
                        material = Material.LIME_STAINED_GLASS_PANE; // 浅绿色
                    }
                } else {
                    playtimeStr = "无记录";
                }
                
                gui.setItem(position, createItem(material,
                    String.format("%02d", day) + "日", 
                    List.of("在线时间: " + playtimeStr)));
            }
            
            // 如果明天是周日（即今天是周六），则跳过一个位置
            LocalDate currentDate = yearMonth.atDay(day);
            if (currentDate.getDayOfWeek().getValue() == 6) { // 6表示Saturday
                position += 3; // 跳过一个空位
            } else {
                position += 1;
            }
        }
    }

    private static void loadPlayerPlaytimeData(AgStatistic plugin, String playerName, YearMonth yearMonth, long[] dailyPlaytime) {
        try {
            Connection connection = plugin.getDatabaseManager().getConnection();
            
            // 获取该月第一天和下一个月第一天的时间戳
            LocalDate firstDay = yearMonth.atDay(1);
            LocalDate lastDay = yearMonth.atEndOfMonth();
            LocalDate nextMonthFirstDay = yearMonth.plusMonths(1).atDay(1);
            
            long startOfMonth = firstDay.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long startOfNextMonth = nextMonthFirstDay.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            
            // 查询该玩家在该月及前后可能影响统计的事件
            String query = "SELECT timestamp, event_type FROM player_sessions WHERE username = ? AND timestamp >= ? AND timestamp < ? ORDER BY timestamp";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, playerName);
                stmt.setLong(2, startOfMonth - 24 * 60 * 60 * 1000); // 包含前一天的数据
                stmt.setLong(3, startOfNextMonth + 24 * 60 * 60 * 1000); // 包含下一天的数据
                
                ResultSet rs = stmt.executeQuery();
                
                long joinTime = 0;
                boolean joined = false;
                
                while (rs.next()) {
                    long timestamp = rs.getLong("timestamp");
                    String eventType = rs.getString("event_type");
                    
                    if ("join".equals(eventType)) {
                        joinTime = timestamp;
                        joined = true;
                    } else if ("quit".equals(eventType) && joined) {
                        // 处理完整的会话记录，将其时间分配到对应的日期
                        distributePlaytimeAcrossDays(joinTime, timestamp, dailyPlaytime, yearMonth);
                        joined = false;
                    }
                }
                
                // 如果玩家当前在线（只有登录时间没有退出时间）
                if (joined) {
                    // 处理进行中的会话记录
                    distributePlaytimeAcrossDays(joinTime, System.currentTimeMillis(), dailyPlaytime, yearMonth);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 将一次会话的时间分配到对应的日期中
     * @param sessionStart 会话开始时间戳（毫秒）
     * @param sessionEnd 会话结束时间戳（毫秒）
     * @param dailyPlaytime 存储每天游玩时间的数组
     * @param yearMonth 当前统计的年月
     */
    private static void distributePlaytimeAcrossDays(long sessionStart, long sessionEnd, long[] dailyPlaytime, YearMonth yearMonth) {
        // 遍历会话时间范围内的每一天
        LocalDate currentDate = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(sessionStart), ZoneId.systemDefault());
        LocalDate endDate = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(sessionEnd), ZoneId.systemDefault());
        
        while (!currentDate.isAfter(endDate)) {
            // 计算这一天的开始和结束时间戳
            long dayStart = currentDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long dayEnd = currentDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            
            // 计算会话在这一天的实际时间范围
            long actualStart = Math.max(sessionStart, dayStart);
            long actualEnd = Math.min(sessionEnd, dayEnd);
            
            // 只有当会话在这一天有重叠时才计算时间
            if (actualStart < actualEnd) {
                int dayOfMonth = currentDate.getDayOfMonth();
                // 确保日期在正确的月份内
                if (currentDate.getYear() == yearMonth.getYear() && 
                    currentDate.getMonthValue() == yearMonth.getMonthValue() &&
                    dayOfMonth < dailyPlaytime.length) {
                    // 将毫秒转换为秒并累加到对应日期
                    dailyPlaytime[dayOfMonth] += (actualEnd - actualStart) / 1000;
                }
            }
            
            // 移动到下一天
            currentDate = currentDate.plusDays(1);
        }
    }

    private static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String getCurrentMonth() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月"));
    }
}