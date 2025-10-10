package cn.org.agatha.agStatistic;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import java.time.YearMonth;

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
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();
        
        // 检查是否是我们插件的GUI界面
        if (holder instanceof StatGUIHolder) {
            event.setCancelled(true);
            
            // 获取被点击的物品
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                return;
            }
            
            Player player = (Player) event.getWhoClicked();
            StatGUIHolder statGUIHolder = (StatGUIHolder) holder;
            YearMonth currentYearMonth = statGUIHolder.getYearMonth();
            
            // 检查是否点击了月份切换按钮
            if (event.getSlot() == 0 && clickedItem.getType() == Material.OAK_SIGN) {
                // 点击了"上一个月"按钮
                YearMonth previousMonth = currentYearMonth.minusMonths(1);
                player.closeInventory();
                StatGUI.openGUI(player, plugin, previousMonth);
            } else if (event.getSlot() == 8 && clickedItem.getType() == Material.OAK_SIGN) {
                // 点击了"下一个月"按钮
                YearMonth nextMonth = currentYearMonth.plusMonths(1);
                player.closeInventory();
                StatGUI.openGUI(player, plugin, nextMonth);
            }
        }
    }
}