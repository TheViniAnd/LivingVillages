package ru.livingvillages;
import org.junit.jupiter.api.Test;
import ru.livingvillages.domain.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PaidProjectionTest {
    private Village.Project project(Village v,UUID building,String state,int index,String before,String after){var p=new Village.Project();p.building=building;p.state=state;p.index=index;p.blocks.add(new Village.Block(1,65,2,0,before,after,"STONE"));v.projects.add(p);return p;}
    @Test void prefixHasOnlyPaidCellsAndFoldsClearance(){var v=new Village();var id=UUID.randomUUID();var p=project(v,id,"BUILDING",2,"minecraft:oak_leaves","minecraft:air");p.blocks.add(new Village.Block(1,65,2,1,"minecraft:air","minecraft:stone","STONE"));p.blocks.add(new Village.Block(3,65,2,1,"minecraft:air","minecraft:stone","STONE"));var out=PaidProjection.prefix(v,p);assertEquals(1,out.size());assertEquals("minecraft:stone",out.getFirst().expected());assertTrue(out.getFirst().recorded().contains("minecraft:oak_leaves"));}
    @Test void newerPaidRenovationWinsOverEarlierBuilding(){var v=new Village();var id=UUID.randomUUID();project(v,id,"COMPLETE",1,"minecraft:air","minecraft:oak_planks");var p=project(v,id,"BUILDING",1,"minecraft:oak_planks","minecraft:stone_bricks");assertEquals("minecraft:stone_bricks",PaidProjection.prefix(v,p).getFirst().expected());assertEquals(3,PaidProjection.prefix(v,p).getFirst().recorded().size());}
    @Test void laterPlansAndUnrelatedBuildingsNeverOverwritePrefix(){var v=new Village();var id=UUID.randomUUID();var p=project(v,id,"BUILDING",1,"minecraft:air","minecraft:stone");project(v,id,"COMPLETE",1,"minecraft:stone","minecraft:gold_block");project(v,UUID.randomUUID(),"COMPLETE",1,"minecraft:air","minecraft:dirt");assertEquals("minecraft:stone",PaidProjection.prefix(v,p).getFirst().expected());}
}
