package ru.livingvillages.domain;

import java.util.*;

/** Selective harvesting avoids filling the warehouse with unwanted secondary crops or saplings. */
public final class Harvest {
    private Harvest(){}
    public static Map<String,Integer> selected(Village v,Rules rules,Rules.Recipe recipe,Map<String,Integer> output){
        if(!rules.foodPlanning||!recipe.input().isEmpty()||!recipe.mode().equals("standard")||!Set.of("irrigated","grove").contains(recipe.condition()))return output;
        var need=Assembly.rawDemand(v,rules);var chosen=new LinkedHashMap<String,Integer>();long foodLeft=Math.max(0,FoodPlan.goal(v,rules)-rules.foodUnits(v));
        for(var e:output.entrySet()){
            int nutrition=rules.foods.getOrDefault(e.getKey(),0),target=Math.max(SelfSupply.reserve(v,rules,e.getKey()),Math.max(v.laws.strategic.getOrDefault(e.getKey(),0),Focus.stockTarget(v,rules,e.getKey())));
            int wanted=nutrition>0?(int)Math.min(Integer.MAX_VALUE,(foodLeft+nutrition-1)/nutrition):Math.max(0,target-v.stock.get(e.getKey()));wanted=Math.max(wanted,need.getOrDefault(e.getKey(),0));int count=Math.min(e.getValue(),wanted);
            if(count>0){chosen.put(e.getKey(),count);foodLeft=Math.max(0,foodLeft-(long)count*nutrition);}
        }return chosen;
    }
}
