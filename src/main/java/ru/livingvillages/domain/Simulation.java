package ru.livingvillages.domain;

import java.util.*;
import static ru.livingvillages.domain.Village.*;

/** Exactly one production path for both loaded and unloaded villages. */
public final class Simulation {
    private record Production(Map<String,Integer> input,Map<String,Integer> output){}
    private final Rules rules;
    public Simulation(Rules rules) { this.rules=rules; }
    public void elapse(Village v,long millis,int maxDays) {
        if(v.paused || v.abandoned) return;
        v.elapsedMillis=Math.addExact(v.elapsedMillis,Math.max(0,millis));
        int budget=maxDays;
        while(v.elapsedMillis>=rules.dayMillis && budget-->0 && !v.abandoned) {
            v.elapsedMillis-=rules.dayMillis;
            day(v);
        }
        Assembly.elapse(v,rules,millis);
        Population.elapse(v,rules,millis);
        boolean builder=v.residents.stream().anyMatch(r -> r.alive && v.adult(r) && (r.role==Role.BUILDER || r.role==Role.ELDER));
        if(builder && v.nutrition>=.75) {
            // One building at a time. Essential common projects take precedence over personal orders.
            Project p=Projects.next(v).orElse(null);
            if(p!=null) p.credit=Math.min(p.blocks.size(),p.credit+millis/1000.0*rules.workPerSecond*v.nutrition);
        }
    }
    public void day(Village v) {
        v.days++;Social.day(v,rules);
        Vitals.reconcile(v,rules);
        Projects.releaseCommonReturns(v);
        Maintenance.day(v,rules);
        Housing.reconcile(v,rules.familySize);
        Contracts.expire(v);
        electReplacement(v,rules);
        Jobs.reconcile(v,rules);
        Recovery.foodWorker(v,rules);Autonomy.assign(v,rules);
        Jobs.fill(v,rules);
        WorkRotation.day(v,rules);

        for(Building b:v.buildings) {
            if(!b.complete || b.damaged || b.upkeepMissing || !b.resourceConfirmed || b.owner!=null || v.underAttack) continue;
            Rules.Recipe recipe=rules.recipes.get(b.recipe);
            if(recipe==null || recipe.condition().equals("water_source") && !rules.waterEnabled || b.resourceLeft==0 || b.cycles>v.days || !Autonomy.wanted(v,b,recipe,rules)) continue;
            double rate=rules.productionRate(v,b);if(rate<=0)continue;
            Resident worker=v.residents.stream().filter(r -> r.economicDay!=v.days && b.id.equals(r.workplace) && Autonomy.producer(v,r,recipe)).findFirst().orElse(null);
            if(worker==null || v.nutrition<=0) continue;
            boolean handGrove=false;
            if(!Tools.acquire(v,worker,Jobs.required(b,rules).name(),rules)){if(recipe.condition().equals("grove") && v.days-b.plantedDay>=recipe.renewDays()*2L)handGrove=true;else continue;}
            if(recipe.condition().equals("iron") && worker.toolKind!=null && worker.toolKind.startsWith("WOODEN_"))continue;
            double efficiency=worker.hungerDays>=rules.exhaustionDays?Math.min(.5,v.nutrition):v.nutrition;
            b.productionCredit=Math.min(1,b.productionCredit+Math.max(.1,efficiency));
            if(b.productionCredit<1) continue;
            var production=new Production(recipe.input(),recipe.output());if(handGrove){var reduced=new LinkedHashMap<String,Integer>();production.output().forEach((k,n)->reduced.put(k,k.endsWith("_LOG")?Math.max(1,n/2):n));production=new Production(production.input(),reduced);}
            int scale=recipe.mode().equals("standard")?(b.resourceLeft<0?b.level:(int)Math.min(b.level,b.resourceLeft)):1;
            if(production.input().containsKey("COAL") && v.stock.get("COAL")<production.input().get("COAL")*scale && v.stock.get("CHARCOAL")>=production.input().get("COAL")*scale){var replaced=new LinkedHashMap<>(production.input());int fuel=replaced.remove("COAL");replaced.merge("CHARCOAL",fuel,Math::addExact);production=new Production(replaced,production.output());}
            if(rules.foodPlanning){var selected=Harvest.selected(v,rules,recipe,production.output());production=new Production(production.input(),selected);if(selected.isEmpty())continue;}
            int input=production.input().values().stream().mapToInt(Integer::intValue).sum()*scale;
            int output=production.output().values().stream().mapToInt(Integer::intValue).sum()*scale;
            if(v.stock.size()-input+output>v.capacity || production.input().entrySet().stream().anyMatch(e->v.stock.get(e.getKey())<e.getValue()*scale)) continue;
            if(recipe.equipment()!=null){long uses=b.equipmentUses.getOrDefault(recipe.equipment(),0L);if(uses<=0 && v.stock.get(recipe.equipment())>0){v.stock.take(recipe.equipment(),1);uses=recipe.equipmentDurability();}if(uses<=0)continue;b.equipmentUses.put(recipe.equipment(),uses-1);}
            production.input().forEach((k,n)->v.stock.take(k,n*scale));
            production.output().forEach((k,n)->v.stock.add(k,n*scale,v.capacity));
            if(!handGrove)Tools.wear(worker,(long)recipe.toolWear()*scale);worker.economicDay=v.days;production.output().forEach((k,n)->v.produced.merge(k,(long)n*scale,Math::addExact));
            if(recipe.condition().equals("grove"))b.plantedDay=v.days;
            b.productionCredit-=1;
            if(b.resourceLeft>0) b.resourceLeft-=scale;
            double previous=b.cycleReadyDay>0?b.cycleReadyDay:b.cycles>0?b.cycles:v.days;
            b.cycleReadyDay=Math.max(previous,v.days-1)+recipe.renewDays()/rate;
            b.cycles=(long)Math.ceil(b.cycleReadyDay-1e-9);
        }
        if(!rules.timedCrafting)Assembly.day(v,rules);

        long need=rules.dailyFood(v), eaten=0;
        // Carry excess nutrition as a persisted village credit, not a discarded fraction.
        int carry=v.nutritionCarry;
        if(carry>0) { int use=(int)Math.min(need,carry); v.nutritionCarry-=use; eaten+=use; }
        for(var e:rules.foods.entrySet()) {
            if(eaten>=need) break;
            int amount=(int)Math.min(v.stock.get(e.getKey()),(need-eaten+e.getValue()-1)/e.getValue());
            v.stock.take(e.getKey(),amount);
            eaten+=(long)amount*e.getValue();
        }
        if(eaten>need) v.nutritionCarry=Math.addExact(v.nutritionCarry,Math.toIntExact(eaten-need));
        v.nutrition=need==0?0:Math.min(1,eaten/(double)need);
        // Partial feeding reduces accumulated deficit proportionally; one carrot cannot reset starvation.
        for(Resident r:v.residents) if(r.alive) {
            r.hungerDays=v.nutrition>=.999 ? Math.max(0,r.hungerDays-1) : r.hungerDays+(1-v.nutrition);
        }
        Resident victim=v.residents.stream().filter(r->r.alive && r.hungerDays>=rules.deathDays)
            .max(Comparator.comparingDouble(r->r.hungerDays)).orElse(null);
        if(victim!=null) { victim.alive=false; victim.health=0;victim.entity=null; }
        Vitals.recover(v,rules);
        v.abandoned=v.living()==0;
        if(v.abandoned)Missions.abandon(v);else Missions.check(v,rules);
        Development.update(v,rules);
        electReplacement(v,rules);
        Council.review(v,rules);
        if(rules.arrivalMillis<=0 && Population.arrivalDue(v,rules))Population.arrive(v,rules,1);
    }
    public static void electReplacement(Village v){electReplacement(v,new Rules());}
    public static void electReplacement(Village v,Rules rules) {
        if(v.residents.stream().noneMatch(r->r.alive && v.adult(r) && r.role==Role.ELDER))
            v.residents.stream().filter(r->r.alive && v.adult(r) && !r.workplacePinned && r.role!=Role.FARMER).findFirst()
                .or(()->v.residents.stream().filter(r->r.alive && v.adult(r)).findFirst()).ifPresent(r->{r.role=Role.ELDER;r.workplacePinned=false;});
        if(v.living()>=3 && v.residents.stream().noneMatch(r->r.alive && v.adult(r) && r.role==Role.GUARD))
            v.residents.stream().filter(r->r.alive && v.adult(r) && !r.workplacePinned && r.role!=Role.ELDER && r.role!=Role.FARMER && r.role!=Role.MERCHANT && (r.role!=Role.BUILDER || Jobs.builders(v)>rules.minimumBuilders)).min(java.util.Comparator.comparingInt(r->r.role==Role.MERCHANT?2:r.role==Role.BUILDER?0:1)).ifPresent(r->{r.role=Role.GUARD;r.workplace=null;});
        if(v.living()>=5 && v.residents.stream().noneMatch(r->r.alive && v.adult(r) && r.role==Role.MERCHANT))
            v.residents.stream().filter(r->r.alive && v.adult(r) && !r.workplacePinned && r.role!=Role.ELDER && r.role!=Role.GUARD && r.role!=Role.FARMER && (r.role!=Role.BUILDER || Jobs.builders(v)>rules.minimumBuilders)).min(java.util.Comparator.comparingInt(r->r.role==Role.BUILDER?0:r.workplace==null?1:2)).ifPresent(r->{r.role=Role.MERCHANT;r.workplace=null;});
    }
}
