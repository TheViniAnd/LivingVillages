package ru.livingvillages.paper;

import ru.livingvillages.i18n.Messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import ru.livingvillages.domain.Money;

public final class Coins {
    private static final NamespacedKey CURRENCY=new NamespacedKey("livingvillages","currency"), VALUE=new NamespacedKey("livingvillages","denomination"), FORMAT=new NamespacedKey("livingvillages","format");
    public ItemStack coin(int value,int amount) {
        Material material=material(value);
        ItemStack item=new ItemStack(material,amount);var meta=item.getItemMeta();
        meta.itemName(Component.text(value==1?Messages.text("Бронзовая монета"):value==64?Messages.text("Серебряная монета"):Messages.text("Золотая монета"),value==4096?NamedTextColor.GOLD:value==64?NamedTextColor.WHITE:NamedTextColor.YELLOW));
        meta.lore(List.of(Component.text(value+Messages.text(" бронзовых · LivingVillages"),NamedTextColor.GRAY)));
        var pdc=meta.getPersistentDataContainer();pdc.set(CURRENCY,PersistentDataType.STRING,"livingvillages:coin");pdc.set(VALUE,PersistentDataType.INTEGER,value);pdc.set(FORMAT,PersistentDataType.INTEGER,1);
        // Use only the material's standard Minecraft texture. PDC carries the currency identity.
        item.setItemMeta(meta);return item;
    }
    public int value(ItemStack item) {
        if(item==null || !item.hasItemMeta())return 0;
        var p=item.getItemMeta().getPersistentDataContainer();
        if(!"livingvillages:coin".equals(p.get(CURRENCY,PersistentDataType.STRING)) || !Integer.valueOf(1).equals(p.get(FORMAT,PersistentDataType.INTEGER)))return 0;
        Integer n=p.get(VALUE,PersistentDataType.INTEGER);
        return n!=null && (n==1 || n==64 || n==4096) && item.getType()==material(n)?n:0;
    }
    public long total(ItemStack[] inventory) {
        long sum=0;for(ItemStack item:inventory) if(item!=null) sum=Money.add(sum,(long)value(item)*item.getAmount());return sum;
    }
    public ItemStack[] balance(ItemStack[] source,long difference) {
        ItemStack[] next=cloneItems(source);long old=total(next),target;
        try{target=Money.checked(Math.addExact(old,difference));}catch(Exception e){throw new IllegalArgumentException(Messages.text("Недостаточно монет или слишком большая сумма"));}
        for(int i=0;i<next.length;i++)if(value(next[i])>0)next[i]=null;
        for(var e:Money.split(target).entrySet()) {
            int left=e.getValue();while(left>0){int count=Math.min(64,left);add(next,coin(e.getKey(),count));left-=count;}
        }
        return next;
    }
    public static ItemStack[] cloneItems(ItemStack[] source) {return Arrays.stream(source).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);}
    public static boolean plain(ItemStack item,Material material) {return item!=null && item.getType()==material && !item.hasItemMeta();}
    public static void removePlain(ItemStack[] inventory,Material material,int count) {
        if(Arrays.stream(inventory).filter(i->plain(i,material)).mapToInt(ItemStack::getAmount).sum()<count)throw new IllegalArgumentException(Messages.text("Нужны обычные предметы без переименования: ")+material);
        for(int i=0;i<inventory.length && count>0;i++)if(plain(inventory[i],material)) {
            int take=Math.min(count,inventory[i].getAmount());count-=take;
            inventory[i].setAmount(inventory[i].getAmount()-take);if(inventory[i].getAmount()==0)inventory[i]=null;
        }
    }
    public static void add(ItemStack[] inventory,ItemStack item) {
        int left=item.getAmount();
        for(ItemStack slot:inventory)if(slot!=null && slot.isSimilar(item) && slot.getAmount()<slot.getMaxStackSize()) {
            int n=Math.min(left,slot.getMaxStackSize()-slot.getAmount());slot.setAmount(slot.getAmount()+n);left-=n;if(left==0)return;
        }
        for(int i=0;i<inventory.length && left>0;i++)if(inventory[i]==null || inventory[i].getType().isAir()) {
            int n=Math.min(left,item.getMaxStackSize());inventory[i]=item.clone();inventory[i].setAmount(n);left-=n;
        }
        if(left>0)throw new IllegalArgumentException(Messages.text("Освободите место в инвентаре"));
    }
    public static String encode(ItemStack[] items) {
        List<String> out=new ArrayList<>();for(ItemStack item:items)out.add(item==null||item.getType().isAir()?null:Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        return ru.livingvillages.persistence.Database.JSON.toJson(out);
    }
    public static ItemStack[] decode(String data) {
        String[] values=ru.livingvillages.persistence.Database.JSON.fromJson(data,String[].class);
        return Arrays.stream(values).map(v->v==null?null:ItemStack.deserializeBytes(Base64.getDecoder().decode(v))).toArray(ItemStack[]::new);
    }
    private static Material material(int value) {return switch(value){case 1->Material.COPPER_NUGGET;case 64->Material.IRON_NUGGET;case 4096->Material.SUNFLOWER;default->throw new IllegalArgumentException(Messages.text("Неверный номинал"));};}
}
