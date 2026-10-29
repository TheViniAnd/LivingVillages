package ru.livingvillages;

import org.junit.jupiter.api.Test;
import ru.livingvillages.domain.*;
import ru.livingvillages.persistence.Database;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PopulationTrustTest {
    private Village village(int people){Village v=new Village();v.laws.immigration=false;for(int i=0;i<people;i++){var r=new Village.Resident();r.role=i==0?Village.Role.ELDER:Village.Role.FARMER;v.residents.add(r);}return v;}
    private Village.Building home(Village v,int beds){var b=new Village.Building();b.complete=true;b.beds=beds;v.buildings.add(b);return b;}
    private Rules rules(){Rules r=new Rules();r.foods.put("CARROT",2);r.prices.put("CARROT",new Rules.Price(3,160,4,90));return r;}
    @Test void hungerHelpNeverBypassesDailyTrustBudget(){Village v=village(5);v.nutrition=.25;v.treasury=100000;UUID p=UUID.randomUUID();Rules r=rules();Economy e=new Economy(r);for(int i=0;i<30;i++){v.stock.items.clear();e.trade(v,p,e.quote(v,p,"CARROT",4,false));}assertEquals(1,v.relation(p).trust);assertEquals(1,v.relation(p).tradeToday);assertTrue(v.relation(p).deeds.contains("food"));}
    @Test void tradeTasksAndDonationsShareOnePersistedLimit(){Village v=village(5);UUID p=UUID.randomUUID();Rules r=rules();Economy e=new Economy(r);for(int i=0;i<6;i++)Reputation.award(v,p,r,"CONSTRUCTION","material"+i,3);var t=Contracts.issue(v,p,"CARROT",4,e);Contracts.deliver(v,t,4,e);v=Database.copy(v);assertEquals(20,v.relation(p).tradeToday);assertEquals(0,Reputation.award(v,p,r,"restoration",100));assertEquals(20,v.relation(p).trust);v.days++;assertTrue(Reputation.award(v,p,r,"donation",10)>0);assertEquals(23,v.relation(p).trust);}
    @Test void repeatingAnItemUsesDiminishingReturnsAcrossSources(){Village v=village(1);Rules r=rules();r.dailyTradeTrust=100;UUID p=UUID.randomUUID();assertEquals(4,Reputation.award(v,p,r,"CARROT",12));assertEquals(2,Reputation.award(v,p,r,"CARROT",12));assertEquals(1,Reputation.award(v,p,r,"CARROT",12));}
    @Test void legacyOvercrowdingIsRepairedWithoutTakingPrivateHomes(){Village v=village(5);var a=home(v,2);var b=home(v,4);var privateHouse=home(v,50);privateHouse.owner=UUID.randomUUID();for(var r:v.residents)r.home=a.id;Housing.reconcile(v);assertEquals(2,Housing.occupancy(v).get(a.id));assertEquals(3,Housing.occupancy(v).get(b.id));assertFalse(Housing.occupancy(v).containsKey(privateHouse.id));assertEquals(6,v.beds());}
    @Test void brokenAndUnfinishedHousesAreNotHabitable(){Village v=village(4);var a=home(v,2);var b=home(v,4);b.damaged=true;var c=home(v,4);c.complete=false;for(var r:v.residents)r.home=b.id;Housing.reconcile(v);assertEquals(2,v.residents.stream().filter(r->r.home==null).count());assertEquals(2,v.beds());assertNull(Housing.vacantHome(v));}
    @Test void deathFreesOnlyTheActualResidentsBed(){Village v=village(2);var a=home(v,1);var b=home(v,1);Housing.reconcile(v);assertNull(Housing.vacantHome(v));v.residents.getFirst().alive=false;assertEquals(a.id,Housing.vacantHome(v));assertEquals(b.id,v.residents.getLast().home);}
    @Test void immigrantGetsTheSecondHomeWhenTheFirstIsFull(){Village v=village(3);var a=home(v,3);var b=home(v,2);for(var r:v.residents)r.home=a.id;v.residents.getLast().role=Village.Role.GUARD;v.laws.immigration=true;v.days=3;v.stock.add("CARROT",100,v.capacity);Rules r=rules();new Simulation(r).day(v);assertEquals(4,v.living());assertEquals(b.id,v.residents.getLast().home);assertEquals(3,Housing.occupancy(v).get(a.id));}
    @Test void reconciliationDoesNotRepeatedlyMoveValidResidents(){Village v=village(3);home(v,2);home(v,2);Housing.reconcile(v);var old=v.residents.stream().map(r->r.home).toList();Housing.reconcile(v);assertEquals(old,v.residents.stream().map(r->r.home).toList());}
}
