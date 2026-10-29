package ru.livingvillages.paper;

import ru.livingvillages.i18n.Messages;

import org.bukkit.*;
import ru.livingvillages.domain.*;
import java.util.*;

/** Bounded roadside placement. Initial posts belong to the saved one-time road plan. */
final class StreetWorks {
    private final LivingVillagesPlugin plugin;private final WorldWorks works;
    private final Map<UUID,Long> next=new HashMap<>();private final Map<UUID,Integer> cursor=new HashMap<>();
    StreetWorks(LivingVillagesPlugin plugin,WorldWorks works){this.plugin=plugin;this.works=works;}
    void starter(Village v,Village.Project road){if(!plugin.content.config.getBoolean("lighting.enabled",true))return;int wanted=plugin.content.config.getInt("lighting.starter-posts",4),spacing=plugin.content.config.getInt("lighting.spacing",8);
        for(var point:Lighting.roads(v,false)){if(wanted<=0)break;if(Lighting.covered(point,Lighting.lights(v),spacing))continue;var post=place(v,point,true);if(post==null)continue;road.blocks.addAll(post.blocks);post.required.forEach((item,n)->road.required.merge(item,n,Math::addExact));v.buildings.remove(post.building(v));v.projects.remove(post);wanted--;}
        road.blocks.sort(Comparator.comparingInt((Village.Block b)->b.phase).thenComparingInt(b->b.y));
    }
    boolean develop(Village v){if(!plugin.content.config.getBoolean("lighting.enabled",true) || Lighting.pending(v) || Lighting.lights(v).size()>=plugin.content.config.getInt("lighting.max-posts",16))return false;
        if(System.nanoTime()<next.getOrDefault(v.id,0L))return false;next.put(v.id,System.nanoTime()+plugin.content.config.getLong("development.plan-interval-seconds",60)*1_000_000_000L);
        World w=Bukkit.getWorld(v.world);if(w==null)return false;var roads=Lighting.roads(v,true);if(roads.isEmpty())return false;
        int start=cursor.getOrDefault(v.id,0),limit=Math.min(roads.size(),plugin.content.config.getInt("lighting.scan-per-pass",16));
        for(int i=0;i<limit;i++){var point=roads.get(Math.floorMod(start+i,roads.size()));if(!w.isChunkLoaded(point.x>>4,point.z>>4) || w.getBlockAt(point.x,point.y+1,point.z).getLightFromBlocks()>0)continue;
            var post=place(v,point,false);if(post!=null){post.priority=30;v.developmentReason=Messages.text("Освещение дороги: нужны материалы светильника");plugin.save(v);cursor.put(v.id,start+i+1);return true;}}
        cursor.put(v.id,start+limit);return false;
    }
    private Village.Project place(Village v,Village.Block point,boolean starter){for(int[] d:new int[][]{{2,0},{-2,0},{0,2},{0,-2}}){int x=point.x+d[0],z=point.z+d[1];if(z==v.z-3)continue; // Keep the shared east/west road spine extendable.
            if(Lighting.lights(v).stream().anyMatch(l->Math.abs(l.x-x)+Math.abs(l.z-z)<5))continue;
            try{return works.light(v,x,z,starter);}catch(IllegalArgumentException ignored){}}
        return null;
    }
}
