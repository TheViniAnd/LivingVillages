package ru.livingvillages.paper;

import org.bukkit.entity.Player;
import org.bukkit.map.*;
import ru.livingvillages.domain.VillageMap;
import java.awt.Color;
import java.util.UUID;

public final class SettlementMap extends MapRenderer {
    private final LivingVillagesPlugin plugin;private final UUID id;
    private MapCanvas lastCanvas;private long lastSignature=Long.MIN_VALUE;
    public SettlementMap(LivingVillagesPlugin plugin,UUID id){super(false);this.plugin=plugin;this.id=id;}
    @Override public void render(MapView view,MapCanvas canvas,Player player){
        var v=plugin.villages.get(id);if(v==null)return;long signature=VillageMap.signature(v);
        if(canvas==lastCanvas && signature==lastSignature)return;
        var image=VillageMap.render(v);for(int x=0;x<128;x++)for(int z=0;z<128;z++)canvas.setPixelColor(x,z,new Color(image.getRGB(x,z)));
        lastCanvas=canvas;lastSignature=signature;
    }
}
