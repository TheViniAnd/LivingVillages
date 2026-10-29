package ru.livingvillages;

import org.junit.jupiter.api.Test;
import ru.livingvillages.domain.*;
import ru.livingvillages.persistence.Database;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BiomeProductionTest {
    private Rules rules(){var r=new Rules();r.foods.put("BREAD",4);r.recipes.put("yield",new Rules.Recipe("ARTISAN",Map.of("SAND",1),Map.of("GLASS",1),"land",1,2));r.biomeProduction.put("DESERT",Map.of("yield",125));r.biomeProduction.put("SNOWY_PLAINS",Map.of("yield",60));r.biomeProduction.put("VOID",Map.of("yield",0));return r;}
    private Village village(String biome){var v=new Village();v.biome=biome;v.culture="polevichi";v.laws.immigration=false;v.capacity=10000;var b=new Village.Building();b.complete=true;b.resourceConfirmed=true;b.recipe="yield";v.buildings.add(b);var worker=new Village.Resident();worker.role=Village.Role.ELDER;worker.workplace=b.id;worker.toolKind="STONE_PICKAXE";worker.toolUses=1000;v.residents.add(worker);v.stock.add("SAND",1000,v.capacity);v.stock.add("BREAD",1000,v.capacity);return v;}
    @Test void sameCultureProducesAtDifferentRenewalRatesWithoutFreeInputs(){var rules=rules();var warm=village("DESERT");var normal=village("PLAINS");var cold=village("SNOWY_PLAINS");for(int i=0;i<20;i++)for(var v:List.of(warm,normal,cold))new Simulation(rules).day(v);assertEquals(10,normal.stock.get("GLASS"));assertTrue(warm.stock.get("GLASS")>normal.stock.get("GLASS"));assertTrue(cold.stock.get("GLASS")<normal.stock.get("GLASS"));for(var v:List.of(warm,normal,cold))assertEquals(1000-v.stock.get("GLASS"),v.stock.get("SAND"));}
    @Test void ActualWorkplaceBiomeOverridesVillageBiome(){var rules=rules();var v=village("DESERT");v.buildings.getFirst().biome="SNOWY_PLAINS";assertEquals(.6,rules.productionRate(v,v.buildings.getFirst()));}
    @Test void FractionalRenewalAndOldCooldownSurviveReload(){var rules=rules();var v=village("DESERT");v.buildings.getFirst().cycles=4;var copy=Database.copy(v);for(int i=0;i<24;i++){new Simulation(rules).day(v);new Simulation(rules).day(copy);copy=Database.copy(copy);}assertEquals(v.stock.items,copy.stock.items);assertEquals(v.buildings.getFirst().cycleReadyDay,copy.buildings.getFirst().cycleReadyDay);assertTrue(v.buildings.getFirst().cycleReadyDay>v.days);}
    @Test void DisabledProductionUsesNoInputsOrTools(){var v=village("VOID");for(int i=0;i<10;i++)new Simulation(rules()).day(v);assertEquals(0,v.stock.get("GLASS"));assertEquals(1000,v.stock.get("SAND"));assertEquals(1000,v.residents.getFirst().toolUses);}
    @Test void LossOfGuardKeepsTheOnlyFarmerAndBuilder(){var v=village("PLAINS");v.residents.getFirst().workplace=null;var farmer=new Village.Resident();farmer.role=Village.Role.FARMER;v.residents.add(farmer);var builder=new Village.Resident();builder.role=Village.Role.BUILDER;v.residents.add(builder);Simulation.electReplacement(v);assertEquals(Village.Role.BUILDER,builder.role);assertEquals(Village.Role.FARMER,farmer.role);assertTrue(v.residents.stream().noneMatch(r->r.role==Village.Role.GUARD));}
    @Test void LostGuardCanBeReplacedByMigrationWhenNoSpareWorkerExists(){var rules=rules();var v=village("PLAINS");v.laws.immigration=true;v.laws.focus="security";var hall=new Village.Building();hall.complete=true;hall.beds=3;v.buildings.add(hall);v.days=3;v.underAttack=true;new Simulation(rules).day(v);assertEquals(1,v.living());v.underAttack=false;v.days=7;new Simulation(rules).day(v);assertEquals(2,v.living());assertEquals(Village.Role.GUARD,v.residents.getLast().role);}
}
