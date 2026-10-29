package ru.livingvillages;

import org.junit.jupiter.api.Test;
import ru.livingvillages.domain.*;
import ru.livingvillages.persistence.Database;
import java.awt.Color;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VillageMapTest {
    
    @Test void surveyedTerrainAndInProgressBuildingsSurviveReload(){var v=new Village();v.mapTerrain[0]=Color.BLUE.getRGB();var b=new Village.Building();b.kind="family_home";b.width=7;b.depth=7;v.buildings.add(b);long before=VillageMap.signature(v);var copy=Database.copy(v);assertEquals(before,VillageMap.signature(copy));assertEquals(Color.BLUE.getRGB(),VillageMap.render(copy).getRGB(1,1));assertEquals(VillageMap.CONSTRUCTION.getRGB(),VillageMap.render(copy).getRGB(66,66));copy.buildings.getFirst().complete=true;assertNotEquals(before,VillageMap.signature(copy));}
    
}
