package ru.livingvillages.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import ru.livingvillages.domain.Village;
import ru.livingvillages.i18n.Messages;
import java.util.*;

/** Relabels derived displays, without changing PDC identity, item counts or operation snapshots. */
final class LanguageSupport implements Listener {
    private final LivingVillagesPlugin plugin;
    private final Set<UUID> pending=new LinkedHashSet<>();
    private static final NamespacedKey MAP=new NamespacedKey("livingvillages","map");
    LanguageSupport(LivingVillagesPlugin plugin){this.plugin=plugin;}
    static void reasons(Village v){
        v.developmentReason=Messages.saved(v.developmentReason);v.expansionReason=Messages.saved(v.expansionReason);
        v.planningReason=Messages.saved(v.planningReason);v.supplyReason=Messages.saved(v.supplyReason);
        v.council.reason=Messages.saved(v.council.reason);for(var g:v.council.goals)g.reason=Messages.saved(g.reason);
        for(var b:v.buildings)b.damageReason=Messages.saved(b.damageReason);
        for(var p:v.projects)p.reason=Messages.saved(p.reason);
    }
    void enqueue(Player player){pending.add(player.getUniqueId());}
    void pulse(){
        var batch=pending.stream().limit(4).toList();
        for(UUID id:batch){pending.remove(id);Player p=Bukkit.getPlayer(id);if(p==null)continue;
            if(plugin.transactions.players.contains(id)){pending.add(id);continue;}
            relabel(p.getInventory());
        }
    }
    void refresh(){
        plugin.menus.languageChanged();plugin.adminMenus.inputs.clear();
        for(var v:plugin.villages.values())reasons(v);
        for(Player p:Bukkit.getOnlinePlayers()){
            var holder=p.getOpenInventory().getTopInventory().getHolder();
            Class<?> owner=holder==null?null:holder.getClass().getEnclosingClass();
            if(owner==Menus.class||owner==AdminMenus.class)p.closeInventory();
            enqueue(p);
        }
        // Existing entities and hall signs refresh through their normal bounded pulses.
        plugin.hallDisplays.lastConflict=Messages.saved(plugin.hallDisplays.lastConflict);
        plugin.worldWorks.lastPlacementReason=Messages.saved(plugin.worldWorks.lastPlacementReason);
        plugin.hallDisplays.languageChanged();
    }
    private void relabel(Inventory inventory){
        for(int slot=0;slot<Math.min(54,inventory.getSize());slot++){
            ItemStack original=inventory.getItem(slot);if(original==null)continue;
            ItemStack translated=relabel(original);if(!translated.equals(original))inventory.setItem(slot,translated);
        }
    }
    ItemStack relabel(ItemStack original){
        int denomination=plugin.coins.value(original);
        String mapId=original.getPersistentDataContainer().get(MAP,PersistentDataType.STRING);
        if(denomination==0&&mapId==null)return original;
        ItemStack next=original.clone();var meta=next.getItemMeta();
        if(denomination>0){var template=plugin.coins.coin(denomination,1).getItemMeta();meta.itemName(template.itemName());meta.displayName(null);meta.lore(template.lore());}
        else {
            Village v;try{v=plugin.villages.get(UUID.fromString(mapId));}catch(IllegalArgumentException e){return original;}
            if(v==null)return original;
            TooltipText.apply(meta,Messages.text("План: ")+v.name,NamedTextColor.WHITE,List.of(Messages.text("Координаты: ")+v.x+" "+v.z));
        }
        next.setItemMeta(meta);return next;
    }
    private static Component component(Component component){
        Component result=component instanceof TextComponent text?text.content(Messages.saved(text.content())):component;
        return result.children(component.children().stream().map(LanguageSupport::component).toList());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void open(InventoryOpenEvent e){
        if(!(e.getPlayer() instanceof Player p)||plugin.transactions.players.contains(p.getUniqueId()))return;
        relabel(p.getInventory());relabel(e.getInventory());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void pickup(EntityPickupItemEvent e){
        if(!(e.getEntity() instanceof Player p)||plugin.transactions.players.contains(p.getUniqueId()))return;
        e.getItem().setItemStack(relabel(e.getItem().getItemStack()));
    }
}
