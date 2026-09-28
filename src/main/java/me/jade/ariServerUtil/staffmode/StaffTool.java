package me.jade.ariServerUtil.staffmode;

import org.bukkit.Material;

public enum StaffTool {
    PLAYERS(0, Material.COMPASS, "Player Selector"),
    FREEZE(1, Material.PACKED_ICE, "Freeze Tool"),
    INVENTORY(2, Material.CHEST, "Inventory Inspector"),
    REPORTS(3, Material.BOOK, "Reports Browser"),
    EXIT(8, Material.BARRIER, "Exit Staff Mode");

    public final int slot;
    public final Material material;
    public final String title;

    StaffTool(int slot, Material material, String title) {
        this.slot = slot;
        this.material = material;
        this.title = title;
    }
}
