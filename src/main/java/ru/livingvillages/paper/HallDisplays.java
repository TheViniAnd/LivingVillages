package ru.livingvillages.paper;

import ru.livingvillages.i18n.Messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.sign.Side;
import org.bukkit.event.*;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import ru.livingvillages.domain.*;
import java.util.*;

/** Derived, protected public notices. Installs only in the saved hall's expected interior air. */
public final class HallDisplays implements Listener {
    public record Point(int x,int y,int z,BlockFace facing){}
    public record Anchor(Village village,Village.Building hall,String kind,Point point){}
    private final LivingVillagesPlugin plugin;
    private final NamespacedKey villageKey=new NamespacedKey("livingvillages","board_village"),hallKey=new NamespacedKey("livingvillages","board_hall"),kindKey=new NamespacedKey("livingvillages","board_kind");
    private final Map<String,Long> nextUpdate=new HashMap<>();
    private int cursor;public long updates;public String lastConflict=Messages.text("нет");
    public HallDisplays(LivingVillagesPlugin plugin){this.plugin=plugin;}
    void languageChanged(){nextUpdate.clear();}
    public static Point point(Village.Building b,int offset,int height){
        int width=b.rotation%2==0?b.width:b.depth,depth=b.rotation%2==0?b.depth:b.width,x=width-2,z=(b.noticeDepth>0?b.noticeDepth:depth)/2+offset;
        int rx=switch(b.rotation){case 1->depth-1-z;case 2->width-1-x;case 3->z;default->x;},rz=switch(b.rotation){case 1->x;case 2->depth-1-z;case 3->width-1-x;default->z;};
        BlockFace facing=switch(b.rotation){case 1->BlockFace.NORTH;case 2->BlockFace.EAST;case 3->BlockFace.SOUTH;default->BlockFace.WEST;};
        return new Point(b.x+rx,b.workY+height,b.z+rz,facing);
    }
    public static List<Anchor> anchors(Village v,Village.Building b){
        List<Anchor> result=new ArrayList<>();List<String> kinds=List.of("people","food","treasury","construction","needs","development");int i=0;
        for(int height=0;height<3;height++)for(int side:new int[]{-1,1})result.add(new Anchor(v,b,kinds.get(i++),point(b,side,height)));
        result.add(new Anchor(v,b,"goal",point(b,-2,1)));result.add(new Anchor(v,b,"materials1",point(b,-2,2)));result.add(new Anchor(v,b,"materials2",point(b,2,1)));result.add(new Anchor(v,b,"materials3",point(b,2,2)));return result;
    }
    private boolean owned(Sign sign,Village v,Village.Building hall){var data=sign.getPersistentDataContainer();return v.id.toString().equals(data.get(villageKey,PersistentDataType.STRING)) && hall.id.toString().equals(data.get(hallKey,PersistentDataType.STRING)) && anchors(v,hall).stream().anyMatch(a->a.point.x==sign.getX() && a.point.y==sign.getY() && a.point.z==sign.getZ() && a.kind.equals(data.get(kindKey,PersistentDataType.STRING)));}
    public boolean managed(Village v,Block block){if(!Tag.ALL_SIGNS.isTagged(block.getType()) || !(block.getState(false) instanceof Sign sign))return false;return v.buildings.stream().filter(b->b.kind.startsWith("hall")).anyMatch(b->owned(sign,v,b));}
    public void pulse(){
        List<Anchor> anchors=new ArrayList<>();for(Village v:plugin.villages.values())for(var b:v.buildings)if(b.complete && !b.damaged && b.kind.startsWith("hall"))anchors.addAll(anchors(v,b));
        int budget=plugin.content.config.getInt("limits.boards-per-pass",6);long now=System.nanoTime();Set<UUID> maps=new HashSet<>();Map<UUID,List<TownInfo.Card>> cards=new HashMap<>();
        for(int i=0;i<anchors.size() && budget>0;i++){
            Anchor a=anchors.get(Math.floorMod(cursor++,anchors.size()));var p=a.point;Village v=a.village;World world=Bukkit.getWorld(v.world);String key=v.id+":"+a.hall.id+":"+a.kind;
            if(world==null || plugin.transactions.busy(v) || now<nextUpdate.getOrDefault(key,0L) || !world.isChunkLoaded(p.x>>4,p.z>>4))continue;
            if(maps.add(v.id))plugin.installTownMap(v);
            nextUpdate.put(key,now+plugin.content.config.getLong("interface.sign-refresh-seconds",5)*1_000_000_000L);budget--;
            Block block=world.getBlockAt(p.x,p.y,p.z);boolean owned=block.getState(false) instanceof Sign existing && owned(existing,v,a.hall);
            if(!plugin.content.config.getBoolean("interface.town-signs",true)){if(owned)block.setType(Material.AIR,false);continue;}
            if(!owned){
                if(!block.getType().isAir()){lastConflict=Messages.text("Табличка ")+a.kind+Messages.text(": место занято ")+block.getType();continue;}
                boolean reservedAir=Projects.expected(v,a.hall.id).stream().anyMatch(cell->cell.x==p.x && cell.y==p.y && cell.z==p.z && Bukkit.createBlockData(cell.after).getMaterial().isAir());
                if(!reservedAir){lastConflict=Messages.text("В чертеже ратуши нет места для ")+a.kind;continue;}
                try{plugin.worldWorks.checkGuards(world,p.x,p.z,p.x,p.z);}catch(IllegalArgumentException e){lastConflict=e.getMessage();continue;}
                String wood=plugin.content.cultures.get(v.culture).palette().get("door").replace("_door","_wall_sign");block.setBlockData(Bukkit.createBlockData(wood+"[facing="+p.facing.name().toLowerCase(Locale.ROOT)+"]"),false);
            }
            if(!(block.getState(false) instanceof Sign sign))continue;
            var card=cards.computeIfAbsent(v.id,id->TownInfo.cards(v,plugin.content.rules,Russian::item,keyId->{var b=plugin.content.blueprints.get(keyId);return b==null?(keyId.equals("fortification")?Messages.text("Укрепления деревни"):Messages.saved(keyId)):b.name();},(int)(now/15_000_000_000L))).stream().filter(c->c.id().equals(a.kind)).findFirst().orElseThrow();
            var front=sign.getSide(Side.FRONT);boolean changed=!owned || !sign.isWaxed();
            for(int line=0;line<4;line++){Component text=Component.text(card.lines().get(line),line==0?NamedTextColor.DARK_BLUE:card.warning()?NamedTextColor.DARK_RED:NamedTextColor.BLACK);if(!front.line(line).equals(text)){front.line(line,text);changed=true;}}
            if(changed){sign.setWaxed(true);sign.getPersistentDataContainer().set(villageKey,PersistentDataType.STRING,v.id.toString());sign.getPersistentDataContainer().set(hallKey,PersistentDataType.STRING,a.hall.id.toString());sign.getPersistentDataContainer().set(kindKey,PersistentDataType.STRING,a.kind);sign.update(false,false);updates++;}
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(PlayerInteractEvent event){
        if(event.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || event.getHand()!=EquipmentSlot.HAND || event.getClickedBlock()==null || !(event.getClickedBlock().getState(false) instanceof Sign sign))return;
        String id=sign.getPersistentDataContainer().get(villageKey,PersistentDataType.STRING);Village v;try{v=plugin.villages.get(UUID.fromString(id));}catch(Exception e){return;}
        if(v==null || !managed(v,event.getClickedBlock()))return;event.setCancelled(true);
        if(Territory.inside(v,event.getPlayer().getWorld().getUID(),event.getPlayer().getX(),event.getPlayer().getZ()))plugin.discoverPlayer(event.getPlayer(),v);
        if(plugin.transactions.busy(v) || plugin.transactions.players.contains(event.getPlayer().getUniqueId())){plugin.tell(event.getPlayer(),Messages.text("Операция сохраняется; подождите."));return;}
        plugin.menus.board(event.getPlayer(),v,sign.getPersistentDataContainer().get(kindKey,PersistentDataType.STRING));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void edit(SignChangeEvent event){Village v=plugin.at(event.getBlock().getLocation());if(v!=null && managed(v,event.getBlock()))event.setCancelled(true);}
}
