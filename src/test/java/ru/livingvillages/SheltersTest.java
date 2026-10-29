package ru.livingvillages;

import org.junit.jupiter.api.Test;
import ru.livingvillages.domain.*;
import static org.junit.jupiter.api.Assertions.*;

class SheltersTest {
    private Village.Block cell(int x,int y,int z,int phase,String after){return new Village.Block(x,y,z,phase,"minecraft:air",after,null);}
    @Test void foundationAndOutsideFieldCannotHideTheRoofedRoom(){var v=new Village();var b=new Village.Building();b.kind="garden";b.x=0;b.z=0;b.width=20;b.depth=7;b.workY=65;v.buildings.add(b);var p=new Village.Project();p.building=b.id;p.state="COMPLETE";v.projects.add(p);
        for(int x=1;x<19;x++)for(int z=1;z<6;z++){p.blocks.add(cell(x,63,z,1,"minecraft:cobblestone"));p.blocks.add(cell(x,64,z,1,x<5?"minecraft:oak_planks":"minecraft:farmland"));if(x<5)p.blocks.add(cell(x,69,z,3,"minecraft:oak_stairs"));}
        var floors=Shelters.floors(v,b,18,4,24);assertEquals(20,floors.size());assertTrue(floors.stream().allMatch(c->c.x<5 && c.y==64));assertEquals(4,floors.getFirst().x);
    }
    @Test void aLowOrAbsentRoofIsNotAPlannedShelter(){var v=new Village();var b=new Village.Building();b.width=4;b.depth=4;b.workY=65;v.buildings.add(b);var p=new Village.Project();p.building=b.id;p.state="COMPLETE";v.projects.add(p);p.blocks.add(cell(1,64,1,1,"minecraft:oak_planks"));p.blocks.add(cell(1,66,1,3,"minecraft:oak_planks"));p.blocks.add(cell(2,64,1,1,"minecraft:oak_planks"));p.blocks.add(cell(2,69,1,3,"minecraft:air"));assertTrue(Shelters.floors(v,b,1,1,24).isEmpty());}
    @Test void completedRepairOverlayKeepsTheOriginalRoof(){var v=new Village();var b=new Village.Building();b.width=4;b.depth=4;b.workY=65;v.buildings.add(b);var p=new Village.Project();p.building=b.id;p.state="COMPLETE";v.projects.add(p);p.blocks.add(cell(1,64,1,1,"minecraft:oak_planks"));p.blocks.add(cell(1,69,1,3,"minecraft:oak_planks"));var repair=new Village.Project();repair.building=b.id;repair.state="COMPLETE";repair.blocks.add(cell(1,64,1,1,"minecraft:oak_planks"));v.projects.add(repair);assertEquals(1,Shelters.floors(v,b,1,1,24).size());}
}
