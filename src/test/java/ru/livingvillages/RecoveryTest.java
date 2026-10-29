package ru.livingvillages;

import org.junit.jupiter.api.Test;
import ru.livingvillages.domain.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryTest {
    Rules rules(){var r=new Rules();r.foods.put("CARROT",2);r.recipes.put("garden",new Rules.Recipe("FARMER",Map.of(),Map.of("CARROT",24),"irrigated",1,1));return r;}
    Village village(){var v=new Village();var elder=new Village.Resident();elder.role=Village.Role.ELDER;v.residents.add(elder);var b=new Village.Building();b.kind="garden";b.recipe="garden";b.complete=true;b.resourceConfirmed=true;v.buildings.add(b);return v;}
    @Test void missingFarmerCanBeCoveredByTheElderWithoutCreatingAnotherWorker(){var v=village();assertTrue(Recovery.foodWorker(v,rules()));assertEquals(Village.Role.ELDER,v.residents.getFirst().role);assertEquals(v.buildings.getFirst().id,v.residents.getFirst().workplace);assertEquals(1,v.residents.size());}
    @Test void childrenAndGuardsCannotBeReassignedByEmergencyFoodRecovery(){var v=village();v.residents.getFirst().role=Village.Role.GUARD;var child=new Village.Resident();child.role=Village.Role.BUILDER;child.matureDay=8;v.residents.add(child);assertFalse(Recovery.foodWorker(v,rules()));assertEquals(Village.Role.GUARD,v.residents.getFirst().role);assertNull(child.workplace);}
    @Test void missingResourceDamageAndFoodReservePreventUnnecessaryReassignment(){var v=village();v.buildings.getFirst().damaged=true;assertFalse(Recovery.foodWorker(v,rules()));v.buildings.getFirst().damaged=false;v.buildings.getFirst().resourceConfirmed=false;assertFalse(Recovery.foodWorker(v,rules()));v.buildings.getFirst().resourceConfirmed=true;v.stock.add("CARROT",20,v.capacity);assertFalse(Recovery.foodWorker(v,rules()));}
}
