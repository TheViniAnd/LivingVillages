package ru.livingvillages.paper;

import org.bukkit.*;
import ru.livingvillages.domain.*;
import java.util.*;

/** Bounded terrain survey. Unknown/unloaded surroundings stay unknown on the map. */
public final class Cartography {
    private final LivingVillagesPlugin plugin;
    private final Map<UUID,Integer> cells=new HashMap<>();
    private final Map<UUID,Long> nextPass=new HashMap<>();
    private int cursor;public long samples;
    Cartography(LivingVillagesPlugin plugin){this.plugin=plugin;}
    public void pulse(){
        var villages=new ArrayList<>(plugin.villages.values());if(villages.isEmpty())return;
        int budget=plugin.content.config.getInt("limits.map-samples-per-pass",64),attempts=budget*4;long now=System.nanoTime(),deadline=now+plugin.content.config.getLong("limits.map-budget-ms",2)*1_000_000L;
        while(budget>0 && attempts-->0 && System.nanoTime()<deadline){Village v=villages.get(Math.floorMod(cursor++,villages.size()));World world=Bukkit.getWorld(v.world);if(world==null || plugin.transactions.busy(v) || now<nextPass.getOrDefault(v.id,0L))continue;
            if(v.mapTerrain==null || v.mapTerrain.length!=1024 || v.mapSurveyRadius!=VillageMap.extent(v)){v.mapTerrain=new int[1024];v.mapSurveyRadius=VillageMap.extent(v);cells.put(v.id,0);}
            int index=cells.getOrDefault(v.id,0),x=VillageMap.terrainCoordinate(v,index%32,true),z=VillageMap.terrainCoordinate(v,index/32,false);cells.put(v.id,(index+1)%1024);
            if(world.isChunkLoaded(x>>4,z>>4)){int y=world.getHighestBlockYAt(x,z,HeightMap.WORLD_SURFACE);int rgb=world.getBlockAt(x,y,z).getBlockData().getMapColor().asRGB();v.mapTerrain[index]=0xFF000000|rgb;samples++;budget--;}
            if(index==1023)nextPass.put(v.id,now+plugin.content.config.getLong("interface.map-refresh-seconds",120)*1_000_000_000L);
        }
    }
}
