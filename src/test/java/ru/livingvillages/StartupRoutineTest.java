package ru.livingvillages;

import org.junit.jupiter.api.Test;
import ru.livingvillages.domain.*;
import static org.junit.jupiter.api.Assertions.*;

class StartupRoutineTest {
    private Village starting(){var v=new Village();v.culture="polevichi";var hall=new Village.Building();hall.kind="hall";hall.x=10;hall.y=63;hall.z=20;hall.width=11;hall.depth=9;hall.workY=65;hall.beds=6;v.buildings.add(hall);var p=new Village.Project();p.kind="hall";p.building=hall.id;p.starter=true;p.state="QUEUED";p.blocks.add(new Village.Block(10,63,20,1,"minecraft:air","minecraft:cobblestone","COBBLESTONE"));p.escrow.put("COBBLESTONE",1);v.projects.add(p);for(var role:new Village.Role[]{Village.Role.ELDER,Village.Role.BUILDER,Village.Role.FARMER,Village.Role.GUARD}){var r=new Village.Resident();r.role=role;r.name=role.name();v.residents.add(r);}return v;}
    @Test void dawnAssemblyAndNightDoNotSuspendInitialConstruction(){var v=starting();var rules=new Rules();for(long time:new long[]{0,300,3000,10000,12500,18000,23999})for(int n:new int[]{0,1}){var target=Routines.plan(v,v.residents.get(n),time,0,rules,false);assertEquals(Routines.Activity.BUILD,target.activity(),"time="+time);assertEquals(v.buildings.getFirst().id,target.building());}}
    @Test void bootstrapDoesNotOverrideDangerChildrenOrGuardDuty(){var v=starting();var rules=new Rules();assertEquals(Routines.Activity.SHELTER,Routines.plan(v,v.residents.getFirst(),18000,0,rules,true).activity());var child=v.residents.get(1);child.matureDay=5;assertNotEquals(Routines.Activity.BUILD,Routines.plan(v,child,3000,0,rules,false).activity());assertEquals(Routines.Activity.PATROL,Routines.plan(v,v.residents.getLast(),18000,0,rules,false).activity());}
    @Test void ordinaryConstructionKeepsTheNightRestSchedule(){var v=starting();v.projects.getFirst().starter=false;assertEquals(Routines.Activity.REST,Routines.plan(v,v.residents.get(1),18000,0,new Rules(),false).activity());}
    @Test void unfundedOrPausedBootstrapIsNotPretendedToBeWork(){var v=starting();v.projects.getFirst().state="COLLECTING";assertNotEquals(Routines.Activity.BUILD,Routines.plan(v,v.residents.get(1),18000,0,new Rules(),false).activity());v.projects.getFirst().state="QUEUED";v.paused=true;assertNotEquals(Routines.Activity.BUILD,Routines.plan(v,v.residents.get(1),18000,0,new Rules(),false).activity());}
    @Test void starterHallPrecedesGardenAndDoesNotInterruptStartedBootstrap(){var v=starting();var garden=new Village.Project();garden.kind="garden";garden.essential=true;garden.starter=true;garden.state="QUEUED";garden.building=v.buildings.getFirst().id;v.projects.add(garden);assertSame(v.projects.getFirst(),Projects.next(v).orElseThrow());garden.state="BUILDING";garden.index=1;assertSame(garden,Projects.next(v).orElseThrow());}
}
