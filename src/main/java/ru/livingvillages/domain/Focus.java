package ru.livingvillages.domain;

import java.util.*;

/** Automatic priorities, without player laws or development policy modes. */
public final class Focus {
    private Focus(){}
    public static int guards(Village v,Rules r){return r.regularResidentsPerGuard<=0?1:Math.min(r.regularGuardLimit,Math.max(1,(v.living()+r.regularResidentsPerGuard-1)/r.regularResidentsPerGuard));}
    public static int stockTarget(Village v,Rules r,String item){return r.prices.containsKey(item)?Math.min(32,r.prices.get(item).target()):8;}
    public static int jobPriority(Village v,Village.Building b,Rules r){var recipe=r.recipes.get(b.recipe);if(r.foodPlanning&&FoodPlan.provider(b,r)&&FoodPlan.capacity(v,r,false)<FoodPlan.target(v,r))return 0;if(recipe!=null&&recipe.output().keySet().stream().anyMatch(r.foods::containsKey)&&r.foodUnits(v)<r.dailyFood(v)*Math.max(3,v.laws.foodDays))return 0;return Objects.toString(b.kind,"").startsWith("tree_garden")?3:4;}
}
