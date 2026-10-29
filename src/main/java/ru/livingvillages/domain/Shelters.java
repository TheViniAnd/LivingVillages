package ru.livingvillages.domain;

import java.util.*;

/** Saved geometry narrows the search; the Paper adapter still checks actual blocks and bodies. */
public final class Shelters {
    private Shelters(){}
    public static boolean usable(Village.Building b){return b.owner==null && b.complete && !b.damaged && !b.upkeepMissing && b.kind!=null && !b.kind.equals("road") && !b.kind.startsWith("street_light");}
    public static List<Village.Block> floors(Village v,Village.Building b,double x,double z,int limit){
        return floors(v,b,x,b.workY,z,limit);
    }
    public static List<Village.Block> floors(Village v,Village.Building b,double x,double y,double z,int limit){
        var expected=Projects.expected(v,b.id);Map<String,Integer> roof=new HashMap<>();Map<String,Village.Block> floor=new HashMap<>();
        for(var c:expected)if(c.phase==3 && !c.after.equals("minecraft:air"))roof.merge(c.x+":"+c.z,c.y,Math::max);
        for(var c:expected)if((c.phase==1 || c.floor) && !c.after.equals("minecraft:air") && c.y>=b.workY-1 && c.x>b.x && c.x<b.x+b.width-1 && c.z>b.z && c.z<b.z+b.depth-1 && roof.getOrDefault(c.x+":"+c.z,Integer.MIN_VALUE)>=(long)c.y+3){
            floor.put(c.x+":"+c.y+":"+c.z,c);
        }
        return floor.values().stream().sorted(Comparator.comparingDouble((Village.Block c)->Math.pow(x-c.x-.5,2)+Math.pow(z-c.z-.5,2)+2*Math.pow(y-c.y-1,2)).thenComparingInt(c->c.x).thenComparingInt(c->c.z)).limit(limit).toList();
    }
}
