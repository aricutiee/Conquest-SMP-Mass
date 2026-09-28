package dev.turtleroles.events;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

/** The seven Purge prizes use ordinary enchantments; only the pick has a mining mode. */
final class WarlordRelics implements Listener {
    static final List<String> PIECES=List.of("helmet","chestplate","leggings","boots","pickaxe","axe","sword");
    static final NamespacedKey PIECE=new NamespacedKey("conquestsmp","purge_relic");
    static final NamespacedKey INSTANCE=new NamespacedKey("conquestsmp","purge_instance");
    static final NamespacedKey MODE=new NamespacedKey("conquestsmp","purge_mining");
    private final JavaPlugin plugin;
    private final YamlConfiguration config;
    private final Set<UUID> breaking=new HashSet<>();
    WarlordRelics(JavaPlugin plugin,YamlConfiguration config){this.plugin=plugin;this.config=config;}
    ItemStack create(String piece) {
        if(!PIECES.contains(piece))throw new IllegalArgumentException("Unknown Warlord piece: "+piece);
        ItemStack item=new ItemStack(Material.valueOf("NETHERITE_"+piece.toUpperCase(Locale.ROOT)));
        var meta=item.getItemMeta();
        String name=switch(piece) { case "sword" -> "Warlord's Ratio"; case "axe" -> "Heavy Hitter Axe";
            default -> "Warlord's "+Character.toUpperCase(piece.charAt(0))+piece.substring(1); };
        meta.displayName(Component.text(name,NamedTextColor.LIGHT_PURPLE));
        meta.getPersistentDataContainer().set(PIECE,PersistentDataType.STRING,piece);
        meta.getPersistentDataContainer().set(INSTANCE,PersistentDataType.STRING,UUID.randomUUID().toString());
        Map<String,Integer> specs=PIECES.indexOf(piece)<4?Map.of("protection",5,"unbreaking",3,"mending",1)
            :piece.equals("pickaxe")?Map.of("efficiency",5,"fortune",3)
            :piece.equals("sword")?Map.of("sharpness",5,"unbreaking",3,"mending",1)
            :Map.of("sharpness",5,"efficiency",5,"unbreaking",3,"mending",1);
        for(var enchant:specs.entrySet()) {
            var type=Registry.ENCHANTMENT.get(NamespacedKey.minecraft(enchant.getKey()));
            if(type!=null)meta.addEnchant(type,Math.clamp(config.getInt("warlord-purge.enchantments."+piece+"."+enchant.getKey(),enchant.getValue()),1,10),true);
        }
        if(piece.equals("pickaxe")) {
            meta.setUnbreakable(true);
            meta.lore(List.of(Component.text("Crouch to toggle 3x3 mining.",NamedTextColor.GRAY),Component.text("Fortune applies. Protected blocks stay protected.",NamedTextColor.DARK_GRAY)));
        }
        item.setItemMeta(meta);return item;
    }
    static boolean pick(ItemStack item){return item!=null&&item.getType()==Material.NETHERITE_PICKAXE&&item.hasItemMeta()
            &&"pickaxe".equals(item.getItemMeta().getPersistentDataContainer().get(PIECE,PersistentDataType.STRING));}
    static boolean enabled(ItemStack item){return pick(item)&&item.getItemMeta().getPersistentDataContainer().has(MODE,PersistentDataType.BYTE);}
    @EventHandler(ignoreCancelled=true) public void sneak(PlayerToggleSneakEvent event) {
        if(!event.isSneaking())return;
        Player player=event.getPlayer();ItemStack item=player.getInventory().getItemInMainHand();if(!pick(item))return;
        boolean on=!enabled(item);var meta=item.getItemMeta();
        if(on)meta.getPersistentDataContainer().set(MODE,PersistentDataType.BYTE,(byte)1);else meta.getPersistentDataContainer().remove(MODE);
        item.setItemMeta(meta);player.getInventory().setItemInMainHand(item);
        player.sendActionBar(Component.text("Warlord Pickaxe: "+(on?"3x3":"Single block"),NamedTextColor.LIGHT_PURPLE));
    }
    static List<int[]> offsets(BlockFace face) {
        List<int[]> result=new ArrayList<>();
        for(int a=-1;a<=1;a++)for(int b=-1;b<=1;b++)if(a!=0||b!=0)
            result.add(face==BlockFace.UP||face==BlockFace.DOWN?new int[]{a,0,b}
                    :face==BlockFace.EAST||face==BlockFace.WEST?new int[]{0,a,b}:new int[]{a,b,0});
        return result;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void mine(BlockBreakEvent event) {
        Player player=event.getPlayer();ItemStack held=player.getInventory().getItemInMainHand();
        if(breaking.contains(player.getUniqueId())||!enabled(held)||!Tag.MINEABLE_PICKAXE.isTagged(event.getBlock().getType())
                ||player.getGameMode()!=GameMode.SURVIVAL)return;
        var ray=player.rayTraceBlocks(6);BlockFace face=ray!=null&&ray.getHitBlockFace()!=null?ray.getHitBlockFace():BlockFace.NORTH;
        String id=held.getItemMeta().getPersistentDataContainer().get(INSTANCE,PersistentDataType.STRING);
        Block center=event.getBlock();Material before=center.getType();
        Bukkit.getScheduler().runTask(plugin,()->{
            ItemStack current=player.getInventory().getItemInMainHand();
            if(event.isCancelled()||!player.isOnline()||player.isDead()||!enabled(current)||center.getType()==before
                    ||!player.getWorld().equals(center.getWorld())||player.getLocation().distanceSquared(center.getLocation())>64
                    ||!Objects.equals(id,current.getItemMeta().getPersistentDataContainer().get(INSTANCE,PersistentDataType.STRING)))return;
            breaking.add(player.getUniqueId());
            try {for(int[] offset:offsets(face)) {
                Block block=center.getRelative(offset[0],offset[1],offset[2]);
                if(!block.getWorld().isChunkLoaded(block.getX()>>4,block.getZ()>>4)||!Tag.MINEABLE_PICKAXE.isTagged(block.getType())
                        ||block.getType().getHardness()<0||block.getState() instanceof org.bukkit.block.TileState)continue;
                // Paper fires normal block-break events and handles Fortune, XP and protection.
                player.breakBlock(block);
            }}finally{breaking.remove(player.getUniqueId());}
        });
    }
}
