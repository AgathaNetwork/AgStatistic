package cn.org.agatha.agStatistic;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.time.YearMonth;

public class StatGUIHolder implements InventoryHolder {
    private Inventory inventory;
    private YearMonth yearMonth;
    
    @Override
    public Inventory getInventory() {
        return inventory;
    }
    
    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
    
    public YearMonth getYearMonth() {
        return yearMonth;
    }
    
    public void setYearMonth(YearMonth yearMonth) {
        this.yearMonth = yearMonth;
    }
}