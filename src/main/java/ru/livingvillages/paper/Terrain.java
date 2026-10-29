package ru.livingvillages.paper;

import ru.livingvillages.i18n.Messages;

import org.bukkit.*;
import org.bukkit.block.TileState;
import org.bukkit.util.BoundingBox;
import java.util.*;

/** Loaded chunks only. Natural clearance becomes a saved construction plan, never a side effect of surveying. */
final class Terrain {
    private final LivingVillagesPlugin plugin;
    private static final Set<String> PLANTS=Set.of("SHORT_GRASS","TALL_GRASS","SHORT_DRY_GRASS","TALL_DRY_GRASS","BUSH","FIREFLY_BUSH","DEAD_BUSH","SWEET_BERRY_BUSH","FERN","LARGE_FERN","VINE","CAVE_VINES","CAVE_VINES_PLANT","GLOW_LICHEN","HANGING_ROOTS","MOSS_CARPET","PALE_MOSS_CARPET","PALE_HANGING_MOSS","BAMBOO","BAMBOO_SAPLING","CACTUS","CACTUS_FLOWER","SUGAR_CANE","SEAGRASS","TALL_SEAGRASS","KELP","KELP_PLANT","BROWN_MUSHROOM","RED_MUSHROOM","AZALEA","FLOWERING_AZALEA","SMALL_DRIPLEAF","BIG_DRIPLEAF","BIG_DRIPLEAF_STEM","SNOW","PINK_PETALS","WILDFLOWERS","LEAF_LITTER");
    Terrain(LivingVillagesPlugin plugin){this.plugin=plugin;}
    boolean vegetation(Material m){
        if(!plugin.content.config.getBoolean("generation.clear-vegetation",true))return false;
        return PLANTS.contains(m.name()) || Tag.FLOWERS.isTagged(m) || Tag.SAPLINGS.isTagged(m) || Tag.LEAVES.isTagged(m)
            || plugin.content.config.getBoolean("generation.clear-trees",true) && Set.of("MANGROVE_ROOTS","MUDDY_MANGROVE_ROOTS").contains(m.name())
            || plugin.content.config.getBoolean("generation.clear-trees",true) && m.name().endsWith("_LOG") && !m.name().startsWith("STRIPPED_");
    }
    boolean replaceable(Material m){return m.isAir() || vegetation(m);}
    static boolean ground(Material m){return Set.of(Material.GRASS_BLOCK,Material.DIRT,Material.COARSE_DIRT,Material.PODZOL,Material.ROOTED_DIRT,Material.SAND,Material.RED_SAND,Material.SANDSTONE,Material.RED_SANDSTONE,Material.STONE,Material.GRAVEL,Material.SNOW_BLOCK,Material.MUD,Material.CLAY,Material.ANDESITE,Material.GRANITE,Material.DIORITE,Material.MOSS_BLOCK).contains(m);}
    int surface(World world,int x,int z){
        if(!world.isChunkLoaded(x>>4,z>>4))throw new IllegalArgumentException(Messages.text("Участок не загружен: ")+x+" "+z);
        int top=world.getHighestBlockYAt(x,z,HeightMap.WORLD_SURFACE),y=top,limit=plugin.content.config.getInt("generation.max-clear-height",48);
        while(y>world.getMinHeight() && replaceable(world.getBlockAt(x,y,z).getType())){
            if(top-y>=limit)throw new IllegalArgumentException(Messages.text("Растительность выше допустимой расчистки: ")+x+" "+z);y--;
        }
        return y;
    }
    int base(World world,int x,int z,int width,int depth,boolean pier){return base(world,x,z,width,depth,pier,false);}
    int base(World world,int x,int z,int width,int depth,boolean pier,boolean lowest){
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
        for(int dx=0;dx<width;dx++)for(int dz=0;dz<depth;dz++){
            int bx=x+dx,bz=z+dz,y=surface(world,bx,bz);Material material=world.getBlockAt(bx,y,bz).getType();
            if(!world.getWorldBorder().isInside(new Location(world,bx,y,bz)))throw new IllegalArgumentException(Messages.text("Участок пересекает границу мира"));
            if(material==Material.WATER && pier){if(!world.getBlockAt(bx,y-3,bz).getType().isSolid())throw new IllegalArgumentException(Messages.text("Слишком глубокая вода для свай"));}
            else if(!ground(material))throw new IllegalArgumentException(material==Material.WATER?Messages.text("Под зданием вода; выберите берег или свайную культуру"):Messages.text("Посторонний блок под зданием: ")+material+" ("+bx+" "+y+" "+bz+")");
            min=Math.min(min,y);max=Math.max(max,y);
        }
        if(max-min>plugin.content.config.getInt("generation.max-height-difference",2))throw new IllegalArgumentException(Messages.text("Перепад высот участка ")+(max-min)+Messages.text(" блоков превышает допустимый: ")+x+" "+z);
        return lowest?min:max;
    }
    Set<String> levelNursery(World world,int x,int z,int width,int depth,int floor,List<ru.livingvillages.domain.Village.Block> blocks){
        Set<String> removed=new HashSet<>();int limit=plugin.content.config.getInt("generation.max-height-difference",2);
        for(int bx=x;bx<x+width;bx++)for(int bz=z;bz<z+depth;bz++){int top=surface(world,bx,bz);if(top-floor>limit)throw new IllegalArgumentException(Messages.text("Слишком крутой склон лесного огорода"));
            for(int y=floor+1;y<=top;y++){var block=world.getBlockAt(bx,y,bz);if(replaceable(block.getType()))continue;if(!ground(block.getType())||block.getState(false) instanceof TileState)throw new IllegalArgumentException(Messages.text("При выравнивании встречен посторонний блок"));blocks.add(new ru.livingvillages.domain.Village.Block(bx,y,bz,0,block.getBlockData().getAsString(),"minecraft:air",null));removed.add(bx+":"+y+":"+bz);}
        }return removed;
    }
    void foundations(World world,int x,int base,int z,ru.livingvillages.content.Content.Blueprint blueprint,ru.livingvillages.content.Content.Culture culture,boolean pier,List<ru.livingvillages.domain.Village.Block> blocks){
        Map<String,ru.livingvillages.content.Content.Cell> lowest=new LinkedHashMap<>();
        for(var cell:blueprint.cells())if((pier?cell.y()==0:cell.floor()||cell.y()<=blueprint.groundLayer())&&Bukkit.createBlockData(plugin.content.resolve(culture,cell.data())).getMaterial().isSolid())lowest.merge(cell.x()+":"+cell.z(),cell,(a,b)->a.y()<=b.y()?a:b);
        boolean bootstrap=blueprint.level()==1&&ru.livingvillages.domain.Autonomy.essential(blueprint.id());
        String after=Bukkit.createBlockData(bootstrap?"cobblestone":culture.palette().get("base")).getAsString(),material=WorldWorks.costMaterial(Bukkit.createBlockData(after).getMaterial());int limit=plugin.content.config.getInt("generation.max-height-difference",2)+1;
        for(var cell:lowest.values()){int bx=x+cell.x(),bz=z+cell.z(),floor=base+cell.y();boolean supported=false;
            for(int by=floor-1;by>=Math.max(world.getMinHeight(),floor-limit);by--){if(!world.isChunkLoaded(bx>>4,bz>>4))throw new IllegalArgumentException(Messages.text("Опора ждёт загруженный чанк"));var old=world.getBlockAt(bx,by,bz);if(old.getType().isSolid()){supported=true;break;}
                if(!replaceable(old.getType())&&!(pier&&old.getType()==Material.WATER))throw new IllegalArgumentException(Messages.text("Под опорой посторонний блок или жидкость"));blocks.add(new ru.livingvillages.domain.Village.Block(bx,by,bz,0,old.getBlockData().getAsString(),after,material));
            }if(!supported)throw new IllegalArgumentException(Messages.text("Под полом глубокая пустота: ")+bx+" "+bz);
        }
    }
    void structures(World world,int x,int y,int z,int width,int height,int depth){
        BoundingBox plot=new BoundingBox(x,y-2,z,x+width,y+height,z+depth);
        for(int cx=x>>4;cx<=(x+width-1)>>4;cx++)for(int cz=z>>4;cz<=(z+depth-1)>>4;cz++){
            if(!world.isChunkLoaded(cx,cz))throw new IllegalArgumentException(Messages.text("Участок не загружен"));
            for(var structure:world.getChunkAt(cx,cz).getStructures())if(structure.getBoundingBox().overlaps(plot))throw new IllegalArgumentException(Messages.text("Участок пересекает ванильную структуру"));
        }
    }
    void clearColumn(World world,int x,int z,int floor,int roof,List<ru.livingvillages.domain.Village.Block> blocks){
        clearColumn(world,x,z,floor,roof,blocks,Map.of());
    }
    void clearColumn(World world,int x,int z,int floor,int roof,List<ru.livingvillages.domain.Village.Block> blocks,Map<String,String> trusted){
        int top=world.getHighestBlockYAt(x,z,HeightMap.WORLD_SURFACE),limit=plugin.content.config.getInt("generation.max-clear-height",48);
        if(top-floor>limit)throw new IllegalArgumentException(Messages.text("Препятствие выше допустимой расчистки"));
        Set<Integer> planned=new HashSet<>(),cleared=new HashSet<>();for(var b:blocks)if(b.x==x && b.z==z){planned.add(b.y);if(b.phase==0 && b.after.equals("minecraft:air"))cleared.add(b.y);}
        for(int y=floor+1;y<=Math.max(top,roof);y++){
            var block=world.getBlockAt(x,y,z);if(cleared.contains(y) || block.getType().isAir())continue;
            String actual=block.getBlockData().getAsString();
            if(WorldWorks.matchesExpected(trusted.get(x+":"+y+":"+z),actual)){
                int height=y;boolean retained=blocks.stream().anyMatch(c->c.x==x&&c.z==z&&c.y==height&&WorldWorks.matchesExpected(c.after,actual));
                if(retained)continue;
            }
            if(!vegetation(block.getType()) && planned.contains(y))continue;
            if(!vegetation(block.getType()) && WorldWorks.matchesExpected(trusted.get(x+":"+y+":"+z),block.getBlockData().getAsString()))continue;
            if(!vegetation(block.getType()) || block.getState(false) instanceof TileState)throw new IllegalArgumentException(Messages.text("Посторонний блок над участком: ")+block.getType()+" ("+x+" "+y+" "+z+")");
            blocks.add(new ru.livingvillages.domain.Village.Block(x,y,z,0,block.getBlockData().getAsString(),"minecraft:air",null));
        }
    }
}
