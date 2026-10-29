package ru.livingvillages.domain;

import java.util.*;

/** An interior field doorway must never become the public road entrance. */
public final class Entrances {
    private Entrances(){}
    public static Village.Block road(Village.Building building,List<Village.Block> cells){
        return cells.stream().filter(c->c.after.contains("_fence_gate[") || c.after.contains("_door[") && c.after.contains("half=lower"))
            .min(Comparator.comparingInt((Village.Block c)->distance(building,c)).thenComparingInt(c->c.after.contains("_door[")?0:1)).orElse(null);
    }
    private static int distance(Village.Building b,Village.Block c){
        if(c.after.contains("facing=north"))return c.z-b.z;
        if(c.after.contains("facing=south"))return b.z+b.depth-1-c.z;
        if(c.after.contains("facing=east"))return b.x+b.width-1-c.x;
        return c.x-b.x;
    }
}
