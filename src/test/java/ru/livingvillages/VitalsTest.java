package ru.livingvillages;

import org.junit.jupiter.api.Test;
import ru.livingvillages.domain.*;
import ru.livingvillages.persistence.Database;
import static org.junit.jupiter.api.Assertions.*;

class VitalsTest {
    Village.Resident add(Village v,Village.Role role,long maturity){var r=new Village.Resident();r.role=role;r.matureDay=maturity;v.residents.add(r);return r;}
    @Test void adultChildAndGuardHaveDifferentHealth(){var v=new Village();var adult=add(v,Village.Role.FARMER,0);var child=add(v,Village.Role.BUILDER,8);var guard=add(v,Village.Role.GUARD,0);Vitals.reconcile(v,new Rules());assertEquals(100,adult.health);assertEquals(20,child.health);assertEquals(180,guard.health);}
    @Test void injurySurvivesReloadAndReconciliationDoesNotHeal(){var v=new Village();var guard=add(v,Village.Role.GUARD,0);Vitals.reconcile(v,new Rules());guard.health=73;v=Database.copy(v);Vitals.reconcile(v,new Rules());assertEquals(73,v.residents.getFirst().health);assertEquals(180,v.residents.getFirst().maxHealth);}
    @Test void maturityScalesHealthProportionallyWithoutChangingImmigrationSystem(){var v=new Village();var child=add(v,Village.Role.BUILDER,8);Vitals.reconcile(v,new Rules());child.health=10;v.days=8;Vitals.reconcile(v,new Rules());assertEquals(50,child.health);assertEquals(100,child.maxHealth);}
    @Test void childrenCannotWorkBuildOrReplaceTheElder(){var v=new Village();v.laws.immigration=false;var child=add(v,Village.Role.BUILDER,8);var project=new Village.Project();project.state="QUEUED";project.blocks.add(new Village.Block(0,0,0,1,"air","stone","STONE"));v.projects.add(project);Simulation.electReplacement(v);Jobs.reconcile(v,new Rules());new Simulation(new Rules()).elapse(v,1000,1);assertEquals(Village.Role.CHILD,child.role);assertNull(child.workplace);assertEquals(0,project.credit);}
    @Test void recoveryNeedsFoodAndSafetyAndCannotReviveTheDead(){var v=new Village();var a=add(v,Village.Role.FARMER,0);var dead=add(v,Village.Role.BUILDER,0);Vitals.reconcile(v,new Rules());a.health=40;dead.alive=false;dead.health=0;v.nutrition=.5;Vitals.recover(v,new Rules());assertEquals(40,a.health);v.nutrition=1;v.underAttack=true;Vitals.recover(v,new Rules());assertEquals(40,a.health);v.underAttack=false;Vitals.recover(v,new Rules());assertEquals(50,a.health);assertEquals(0,dead.health);assertFalse(dead.alive);}
    @Test void childrenUseTheSameMigrationFoodAndHousingLimits(){var v=new Village();v.days=3;v.laws.immigration=true;add(v,Village.Role.ELDER,0);add(v,Village.Role.GUARD,0);var house=new Village.Building();house.complete=true;house.beds=4;v.buildings.add(house);var rules=new Rules();add(v,Village.Role.BUILDER,0);rules.childImmigrationPercent=100;rules.foods.put("BREAD",4);v.stock.add("BREAD",100,v.capacity);new Simulation(rules).day(v);assertEquals(4,v.living());assertFalse(v.adult(v.residents.getLast()));assertEquals(20,v.residents.getLast().health);v.days=7;new Simulation(rules).day(v);assertEquals(4,v.living());}
}
