package ru.livingvillages.paper;

import ru.livingvillages.i18n.Messages;

import org.bukkit.entity.*;
import ru.livingvillages.domain.Village;

/** A cave monster or a neutral animal is not an observed attack on the settlement. */
final class Threats {
    private Threats(){}
    static String name(LivingEntity e){return switch(e.getType()){case ZOMBIE,ZOMBIE_VILLAGER,HUSK,DROWNED->Messages.text("зомби");case SKELETON,STRAY,WITHER_SKELETON,BOGGED->Messages.text("скелет");case CREEPER->Messages.text("крипер");case SPIDER,CAVE_SPIDER->Messages.text("паук");case PILLAGER,VINDICATOR,EVOKER->Messages.text("разбойник");case PHANTOM->Messages.text("фантом");case WITCH->Messages.text("ведьма");case ENDERMAN->Messages.text("разгневанный странник Края");default->Messages.text("враждебный моб");};}
    static boolean visible(Village village,Villager observer,LivingEntity enemy,double maximumHeight){
        if(!(enemy instanceof Enemy) || !enemy.isValid() || enemy.isDead() || !observer.getWorld().equals(enemy.getWorld()))return false;
        if(Math.abs(enemy.getY()-observer.getY())>maximumHeight || Math.abs(enemy.getX()-village.x)>village.radius+8 || Math.abs(enemy.getZ()-village.z)>village.radius+8)return false;
        if(enemy instanceof Enderman || enemy instanceof PigZombie){if(!(enemy instanceof Mob mob) || mob.getTarget()==null)return false;}
        if(enemy instanceof Spider spider && spider.getTarget()==null && enemy.getLocation().getBlock().getLightLevel()>7)return false;
        return observer.hasLineOfSight(enemy);
    }
}
