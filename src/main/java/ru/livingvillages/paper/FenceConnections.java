package ru.livingvillages.paper;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.*;
import java.util.*;
import ru.livingvillages.domain.Village;

/** Physics-free placement still needs real horizontal rails. Never edits neighboring blocks. */
final class FenceConnections {
    private FenceConnections(){}
    private static final List<BlockFace> SIDES=List.of(BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST);
    static String key(int x,int y,int z){return x+":"+y+":"+z;}
    static Map<String,String> plan(List<Village.Block> cells){var map=new HashMap<String,String>();for(var c:cells)map.put(key(c.x,c.y,c.z),c.after);return map;}
    static boolean supported(BlockData data){return data instanceof Fence||data instanceof Wall||data instanceof MultipleFacing&&(data.getMaterial()==Material.IRON_BARS||data.getMaterial().name().endsWith("GLASS_PANE"));}
    static BlockData shape(Block block,BlockData base,Map<String,String> plan){
        if(!supported(base))return base;var fence=base.clone();var w=block.getWorld();
        for(var side:SIDES){int x=block.getX()+side.getModX(),z=block.getZ()+side.getModZ();String saved=plan.get(key(x,block.getY(),z));BlockData neighbor;
            if(saved!=null)neighbor=Bukkit.createBlockData(saved);
            else {if(!w.isChunkLoaded(x>>4,z>>4))continue;neighbor=w.getBlockAt(x,block.getY(),z).getBlockData();}
            if(fence instanceof Fence rails)rails.setFace(side,connects(rails,neighbor,side));
            else if(fence instanceof Wall wall)wall.setHeight(side,neighbor instanceof Wall||neighbor.getMaterial().isOccluding()||neighbor instanceof Gate?Wall.Height.LOW:Wall.Height.NONE);
            else if(fence instanceof MultipleFacing panes)panes.setFace(side,neighbor.getMaterial().isOccluding()||neighbor.getMaterial()==fence.getMaterial()||neighbor.getMaterial()==Material.GLASS||neighbor.getMaterial().name().endsWith("_STAINED_GLASS")||neighbor.getMaterial().name().endsWith("GLASS_PANE"));
        }return fence;
    }
    static boolean connects(Fence fence,BlockData neighbor,BlockFace side){
        if(neighbor instanceof Fence)return (fence.getMaterial()==Material.NETHER_BRICK_FENCE)==(neighbor.getMaterial()==Material.NETHER_BRICK_FENCE);
        if(neighbor instanceof Gate gate)return gate.getFacing()!=side && gate.getFacing()!=side.getOppositeFace();
        return neighbor.getMaterial().isOccluding();
    }
}
