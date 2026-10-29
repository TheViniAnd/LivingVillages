package ru.livingvillages.paper;

import ru.livingvillages.i18n.Messages;

import org.bukkit.*;
import org.bukkit.block.Block;
import ru.livingvillages.domain.*;
import java.util.*;

/** Saved last-applied states, a shared block budget, loaded chunks only; no harvest here. */
public final class GroveVisuals {
    private final LivingVillagesPlugin plugin;private int cursor;
    GroveVisuals(LivingVillagesPlugin plugin){this.plugin=plugin;}
    public static boolean managed(Village.Building b,Block block){String key=block.getX()+":"+block.getY()+":"+block.getZ();return compatible(b.treeVisual.get(key),block.getBlockData().getAsString());}
    static boolean compatible(String expected,String actual){
        if(expected==null)return false;if(expected.equals(actual))return true;
        var a=Bukkit.createBlockData(expected);var b=Bukkit.createBlockData(actual);if(a.getMaterial()!=b.getMaterial())return false;
        if(a instanceof org.bukkit.block.data.type.Sapling seed && b instanceof org.bukkit.block.data.type.Sapling nativeSeed){nativeSeed.setStage(seed.getStage());if(a instanceof org.bukkit.block.data.type.MangrovePropagule p && b instanceof org.bukkit.block.data.type.MangrovePropagule q)q.setAge(p.getAge());return a.equals(b);}
        if(a instanceof org.bukkit.block.data.type.Leaves leaves && b instanceof org.bukkit.block.data.type.Leaves nativeLeaves){nativeLeaves.setDistance(leaves.getDistance());return a.equals(b);}return false;
    }
    int pulse(int sharedBudget){int remaining=Math.min(sharedBudget,plugin.content.config.getInt("limits.grove-blocks-per-pass",8)),used=0;var work=new ArrayList<Map.Entry<Village,Village.Building>>();for(var v:plugin.villages.values())for(var b:v.buildings)if(b.complete && !b.damaged && !b.trees.isEmpty())work.add(Map.entry(v,b));
        for(int n=0;n<work.size() && remaining>0;n++){var pair=work.get(Math.floorMod(cursor++,work.size()));var v=pair.getKey();var b=pair.getValue();if(plugin.transactions.busy(v))continue;World w=Bukkit.getWorld(v.world);if(w==null)continue;
            Map<String,String> baseline=new HashMap<>();for(var cell:Projects.expected(v,b.id))baseline.put(cell.x+":"+cell.y+":"+cell.z,cell.after);
            for(var cell:Groves.visual(v,b,plugin.content.rules).entrySet()){if(remaining<=0)break;String[] xyz=cell.getKey().split(":");int x=Integer.parseInt(xyz[0]),y=Integer.parseInt(xyz[1]),z=Integer.parseInt(xyz[2]);if(!w.isChunkLoaded(x>>4,z>>4))continue;
                Block block=w.getBlockAt(x,y,z);String actual=block.getBlockData().getAsString(),expected=b.treeVisual.getOrDefault(cell.getKey(),baseline.get(cell.getKey())),after=Bukkit.createBlockData(cell.getValue()).getAsString();
                if(expected==null || !compatible(expected,actual)){b.damaged=true;b.damageReason=Messages.text("Изменена клетка лесного огорода: ")+cell.getKey();break;}
                if(actual.equals(after)){b.treeVisual.putIfAbsent(cell.getKey(),actual);continue;}
                try{plugin.worldWorks.checkGuards(w,x,z,x,z);}catch(IllegalArgumentException denied){break;}
                block.setBlockData(Bukkit.createBlockData(after),false);b.treeVisual.put(cell.getKey(),after);used++;remaining--;
            }
        }return used;
    }
}
