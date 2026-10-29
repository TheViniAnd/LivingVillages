package ru.livingvillages;

import org.junit.jupiter.api.Test;
import ru.livingvillages.domain.*;
import ru.livingvillages.persistence.Database;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WaterSupplyTest {
    @Test void realProductionRequiresConfirmedFacilityWorkerAndContainerAndCannotDuplicateOnReload(){
        var v=new Village();var rules=new Rules();rules.foods.put("CARROT",2);rules.recipes.put("water_supply",new Rules.Recipe("WATER_CARRIER",Map.of("BUCKET",1),Map.of("WATER_BUCKET",1),"water_source",1,1));
        var worker=new Village.Resident();worker.role=Village.Role.WATER_CARRIER;v.residents.add(worker);v.stock.add("CARROT",100,v.capacity);var pump=new Village.Building();pump.kind="water_intake";pump.recipe="water_supply";pump.complete=true;v.buildings.add(pump);worker.workplace=pump.id;var sim=new Simulation(rules);
        v.stock.add("BUCKET",1,v.capacity);sim.day(v);assertEquals(1,v.stock.get("BUCKET"));assertEquals(0,v.stock.get("WATER_BUCKET"));pump.resourceConfirmed=true;sim.day(v);assertEquals(0,v.stock.get("BUCKET"));assertEquals(1,v.stock.get("WATER_BUCKET"));v=Database.copy(v);sim.day(v);assertEquals(1,v.stock.get("WATER_BUCKET"));assertEquals(1,v.produced.get("WATER_BUCKET"));
        v.stock.add("BUCKET",1,v.capacity);var savedPump=v.buildings.getFirst();savedPump.complete=false;sim.day(v);assertEquals(1,v.stock.get("BUCKET"));assertEquals(1,v.stock.get("WATER_BUCKET"));savedPump.complete=true;savedPump.damaged=true;sim.day(v);assertEquals(1,v.stock.get("BUCKET"));assertEquals(1,v.stock.get("WATER_BUCKET"));
    }
    @Test void aSavedCurrentCycleDoesNotRunTwice(){var v=new Village();v.laws.immigration=false;var rules=new Rules();rules.foods.put("CARROT",2);rules.recipes.put("water_supply",new Rules.Recipe("WATER_CARRIER",Map.of("BUCKET",1),Map.of("WATER_BUCKET",1),"water_source",1,2));var r=new Village.Resident();r.role=Village.Role.WATER_CARRIER;v.residents.add(r);var b=new Village.Building();b.kind="water_intake";b.recipe="water_supply";b.complete=true;b.resourceConfirmed=true;v.buildings.add(b);r.workplace=b.id;v.stock.add("CARROT",100,v.capacity);v.stock.add("BUCKET",3,v.capacity);new Simulation(rules).elapse(v,rules.dayMillis,1);var copy=Database.copy(v);new Simulation(rules).elapse(copy,0,1);assertEquals(v.stock.items,copy.stock.items);new Simulation(rules).elapse(copy,rules.dayMillis,1);assertEquals(1,copy.stock.get("WATER_BUCKET"));assertEquals(2,copy.stock.get("BUCKET"));}
}
