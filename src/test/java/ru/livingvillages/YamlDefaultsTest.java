package ru.livingvillages;

import org.junit.jupiter.api.Test;
import ru.livingvillages.content.YamlDefaults;
import java.io.StringReader;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class YamlDefaultsTest {
    private static final String GRAIN="""
        grain:
          fee: 10
          symbols:
            '.': air
            C: wheat[age=7]
          layers:
            - phase: 1
              rows: [C.C]
        """;
    
    @Test void newBlueprintKeepsDotSymbolWhenUpdatingAnOlderFile() throws Exception {
        var merged=YamlDefaults.load(new StringReader("hall:\n  fee: 20\n"),new StringReader(GRAIN));
        assertEquals("air",merged.getConfigurationSection("grain.symbols").getValues(false).get("."));
        assertEquals("wheat[age=7]",merged.getString("grain.symbols.C"));
        assertEquals(1,merged.getMapList("grain.layers").size());assertEquals(20,merged.getInt("hall.fee"));
    }
    @Test void incompleteBlueprintGetsDotWithoutOverwritingCustomContent() throws Exception {
        var merged=YamlDefaults.load(new StringReader("grain:\n  fee: 17\n  symbols:\n    C: carrots[age=7]\n"),new StringReader(GRAIN));
        assertEquals("air",merged.getConfigurationSection("grain.symbols").getValues(false).get("."));
        assertEquals("carrots[age=7]",merged.getString("grain.symbols.C"));assertEquals(17,merged.getInt("grain.fee"));
    }
    @Test void existingListsAndSettingsKeepTheirValues() throws Exception {
        var merged=YamlDefaults.load(new StringReader("generation:\n  worlds: [custom]\n  chance: 0.07\n"),new StringReader("generation:\n  worlds: [world]\n  chance: 0.015\n  clear-vegetation: true\n"));
        assertEquals(List.of("custom"),merged.getStringList("generation.worlds"));assertEquals(.07,merged.getDouble("generation.chance"));assertTrue(merged.getBoolean("generation.clear-vegetation"));assertEquals('.',merged.options().pathSeparator());
    }
    @Test void everyBundledBlueprintCanBeAddedToAnOlderFileAndLoadedAgain() throws Exception {
        String defaults=Files.readString(Path.of("src/main/resources/buildings.yml"));
        var merged=YamlDefaults.load(new StringReader("hall:\n  fee: 37\n"),new StringReader(defaults));
        assertEquals(19,merged.getKeys(false).size());assertEquals(37,merged.getInt("hall.fee"));
        for(String id:merged.getKeys(false)){
            var symbols=merged.getConfigurationSection(id+".symbols");assertNotNull(symbols,id);
            for(var layer:merged.getMapList(id+".layers"))for(Object row:(List<?>)layer.get("rows"))for(char c:row.toString().toCharArray())if(c!=' ')assertNotNull(symbols.getValues(false).get(String.valueOf(c)),id+" symbol "+c);
        }
        var reloaded=YamlDefaults.load(new StringReader(merged.saveToString()),new StringReader(defaults));
        assertEquals("air",reloaded.getConfigurationSection("garden.symbols").getValues(false).get("."));assertEquals(37,reloaded.getInt("hall.fee"));
    }
}
