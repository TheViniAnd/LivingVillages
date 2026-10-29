package ru.livingvillages;

import org.junit.jupiter.api.Test;
import ru.livingvillages.domain.*;
import ru.livingvillages.persistence.Database;
import ru.livingvillages.content.YamlDefaults;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TrustAndNamesTest {
    Rules names()throws Exception{var y=YamlDefaults.load(Files.newBufferedReader(Path.of("src/main/resources/names.yml")),new StringReader(""));var r=new Rules();r.villageNames=y.getStringList("villages");r.residentNames=y.getStringList("residents");r.surnames=y.getStringList("surnames");r.legacyResidentNames.addAll(y.getStringList("legacy-residents"));return r;}
    @Test void giftsRequireFiftyBeforeAnyStockChange(){var v=new Village();var r=new Rules();var actor=UUID.randomUUID();v.relation(actor).trust=49;assertThrows(IllegalArgumentException.class,()->CommunalStore.gift(v,actor,"STONE",16,r));assertEquals(0,v.stock.size());v.relation(actor).trust=50;CommunalStore.gift(v,actor,"STONE",16,r);assertEquals(16,v.stock.get("STONE"));assertEquals(50,v.relation(actor).trust);}
    @Test void fullWarehouseRejectsGiftWithoutPartialAcceptance(){var v=new Village();v.capacity=8;var actor=UUID.randomUUID();v.relation(actor).trust=50;v.stock.add("STONE",8,v.capacity);assertThrows(IllegalArgumentException.class,()->CommunalStore.gift(v,actor,"OAK_LOG",1,new Rules()));assertEquals(8,v.stock.size());assertEquals(0,v.stock.get("OAK_LOG"));}
    
    
    
    @Test void talkingAndTradeCanEarnTrustBeforeGiftsAreAvailable(){var v=new Village();var actor=UUID.randomUUID();var npc=new Village.Resident();v.residents.add(npc);var r=new Rules();assertEquals(1,Social.talk(v,actor,npc.id,"friend",r).trust());assertEquals(0,Social.talk(v,actor,npc.id,"friend",r).trust());assertEquals(1,Reputation.award(v,actor,r,"TRADE","STONE",1));assertEquals(0,Reputation.award(v,actor,r,"TRADE","STONE",1));assertEquals(2,v.relation(actor).trust);assertFalse(TrustAccess.friend(v,actor,r));}
    
    
    @Test void namesRejectCommandsMarkupControlsAndExcessiveLength(){for(String name:List.of("ab","/op player","<red>Test","Test§c","Test\nTown","Test\u200BTown","Test  Town","a".repeat(33)," Name ")){if(name.equals(" Name ")){assertEquals("Name",Names.checked(name));continue;}assertThrows(IllegalArgumentException.class,()->Names.checked(name),name);}assertEquals("Étoile-d'Or",Names.checked("Étoile-d'Or"));}
    @Test void catalogHasExactlyFiftyDistinctVillageNames()throws Exception{var r=names();assertEquals(50,r.villageNames.size());assertEquals(50,r.villageNames.stream().map(Names::key).distinct().count());assertTrue(r.residentNames.size()>=150);assertTrue(r.surnames.size()>=50);for(String n:r.villageNames)Names.checked(n);}
    @Test void seventyFiveVillagesNeverRepeatANameWhenCatalogIsExhausted()throws Exception{var r=names();var found=new HashSet<String>();for(int i=0;i<75;i++){var v=new Village();v.name=Names.village(v,r);assertTrue(found.add(Names.key(v.name)));}assertEquals(75,found.size());assertTrue(found.containsAll(r.villageNames.stream().map(Names::key).toList()));}
    @Test void sixHundredResidentsAcrossVillagesUseDistinctEuropeanNames()throws Exception{var r=names();var found=new HashSet<String>();for(int j=0;j<12;j++){var v=new Village();for(int i=0;i<50;i++){var person=new Village.Resident();person.name=Names.resident(v,r,person.id);v.residents.add(person);assertTrue(found.add(Names.key(person.name)));assertFalse(person.name.matches(".*[А-Яа-яЁё].*"));}}assertEquals(600,found.size());}
    @Test void legacyNamesChangeWithoutReplacingIdsOrCustomNames()throws Exception{var r=names();var v=new Village();v.name="Полевичи · "+v.id.toString().substring(0,6);var old=new Village.Resident();old.name="Яромир 2";v.residents.add(old);var custom=new Village.Resident();custom.name="Custom Knight";v.residents.add(custom);UUID id=old.id;Names.seed(List.of(v),r);Names.assign(v,r);assertTrue(r.villageNames.contains(v.name));assertEquals(id,old.id);assertFalse(old.name.contains("Яромир"));assertEquals("Custom Knight",custom.name);String name=v.name,npc=old.name;Names.assign(v,r);assertEquals(name,v.name);assertEquals(npc,old.name);}
    @Test void savedNamesRemainStableWithAFreshRegistryAfterRestart()throws Exception{var r=names();var v=new Village();v.name=Names.village(v,r);var npc=new Village.Resident();npc.name=Names.resident(v,r,npc.id);v.residents.add(npc);var copy=Database.copy(v);var fresh=names();Names.seed(List.of(copy),fresh);Names.assign(copy,fresh);assertEquals(v.id,copy.id);assertEquals(v.name,copy.name);assertEquals(npc.id,copy.residents.getFirst().id);assertEquals(npc.name,copy.residents.getFirst().name);}
    
}
