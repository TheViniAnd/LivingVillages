package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

import java.util.*;
import static ru.livingvillages.domain.Village.*;

/** Each real workplace has one assignment; small villages may temporarily combine a civic office and a trade. */
public final class Jobs {
    private Jobs(){}
    public static Role required(Building b,Rules rules){
        if(b.owner!=null)return null;String kind=Objects.toString(b.kind,"");
        if(kind.startsWith("water_intake"))return Role.WATER_CARRIER;
        if(kind.startsWith("forge"))return Role.SMITH;
        if(kind.startsWith("camp_workbench") || kind.startsWith("kiln_house"))return Role.ARTISAN;
        if(kind.startsWith("hunting_lodge"))return Role.HUNTER;
        if(b.recipe==null)return null;
        return switch(b.recipe){case "quarry"->Role.STONEMASON;case "stone_pit","sand_pit","iron_pit","iron","clay"->Role.MINER;case "tools","smelter"->Role.SMITH;case "cattle","pigs","wool"->Role.HERDER;case "honey","combs"->Role.BEEKEEPER;case "bread"->Role.BAKER;default->{var recipe=rules.recipes.get(b.recipe);yield recipe==null?null:Role.valueOf(recipe.role());}};
    }
    public static boolean usable(Building b){return b.owner==null && b.complete && !b.damaged && !b.upkeepMissing && b.resourceLeft!=0;}
    private static boolean civic(Resident r){return r.role==Role.ELDER || r.role==Role.MERCHANT;}
    public static void reconcile(Village v,Rules rules){Set<UUID> used=new HashSet<>();
        for(var r:v.residents)if(r.alive && v.adult(r) && r.workplacePinned && r.workplace!=null)used.add(r.workplace);
        for(var r:v.residents)if(r.alive){
            if(!v.adult(r)){r.role=Role.CHILD;r.workplace=null;r.workplacePinned=false;continue;}
            if(r.role==Role.GUARD){r.workplace=null;continue;}
            if(r.workplacePinned)continue;
            if(r.role==Role.MERCHANT){r.workplace=v.buildings.stream().filter(b->b.owner==null && b.kind!=null && b.kind.startsWith("warehouse")).map(b->b.id).findFirst().orElse(null);continue;}
            if(r.role==Role.BUILDER && !r.workplacePinned && builders(v)<=rules.minimumBuilders){r.workplace=null;continue;}
            var current=v.buildings.stream().filter(b->b.id.equals(r.workplace)).findFirst().orElse(null);var job=current==null?null:required(current,rules);
            if(current!=null && job!=null && current.resourceLeft!=0 && (current.complete || v.projects.stream().anyMatch(p->p.active() && p.building.equals(current.id))) && used.add(current.id)){if(!civic(r))r.role=job;continue;}
            if(current!=null && job!=null && !civic(r))r.role=job;r.workplace=null;
            if(civic(r)){String kind=r.role==Role.ELDER?"hall":"warehouse";r.workplace=v.buildings.stream().filter(b->b.kind!=null && b.kind.startsWith(kind)).map(b->b.id).findFirst().orElse(null);continue;}
            for(var b:v.buildings)if(usable(b) && required(b,rules)==r.role && used.add(b.id)){r.workplace=b.id;break;}
        }
    }
    public static long builders(Village v){return v.residents.stream().filter(r->r.alive && v.adult(r) && r.role==Role.BUILDER).count();}
    public static boolean recruitable(Village v,Resident r,Rules rules){return r.alive && v.adult(r) && (r.role!=Role.BUILDER || builders(v)>rules.minimumBuilders);}
    public static void ensureBuilders(Village v,Rules rules){
        int missing=rules.minimumBuilders-(int)builders(v);if(missing<=0)return;if(v.projects.stream().noneMatch(Village.Project::active) && v.residents.stream().filter(r->r.alive && v.adult(r) && r.role==Role.FARMER).count()<=1)return;
        for(var r:v.residents.stream().filter(r->r.alive && v.adult(r) && !r.workplacePinned && !civic(r) && r.role!=Role.GUARD && r.role!=Role.BUILDER).sorted(Comparator.comparingInt(r->r.workplace==null?0:r.role==Role.FARMER && v.residents.stream().filter(k->k.alive && v.adult(k) && k.role==Role.FARMER).count()>1?1:r.role==Role.FARMER?3:2)).toList()){
            var b=v.buildings.stream().filter(h->h.id.equals(r.workplace)).findFirst().orElse(null);boolean food=b!=null && rules.recipes.containsKey(b.recipe) && rules.recipes.get(b.recipe).output().keySet().stream().anyMatch(rules.foods::containsKey);
            if(food && v.residents.stream().filter(other->other.alive && v.adult(other) && other.role==Role.FARMER).count()<=1)continue;
            r.role=Role.BUILDER;r.workplace=null;if(--missing<=0)break;
        }
    }
    public static void fill(Village v,Rules rules){
        ensureBuilders(v,rules);
        for(var b:v.buildings.stream().filter(Jobs::usable).filter(b->required(b,rules)!=null).sorted(Comparator.comparingInt((Building b)->Focus.jobPriority(v,b,rules))).toList()){
            var assigned=v.residents.stream().filter(r->r.alive && v.adult(r) && b.id.equals(r.workplace)).findFirst().orElse(null);
            if(assigned!=null && assigned.workplacePinned)continue;
            Resident free=v.residents.stream().filter(r->r.alive && v.adult(r) && r.role!=Role.GUARD && !r.workplacePinned && !civic(r) && r.workplace==null && recruitable(v,r,rules)).findFirst().orElse(null);
            if(assigned!=null && (!civic(assigned) || free==null))continue;
            if(free==null && assigned==null && required(b,rules)!=Role.HUNTER)free=v.residents.stream().filter(r->r.alive && v.adult(r) && r.role==Role.ELDER && !r.workplacePinned).filter(r->v.buildings.stream().noneMatch(other->other.id.equals(r.workplace) && required(other,rules)!=null)).findFirst().orElse(null);
            if(free!=null){if(assigned!=null)assigned.workplace=null;free.workplace=b.id;if(!civic(free))free.role=required(b,rules);}
        }
        reconcile(v,rules);
        if(rules.foodPlanning&&FoodPlan.stableCapacity(v,rules)<FoodPlan.target(v,rules))for(var farm:v.buildings)if(usable(farm)&&farm.resourceConfirmed&&FoodPlan.provider(farm,rules)&&required(farm,rules)==Role.FARMER&&v.residents.stream().noneMatch(w->w.alive&&v.adult(w)&&farm.id.equals(w.workplace)&&(w.role==Role.FARMER||w.workplacePinned))){
            var helper=v.residents.stream().filter(w->w.alive&&v.adult(w)&&!w.workplacePinned&&!civic(w)&&w.role!=Role.GUARD&&recruitable(v,w,rules)).filter(w->w.workplace==null||w.role==Role.ARTISAN||w.role==Role.SMITH).findFirst().orElse(null);
            if(helper!=null){for(var temporary:v.residents)if(temporary!=helper&&farm.id.equals(temporary.workplace)&&!temporary.workplacePinned)temporary.workplace=null;helper.role=Role.FARMER;helper.workplace=farm.id;v.developmentReason=Messages.text("Назначен фермер для продовольственного запаса: ")+helper.name;if(FoodPlan.stableCapacity(v,rules)>=FoodPlan.target(v,rules))break;}
        }
    }
    public static boolean available(Village v,Resident r,Rules rules){return r.alive && v.adult(r) && r.role!=Role.GUARD && r.role!=Role.MERCHANT && !r.workplacePinned && (r.workplace==null || civic(r) && v.buildings.stream().noneMatch(b->b.id.equals(r.workplace) && required(b,rules)!=null));}
    
    
    public static Role activity(Village v,Resident r,Rules rules){return v.buildings.stream().filter(b->b.id.equals(r.workplace)).map(b->required(b,rules)).filter(Objects::nonNull).findFirst().orElse(r.role);}
}
