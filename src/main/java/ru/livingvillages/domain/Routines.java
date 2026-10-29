package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

import java.util.*;

/** Display schedules choose destinations only; world time never awards production or food. */
public final class Routines {
    private Routines(){}
    public enum Activity { WORK, CRAFT, BUILD, TRADE, OFFICE, REST, SHELTER, MEET, PLAY, PATROL, TRAIN, GARRISON, IDLE }
    public record Target(Activity activity,UUID building,int x,int y,int z){}
    public static Target plan(Village v,Village.Resident r,long worldTime,long patrolSlot,Rules rules,boolean danger){
        return plan(v,r,worldTime,patrolSlot,patrolSlot,rules,danger);
    }
    public static Target plan(Village v,Village.Resident r,long worldTime,long patrolSlot,long workSlot,Rules rules,boolean danger){
        long time=Math.floorMod(worldTime,24000);boolean night=time>=12500 || time<500;
        if(danger && (!v.adult(r) || r.role!=Village.Role.GUARD))return home(v,r,Activity.SHELTER);
        var project=!v.paused && !v.abandoned && v.adult(r) && (r.role==Village.Role.BUILDER || r.role==Village.Role.ELDER)?Projects.next(v).orElse(null):null;
        // Settlers finish their funded starting camp before joining the ordinary daily schedule.
        if(project!=null && project.starter)return build(v,project);
        if(night && r.role!=Village.Role.GUARD)return home(v,r,Activity.REST);
        if(!v.adult(r))return square(v,r,Activity.PLAY,patrolSlot);
        if(r.role==Village.Role.GUARD){var roads=Lighting.roads(v,true);if(!roads.isEmpty()){var c=roads.get(Math.floorMod(r.id.hashCode()+(int)patrolSlot*13,roads.size()));return new Target(Activity.PATROL,null,c.x,c.y+1,c.z);}return square(v,r,Activity.PATROL,patrolSlot);}
        if(time>=9000 && time<11500)return square(v,r,Activity.MEET,0);
        if(project!=null)return build(v,project);
        if(time>=5000 && time<6500 && r.craftDay==v.days && r.craftWorkplace!=null){var b=building(v,r.craftWorkplace);if(usable(b))return work(v,r,b,Activity.CRAFT,rules,workSlot);}
        var b=building(v,r.workplace);
        if(usable(b)){Activity activity=Jobs.required(b,rules)!=null?Activity.WORK:r.role==Village.Role.MERCHANT?Activity.TRADE:r.role==Village.Role.ELDER?Activity.OFFICE:Activity.IDLE;return work(v,r,b,activity,rules,workSlot);}
        return square(v,r,Activity.IDLE,workSlot);
    }
    private static Target build(Village v,Village.Project project){var b=project.building(v);
        if("road".equals(b.kind) && project.index<project.blocks.size()){var cell=project.blocks.get(project.index);return new Target(Activity.BUILD,b.id,cell.x,cell.y+1,cell.z);}
        return new Target(Activity.BUILD,b.id,b.x+b.width/2,b.workY!=0?b.workY:b.y+2,b.z+b.depth+1);
    }
    private static boolean usable(Village.Building b){return b!=null && b.complete && !b.damaged && !b.upkeepMissing && b.owner==null;}
    private static Village.Building building(Village v,UUID id){return v.buildings.stream().filter(b->b.id.equals(id)).findFirst().orElse(null);}
    public static Target shelter(Village v,Village.Resident r){return home(v,r,Activity.SHELTER);}
    private static Target home(Village v,Village.Resident r,Activity activity){var b=building(v,r.home);if(b==null || !Housing.usable(b))b=v.buildings.stream().filter(Housing::usable).findFirst().orElse(null);if(b==null)return square(v,r,activity,0);
        var beds=Projects.expected(v,b.id).stream().filter(c->c.after.contains("_bed[") && c.after.contains("part=foot")).toList();int index=0;for(var person:v.residents){if(person==r || person.id.equals(r.id))break;if(person.alive && b.id.equals(person.home))index++;}
        if(!beds.isEmpty()){var bed=beds.get(index%beds.size());int dx=0,dz=1;
            if(bed.after.contains("facing=south"))dz=-1;
            else if(bed.after.contains("facing=east")){dx=-1;dz=0;}
            else if(bed.after.contains("facing=west")){dx=1;dz=0;}
            return new Target(activity,b.id,bed.x+dx,bed.y,bed.z+dz);}
        var cells=Projects.expected(v,b.id);var floors=Shelters.floors(v,b,b.workX,b.workZ,64).stream().filter(f->cells.stream().noneMatch(c->c.x==f.x && c.z==f.z && c.y>f.y && c.y<=f.y+2 && !c.after.equals("minecraft:air"))).toList();
        if(!floors.isEmpty()){var floor=floors.get(index%floors.size());return new Target(activity,b.id,floor.x,floor.y+1,floor.z);}
        int dx=Math.floorMod(index,3),dz=index/3;return new Target(activity,b.id,b.x+1+dx,b.workY,b.z+b.depth-2-dz);
    }
    private static Target work(Village v,Village.Resident r,Village.Building b,Activity activity,Rules rules,long slot){var recipe=rules.recipes.get(b.recipe);
        if(activity==Activity.OFFICE){var roads=Lighting.roads(v,true);if(!roads.isEmpty()){var point=roads.get(Math.floorMod(r.id.hashCode()+(int)slot*7,roads.size()));return new Target(activity,null,point.x,point.y+1,point.z);}}

        if(recipe!=null && recipe.role().equals("FARMER")){var crops=Projects.expected(v,b.id).stream().filter(c->c.after.contains("carrots[") || c.after.contains("wheat[") || c.after.contains("sweet_berry_bush[") || c.after.endsWith(":sugar_cane")).toList();if(!crops.isEmpty()){var cell=crops.get(Math.floorMod(r.id.hashCode()+(int)slot,crops.size()));return new Target(activity,b.id,cell.x,cell.y,cell.z);}}
        if(recipe!=null && recipe.condition().equals("grove") && !b.trees.isEmpty()){var tree=b.trees.get(Math.floorMod(r.id.hashCode()+(int)slot,b.trees.size()));return new Target(activity,b.id,tree.x+1,tree.y,tree.z);}
        if(b.width<3 || b.depth<3)return new Target(activity,b.id,b.workX,b.workY,b.workZ);
        int index=Math.floorMod(r.id.hashCode()+(int)slot*5,9);
        int x=Math.clamp(b.workX+index%3-1,b.x+1,b.x+Math.max(1,b.width-2)),z=Math.clamp(b.workZ+index/3-1,b.z+1,b.z+Math.max(1,b.depth-2));
        return new Target(activity,b.id,x,b.workY,z);
    }
    private static Target square(Village v,Village.Resident r,Activity activity,long slot){int index=Math.floorMod(r.id.hashCode()+(int)slot,9);return new Target(activity,null,v.x+(index%3-1)*3,v.y+1,v.z-3+(index/3-1)*3);}
    public static String label(Activity a){return switch(a){case WORK->Messages.text("На работе");case CRAFT->Messages.text("В мастерской");case BUILD->Messages.text("На стройке");case TRADE->Messages.text("В торговом доме");case OFFICE->Messages.text("В ратуше");case REST->Messages.text("Отдыхает дома");case SHELTER->Messages.text("Ищет укрытие");case MEET->Messages.text("На общем дворе");case PLAY->Messages.text("Гуляет рядом с домом");case PATROL->Messages.text("Патрулирует");case TRAIN->Messages.text("Обучается в казарме");case GARRISON->Messages.text("Служит в гарнизоне");case IDLE->Messages.text("Нет доступной работы");};}
}
