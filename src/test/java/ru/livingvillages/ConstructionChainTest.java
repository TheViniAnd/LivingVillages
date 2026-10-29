package ru.livingvillages;
import org.junit.jupiter.api.Test;
import ru.livingvillages.domain.*;
import ru.livingvillages.persistence.Database;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ConstructionChainTest {
    @Test void failedPlacementGoalAndCursorSurviveRestartAndNewSuggestions(){var v=new Village();assertEquals("tree_garden",BuildAgenda.choose(v,"tree_garden"));v.planningReason="Участок ещё ищется";v.siteSearch.put("tree_garden",8);v=Database.copy(v);assertEquals("tree_garden",BuildAgenda.choose(v,"landmark_test"));assertEquals(8,v.siteSearch.get("tree_garden"));assertNotNull(v.planningReason);BuildAgenda.reserved(v,"tree_garden");assertNull(v.plannedBuilding);}
    @Test void supplierDoesNotEraseADeferredHouseGoal(){var v=new Village();BuildAgenda.choose(v,"residence");assertEquals("tree_garden",BuildAgenda.choose(v,"tree_garden"));BuildAgenda.reserved(v,"tree_garden");assertEquals("residence",v.plannedBuilding);}
    @Test void woodIsNotProducedByPlannedUnbuiltOrBlockedNursery()throws Exception{for(String state:List.of("COLLECTING","QUEUED","SITE_BLOCKED","PAUSED")){var r=AutonomyData.rules();var v=AutonomyData.village();var trees=v.buildings.stream().filter(b->b.kind.equals("tree_garden")).findFirst().orElseThrow();trees.complete=false;var p=v.projects.stream().filter(q->q.building.equals(trees.id)).findFirst().orElseThrow();p.state=state;for(int d=0;d<20;d++)new Simulation(r).day(v);assertEquals(0,v.produced.getOrDefault("OAK_LOG",0L),state);assertEquals(0,v.stock.get("OAK_LOG"),state);}}
    @Test void missingWoodIsScheduledBeforeAnotherUnbuiltCoreFacility()throws Exception{var r=AutonomyData.rules();r.timedCrafting=true;var v=AutonomyData.village();var trees=v.buildings.stream().filter(b->b.kind.equals("tree_garden")).findFirst().orElseThrow();v.projects.removeIf(p->p.building.equals(trees.id));v.buildings.remove(trees);var pending=new Village.Project();pending.kind="family_home";pending.building=v.buildings.getFirst().id;v.projects.add(pending);assertTrue(Autonomy.next(v,r).startsWith("tree_garden"));}
    @Test void soilCanBootstrapOnSandBeforeWoodProduction()throws Exception{var r=AutonomyData.rules();var v=AutonomyData.village();v.stock.items.clear();v.stock.add("COBBLESTONE",64,v.capacity);TestDemand.add(v,"DIRT",30);assertEquals("soil_pit",Autonomy.next(v,r));}
    
    
    @Test void blockedResourceProjectRemainsActiveAndPersisted(){var v=new Village();var p=new Village.Project();p.state="SITE_BLOCKED";p.kind="sand_pit";v.projects.add(p);var loaded=Database.copy(v).projects.getFirst();assertTrue(loaded.active());assertEquals(p.id,loaded.id);assertEquals("SITE_BLOCKED",loaded.state);}
    @Test void explicitRequestCannotOverwriteAnotherPendingGoal(){var v=new Village();BuildAgenda.request(v,"tree_garden");assertThrows(IllegalArgumentException.class,()->BuildAgenda.request(v,"residence"));assertEquals("tree_garden",Database.copy(v).plannedBuilding);}

}
