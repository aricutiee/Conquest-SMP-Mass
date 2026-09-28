package dev.turtleroles.service;

import org.bukkit.inventory.ItemStack;

/** Simulates all merges before changing an inventory or reserving a cooldown. */
public final class KitInventoryPlan {
    private KitInventoryPlan() {}
    public static ItemStack[] fit(ItemStack[] storage, ItemStack[] rewards, int inventoryLimit) {
        ItemStack[] result=new ItemStack[storage.length];
        for(int i=0;i<storage.length;i++)result[i]=empty(storage[i])?null:storage[i].clone();
        for(ItemStack reward:rewards) {
            if(empty(reward))continue;
            int remaining=reward.getAmount();
            int limit=Math.max(1,Math.min(inventoryLimit,reward.getMaxStackSize()));
            for(ItemStack slot:result) {
                if(slot==null || !slot.isSimilar(reward))continue;
                int amount=Math.min(remaining,Math.max(0,limit-slot.getAmount()));
                slot.setAmount(slot.getAmount()+amount);remaining-=amount;
                if(remaining==0)break;
            }
            for(int i=0;i<result.length && remaining>0;i++) {
                if(result[i]!=null)continue;
                int amount=Math.min(limit,remaining);
                result[i]=reward.clone();result[i].setAmount(amount);remaining-=amount;
            }
            if(remaining>0)return null;
        }
        return result;
    }
    private static boolean empty(ItemStack item){return item==null || item.getType().isAir() || item.getAmount()<=0;}
}
