package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

import java.util.*;

/** Emergency reassignment preserves the guard and uses only a real usable food workplace. */
public final class Recovery {
    private Recovery(){}
    public static boolean foodWorker(Village v,Rules rules){
        if(v.abandoned || v.underAttack || rules.foodUnits(v)>=rules.dailyFood(v)*rules.warningDays)return false;
        var candidates=v.buildings.stream().filter(b->b.owner==null && b.complete && !b.damaged && !b.upkeepMissing && b.resourceConfirmed && b.resourceLeft!=0 && rules.recipes.containsKey(b.recipe)).filter(b->{var recipe=rules.recipes.get(b.recipe);return recipe.output().keySet().stream().anyMatch(rules.foods::containsKey) && recipe.input().entrySet().stream().allMatch(e->v.stock.get(e.getKey())>=e.getValue());}).sorted(Comparator.comparingDouble((Village.Building b)->{var recipe=rules.recipes.get(b.recipe);return -recipe.output().entrySet().stream().mapToLong(e->(long)e.getValue()*rules.foods.getOrDefault(e.getKey(),0)).sum()*rules.productionRate(v,b)/recipe.renewDays();})).toList();
        if(candidates.isEmpty())return false;var target=candidates.getFirst();var recipe=rules.recipes.get(target.recipe);
        if(v.residents.stream().anyMatch(r->r.alive && v.adult(r) && target.id.equals(r.workplace) && (r.role==Village.Role.ELDER || r.role.name().equals(recipe.role()))))return false;
        var worker=v.residents.stream().filter(r->r.alive && v.adult(r) && !r.workplacePinned && r.role==Village.Role.BUILDER && Jobs.recruitable(v,r,rules)).findFirst().or(()->v.residents.stream().filter(r->r.alive && v.adult(r) && !r.workplacePinned && r.role==Village.Role.ELDER).findFirst()).orElse(null);
        if(worker==null)return false;worker.workplace=target.id;if(worker.role!=Village.Role.ELDER)worker.role=Village.Role.valueOf(recipe.role());v.developmentReason=Messages.text("Восстановление питания: ")+worker.name+Messages.text(" назначен на ")+target.kind;return true;
    }
}
