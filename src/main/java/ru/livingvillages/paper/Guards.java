package ru.livingvillages.paper;

import org.bukkit.GameMode;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import ru.livingvillages.domain.*;
import ru.livingvillages.i18n.Messages;

/** Local defence against visible hostile mobs and players who attacked the settlement. */
public final class Guards implements Listener {
    private final LivingVillagesPlugin plugin;
    Guards(LivingVillagesPlugin plugin){this.plugin=plugin;}
    boolean enemy(Village v,Village.Resident r,Villager npc,LivingEntity target){
        if(plugin.npcs.isNpc(target)||!npc.hasLineOfSight(target)||Math.abs(target.getY()-npc.getY())>vertical(r))return false;
        if(target instanceof Player p)return p.getGameMode()!=GameMode.SPECTATOR&&Population.hostile(v,p.getUniqueId(),plugin.content.rules)&&Territory.inside(v,v.world,p.getX(),p.getZ());
        return Threats.visible(v,npc,target,vertical(r));
    }
    double vertical(Village.Resident r){return plugin.content.config.getDouble("guards.maximum-height-difference",4);}
    String name(LivingEntity target){return target instanceof Player p?Messages.text("агрессор ")+p.getName():Threats.name(target);}
    double melee(Village v,Village.Resident r){return r.role==Village.Role.GUARD?plugin.content.config.getDouble("guards.melee-damage",12):plugin.content.config.getDouble("guards.civilian-damage",3);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void friendlyFire(EntityDamageByEntityEvent event){if(plugin.npcs.isNpc(event.getDamager())&&plugin.npcs.isNpc(event.getEntity()))event.setCancelled(true);}
}
