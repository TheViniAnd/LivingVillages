package ru.livingvillages.domain;
import java.util.*;
public final class BuildingFloors {private BuildingFloors(){}
private static boolean air(String data){return data==null||data.equals("air")||data.equals("minecraft:air");}
public static int standingFloor(List<Village.Block> cells,int fallback){
        Map<String,String> data=new HashMap<>();for(var c:cells)data.put(c.x+":"+c.y+":"+c.z,c.after);
        return cells.stream().filter(c->c.floor&&!air(c.after)&&air(data.get(c.x+":"+(c.y+1)+":"+c.z))&&air(data.get(c.x+":"+(c.y+2)+":"+c.z))).mapToInt(c->c.y).min().orElse(fallback);
    }
}
