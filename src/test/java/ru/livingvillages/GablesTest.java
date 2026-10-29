package ru.livingvillages;

import org.junit.jupiter.api.Test;
import ru.livingvillages.domain.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GablesTest {
    private List<Gables.Cell> roof(){var cells=new ArrayList<Gables.Cell>();for(int z=0;z<5;z++){for(int x=0;x<7;x++)cells.add(new Gables.Cell(x,4,z,2,"minecraft:oak_planks"));for(int level=0;level<3;level++){cells.add(new Gables.Cell(level,5+level,z,3,"minecraft:oak_stairs[facing=east]"));cells.add(new Gables.Cell(6-level,5+level,z,3,"minecraft:oak_stairs[facing=west]"));}}return cells;}
    @Test void closesBothTrianglesAndLeavesTheAtticHollow(){var missing=Gables.missing(roof(),0);assertEquals(18,missing.size());assertTrue(missing.stream().allMatch(c->c.z()==0 || c.z()==4));assertTrue(missing.stream().anyMatch(c->c.x()==3 && c.y()==7));var complete=roof();complete.addAll(missing);assertTrue(Gables.missing(complete,0).isEmpty());}
    @Test void rotationAndExistingCustomBlocksArePreserved(){var cells=roof();cells.add(new Gables.Cell(3,5,0,3,"minecraft:glass_pane"));var rotated=cells.stream().map(c->new Gables.Cell(4-c.z(),c.y(),c.x(),c.phase(),c.data())).toList();var missing=Gables.missing(rotated,1);assertEquals(17,missing.size());assertFalse(missing.stream().anyMatch(c->c.x()==4 && c.y()==5 && c.z()==3));}
    @Test void flatRoofsAndUnrelatedPlansAreNotRewritten(){assertTrue(Gables.missing(List.of(new Gables.Cell(0,5,0,3,"minecraft:oak_slab")),0).isEmpty());}
}
