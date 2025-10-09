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
        Inventory gui = Bukkit.createInventory(player, GUI_SIZE, "玩家在线统计 - " + getCurrentMonth());

        // 异步加载数据
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                populateGUI(gui, plugin, player.getName());
                
                // 切换到主线程打开GUI
                plugin.getServer().getScheduler().runTask(plugin, () -> player.openInventory(gui));
            } catch (Exception e) {
                plugin.getLogger().severe("加载统计GUI时出错: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private static void populateGUI(Inventory gui, AgStatistic plugin, String playerName) {
        YearMonth currentYearMonth = YearMonth.now();
        int daysInMonth = currentYearMonth.lengthOfMonth();
        LocalDate firstDayOfMonth = currentYearMonth.atDay(1);
        
        // 填充日历背景
        for (int i = 0; i < GUI_SIZE; i++) {
            gui.setItem(i, createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null));
        }
        
        // 设置日历标题行
        String[] weekdays = {"日", "一", "二", "三", "四", "五", "六"};
        for (int i = 0; i < 7; i++) {
            gui.setItem(i + 1, createItem(Material.WHITE_STAINED_GLASS_PANE, weekdays[i], null));
        }
        
        // 计算该月第一天是星期几 (0=星期日, 1=星期一, ..., 6=星期六)
        int firstDayOfWeek = firstDayOfMonth.getDayOfWeek().getValue() % 7;
        
        // 填充日期
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = currentYearMonth.atDay(day);
            int dayOfWeek = date.getDayOfWeek().getValue() % 7;
            
            // 计算在GUI中的位置
            // 第一行是标题(0-8)，从第二行开始放置日期(9-53)
            int position = 10 + (dayOfWeek - firstDayOfWeek) + ((day + firstDayOfWeek - 1) / 7) * 9;
            
            if (position >= 9 && position < GUI_SIZE) {
                String playtime = getPlayerPlaytimeForDay(plugin, playerName, day);
                gui.setItem(position, createItem(Material.LIME_STAINED_GLASS_PANE, 
                    String.format("%02d", day) + "日", 
                    playtime != null ? List.of("在线时间: " + playtime) : List.of("无记录")));
            }
        }
    }

    private static String getPlayerPlaytimeForDay(AgStatistic plugin, String playerName, int day) {
        try {
            Connection connection = plugin.getDatabaseManager().getConnection();
            String month = getCurrentMonth();
            
            // 获取该月指定日期的开始和结束时间戳
            YearMonth yearMonth = YearMonth.now();
            LocalDate targetDay = yearMonth.atDay(day);
            LocalDate nextDay = targetDay.plusDays(1);
            
            // 转换为毫秒时间戳
            long startOfDay = targetDay.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long endOfDay = nextDay.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            
            // 查询该玩家在该天的所有事件
            String query = "SELECT timestamp, event_type FROM player_sessions WHERE username = ? AND timestamp >= ? AND timestamp < ? ORDER BY timestamp";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, playerName);
                stmt.setLong(2, startOfDay);
                stmt.setLong(3, endOfDay);
                
                ResultSet rs = stmt.executeQuery();
                
                long totalPlaytimeMillis = 0;
                long joinTime = 0;
                boolean joined = false;
                
                while (rs.next()) {
                    long timestamp = rs.getLong("timestamp");
                    String eventType = rs.getString("event_type");
                    
                    if ("join".equals(eventType)) {
                        joinTime = timestamp;
                        joined = true;
                    } else if ("quit".equals(eventType) && joined) {
                        totalPlaytimeMillis += (timestamp - joinTime);
                        joined = false;
                    }
                }
                
                // 如果玩家在当天加入了但尚未退出，计算到当前时间
                if (joined) {
                    totalPlaytimeMillis += (System.currentTimeMillis() - joinTime);
                }
                
                if (totalPlaytimeMillis > 0) {
                    long seconds = totalPlaytimeMillis / 1000;
                    long minutes = seconds / 60;
                    long hours = minutes / 60;
                    
                    return String.format("%02d小时%02d分钟", hours, minutes % 60);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
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
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    // 内部类用于解析JSON数据
    private static class PlayerSessionEntry {
        long join;
        long exit;
    }
}