package ru.livingvillages.content;

import java.util.*;
import static ru.livingvillages.content.Content.*;

/** Full-height roof surfaces; entrance/interior steps and saved project cells are untouched. */
public final class Roofing {
    public static final int VERSION=6;
    private static final Set<String> WOODS=Set.of("oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry","pale_oak","bamboo","crimson","warped");
    private Roofing(){}
    public static String solid(String data){
        String name=data.split("\\[")[0].replace("minecraft:","").toLowerCase(Locale.ROOT);
        if(!name.endsWith("_slab")&&!name.endsWith("_stairs"))return data;
        String base=name.replaceFirst("_(slab|stairs)$","");
        if(WOODS.contains(base))return base+"_planks";
        return switch(base){
            case "brick","stone_brick","mossy_stone_brick","nether_brick","red_nether_brick","end_stone_brick","polished_blackstone_brick","deepslate_brick","mud_brick","tuff_brick","resin_brick"->base+"s";
            case "deepslate_tile"->"deepslate_tiles";
            case "purpur","quartz"->base+"_block";
            case "petrified_oak"->"oak_planks";
            default->base;
        };
    }
    public static Blueprint apply(Blueprint b){
        var cells=new ArrayList<Cell>(b.cells().size());boolean changed=false;
        for(var c:b.cells()){
            String data=c.data();
            if(c.phase()==3){
                if(data.startsWith("$roof")||data.startsWith("$stairs"))data="$roof_block";
                else data=solid(data);
            }
            changed|=!data.equals(c.data());cells.add(data.equals(c.data())?c:new Cell(c.x(),c.y(),c.z(),c.phase(),data,c.floor()));
        }
        return !changed?b:new Blueprint(b.id(),b.name(),b.width(),b.depth(),b.beds(),b.level(),b.recipe(),List.copyOf(cells),b.upkeep(),b.upkeepDays(),b.unlockStage(),b.entrance(),b.groundLayer(),b.closedGables());
    }
}
