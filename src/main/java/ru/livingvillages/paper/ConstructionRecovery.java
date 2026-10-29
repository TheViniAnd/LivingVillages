package ru.livingvillages.paper;

import ru.livingvillages.i18n.Messages;

import org.bukkit.*;
import org.bukkit.block.TileState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.*;
import org.bukkit.event.world.ChunkLoadEvent;
import ru.livingvillages.domain.*;
import java.util.*;

/** Bounded replay of paid ACTIVE prefixes only. Completed personal property is never replayed. */
final class ConstructionRecovery implements Listener {
    private final LivingVillagesPlugin plugin;
    private final Map<UUID,Check> checks=new LinkedHashMap<>();private final Map<UUID,Integer> verified=new HashMap<>();
    private static final class Check {final UUID village,project;List<PaidProjection.Cell> cells;int cursor,paidIndex;Check(UUID v,UUID p){village=v;project=p;}}
    long inspected,restored;
    ConstructionRecovery(LivingVillagesPlugin p){plugin=p;}
    void loaded(Village v){for(var p:v.projects)if(p.active()&&p.index>verified.getOrDefault(p.id,0)&&!checks.containsKey(p.id))checks.put(p.id,new Check(v.id,p.id));}
    @EventHandler(priority=EventPriority.MONITOR)public void chunk(ChunkLoadEvent event){for(var v:plugin.villages.values())if(v.world.equals(event.getWorld().getUID()))for(var p:v.projects)if(p.active()&&p.index>0&&p.blocks.stream().limit(p.index).anyMatch(c->c.x>>4==event.getChunk().getX()&&c.z>>4==event.getChunk().getZ())){verified.remove(p.id);checks.put(p.id,new Check(v.id,p.id));}}
    boolean pending(Village v){return checks.values().stream().anyMatch(c->c.village.equals(v.id));}
    int pulse(Village v,int budget){int used=0;World world=Bukkit.getWorld(v.world);if(v.paused||world==null||plugin.transactions.busy(v))return 0;
        for(var check:new ArrayList<>(checks.values())){if(used>=budget)break;if(!v.id.equals(check.village))continue;var p=v.projects.stream().filter(q->q.id.equals(check.project)&&q.active()).findFirst().orElse(null);if(p==null){checks.remove(check.project);continue;}if(check.cells==null){check.cells=PaidProjection.prefix(v,p);check.paidIndex=p.index;}
            while(used<budget&&check.cursor<check.cells.size()){
                var cell=check.cells.get(check.cursor++);used++;inspected++;if(!world.isChunkLoaded(cell.x()>>4,cell.z()>>4))continue;
                var block=world.getBlockAt(cell.x(),cell.y(),cell.z());String actual=block.getBlockData().getAsString();if(WorldWorks.matchesExpected(cell.expected(),actual))continue;
                // Native crop/tree display and hall signs have their own authoritative reconciliation.
                var b=p.building(v);if(plugin.hallDisplays.managed(v,block)||GroveVisuals.compatible(cell.expected(),actual))continue;
                if(block.getState(false) instanceof TileState||cell.recorded().stream().noneMatch(s->WorldWorks.matchesExpected(s,actual))){p.state="PAUSED";p.reason=Messages.text("Сверка оплаченной стройки: посторонний блок ")+cell.x()+" "+cell.y()+" "+cell.z();checks.remove(check.project);plugin.save(v);return used;}
                try{plugin.worldWorks.checkGuards(world,cell.x(),cell.z(),cell.x(),cell.z());}catch(IllegalArgumentException denied){check.cursor--;p.reason=Messages.text("Сверка: ")+denied.getMessage();return used;}
                var data=Bukkit.createBlockData(cell.expected());if(data.getMaterial().isSolid()){
                    var area=new org.bukkit.util.BoundingBox(cell.x(),cell.y(),cell.z(),cell.x()+1,cell.y()+(data.getMaterial().name().endsWith("_FENCE")||data.getMaterial().name().endsWith("_WALL")?1.5:1),cell.z()+1);
                    if(world.getNearbyEntities(new Location(world,cell.x()+.5,cell.y()+.5,cell.z()+.5),1,2,1).stream().anyMatch(e->e instanceof LivingEntity&&e.getBoundingBox().overlaps(area))){check.cursor--;plugin.npcs.clearConstruction(v,area);p.reason=Messages.text("Сверка: освободите место оплаченного блока");return used;}
                }
                block.setBlockData(data,false);restored++;
            }
            if(check.cursor>=check.cells.size()){checks.remove(check.project);verified.merge(check.project,check.paidIndex,Math::max);}else p.reason=Messages.text("Проверка оплаченной части стройки после загрузки");
        }return used;
    }
}
