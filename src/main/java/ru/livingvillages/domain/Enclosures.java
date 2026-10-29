package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

import java.util.*;

/** Perimeter lies inside the reserved plot; it never claims neighboring land. */
public final class Enclosures {
    private Enclosures(){}
    public record Cell(int x,int y,int z,boolean gate,String facing){}
    public static boolean needed(Village.Building b){return b.owner==null && b.recipe!=null && (b.recipe.startsWith("grove_") || Set.of("garden","grain","reeds","berries","cactus").contains(b.recipe));}
    public static List<Cell> cells(Village.Building b,List<Village.Block> expected){
        if(!needed(b))return List.of();int minX=b.x,maxX=b.x+b.width-1,minZ=b.z,maxZ=b.z+b.depth-1,y=b.workY;String facing=b.entryFacing==null?"NORTH":b.entryFacing;
        if(!b.recipe.startsWith("grove_")){
            var soil=expected.stream().filter(c->c.phase==1 && Set.of("minecraft:farmland","minecraft:grass_block","minecraft:sand","minecraft:water").contains(c.after.split("\\[",2)[0])).toList();
            if(soil.isEmpty())return List.of();minX=soil.stream().mapToInt(c->c.x).min().orElseThrow();maxX=soil.stream().mapToInt(c->c.x).max().orElseThrow();minZ=soil.stream().mapToInt(c->c.z).min().orElseThrow();maxZ=soil.stream().mapToInt(c->c.z).max().orElseThrow();y=soil.stream().mapToInt(c->c.y).max().orElseThrow()+1;
            var lowerDoors=expected.stream().filter(c->c.after.contains("_door[") && c.after.contains("half=lower")).toList();var door=lowerDoors.stream().filter(c->c.x==b.x || c.x==b.x+b.width-1 || c.z==b.z || c.z==b.z+b.depth-1).findFirst().orElse(lowerDoors.isEmpty()?null:lowerDoors.getLast());facing=door==null?"SOUTH":door.after.contains("facing=north")?"NORTH":door.after.contains("facing=east")?"EAST":door.after.contains("facing=west")?"WEST":"SOUTH";
        }
        int gx=(minX+maxX)/2,gz=(minZ+maxZ)/2;switch(facing){case "NORTH"->gz=minZ;case "SOUTH"->gz=maxZ;case "WEST"->gx=minX;case "EAST"->gx=maxX;default->throw new IllegalArgumentException(Messages.text("Направление калитки"));}
        int left=minX,right=maxX,top=minZ,bottom=maxZ,level=y;boolean explicitGate=expected.stream().anyMatch(c->c.y==level && c.x>=left && c.x<=right && c.z>=top && c.z<=bottom && (c.x==left || c.x==right || c.z==top || c.z==bottom) && c.after.contains("_fence_gate["));
        var result=new ArrayList<Cell>();for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(x==minX || x==maxX || z==minZ || z==maxZ){int px=x,pz=z,py=y;var existing=expected.stream().filter(c->c.x==px && c.y==py && c.z==pz && c.after.contains("_fence_gate[")).findFirst().orElse(null);String direction=existing==null?facing:existing.after.contains("facing=east")?"EAST":existing.after.contains("facing=west")?"WEST":existing.after.contains("facing=north")?"NORTH":"SOUTH";result.add(new Cell(x,y,z,!explicitGate && x==gx && z==gz || existing!=null,direction));}return result;
    }
}
