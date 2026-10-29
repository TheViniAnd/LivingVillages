package ru.livingvillages;

import org.junit.jupiter.api.Test;
import ru.livingvillages.domain.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EntrancesTest {
    @Test void fieldDoorDoesNotStealThePublicRoad(){var b=new Village.Building();b.width=10;b.depth=5;
        var side=new Village.Block(4,2,1,2,"minecraft:air","minecraft:oak_door[facing=east,half=lower]",null);
        var gate=new Village.Block(5,2,1,2,"minecraft:air","minecraft:oak_fence_gate[facing=east]",null);
        var front=new Village.Block(2,2,4,2,"minecraft:air","minecraft:oak_door[facing=south,half=lower]",null);
        var outer=new Village.Block(7,2,4,2,"minecraft:air","minecraft:oak_fence_gate[facing=south]",null);
        assertSame(front,Entrances.road(b,List.of(side,gate,front,outer)));
    }
    @Test void rotatedEntranceAndGateOnlyBuildingsStillWork(){var b=new Village.Building();b.width=5;b.depth=10;
        var side=new Village.Block(3,2,4,2,"minecraft:air","minecraft:oak_door[facing=south,half=lower]",null);
        var front=new Village.Block(0,2,2,2,"minecraft:air","minecraft:oak_fence_gate[facing=west]",null);
        assertSame(front,Entrances.road(b,List.of(side,front)));assertSame(front,Entrances.road(b,List.of(front)));assertNull(Entrances.road(b,List.of()));
    }
}
