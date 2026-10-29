package ru.livingvillages.paper;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import ru.livingvillages.domain.*;
import java.util.*;

/** Conservative body clearance and footing. Every world read is restricted to loaded chunks. */
final class NpcSafety {
    private final LivingVillagesPlugin plugin;
    private static final Set<Material> HAZARDS=Set.of(Material.CACTUS,Material.MAGMA_BLOCK,Material.CAMPFIRE,Material.SOUL_CAMPFIRE,Material.FIRE,Material.SOUL_FIRE,Material.POWDER_SNOW,Material.SWEET_BERRY_BUSH);
    NpcSafety(LivingVillagesPlugin plugin){this.plugin=plugin;}
    boolean permitted(Village v,World w,Location p){return p.getY()>w.getMinHeight()&&p.getY()+2<w.getMaxHeight()&&w.getUID().equals(v.world)&&Territory.inside(v,v.world,p.getX(),p.getZ())&&w.getWorldBorder().isInside(p);}
    boolean clear(World w,Location p){
        var body=new BoundingBox(p.getX()-.299,p.getY()+.005,p.getZ()-.299,p.getX()+.299,p.getY()+1.94,p.getZ()+.299);
        for(int x=(int)Math.floor(body.getMinX());x<=Math.floor(body.getMaxX());x++)for(int z=(int)Math.floor(body.getMinZ());z<=Math.floor(body.getMaxZ());z++){
            if(!w.isChunkLoaded(x>>4,z>>4))return false;
            for(int y=(int)Math.floor(body.getMinY());y<=Math.floor(body.getMaxY());y++){if(y<w.getMinHeight() || y>=w.getMaxHeight())return false;Block b=w.getBlockAt(x,y,z);if(b.isLiquid() || HAZARDS.contains(b.getType()))return false;
                if(b.getCollisionShape().overlaps(body.clone().shift(-x,-y,-z)))return false;
            }
        }return true;
    }
    /** Highest safe support below a point, including slabs, stairs, farmland and dirt paths. */
    double floor(World w,double x,double y,double z,double depth){int bx=(int)Math.floor(x),bz=(int)Math.floor(z);if(!w.isChunkLoaded(bx>>4,bz>>4))return Double.NaN;double best=Double.NaN;
        for(int by=Math.min(w.getMaxHeight()-1,(int)Math.floor(y+.1));by>=Math.max(w.getMinHeight(),(int)Math.floor(y-depth));by--){Block b=w.getBlockAt(bx,by,bz);Material type=b.getType();if(HAZARDS.contains(type) || Tag.LEAVES.isTagged(type) || Tag.FENCES.isTagged(type) || Set.of(Material.CHEST,Material.BARREL,Material.CRAFTING_TABLE,Material.COMPOSTER).contains(type))continue;
            for(var box:b.getCollisionShape().getBoundingBoxes()){double top=by+box.getMaxY();if(x-bx>=box.getMinX() && x-bx<=box.getMaxX() && z-bz>=box.getMinZ() && z-bz<=box.getMaxZ() && top<=y+.1 && top>=y-depth && (!Double.isFinite(best) || top>best))best=top;}
        }return best;
    }
    Location settle(Village v,World w,Location p){if(!permitted(v,w,p))return null;double floor=floor(w,p.getX(),p.getY(),p.getZ(),1.05);if(!Double.isFinite(floor))return null;Location at=p.clone();at.setY(floor);return stable(v,w,at)?at:null;}
    boolean stable(Village v,World w,Location p){if(!permitted(v,w,p) || !clear(w,p))return false;double floor=floor(w,p.getX(),p.getY(),p.getZ(),.15);double reference=reference(v,w,p.getBlockX(),p.getBlockZ(),p.getY());return Double.isFinite(floor) && Math.abs(p.getY()-floor)<.15 && (!Double.isFinite(reference) || p.getY()-reference<=1.25) && !belowGround(v,w,p,reference);}
    boolean embedded(World w,Location p){if(p.getY()<=w.getMinHeight() || p.getY()+2>=w.getMaxHeight())return true;var body=new BoundingBox(p.getX()-.285,p.getY()+.08,p.getZ()-.285,p.getX()+.285,p.getY()+1.85,p.getZ()+.285);
        for(int x=(int)Math.floor(body.getMinX());x<=Math.floor(body.getMaxX());x++)for(int z=(int)Math.floor(body.getMinZ());z<=Math.floor(body.getMaxZ());z++){if(!w.isChunkLoaded(x>>4,z>>4))return false;for(int y=(int)Math.floor(body.getMinY());y<=Math.floor(body.getMaxY());y++){var block=w.getBlockAt(x,y,z);if(block.getBlockData() instanceof org.bukkit.block.data.type.Door || block.getBlockData() instanceof org.bukkit.block.data.type.Gate)continue;if(block.getCollisionShape().overlaps(body.clone().shift(-x,-y,-z)))return true;}}return false;
    }
    double referenceHeight(Village v,World w,int x,int z,double expectedY){double height=reference(v,w,x,z,expectedY);return Double.isFinite(height)?height:expectedY;}
    private double reference(Village v,World w,int x,int z,double expectedY){if(!w.isChunkLoaded(x>>4,z>>4))return Double.NaN;
        if(v.buildings.stream().noneMatch(b->b.owner==null && !"road".equals(b.kind) && Territory.plot(b,x,z)))for(var other:plugin.villages.values())if(!other.id.equals(v.id) && other.world.equals(v.world) && other.buildings.stream().anyMatch(b->b.owner==null && !"road".equals(b.kind) && Territory.plot(b,x,z)))return reference(other,w,x,z,expectedY);
        var house=v.buildings.stream().filter(b->b.owner==null && !"road".equals(b.kind) && Territory.plot(b,x,z) && (b.complete || v.projects.stream().anyMatch(p->p.building.equals(b.id) && p.active() && p.index>0))).findFirst().orElse(null);if(house!=null){Map<Integer,Village.Block> column=new HashMap<>();for(var project:v.projects)if(project.building.equals(house.id) && (project.state.equals("COMPLETE") || project.active() && project.index>0))for(var cell:project.state.equals("COMPLETE")?project.blocks:project.blocks.subList(0,Math.min(project.index,project.blocks.size())))if(cell.x==x && cell.z==z)column.put(cell.y,cell);var floor=column.values().stream().filter(c->(c.phase==1 || c.floor) && c.y+1<=expectedY+1.25 && !c.after.equals("minecraft:air") && w.getBlockAt(c.x,c.y,c.z).getType()==Bukkit.createBlockData(c.after).getMaterial()).mapToInt(c->c.y+1).max();if(floor.isPresent())return floor.getAsInt();if(house.complete && column.values().stream().anyMatch(c->(c.phase==1 || c.floor) && !c.after.equals("minecraft:air")))return house.workY;}
        int top=w.getHighestBlockYAt(x,z,HeightMap.MOTION_BLOCKING_NO_LEAVES),limit=plugin.content.config.getInt("generation.max-clear-height",48);
        for(int y=top;y>=Math.max(w.getMinHeight(),top-limit);y--){var type=w.getBlockAt(x,y,z).getType();if(type.isSolid() && !Tag.LEAVES.isTagged(type) && !(type.name().endsWith("_LOG") && !type.name().startsWith("STRIPPED_")))return y+1;}
        return Double.NaN;
    }
    boolean belowGround(Village v,World w,Location p){return belowGround(v,w,p,reference(v,w,p.getBlockX(),p.getBlockZ(),p.getY()));}
    private boolean belowGround(Village v,World w,Location p,double reference){double limit=plugin.content.config.getDouble("residents.routine.rescue-drop",2.5);if(Double.isFinite(reference) && reference-p.getY()>limit)return true;
        // A pit has high edges on at least three sides; an ordinary slope has a low side.
        int high=0;for(int[] d:new int[][]{{3,0},{-3,0},{0,3},{0,-3}}){double rim=reference(v,w,p.getBlockX()+d[0],p.getBlockZ()+d[1],p.getY());if(Double.isFinite(rim) && rim-p.getY()>limit)high++;}return high>=3;
    }
    boolean stranded(Village v,World w,Location p){double approved=reference(v,w,p.getBlockX(),p.getBlockZ(),p.getY());return Double.isFinite(approved) && p.getY()-approved>1.25 && Double.isFinite(floor(w,p.getX(),p.getY(),p.getZ(),.15));}
    boolean fallen(Village v,World w,Location p,double lastGround){return embedded(w,p) || belowGround(v,w,p) || Double.isFinite(lastGround) && lastGround-p.getY()>plugin.content.config.getDouble("residents.routine.rescue-drop",2.5);}
    boolean step(World w,Location at,double dx,double dz,double ground){double maxDrop=plugin.content.config.getDouble("residents.routine.max-step-drop",1.25);
        for(double x:new double[]{at.getX()+dx-.27,at.getX()+dx+.27})for(double z:new double[]{at.getZ()+dz-.27,at.getZ()+dz+.27}){double floor=floor(w,x,Math.max(at.getY(),ground)+.1,z,Math.max(at.getY(),ground)-ground+maxDrop+.1);if(!Double.isFinite(floor) || floor<ground-maxDrop)return false;}return true;
    }
}
