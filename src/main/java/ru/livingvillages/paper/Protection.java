package ru.livingvillages.paper;

import ru.livingvillages.i18n.Messages;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import ru.livingvillages.domain.Village;
import java.util.*;

public final class Protection implements Listener {
    private final LivingVillagesPlugin plugin;
    private final Set<UUID> bypassOff=new HashSet<>();
    public Protection(LivingVillagesPlugin plugin){this.plugin=plugin;}
    public boolean bypass(Player player){return player.hasPermission("livingvillages.bypass") && !bypassOff.contains(player.getUniqueId());}
    public void setBypass(Player player,boolean enabled){if(!player.hasPermission("livingvillages.bypass"))throw new IllegalArgumentException(Messages.text("Требуется livingvillages.bypass"));if(enabled)bypassOff.remove(player.getUniqueId());else bypassOff.add(player.getUniqueId());plugin.db.audit(player.getUniqueId(),null,Messages.text("Обход защиты блоков: ")+enabled);}
    private Village village(Location l){return plugin.at(l);}
    private boolean locked(Player p){return plugin.transactions.players.contains(p.getUniqueId());}
    private boolean protectedAt(Location l){return plugin.worldWorks.reservedAt(l) || plugin.content.config.getBoolean("protection.enabled",true) && village(l)!=null;}
    private boolean allowed(Player p,Location l){
        if(plugin.worldWorks.reservedAt(l))return false;Village v=village(l);if(v==null || !plugin.content.config.getBoolean("protection.enabled",true))return true;
        if(bypass(p))return true;
        return v.buildings.stream().anyMatch(b->p.getUniqueId().equals(b.owner) && (b.complete || v.projects.stream().anyMatch(q->q.building.equals(b.id) && q.state.equals("CANCELLED"))) && ru.livingvillages.domain.Territory.plot(b,l.getBlockX(),l.getBlockZ()));
    }
    private void harm(Player p,Location at){Village v=village(at);if(v!=null && !plugin.transactions.busy(v)){v.relation(p.getUniqueId()).add(-2);plugin.save(v);}}
    private Player attacker(Entity entity){if(entity instanceof Player p)return p;if(entity instanceof Projectile p && p.getShooter() instanceof Player player)return player;if(entity instanceof TNTPrimed t && t.getSource() instanceof Player p)return p;return null;}
    @EventHandler(ignoreCancelled=true)public void hanging(org.bukkit.event.hanging.HangingBreakEvent e){if(protectedAt(e.getEntity().getLocation()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void interactEntity(PlayerInteractEntityEvent e){if(locked(e.getPlayer()) || e.getRightClicked() instanceof ItemFrame && protectedAt(e.getRightClicked().getLocation()) && !allowed(e.getPlayer(),e.getRightClicked().getLocation()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void manipulate(PlayerArmorStandManipulateEvent e){if(locked(e.getPlayer()) || !allowed(e.getPlayer(),e.getRightClicked().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void breakBlock(BlockBreakEvent e){if(locked(e.getPlayer()) || !allowed(e.getPlayer(),e.getBlock().getLocation())){e.setCancelled(true);if(!locked(e.getPlayer())){harm(e.getPlayer(),e.getBlock().getLocation());plugin.tell(e.getPlayer(),Messages.text("Эта территория принадлежит поселению."));}}else auditEdit(e.getPlayer(),e.getBlock(),Messages.text("Разрешено разрушение"));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void place(BlockPlaceEvent e){if(locked(e.getPlayer()) || plugin.coins.value(e.getItemInHand())>0 || !allowed(e.getPlayer(),e.getBlockPlaced().getLocation()) || e instanceof BlockMultiPlaceEvent multi && multi.getReplacedBlockStates().stream().anyMatch(b->!allowed(e.getPlayer(),b.getLocation())))e.setCancelled(true);else auditEdit(e.getPlayer(),e.getBlockPlaced(),Messages.text("Разрешена установка"));}
    private void auditEdit(Player player,org.bukkit.block.Block block,String action){Village v=village(block.getLocation());if(v!=null && bypass(player))plugin.db.audit(player.getUniqueId(),v.id,Messages.text("АДМИН: ")+action+" "+block.getType()+" "+block.getX()+" "+block.getY()+" "+block.getZ());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void interact(PlayerInteractEvent e){
        if(locked(e.getPlayer())){e.setCancelled(true);return;}
        if(e.getClickedBlock()!=null && plugin.worldWorks.reservedAt(e.getClickedBlock().getLocation())){e.setCancelled(true);return;}
        if(plugin.coins.value(e.getItem())>0){e.setCancelled(true);return;}
        if(e.getClickedBlock()!=null && protectedAt(e.getClickedBlock().getLocation()) && !allowed(e.getPlayer(),e.getClickedBlock().getLocation())) {
            Material m=e.getClickedBlock().getType();
            if(m==Material.BARREL || m==Material.CHEST){e.setCancelled(true);}
            else if(e.getAction()==Action.PHYSICAL || !(m.name().endsWith("_DOOR") || m.name().endsWith("_GATE")))e.setCancelled(true);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void bucket(PlayerBucketEmptyEvent e){if(locked(e.getPlayer()) || !allowed(e.getPlayer(),e.getBlock().getRelative(e.getBlockFace()).getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void bucketFill(PlayerBucketFillEvent e){if(locked(e.getPlayer()) || !allowed(e.getPlayer(),e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void fish(PlayerFishEvent e){
        Location l=e.getHook().getLocation();Village v=village(l);if(v==null)return;
        boolean working=v.buildings.stream().filter(b->b.complete && b.owner==null).anyMatch(b->{for(int dy=-1;dy<=1;dy++)if(b.resourceCells.contains(l.getBlockX()+":"+(l.getBlockY()+dy)+":"+l.getBlockZ()))return true;return false;});
        if(locked(e.getPlayer()) || working){e.setCancelled(true);if(working)plugin.tell(e.getPlayer(),Messages.text("Это рабочий водоём поселения. Рыбачьте за его пределами."));}
    }
    @EventHandler(ignoreCancelled=true)public void explode(EntityExplodeEvent e){e.blockList().removeIf(b->protectedAt(b.getLocation()));}
    @EventHandler(ignoreCancelled=true)public void explodeBlock(BlockExplodeEvent e){e.blockList().removeIf(b->protectedAt(b.getLocation()));}
    @EventHandler(ignoreCancelled=true)public void burn(BlockBurnEvent e){if(protectedAt(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void ignite(BlockIgniteEvent e){if(protectedAt(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void flow(BlockFromToEvent e){if(protectedAt(e.getToBlock().getLocation()) || protectedAt(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void extend(BlockPistonExtendEvent e){if(protectedAt(e.getBlock().getLocation()) || e.getBlocks().stream().anyMatch(b->protectedAt(b.getLocation()) || protectedAt(b.getRelative(e.getDirection()).getLocation())))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void retract(BlockPistonRetractEvent e){if(protectedAt(e.getBlock().getLocation()) || e.getBlocks().stream().anyMatch(b->protectedAt(b.getLocation()) || protectedAt(b.getRelative(e.getDirection()).getLocation())))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void entityBlock(EntityChangeBlockEvent e){if(protectedAt(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void spread(BlockSpreadEvent e){if(protectedAt(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void fertilize(BlockFertilizeEvent e){if(protectedAt(e.getBlock().getLocation()) || e.getBlocks().stream().anyMatch(b->protectedAt(b.getLocation())))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void tree(org.bukkit.event.world.StructureGrowEvent e){if(protectedAt(e.getLocation()) || e.getBlocks().stream().anyMatch(b->protectedAt(b.getLocation())))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void form(BlockFormEvent e){if(protectedAt(e.getBlock().getLocation()) && (!(e instanceof EntityBlockFormEvent by) || !(by.getEntity() instanceof Player player) || !allowed(player,e.getBlock().getLocation())))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void absorb(SpongeAbsorbEvent e){e.getBlocks().removeIf(b->protectedAt(b.getLocation()));}
    @EventHandler(ignoreCancelled=true)public void portal(org.bukkit.event.world.PortalCreateEvent e){if(e.getBlocks().stream().anyMatch(b->protectedAt(b.getLocation())))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void grow(BlockGrowEvent e){if(protectedAt(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void fade(BlockFadeEvent e){if(protectedAt(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void inventoryMove(InventoryMoveItemEvent e){if(plugin.coins.value(e.getItem())>0 || protectedInventory(e.getSource()) || protectedInventory(e.getDestination()))e.setCancelled(true);}
    private boolean protectedInventory(Inventory i){return i.getLocation()!=null && protectedAt(i.getLocation());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void open(InventoryOpenEvent e){if(e.getPlayer() instanceof Player p && (locked(p) || e.getInventory().getLocation()!=null && !allowed(p,e.getInventory().getLocation())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void inventory(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;if(locked(p)){e.setCancelled(true);return;}
        boolean coin=plugin.coins.value(e.getCurrentItem())>0 || plugin.coins.value(e.getCursor())>0;
        if(e.getHotbarButton()>=0)coin|=plugin.coins.value(p.getInventory().getItem(e.getHotbarButton()))>0;
        if(coin && !storageType(e.getView().getTopInventory().getType()) && (e.getRawSlot()<e.getView().getTopInventory().getSize() || e.isShiftClick()))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void inventoryDrag(InventoryDragEvent e){
        if(e.getWhoClicked() instanceof Player p && (locked(p) || plugin.coins.value(e.getOldCursor())>0 && !storageType(e.getView().getTopInventory().getType()) && e.getRawSlots().stream().anyMatch(i->i<e.getView().getTopInventory().getSize())))e.setCancelled(true);
    }
    private boolean storageType(InventoryType t){return t==InventoryType.CHEST || t==InventoryType.BARREL || t==InventoryType.SHULKER_BOX || t==InventoryType.ENDER_CHEST || t==InventoryType.PLAYER;}
    @EventHandler public void craft(PrepareItemCraftEvent e){if(Arrays.stream(e.getInventory().getMatrix()).anyMatch(i->plugin.coins.value(i)>0))e.getInventory().setResult(null);}
    @EventHandler(ignoreCancelled=true)public void automatedCraft(CrafterCraftEvent e){if(e.getBlock().getState() instanceof org.bukkit.block.Crafter c && Arrays.stream(c.getInventory().getContents()).anyMatch(i->plugin.coins.value(i)>0))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void smelt(FurnaceSmeltEvent e){if(plugin.coins.value(e.getSource())>0)e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void burnCoin(FurnaceBurnEvent e){if(plugin.coins.value(e.getFuel())>0)e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void consume(PlayerItemConsumeEvent e){if(locked(e.getPlayer()) || plugin.coins.value(e.getItem())>0)e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void drop(PlayerDropItemEvent e){if(locked(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void pickup(EntityPickupItemEvent e){if(e.getEntity() instanceof Player p && locked(p))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void swap(PlayerSwapHandItemsEvent e){if(locked(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void command(PlayerCommandPreprocessEvent e){if(locked(e.getPlayer())){e.setCancelled(true);plugin.tell(e.getPlayer(),Messages.text("Команды временно недоступны: сохраняется операция."));}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void damage(EntityDamageEvent e){
        if(e instanceof EntityDamageByEntityEvent by){Player player=attacker(by.getDamager());if(player!=null && locked(player)){e.setCancelled(true);return;}}
        if(e.getEntity() instanceof ItemFrame && protectedAt(e.getEntity().getLocation())){e.setCancelled(true);return;}
        if(e.getEntity() instanceof Player p && locked(p)){e.setCancelled(true);return;}
        if(plugin.npcs.isNpc(e.getEntity())){
            Village v=plugin.villages.get(plugin.npcs.villageId(e.getEntity()));if(v!=null && plugin.transactions.busy(v))e.setCancelled(true);
            
        }
    }
    public static UUID responsible(Entity source){if(source instanceof Player p)return p.getUniqueId();if(source instanceof Projectile p && p.getShooter() instanceof Entity e)return responsible(e);if(source instanceof TNTPrimed t && t.getSource()!=null)return responsible(t.getSource());if(source instanceof Tameable tame && tame.getOwner()!=null)return tame.getOwner().getUniqueId();return null;}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void residentHurt(EntityDamageByEntityEvent e){if(e.getFinalDamage()<=0 || !plugin.npcs.isNpc(e.getEntity()) || e.getFinalDamage()>=((LivingEntity)e.getEntity()).getHealth())return;var v=plugin.villages.get(plugin.npcs.villageId(e.getEntity()));var r=plugin.npcs.resident(e.getEntity());UUID player=responsible(e.getDamager());if(v!=null && r!=null && player!=null && !plugin.transactions.busy(v)){ru.livingvillages.domain.Social.harm(v,player,r.id,plugin.content.rules,false);plugin.save(v);}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void defense(EntityDeathEvent e){
        if(!(e.getEntity() instanceof Enemy) || e.getEntity().getKiller()==null)return;
        Player p=e.getEntity().getKiller();Village v=village(e.getEntity().getLocation());
        if(v!=null && plugin.npcs.danger(v) && !plugin.transactions.busy(v)){boolean tracked=plugin.npcs.isThreat(v,e.getEntity().getUniqueId());if(!tracked)return;var relation=v.relation(p.getUniqueId());relation.deeds.add("defense");ru.livingvillages.domain.Reputation.award(v,p.getUniqueId(),plugin.content.rules,"defense",2);ru.livingvillages.domain.Missions.defeat(v,p.getUniqueId(),e.getEntity().getUniqueId(),plugin.content.rules,true);plugin.save(v);}
    }
}
