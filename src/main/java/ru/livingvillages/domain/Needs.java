package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

import java.util.*;

/** Purchases follow current work, not a permanent shopping list. All counts are free stock. */
public final class Needs {
    private Needs(){}
    public record Need(int count,String reason){}
    private record Derived(Map<String,Integer> direct,Map<String,Integer> expanded,Map<String,Integer> raw){}
    /** Immutable display result; execution deliberately recalculates current needs under the village lock. */
    public static Map<String,Need> catalog(Village v,Rules rules){
        var direct=Assembly.demands(v,rules);var expanded=Assembly.materialPlan(v,rules,false);var raw=new HashMap<String,Integer>();expanded.forEach((item,n)->{if(Assembly.recipes(rules,v,item).isEmpty())raw.put(item,n);});
        var derived=new Derived(direct,expanded,raw);var out=new LinkedHashMap<String,Need>();for(String item:rules.prices.keySet())out.put(item,current(v,rules,item,derived));return Collections.unmodifiableMap(out);
    }
    public static Need current(Village v,Rules rules,String item){
        return current(v,rules,item,null);
    }
    private static Need current(Village v,Rules rules,String item,Derived derived){
        if(v.abandoned)return new Need(0,Messages.text("Поселение заброшено"));
        int target=v.laws.strategic.getOrDefault(item,0);var reasons=new LinkedHashSet<String>();if(target>v.stock.get(item))reasons.add(Messages.text("стратегический запас по закону"));
        for(var p:v.projects)if(p.owner==null && p.state.equals("COLLECTING")){
            int missing=Math.max(0,p.required.getOrDefault(item,0)-p.escrow.getOrDefault(item,0));
            if(missing>0){target=Math.addExact(target,missing);reasons.add(Messages.text("общая стройка"));}
        }
        int upkeep=Maintenance.reserve(v,item);if(upkeep>0){target=Math.max(target,upkeep);reasons.add(Messages.text("обслуживание"));}
        for(var b:v.buildings)if(b.owner==null && b.complete && !b.damaged && !b.upkeepMissing){
            var recipe=rules.recipes.get(b.recipe);boolean staffed=v.residents.stream().anyMatch(r->r.alive && v.adult(r) && b.id.equals(r.workplace));
            if(recipe!=null && staffed && Autonomy.wanted(v,b,recipe,rules,derived==null?null:derived.raw())){int n=recipe.input().getOrDefault(item,0);if(n>0){target=Math.max(target,n*2);reasons.add(Messages.text("сырьё для работающего объекта"));}}
            var role=Jobs.required(b,rules);var tool=role==null?null:rules.tools.get(role.name());if(staffed && tool!=null && tool.item().equals(item)){target=Math.max(target,1);reasons.add(Messages.text("запас рабочего инструмента"));}
        }
        if(rules.councilEnabled){int reserve=SelfSupply.reserve(v,rules,item);if(reserve>0){target=Math.max(target,reserve);reasons.add(Messages.text("сохранение рабочего и семенного запаса"));}}
        var direct=derived==null?Assembly.demands(v,rules):derived.direct();var raw=derived==null?Assembly.materialPlan(v,rules,false):derived.expanded();
        if(rules.autonomy && direct.getOrDefault(item,0)>0){target=Math.max(target,direct.get(item));reasons.add(Messages.text("текущее снабжение"));}
        // rawDemand already subtracts stock; its expansion is also needed when the end item is not traded.
        int need=Math.max(Math.max(0,target-v.stock.get(item)),rules.autonomy?raw.getOrDefault(item,0):0);
        if(raw.getOrDefault(item,0)>0 && rules.autonomy)reasons.add(Messages.text("сырьё текущих работ"));
        if(rules.foods.containsKey(item)){
            long missing=Math.max(0,rules.dailyFood(v)*Math.max(rules.foodTargetDays,v.laws.foodDays)-rules.foodUnits(v));int units=rules.foods.get(item);
            need=Math.max(need,Math.toIntExact(Math.min(Integer.MAX_VALUE,(missing+units-1)/units)));if(missing>0)reasons.add(Messages.text("запас питания"));
        }
        need=Math.max(0,Math.min(v.capacity-v.stock.size(),need));
        return new Need(need,need==0?Messages.text("Сейчас деревне не требуется"):String.join("; ",reasons));
    }
}
