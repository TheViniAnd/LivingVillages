package ru.livingvillages.domain;

import java.util.*;

/** Public repairs preserve private property and rank actual damaged objects. */
public final class Repairs {
    private Repairs(){}
    public static int priority(Village.Building b,Rules rules){var recipe=rules.recipes.get(b.recipe);if(recipe!=null && recipe.output().keySet().stream().anyMatch(rules.foods::containsKey))return 100;if(b.beds>0 || b.kind.startsWith("hall"))return 90;if(b.kind.startsWith("warehouse"))return 80;if(b.kind.startsWith("street_light") || b.kind.equals("road"))return 70;return 60;}
    public static List<Village.Building> needed(Village v,Rules rules){return v.buildings.stream().filter(b->b.owner==null && b.damaged && v.projects.stream().noneMatch(p->p.building.equals(b.id) && p.active())).sorted(Comparator.comparingInt((Village.Building b)->priority(b,rules)).reversed()).toList();}
}
