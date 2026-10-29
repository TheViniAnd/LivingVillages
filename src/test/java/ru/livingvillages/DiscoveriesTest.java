package ru.livingvillages;

import org.junit.jupiter.api.Test;
import ru.livingvillages.domain.*;
import ru.livingvillages.persistence.Database;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DiscoveriesTest {
    @Test void nearbyRequiresTheSameWorldAndIncludesTheRadius(){var v=new Village();v.world=UUID.randomUUID();v.x=-100;v.z=20;assertTrue(Discoveries.nearby(v,v.world,-36,20,64));assertFalse(Discoveries.nearby(v,v.world,-35.99,20,64));assertFalse(Discoveries.nearby(v,UUID.randomUUID(),-100,20,64));assertFalse(Discoveries.nearby(v,v.world,Double.NaN,20,64));}
    @Test void eachPlayerAndVillageHasOneIndependentDiscovery(){var v=new Village();var other=new Village();UUID a=UUID.randomUUID(),b=UUID.randomUUID();assertTrue(Discoveries.record(v,a,"Анна"));assertTrue(Discoveries.record(v,b,"Борис"));assertFalse(Discoveries.record(v,a,"Анна"));assertTrue(Discoveries.record(other,a,"Анна"));assertEquals(a,v.discoverer);assertEquals(Set.of(a,b),v.discovered);assertEquals(0,v.relation(a).trust);}
    @Test void restartPreservesDiscoveryFirstFinderAndUpdatedNames(){var v=new Village();var player=UUID.randomUUID();Discoveries.record(v,player,"До");var restored=Database.copy(v);assertFalse(Discoveries.record(restored,player,"После"));assertEquals(player,restored.discoverer);assertEquals("После",restored.relation(player).playerName);}
    @Test void notificationContainsActualNameCultureCoordinatesAndWorld(){var v=new Village();v.name="Тихий берег";v.x=-12;v.y=73;v.z=105;String message=Discoveries.message(v,"Береговики","мир");for(String part:List.of("Тихий берег","Береговики","X -12","Y 73","Z 105","мир","/lv menu"))assertTrue(message.contains(part));v.abandoned=true;assertTrue(Discoveries.message(v,"Береговики","мир").contains("заброшенная"));}
}
