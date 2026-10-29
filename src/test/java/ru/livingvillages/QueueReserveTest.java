package ru.livingvillages;

import org.junit.jupiter.api.Test;
import ru.livingvillages.domain.*;
import static org.junit.jupiter.api.Assertions.*;

class QueueReserveTest {
    @Test void ordinaryProjectsCannotUseTheLastEmergencySlot(){var v=new Village();var rules=new Rules();for(int i=0;i<5;i++)v.projects.add(new Village.Project());assertThrows(IllegalArgumentException.class,()->Projects.requireSlot(v,rules,false));assertDoesNotThrow(()->Projects.requireSlot(v,rules,true));v.projects.add(new Village.Project());assertThrows(IllegalArgumentException.class,()->Projects.requireSlot(v,rules,true));}
    @Test void aOneProjectQueueReservesItsOnlySlotForEssentialWork(){var v=new Village();var r=new Rules();r.maxProjects=1;assertThrows(IllegalArgumentException.class,()->Projects.requireSlot(v,r,false));assertDoesNotThrow(()->Projects.requireSlot(v,r,true));}
    @Test void cancelledAndCompletedProjectsDoNotOccupyQueueSlots(){var v=new Village();var r=new Rules();r.maxProjects=2;var a=new Village.Project();a.state="COMPLETE";v.projects.add(a);var b=new Village.Project();b.state="CANCELLED";v.projects.add(b);assertDoesNotThrow(()->Projects.requireSlot(v,r,false));}
}
