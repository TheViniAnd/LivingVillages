package ru.livingvillages.domain;

import java.util.*;

/** A passage through the shared house wall, preserving furniture and the outer field gate. */
public final class CropAccess {
    private CropAccess(){}
    public record Cell(int x,int y,int z,String part,String facing){}
    public static List<Cell> cells(Village.Building building,List<Village.Block> expected){
        if(!Enclosures.needed(building) || building.recipe.startsWith("grove_"))return List.of();
        var perimeter=Enclosures.cells(building,expected);if(perimeter.isEmpty())return List.of();
        int minX=perimeter.stream().mapToInt(Enclosures.Cell::x).min().orElseThrow(),maxX=perimeter.stream().mapToInt(Enclosures.Cell::x).max().orElseThrow(),minZ=perimeter.stream().mapToInt(Enclosures.Cell::z).min().orElseThrow(),maxZ=perimeter.stream().mapToInt(Enclosures.Cell::z).max().orElseThrow(),y=perimeter.getFirst().y();
        Map<String,String> blocks=new HashMap<>();for(var cell:expected)blocks.put(key(cell.x,cell.y,cell.z),cell.after);
        for(int[] direction:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}){int dx=direction[0],dz=direction[1];String facing=dx<0?"east":dx>0?"west":dz<0?"south":"north";
            for(var edge:perimeter){int x=edge.x(),z=edge.z();if(x==minX && z==minZ || x==maxX && z==maxZ || x==minX && z==maxZ || x==maxX && z==minZ)continue;
                if(dx<0 && x!=minX || dx>0 && x!=maxX || dz<0 && z!=minZ || dz>0 && z!=maxZ)continue;
                int wallX=x+dx,wallZ=z+dz,insideX=x+dx*2,insideZ=z+dz*2;
                if(insideX<=building.x || insideX>=building.x+building.width-1 || insideZ<=building.z || insideZ>=building.z+building.depth-1)continue;
                if(!air(blocks.get(key(insideX,y,insideZ))) || !air(blocks.get(key(insideX,y+1,insideZ))))continue;
                String lower=blocks.get(key(wallX,y,wallZ)),upper=blocks.get(key(wallX,y+1,wallZ));
                if(lower!=null && lower.contains("_door[") && upper!=null && upper.contains("_door[") && Objects.toString(blocks.get(key(x,y,z)),"").contains("_fence_gate["))return List.of();
                if(!wall(lower) || !wall(upper))continue;
                return List.of(new Cell(wallX,y,wallZ,"door-lower",facing),new Cell(wallX,y+1,wallZ,"door-upper",facing),new Cell(x,y,z,"gate",facing),new Cell(x,y+1,z,"air",facing));
            }
        }return List.of();
    }
    private static boolean air(String data){return data==null || data.equals("minecraft:air");}
    private static boolean wall(String data){return data!=null && (data.contains("_planks") || data.contains("_log") || data.contains("glass_pane") || data.contains("sandstone") || data.equals("minecraft:cobblestone") || data.equals("minecraft:stone_bricks"));}
    private static String key(int x,int y,int z){return x+":"+y+":"+z;}
}
