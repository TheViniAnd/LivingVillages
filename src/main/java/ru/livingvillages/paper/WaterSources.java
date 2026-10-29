package ru.livingvillages.paper;

import ru.livingvillages.i18n.Messages;

import org.bukkit.*;
import org.bukkit.block.data.Levelled;
import ru.livingvillages.domain.*;
import java.util.*;

/** A verified renewable 2x2 source; extraction uses a bucket in the one logical production path. */
final class WaterSources {
    private WaterSources(){}
    private static boolean source(World world,int x,int y,int z){if(!world.isChunkLoaded(x>>4,z>>4))return false;var block=world.getBlockAt(x,y,z);return block.getType()==Material.WATER && block.getBlockData() instanceof Levelled level && level.getLevel()==0;}
    static Set<String> square(LivingVillagesPlugin plugin,Village village,World world,int x,int y,int z){
        if(!plugin.content.config.getBoolean("water.enabled",true) || !Boolean.TRUE.equals(world.getGameRuleValue(GameRules.WATER_SOURCE_CONVERSION)) || y<=world.getMinHeight())return Set.of();
        var result=new LinkedHashSet<String>();for(int dx=0;dx<2;dx++)for(int dz=0;dz<2;dz++){
            int bx=x+dx,bz=z+dz;if(!Territory.inside(village,world.getUID(),bx,bz) || !source(world,bx,y,bz))return Set.of();
            if(village.buildings.stream().anyMatch(b->b.owner!=null && Territory.plot(b,bx,bz)))return Set.of();
            if(!world.getBlockAt(bx,y-1,bz).getType().isSolid() && !source(world,bx,y-1,bz))return Set.of();result.add(bx+":"+y+":"+bz);
        }
        try{plugin.worldWorks.checkGuards(world,x,z,x+1,z+1);}catch(IllegalArgumentException denied){return Set.of();}return result;
    }
    static void verifyLoaded(LivingVillagesPlugin plugin,Village v){var w=Bukkit.getWorld(v.world);if(w==null)return;
        for(var b:v.buildings)if((b.complete || v.projects.stream().anyMatch(project->project.building.equals(b.id) && project.active())) && "water_supply".equals(b.recipe) && !b.resourceCells.isEmpty()){
            int minX=Integer.MAX_VALUE,minY=0,minZ=Integer.MAX_VALUE;boolean loaded=true;for(String cell:b.resourceCells){String[] parts=cell.split(":");int x=Integer.parseInt(parts[0]),y=Integer.parseInt(parts[1]),z=Integer.parseInt(parts[2]);loaded&=w.isChunkLoaded(x>>4,z>>4);minX=Math.min(minX,x);minZ=Math.min(minZ,z);minY=y;}
            if(loaded){b.resourceConfirmed=square(plugin,v,w,minX,minY,minZ).equals(b.resourceCells);if(!b.resourceConfirmed)v.supplyReason=Messages.text("Водозабор: источник изменён или закрыт защитой; нужна новая разведка");}
        }
    }
}
