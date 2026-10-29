package ru.livingvillages.paper;

import ru.livingvillages.i18n.Messages;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import ru.livingvillages.domain.*;

/** Public building containers are entrances to the one warehouse model, including for operators. */
public final class WarehouseAccess implements Listener {
    private final LivingVillagesPlugin plugin;
    public WarehouseAccess(LivingVillagesPlugin plugin){this.plugin=plugin;}
    static boolean container(Material m){return m==Material.CHEST||m==Material.TRAPPED_CHEST||m==Material.BARREL;}
    Village owner(Block block){if(!container(block.getType()))return null;for(var v:plugin.villages.values())if(v.world.equals(block.getWorld().getUID()))for(var b:v.buildings)if(b.owner==null && Territory.plot(b,block.getX(),block.getZ()) && Projects.expected(v,b.id).stream().anyMatch(c->c.x==block.getX()&&c.y==block.getY()&&c.z==block.getZ()&&Bukkit.createBlockData(c.after).getMaterial()==block.getType()))return v;return null;}
    private boolean legacy(Player p,Block block){return p.isOp()&&p.isSneaking()&&block.getState(false) instanceof InventoryHolder holder&&java.util.Arrays.stream(holder.getInventory().getContents()).anyMatch(i->i!=null&&!i.getType().isAir());}
    void show(Player p,Village v){if(plugin.transactions.players.contains(p.getUniqueId())||plugin.transactions.busy(v)){plugin.tell(p,Messages.text("Склад сохраняется; подождите."));return;}if(!Territory.inside(v,p.getWorld().getUID(),p.getX(),p.getZ())){plugin.tell(p,Messages.text("Подойдите к складу на территории деревни."));return;}plugin.discoverPlayer(p,v);plugin.menus.warehouse(p,v,0);}
    @EventHandler(priority=EventPriority.HIGHEST)public void click(PlayerInteractEvent e){if(e.getAction()!=Action.RIGHT_CLICK_BLOCK||e.getHand()!=EquipmentSlot.HAND||e.getClickedBlock()==null)return;var v=owner(e.getClickedBlock());if(v==null||legacy(e.getPlayer(),e.getClickedBlock()))return;e.setCancelled(true);show(e.getPlayer(),v);}
    @EventHandler(priority=EventPriority.HIGHEST)public void open(InventoryOpenEvent e){if(!(e.getPlayer() instanceof Player p)||e.getInventory().getLocation()==null)return;var block=e.getInventory().getLocation().getBlock();var v=owner(block);if(v==null||legacy(p,block))return;e.setCancelled(true);plugin.sync(()->show(p,v));}
}
