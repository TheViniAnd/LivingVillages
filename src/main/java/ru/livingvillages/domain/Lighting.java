package ru.livingvillages.domain;

import java.util.*;

/** Geometry of reserved exterior lights; physical light levels are checked by the Paper adapter. */
public final class Lighting {
    private Lighting(){}
    private static boolean surface(Village.Block b){return b.phase==5 && Set.of("minecraft:dirt_path","minecraft:cobblestone","minecraft:gravel","minecraft:mangrove_planks").contains(b.after);}
    public static List<Village.Block> roads(Village v,boolean complete){return complete?Projects.memo(v,"completed-roads",()->collectRoads(v,true)):collectRoads(v,false);}
    private static List<Village.Block> collectRoads(Village v,boolean complete){Map<String,Village.Block> cells=new LinkedHashMap<>();for(var p:v.projects)if(!p.state.equals("CANCELLED") && (!complete || p.state.equals("COMPLETE")))for(var b:p.blocks)if(surface(b))cells.put(b.x+":"+b.z,b);return new ArrayList<>(cells.values());}
    public static List<Village.Block> lights(Village v){Set<UUID> buildings=new HashSet<>();for(var b:v.buildings)if(b.owner==null && (b.kind.equals("road") || b.kind.startsWith("street_light")))buildings.add(b.id);Map<String,Village.Block> cells=new LinkedHashMap<>();for(var p:v.projects)if(!p.state.equals("CANCELLED") && buildings.contains(p.building))for(var b:p.blocks)if(b.after.equals("minecraft:torch"))cells.put(b.x+":"+b.y+":"+b.z,b);return new ArrayList<>(cells.values());}
    public static boolean covered(Village.Block road,List<Village.Block> lights,int spacing){return lights.stream().anyMatch(light->Math.abs(light.x-road.x)+Math.abs(light.z-road.z)<=spacing && Math.abs(light.y-road.y)<=6);}
    public static boolean pending(Village v){return v.projects.stream().anyMatch(p->p.kind.startsWith("street_light") && p.active());}
}
