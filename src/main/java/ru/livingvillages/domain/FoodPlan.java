package ru.livingvillages.domain;

import java.util.*;

/** Plans physical farms before growth consumes the buffer; never grants food or workers. */
public final class FoodPlan {
    private FoodPlan(){}
    public static boolean provider(Village.Building b,Rules r){var recipe=r.recipes.get(b.recipe);return b.owner==null&&recipe!=null&&recipe.output().keySet().stream().anyMatch(r.foods::containsKey);}
    private static boolean primary(Village.Building b,Rules r){var recipe=r.recipes.get(b.recipe);return provider(b,r)&&recipe.input().isEmpty()&&recipe.mode().equals("standard");}
    public static double output(Village v,Village.Building b,Rules r){var recipe=r.recipes.get(b.recipe);return recipe==null?0:recipe.output().entrySet().stream().mapToLong(e->(long)e.getValue()*r.foods.getOrDefault(e.getKey(),0)).sum()*b.level*r.productionRate(v,b)/recipe.renewDays();}
    public static double capacity(Village v,Rules r,boolean vacancies){double n=0;var used=new HashSet<UUID>();for(var b:v.buildings)if(Jobs.usable(b)&&b.resourceConfirmed&&primary(b,r)){var worker=v.residents.stream().filter(p->p.alive&&v.adult(p)&&b.id.equals(p.workplace)&&!used.contains(p.id)&&Autonomy.producer(v,p,r.recipes.get(b.recipe))).findFirst().orElse(null);if(worker!=null){used.add(worker.id);n+=output(v,b,r);}else if(vacancies)n+=output(v,b,r);}return n;}
    public static double stableCapacity(Village v,Rules r){return v.buildings.stream().filter(b->Jobs.usable(b)&&b.resourceConfirmed&&primary(b,r)&&v.residents.stream().anyMatch(w->w.alive&&v.adult(w)&&b.id.equals(w.workplace)&&Autonomy.producer(v,w,r.recipes.get(b.recipe))&&(w.workplacePinned||w.role!=Village.Role.ELDER&&w.role!=Village.Role.MERCHANT))).mapToDouble(b->output(v,b,r)).sum();}
    public static long goal(Village v,Rules r){long people=Math.max(v.living(),Math.min(r.maxPopulation,v.living()+Math.max(r.arrivalBatch,r.foodGrowthBuffer)));return people*r.foodPerResident*r.foodTargetDays+r.dailyFood(v);}
    public static double target(Village v,Rules r){long people=Math.max(v.living(),Math.min(r.maxPopulation,v.living()+Math.max(r.arrivalBatch,r.foodGrowthBuffer)));long day=(long)people*r.foodPerResident;double refill=Math.max(0,day*r.foodTargetDays-r.foodUnits(v))/(double)Math.max(1,r.planDays);return day*r.foodCapacityMargin+Math.min(day,refill);}
    public static boolean pending(Village v,Rules r){return v.projects.stream().anyMatch(p->p.owner==null&&p.active()&&provider(p.building(v),r));}
    public static String next(Village v,Rules r){
        if(!r.foodPlanning||pending(v,r)||capacity(v,r,true)>=target(v,r))return null;
        boolean vacant=v.buildings.stream().anyMatch(b->Jobs.usable(b)&&b.resourceConfirmed&&primary(b,r)&&v.residents.stream().noneMatch(p->p.alive&&v.adult(p)&&b.id.equals(p.workplace)));
        if(vacant)return null;
        if(!Autonomy.ready(v,"tree_garden")||!(Autonomy.ready(v,"stone_pit")||Autonomy.ready(v,"quarry_house")))return null;
        if(v.nutrition<1||r.foodUnits(v)<r.dailyFood(v)*3)return "relief_garden";
        return "garden";
    }
    public static boolean canFeedArrival(Village v,Rules r,int amount){if(!r.foodPlanning)return true;double current=capacity(v,r,false);var empty=v.buildings.stream().filter(b->Jobs.usable(b)&&b.resourceConfirmed&&primary(b,r)&&v.residents.stream().noneMatch(p->p.alive&&v.adult(p)&&b.id.equals(p.workplace))).sorted(Comparator.comparingDouble((Village.Building b)->output(v,b,r)).reversed()).limit(amount).toList();for(var b:empty)current+=output(v,b,r);return current+1e-9>=(v.living()+amount)*(long)r.foodPerResident;}
    public static Map<String,Integer> cooking(Village v,Rules r){
        var needs=new LinkedHashMap<String,Integer>();long missing=Math.max(0,goal(v,r)-r.foodUnits(v));if(missing==0)return needs;
        for(String item:List.of("BREAD","BAKED_POTATO","COOKED_COD","COOKED_BEEF")){int food=r.foods.getOrDefault(item,0);if(food==0)continue;var craft=Assembly.recipes(r,v,item).stream().filter(c->Assembly.facility(v,c.facility())!=null).findFirst().orElse(null);if(craft==null)continue;
            boolean source=craft.input().keySet().stream().allMatch(raw->v.stock.get(raw)>0||v.buildings.stream().anyMatch(b->Jobs.usable(b)&&r.recipes.containsKey(b.recipe)&&r.recipes.get(b.recipe).output().containsKey(raw)));if(!source)continue;
            needs.put(item,v.stock.get(item)+(int)Math.min(32,(missing+food-1)/food));
        }return needs;
    }
}
