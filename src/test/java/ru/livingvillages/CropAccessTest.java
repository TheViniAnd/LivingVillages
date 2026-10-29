package ru.livingvillages;

import org.junit.jupiter.api.Test;
import ru.livingvillages.domain.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CropAccessTest {
    @Test void everyShippedCropBlueprintHasAFreeDoorAndGatePassage() throws Exception {
        var path=java.nio.file.Path.of("src/main/resources/buildings.yml");var yaml=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(path.toFile());int checked=0;
        for(var id:yaml.getKeys(false)){var plan=yaml.getConfigurationSection(id);String recipe=plan.getString("recipe","");if(!Set.of("garden","grain","berries","reeds","cactus").contains(recipe))continue;
            var b=new Village.Building();b.kind=id;b.recipe=recipe;var blocks=new ArrayList<Village.Block>();int y=0;
            for(var layer:plan.getMapList("layers")){int z=0;for(Object row:(List<?>)layer.get("rows")){String text=row.toString();b.width=Math.max(b.width,text.length());for(int x=0;x<text.length();x++){char symbol=text.charAt(x);if(symbol==' ')continue;String data=plan.getConfigurationSection("symbols").getString(String.valueOf(symbol));assertNotNull(data,id+" / "+symbol);data=data.replace("$door","oak_door").replace("$gate","oak_fence_gate").replace("$fence","oak_fence").replace("$wall","oak_planks").replace("$planks","oak_planks").replace("$log","oak_log");if(!data.startsWith("minecraft:"))data="minecraft:"+data;blocks.add(new Village.Block(x,y,z,((Number)layer.get("phase")).intValue(),"minecraft:air",data,null));}z++;}b.depth=Math.max(b.depth,z);y++;}
            assertTrue(CropAccess.cells(b,blocks).isEmpty(),id+" не требует доработки");var gates=Enclosures.cells(b,blocks).stream().filter(Enclosures.Cell::gate).toList();assertEquals(id.equals("relief_garden")?1:2,gates.size(),id+" — открытый огород или проход через дом");
            for(var door:blocks.stream().filter(c->c.after.contains("oak_door[") && c.after.contains("facing=east") && c.after.contains("half=lower")).toList()){
                assertTrue(blocks.stream().anyMatch(c->c.x==door.x-1 && c.y==door.y && c.z==door.z && c.after.equals("minecraft:air")),id+" — свободное место перед дверью");assertTrue(gates.stream().anyMatch(c->c.x()==door.x+1 && c.z()==door.z),id+" — калитка напротив двери");
            }checked++;
        }assertEquals(2,checked,"проверены оба огорода Free");
    }
    @Test void passageAvoidsTheStorageAndKeepsTheOuterGate(){
        var b=new Village.Building();b.kind="garden";b.recipe="garden";b.width=10;b.depth=5;b.workY=2;
        var cells=new ArrayList<Village.Block>();
        for(int x=0;x<10;x++)for(int z=0;z<5;z++){
            cells.add(new Village.Block(x,1,z,1,"minecraft:air",x>=5?"minecraft:farmland":"minecraft:oak_planks",null));
            for(int y=2;y<=3;y++)cells.add(new Village.Block(x,y,z,2,"minecraft:air",x==4?"minecraft:oak_planks":x==3 && z==2 && y==2?"minecraft:barrel":"minecraft:air",null));
        }
        cells.removeIf(c->c.x==7 && c.y==2 && c.z==4);cells.add(new Village.Block(7,2,4,2,"minecraft:air","minecraft:oak_fence_gate[facing=south]",null));
        var passage=CropAccess.cells(b,cells);assertEquals(4,passage.size());assertTrue(passage.stream().noneMatch(c->c.z()==2));assertEquals("east",passage.getFirst().facing());
        for(var c:passage){cells.removeIf(old->old.x==c.x() && old.y==c.y() && old.z==c.z());cells.add(new Village.Block(c.x(),c.y(),c.z(),2,"minecraft:air",switch(c.part()){case "gate"->"minecraft:oak_fence_gate[facing=east]";case "door-lower"->"minecraft:oak_door[half=lower]";case "door-upper"->"minecraft:oak_door[half=upper]";default->"minecraft:air";},null));}
        assertTrue(CropAccess.cells(b,cells).isEmpty());assertEquals(2,Enclosures.cells(b,cells).stream().filter(Enclosures.Cell::gate).count());
        assertTrue(cells.stream().anyMatch(c->c.x==3 && c.z==2 && c.after.equals("minecraft:barrel")));
    }
    @Test void personalPlotsAndGrovesAreNotCutThrough(){var b=new Village.Building();b.recipe="grove_oak";assertTrue(CropAccess.cells(b,List.of()).isEmpty());b.recipe="garden";b.owner=UUID.randomUUID();assertTrue(CropAccess.cells(b,List.of()).isEmpty());}
}
