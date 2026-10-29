package ru.livingvillages;

import org.junit.jupiter.api.Test;
import ru.livingvillages.domain.*;
import ru.livingvillages.persistence.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FreeBoundaryTest {
    @Test void thereIsNoSecondEditionToSelect(){assertArrayEquals(new Edition[]{Edition.FREE},Edition.values());assertNull(getClass().getResource("/edition.properties"));}
    @Test void limitsAndOneCultureAreIndependentOfConfiguration(){var rules=new Rules();rules.maxPopulation=999;Edition.FREE.apply(rules);assertEquals(12,rules.maxPopulation);assertEquals(3,Edition.FREE.levelLimit());assertEquals(48,Edition.FREE.radiusLimit());assertTrue(Edition.FREE.culture("polevichi"));assertFalse(Edition.FREE.culture("severyane"));}
    @Test void paidImplementationsAreNotOnTheClasspath(){for(String name:List.of("domain.Diplomacy","domain.MilitaryLedger","domain.Weapons","domain.RareCrafts","domain.Landmarks","domain.Husbandry","domain.Leadership","domain.Renovations","paper.Military","paper.Sieges","paper.Defenses","paper.RareItems","paper.IdentityMenus","paper.Architecture","paper.Livestock","paper.Hunting","paper.Raids","content.CulturalArchitecture"))assertThrows(ClassNotFoundException.class,()->Class.forName("ru.livingvillages."+name),name);}
    @Test void exclusiveContentIsNotBundled(){for(String name:List.of("premium.yml","premium-buildings.yml","weapons.yml","rare-items.yml","landmarks.yml","monuments.yml","churches.yml","architecture.yml"))assertNull(getClass().getResource("/"+name),name);}
    @Test void expandedAndPrivateSitesCannotEnterTheCatalogue(){for(String id:List.of("small","medium","large","barracks","archery_range","fortification","church_polevichi","hall_u2","estate_polevichi","hall_severyane"))assertFalse(Edition.FREE.building(id),id);assertTrue(Edition.FREE.building("tree_garden"));assertTrue(Edition.FREE.building("soil_pit"));}
    @Test void currentFreeSavesRoundTrip(){var v=new Village();v.culture="polevichi";v.radius=40;v.stock.add("OAK_LOG",12,v.capacity);v.treasury=717;var restored=Database.decode(Database.encode(v));assertEquals(v.id,restored.id);assertEquals(717,restored.treasury);assertEquals(12,restored.stock.get("OAK_LOG"));}
    @Test void anEmptyLegacyPremiumMetadataShapeIsCompatible(){String raw="{\"format\":15,\"culture\":\"polevichi\",\"radius\":40,\"level\":1,\"leader\":null,\"siege\":null,\"assistants\":[],\"diplomacy\":{},\"animals\":[],\"rareWorks\":[]}";assertDoesNotThrow(()->Database.decode(raw));}
    @Test void unsupportedFieldsAreCheckedBeforeDeserialising(){for(String key:List.of("leader","overlord","siege","assistants","diplomacy","animals","rareWorks")){String value=Set.of("leader","overlord").contains(key)?"\""+UUID.randomUUID()+"\"":"[{}]";assertThrows(IllegalArgumentException.class,()->Database.decode("{\"format\":15,\""+key+"\":"+value+"}"),key);}}
    @Test void privateOrdersAreRejectedRatherThanSilentlyDropped(){String raw="{\"format\":15,\"projects\":[{\"owner\":\""+UUID.randomUUID()+"\"}]}";assertThrows(IllegalArgumentException.class,()->Database.decode(raw));}
    @Test void militaryRecordsAreRejectedRatherThanSilentlyDropped(){assertThrows(IllegalArgumentException.class,()->Database.decode("{\"format\":15,\"residents\":[{\"militaryKind\":\"archer\"}]}"));}
    @Test void unknownUpgradedBuildingDataIsRejected(){assertThrows(IllegalArgumentException.class,()->Database.decode("{\"format\":15,\"buildings\":[{\"kind\":\"hall\",\"level\":2}]}"));}
    @Test void aModifiedEditionFieldInJsonDoesNotSelectAnotherImplementation(){var v=Database.decode("{\"format\":15,\"culture\":\"polevichi\",\"edition\":\"PREMIUM\"}");assertSame(Edition.FREE,v.edition);}
    @Test void personalPaymentStateCannotBeSilentlyDiscarded(){assertThrows(IllegalArgumentException.class,()->Database.decode("{\"format\":15,\"projects\":[{\"laborPaid\":512}]}"));}
    @Test void everyStartLayoutUsesOnlyActuallyShippedPlans() throws Exception {
        var base=java.nio.file.Path.of("src/main/resources");var plans=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(base.resolve("buildings.yml").toFile());var layouts=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(base.resolve("layouts.yml").toFile());
        assertEquals(19,plans.getKeys(false).size());for(String key:layouts.getKeys(false))for(var plot:layouts.getMapList(key+".plots"))assertTrue(plans.contains(plot.get("kind").toString()),key+"/"+plot);
    }
}
