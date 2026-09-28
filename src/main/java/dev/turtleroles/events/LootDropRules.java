package dev.turtleroles.events;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import java.util.*;
import java.util.random.RandomGenerator;

/** Inclusive loot ranges, area-uniform coordinates and shuffled split stacks. */
final class LootDropRules {
    record Offset(int x, int z) {}
    private static final Material[] GEAR = {Material.DIAMOND_SWORD,Material.DIAMOND_PICKAXE,
            Material.DIAMOND_AXE,Material.DIAMOND_SHOVEL,Material.DIAMOND_HOE,Material.DIAMOND_HELMET,
            Material.DIAMOND_CHESTPLATE,Material.DIAMOND_LEGGINGS,Material.DIAMOND_BOOTS};
    static Offset offset(RandomGenerator random, int minimum, int maximum) {
        for (;;) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = Math.sqrt((double)minimum*minimum + random.nextDouble() * ((double)maximum*maximum-(double)minimum*minimum));
            int x=(int)Math.round(Math.cos(angle)*radius), z=(int)Math.round(Math.sin(angle)*radius);
            if(inRange(x,z,minimum,maximum))return new Offset(x,z);
        }
    }
    static boolean inRange(double x,double z,int minimum,int maximum) {
        double squared=x*x+z*z;
        return squared >= (double)minimum*minimum && squared <= (double)maximum*maximum;
    }
    static List<ItemStack> loot(YamlConfiguration config, RandomGenerator random) {
        List<ItemStack> result=new ArrayList<>();
        add(result,config,random,"enchanted-golden-apples",Material.ENCHANTED_GOLDEN_APPLE,1,2);
        add(result,config,random,"netherite-ingots",Material.NETHERITE_INGOT,1,2);
        add(result,config,random,"upgrade-templates",Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,1,1);
        add(result,config,random,"nether-wart",Material.NETHER_WART,8,32);
        add(result,config,random,"golden-apples",Material.GOLDEN_APPLE,4,16);
        add(result,config,random,"emeralds",Material.EMERALD,2,64);
        add(result,config,random,"diamonds",Material.DIAMOND,16,64);
        result.add(new ItemStack(GEAR[random.nextInt(GEAR.length)]));
        return result;
    }
    private static void add(List<ItemStack> result,YamlConfiguration config,RandomGenerator random,
                            String path,Material material,int minimum,int maximum) {
        int min=Math.clamp(config.getInt("loot."+path+".min",minimum),1,64);
        int max=Math.clamp(config.getInt("loot."+path+".max",maximum),min,64);
        result.add(new ItemStack(material,random.nextInt(min,max+1)));
    }
    static ItemStack[] scatter(List<ItemStack> loot,RandomGenerator random) {
        List<ItemStack> stacks=new ArrayList<>();
        for(ItemStack item:loot) {
            int remaining=item.getAmount(), pieces=random.nextInt(1,Math.min(3,remaining)+1);
            for(int i=pieces;i>0;i--) {
                int amount=i==1?remaining:random.nextInt(1,remaining-i+2);
                ItemStack stack=item.clone();stack.setAmount(amount);stacks.add(stack);remaining-=amount;
            }
        }
        if(stacks.size()>27)throw new IllegalStateException("Loot exceeds chest capacity");
        List<Integer> slots=new ArrayList<>();for(int i=0;i<27;i++)slots.add(i);
        for(int i=26;i>0;i--)Collections.swap(slots,i,random.nextInt(i+1));
        ItemStack[] contents=new ItemStack[27];
        for(int i=0;i<stacks.size();i++)contents[slots.get(i)]=stacks.get(i);
        return contents;
    }
}
