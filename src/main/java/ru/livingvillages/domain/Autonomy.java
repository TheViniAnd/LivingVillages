package ru.livingvillages.domain;

import java.util.*;

/** Slow priority decisions, independent of loaded entities. WorldWorks reserves actual sites. */
public final class Autonomy {
    public static final Set<String> CORE=Set.of("tree_garden","camp_workbench","stone_pit","sand_pit","iron_pit","kiln_house","forge","settler_lodge","family_home","water_intake","soil_pit","starter_quarry");
    private Autonomy(){}
    public static boolean essential(String kind){return CORE.contains(kind.replace("_terrace","").replace("_pier","").replaceAll("_u[0-9]+$",""))||kind.startsWith("tree_garden_")||kind.startsWith("relief_garden");}
    public static boolean producer(Village v,Village.Resident r,Rules.Recipe recipe){return r.alive && v.adult(r) && (r.role.name().equals(recipe.role()) || Set.of(Village.Role.MINER,Village.Role.STONEMASON,Village.Role.SMITH,Village.Role.BAKER).contains(r.role) && recipe.role().equals("ARTISAN") || Set.of(Village.Role.HERDER,Village.Role.BEEKEEPER).contains(r.role) && recipe.role().equals("FARMER") || r.role==Village.Role.ELDER || r.role==Village.Role.MERCHANT && recipe.role().equals("ARTISAN"));}
    public static void assign(Village v,Rules rules){if(rules.autonomy)Jobs.fill(v,rules);}
    public static boolean has(Village v,String kind){return v.buildings.stream().anyMatch(b->b.owner==null && (b.kind.equals(kind) || b.kind.equals(kind+"_terrace") || b.kind.equals(kind+"_pier") || kind.equals("tree_garden")&&b.kind.startsWith("tree_garden_") || kind.equals("stone_pit")&&"stone_pit".equals(b.recipe)) && b.resourceLeft!=0);}
    public static boolean ready(Village v,String kind){return v.buildings.stream().anyMatch(b->b.complete && !b.damaged && !b.upkeepMissing && (b.kind.startsWith(kind)||kind.equals("stone_pit")&&"stone_pit".equals(b.recipe)));}
    public static String next(Village v,Rules rules){String next=choose(v,rules);return next==null||rules.edition.building(next)?next:null;}
    private static String choose(Village v,Rules rules){
        if(rules.timedCrafting&&Assembly.facility(v,"bench")==null && !has(v,"camp_workbench"))return "camp_workbench";
        // Existing projects may need glass/iron before more housing or food capacity can finish.
        // Their suppliers must be allowed into the queue, including while a CORE house waits.
        var materials=Assembly.materialPlan(v,rules,false);var raw=new LinkedHashMap<String,Integer>();materials.forEach((item,n)->{if(Assembly.recipes(rules,v,item).isEmpty())raw.put(item,n);});
        if(rules.foodPlanning&&ready(v,"tree_garden")&&!ready(v,"stone_pit")&&!ready(v,"quarry_house")&&!has(v,"starter_quarry"))return "starter_quarry";
        if(ready(v,"tree_garden")&&(ready(v,"stone_pit")||ready(v,"quarry_house"))&&Assembly.facility(v,"furnace")==null){String supplier="kiln_house";if(!has(v,supplier))return supplier;}
        if(rules.waterEnabled && raw.getOrDefault("WATER_BUCKET",0)>0 && !has(v,"water_intake") && ready(v,"tree_garden") && Assembly.facility(v,"bench")!=null && Assembly.facility(v,"furnace")!=null && (ready(v,"stone_pit") || ready(v,"quarry_house")))return "water_intake";
        if(v.buildings.stream().anyMatch(b->b.complete && !b.damaged && !b.upkeepMissing && b.resourceLeft!=0 && ("stone_pit".equals(b.recipe) || "quarry".equals(b.recipe))) && Assembly.facility(v,"furnace")!=null){
            if(raw.getOrDefault("SAND",0)>0 && !has(v,"sand_pit"))return "sand_pit";
            if(raw.getOrDefault("RAW_IRON",0)>0 && !has(v,"iron_pit") && v.residents.stream().filter(r->r.alive && v.adult(r)).count()>=5)return "iron_pit";
            if(ready(v,"iron_pit") && !has(v,"forge"))return "forge";
        }
        if(materials.getOrDefault("DIRT",0)>0&&!has(v,"soil_pit")&&Assembly.facility(v,"bench")!=null&&(ready(v,"stone_pit")||ready(v,"quarry_house")||v.stock.get("COBBLESTONE")>=8))return "soil_pit";
        for(var grove:rules.groveKinds.entrySet())if(raw.getOrDefault(grove.getValue()+"_LOG",0)>0&&v.stock.get(Groves.sapling(grove.getValue()))>=2&&v.buildings.stream().noneMatch(b->b.kind.startsWith("tree_garden")&&grove.getValue().equals(b.wood)&&b.resourceLeft!=0))return grove.getKey();
        boolean housingPending=v.projects.stream().anyMatch(p->p.owner==null && p.active() && Set.of("family_home","residence","settler_lodge").contains(p.kind.replace("_terrace","").replace("_pier","")));
        if(v.living()>=v.beds() && v.living()<rules.maxPopulation && !housingPending && ready(v,"tree_garden") && (ready(v,"stone_pit") || ready(v,"quarry_house")))return "family_home";
        if(!has(v,"tree_garden"))return "tree_garden";
        if(v.projects.stream().anyMatch(p->p.owner==null && p.active() && !p.state.equals("SITE_BLOCKED") && (CORE.contains(p.kind.replace("_terrace","").replace("_pier",""))||p.kind.startsWith("tree_garden"))))return null;
        if(Assembly.facility(v,"bench")==null && !has(v,"camp_workbench"))return "camp_workbench";
        if(!has(v,"stone_pit") && !v.buildings.stream().anyMatch(b->b.complete && "quarry".equals(b.recipe) && b.resourceLeft!=0))return "stone_pit";
        if(Assembly.facility(v,"furnace")==null && !has(v,"kiln_house")){String workshop="kiln_house";if(!has(v,workshop))return workshop;}
        String farm=FoodPlan.next(v,rules);if(farm!=null)return farm;
        double food=v.buildings.stream().filter(b->b.owner==null && b.complete && !b.damaged && rules.recipes.containsKey(b.recipe)).mapToDouble(b->{var recipe=rules.recipes.get(b.recipe);return recipe.input().isEmpty()?recipe.output().entrySet().stream().mapToLong(e->(long)e.getValue()*rules.foods.getOrDefault(e.getKey(),0)).sum()*rules.productionRate(v,b)*b.level/recipe.renewDays():0;}).sum();
        if(!rules.foodPlanning&&food<rules.dailyFood(v)*1.2)return "garden";
        if(v.living()>=v.beds()-1 && v.living()<rules.maxPopulation)return "family_home";
        if(v.days>=rules.familyStartDay && v.days-v.lastFamilyPlanDay>=rules.familyIntervalDays && Housing.waitingFamily(v)!=null)return rules.familySize<=2?"family_home":"residence";
        if(v.living()>=v.beds()-1 && v.living()<rules.maxPopulation)return "family_home";
        if(raw.getOrDefault("SAND",0)>0 && !has(v,"sand_pit"))return "sand_pit";
        boolean exhaustedSearch=v.failedSites.stream().filter(k->k.startsWith("iron_pit:")).count()>=4;
        if((v.days>=12 && !exhaustedSearch && (long)v.stock.get("IRON_INGOT")+v.stock.get("RAW_IRON")<8 || raw.getOrDefault("RAW_IRON",0)>0) && !has(v,"iron_pit") && v.residents.stream().filter(r->r.alive && v.adult(r)).count()>=5)return "iron_pit";
        if(ready(v,"iron_pit") && !has(v,"forge"))return "forge";
        if(v.projects.stream().anyMatch(p->p.owner==null && p.active()))return null;
        boolean spare=v.residents.stream().anyMatch(r->Jobs.available(v,r,rules));
        return spare?rules.cultureProjects.getOrDefault(v.culture,List.of()).stream().filter(rules.edition::building).filter(k->!has(v,k)).findFirst().orElse(null):null;
    }
    public static String foodRecovery(Village v,Rules rules){String kind=FoodPlan.next(v,rules);if(kind==null)kind=next(v,rules);if(kind==null)return null;String chosen=kind;if(v.projects.stream().anyMatch(p->p.owner==null && p.active() && Objects.toString(p.kind,"").replace("_terrace","").replace("_pier","").equals(chosen)))return null;return kind.startsWith("tree_garden_")||kind.equals("relief_garden")||Set.of("garden","water_intake","tree_garden","stone_pit","iron_pit","kiln_house","forge","camp_workbench","sand_pit","soil_pit","starter_quarry").contains(kind)?kind:null;}
    public static boolean wanted(Village v,Village.Building b,Rules.Recipe recipe,Rules rules){
        return wanted(v,b,recipe,rules,null);
    }
    public static boolean wanted(Village v,Village.Building b,Rules.Recipe recipe,Rules rules,Map<String,Integer> computedRaw){
        if(!rules.autonomy)return true;
        long nutrition=recipe.output().entrySet().stream().mapToLong(e->(long)e.getValue()*rules.foods.getOrDefault(e.getKey(),0)).sum();
        if(nutrition>0 && rules.foodUnits(v)<(rules.foodPlanning?FoodPlan.goal(v,rules):rules.dailyFood(v)*rules.foodTargetDays))return true;
        var needs=computedRaw==null?Assembly.rawDemand(v,rules):computedRaw;return recipe.output().keySet().stream().anyMatch(item->needs.getOrDefault(item,0)>0 || v.stock.get(item)<Math.max(v.laws.strategic.getOrDefault(item,0),Focus.stockTarget(v,rules,item)));
    }
}
