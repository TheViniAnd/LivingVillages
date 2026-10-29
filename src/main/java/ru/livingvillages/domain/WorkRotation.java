package ru.livingvillages.domain;

import java.util.*;

/** Small settlements combine the elder's compatible jobs on different economic days. */
public final class WorkRotation {
    private WorkRotation(){}
    public static void day(Village v,Rules rules){if(!rules.foodPlanning||!rules.autonomy||v.underAttack)return;var raw=Assembly.rawDemand(v,rules);
        for(var elder:v.residents)if(elder.alive&&v.adult(elder)&&elder.role==Village.Role.ELDER&&!elder.workplacePinned){
            var candidates=v.buildings.stream().filter(b->Jobs.usable(b)&&b.resourceConfirmed&&b.cycles<=v.days&&b.resourceLeft!=0).filter(b->{var recipe=rules.recipes.get(b.recipe);var role=Jobs.required(b,rules);return recipe!=null&&role!=null&&Set.of(Village.Role.FARMER,Village.Role.LUMBERJACK,Village.Role.MINER,Village.Role.STONEMASON,Village.Role.WATER_CARRIER).contains(role)&&recipe.input().entrySet().stream().allMatch(e->v.stock.get(e.getKey())>=e.getValue())&&Autonomy.wanted(v,b,recipe,rules);}).filter(b->v.residents.stream().noneMatch(other->other.alive&&v.adult(other)&&!other.id.equals(elder.id)&&b.id.equals(other.workplace))).sorted(Comparator.comparingInt((Village.Building b)->FoodPlan.provider(b,rules)&&rules.foodUnits(v)<rules.dailyFood(v)*3?0:rules.recipes.get(b.recipe).output().keySet().stream().anyMatch(k->raw.getOrDefault(k,0)>0)?1:2).thenComparingDouble(b->b.cycleReadyDay)).toList();
            if(!candidates.isEmpty())elder.workplace=candidates.getFirst().id;else elder.workplace=v.buildings.stream().filter(b->b.kind.startsWith("hall")).map(b->b.id).findFirst().orElse(null);
        }
    }
}
