package dev.turtleroles.service;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import java.util.*;
import java.util.random.RandomGenerator;

/** Explicit virtual loot tables. Lore and production use this same definition. */
public enum SpawnerKind {
    COW(250,1,3, drop(Material.BEEF,1,3),drop(Material.LEATHER,0,2)),
    PIG(250,1,3,drop(Material.PORKCHOP,1,3)),
    CHICKEN(250,1,3,drop(Material.CHICKEN,1,1),drop(Material.FEATHER,0,2)),
    ZOMBIE(750,5,5,drop(Material.ROTTEN_FLESH,0,2)),
    SKELETON(750,5,5,drop(Material.BONE,0,2),drop(Material.ARROW,0,2)),
    BLAZE(1000,10,10,drop(Material.BLAZE_ROD,0,1)),
    SPIDER(750,5,5,drop(Material.STRING,0,2),new Drop(Material.SPIDER_EYE,0,1,0,1.0/3)),
    PHANTOM(1000,5,5,drop(Material.PHANTOM_MEMBRANE,0,1)),
    MAGMA_CUBE(1000,4,4,drop(Material.MAGMA_CREAM,0,1)),
    VILLAGER(1000,5,5,new Drop(Material.EMERALD,1,3,0,1)),
    CREEPER(1000,5,5,drop(Material.GUNPOWDER,0,2));
    public record Drop(Material material,int min,int max,int looting,double chance) {
        int roll(RandomGenerator random){return random.nextDouble()<chance?random.nextInt(min,max+1)+(looting==0?0:(int)Math.round(random.nextDouble()*looting)):0;}
        public String description(){return material.name().toLowerCase(Locale.ROOT).replace('_',' ')+": "+min+"-"+(max+looting)+(chance<1?" (33% chance)":"");}
    }
    public record Batch(Map<Material,Integer> items,int xp){}
    private static Drop drop(Material m,int min,int max){return new Drop(m,min,max,3,1);}
    public final long price; public final int xpMin,xpMax; public final List<Drop> drops;
    SpawnerKind(long price,int xpMin,int xpMax,Drop...drops){this.price=price;this.xpMin=xpMin;this.xpMax=xpMax;this.drops=List.of(drops);}
    public String title(){return name().toLowerCase(Locale.ROOT).replace('_',' ');}
    public EntityType entity(){return EntityType.valueOf(name());}
    public Batch roll(int count,RandomGenerator random) {
        if(count<1||count>500)throw new IllegalArgumentException("Spawner count must be 1-500");
        Map<Material,Integer> items=new EnumMap<>(Material.class);int xp=0;
        for(int i=0;i<count;i++) {
            for(Drop drop:drops){int amount=drop.roll(random);if(amount>0)items.merge(drop.material(),amount,Integer::sum);}
            if(this==ZOMBIE&&random.nextDouble()<.055)items.merge(List.of(Material.IRON_INGOT,Material.CARROT,Material.POTATO).get(random.nextInt(3)),1,Integer::sum);
            xp+=random.nextInt(xpMin,xpMax+1);
        }
        return new Batch(Map.copyOf(items),xp);
    }
    public List<String> lootLore(){
        List<String> lines=new ArrayList<>();lines.add("Per spawner, every 2 active minutes:");
        for(Drop drop:drops)lines.add(drop.description());
        if(this==ZOMBIE)lines.add("5.5%: 1 iron ingot, carrot or potato");
        lines.add("XP: "+xpMin+(xpMin==xpMax?"":"-"+xpMax));
        lines.add(this==VILLAGER?"Custom emerald-producing villager":"Looting III-style virtual loot; no equipment");
        lines.add("Only generates while its chunk is loaded");
        return lines;
    }
}
