package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

import java.util.*;

/** Bounded demand-driven crafting from free communal stock. Escrow is never an input. */
public final class Assembly {
    private Assembly(){}
    public static Village.Building facility(Village v,String kind){return v.buildings.stream().filter(b->supports(v,b,kind)).findFirst().orElse(null);}
    public static boolean supports(Village v,Village.Building b,String kind){if(b.owner!=null||!b.complete||b.damaged||b.upkeepMissing)return false;
        if(kind.equals("hand"))return true;
        var cells=Projects.expected(v,b.id);boolean bench=cells.stream().anyMatch(c->c.after.startsWith("minecraft:crafting_table")),furnace=cells.stream().anyMatch(c->c.after.startsWith("minecraft:furnace")),smith=cells.stream().anyMatch(c->c.after.startsWith("minecraft:smithing_table"));return kind.equals("bench")&&bench||kind.equals("furnace")&&furnace||kind.equals("forge")&&furnace&&smith;
    }
    public static String wood(Village v){return v.buildings.stream().filter(b->b.wood!=null && b.kind.startsWith("tree_garden")).map(b->b.wood).findFirst().orElse("OAK");}
    public static String wood(Village v,Rules rules){return rules.cultureWood.getOrDefault(v.culture,wood(v));}
    public static List<Rules.Craft> recipes(Rules rules,Village v,String item){String wood=wood(v,rules);return rules.crafts.values().stream().filter(c->c.output().containsKey(item)).sorted(Comparator.comparingInt(c->c.input().keySet().stream().anyMatch(k->k.startsWith(wood+"_"))?0:1)).toList();}
    public static LinkedHashMap<String,Integer> demands(Village v,Rules rules){return essentialDemands(v,rules);}
    public static LinkedHashMap<String,Integer> essentialDemands(Village v,Rules rules){
        var result=new LinkedHashMap<String,Integer>();for(var entry:rules.tools.entrySet())if(v.residents.stream().anyMatch(r->r.alive && v.adult(r) && r.role.name().equals(entry.getKey())) || v.buildings.stream().anyMatch(b->b.owner==null && Jobs.required(b,rules)!=null && Jobs.required(b,rules).name().equals(entry.getKey())))result.put(entry.getValue().item(),1);if(v.stock.get("COBBLESTONE")<3){result.put("WOODEN_PICKAXE",1);result.put("WOODEN_AXE",1);result.put("WOODEN_HOE",1);}
        if(rules.autonomy){SelfSupply.tools(v,rules).forEach((k,n)->result.merge(k,n,Math::max));for(String item:List.of("CHARCOAL","WHEAT_SEEDS",Groves.sapling(wood(v,rules))))result.merge(item,SelfSupply.reserve(v,rules,item),Math::max);}
        for(var p:v.projects.stream().filter(p->p.owner==null && p.state.equals("COLLECTING")).sorted(Comparator.comparingInt(p->p.essential?0:1)).toList())for(var e:p.required.entrySet())result.merge(e.getKey(),Math.max(0,e.getValue()-p.escrow.getOrDefault(e.getKey(),0)),Math::addExact);
        for(var b:v.buildings)if(b.owner==null && b.upkeepMissing)b.upkeep.forEach((k,n)->result.merge(k,n,Math::max));
        result.merge(wood(v,rules)+"_LOG",rules.stockLogs,Math::max);result.merge("COBBLESTONE",rules.stockStone,Math::max);result.merge("CHARCOAL",8,Math::max);
        if(rules.foodPlanning)FoodPlan.cooking(v,rules).forEach((item,n)->result.merge(item,n,Math::max));
        v.laws.strategic.forEach((item,n)->result.merge(item,n,Math::max));
        if(demandsWater(v))result.merge("BUCKET",1,Math::max);
        if(facility(v,"forge")!=null)for(var tier:rules.upgrades.values())if(!tier.isEmpty())result.putIfAbsent(tier.getFirst().item(),1);
        if(rules.autonomy){var ordered=new LinkedHashMap<String,Integer>();SelfSupply.tools(v,rules).keySet().forEach(k->{if(result.containsKey(k))ordered.put(k,result.get(k));});result.forEach(ordered::putIfAbsent);return ordered;}
        return result;
    }
    private static boolean demandsWater(Village v){return v.buildings.stream().anyMatch(b->b.complete && "water_supply".equals(b.recipe));}
    public static Map<String,Integer> rawDemand(Village v,Rules rules){return materialPlan(v,rules,true);}
    public static Map<String,Integer> materialPlan(Village v,Rules rules,boolean rawOnly){
        var targets=new LinkedHashMap<>(demands(v,rules));var expanded=new HashMap<String,Integer>();
        for(int pass=0;pass<20;pass++){boolean changed=false;
            for(var e:new ArrayList<>(targets.entrySet())){int missing=Math.max(0,e.getValue()-v.stock.get(e.getKey()));var choices=recipes(rules,v,e.getKey());if(missing==0||choices.isEmpty())continue;var craft=choices.getFirst();
                int times=(missing+craft.output().get(e.getKey())-1)/craft.output().get(e.getKey()),before=expanded.getOrDefault(e.getKey(),0);if(times<=before)continue;expanded.put(e.getKey(),times);changed=true;
                for(var input:craft.input().entrySet()){targets.merge(input.getKey(),Math.multiplyExact(input.getValue(),times-before),Math::addExact);targets.merge(input.getKey(),SelfSupply.reserve(v,rules,input.getKey()),Math::max);}
                for(var fuel:craft.fuel().entrySet()){int jobs=(times+craft.batch()-1)/craft.batch()-(before+craft.batch()-1)/craft.batch();if(jobs>0)targets.merge(fuel.getKey(),Math.multiplyExact(fuel.getValue(),jobs),Math::addExact);}
            }if(!changed)break;
        }
        var result=new LinkedHashMap<String,Integer>();targets.forEach((item,n)->{int missing=Math.max(0,n-v.stock.get(item));if(missing>0&&(!rawOnly||recipes(rules,v,item).isEmpty()))result.put(item,missing);});return result;
    }
    public static void elapse(Village v,Rules rules,long millis){
        if(!rules.timedCrafting||v.paused||v.abandoned)return;v.craftElapsedMillis=Math.addExact(v.craftElapsedMillis,Math.max(0,millis));int passes=rules.maxCraftPasses;
        while(v.craftElapsedMillis>=rules.craftIntervalMillis&&passes-->0){v.craftElapsedMillis-=rules.craftIntervalMillis;v.craftRound=Math.addExact(v.craftRound,1);round(v,rules,true);}
    }
    public static void day(Village v,Rules rules){round(v,rules,false);}
    private static void round(Village v,Rules rules,boolean timed){if(!rules.autonomy || v.abandoned || v.underAttack || v.nutrition<.75)return;int count=0;v.supplyReason=rawDemand(v,rules).isEmpty()?Messages.text("Текущие припасы обеспечены"):Messages.text("Нужно сырьё или свободный работник");
        for(var r:v.residents)if(count<(timed?rules.craftWorkersPerPass:rules.craftWorkers) && r.alive && v.adult(r) && (timed?r.lastCraftRound!=v.craftRound:r.economicDay!=v.days) && Set.of(Village.Role.ELDER,Village.Role.MERCHANT,Village.Role.BUILDER,Village.Role.ARTISAN,Village.Role.SMITH,Village.Role.BAKER,Village.Role.STONEMASON).contains(r.role)){
            for(var e:demands(v,rules).entrySet())if(v.stock.get(e.getKey())<e.getValue() && make(v,rules,r,e.getKey(),e.getValue(),new HashSet<>(),0)){r.lastCraftRound=v.craftRound;count++;break;}
        }
    }
    private static Village.Building station(Village v,Village.Resident worker,String kind){
        var assigned=v.buildings.stream().filter(b->b.id.equals(worker.workplace) && Jobs.usable(b)).findFirst().orElse(null);
        if(assigned!=null){var cells=Projects.expected(v,assigned.id);boolean bench=cells.stream().anyMatch(c->c.after.startsWith("minecraft:crafting_table")),furnace=cells.stream().anyMatch(c->c.after.startsWith("minecraft:furnace")),smith=cells.stream().anyMatch(c->c.after.startsWith("minecraft:smithing_table"));if(kind.equals("hand") || kind.equals("bench") && bench || kind.equals("furnace") && furnace || kind.equals("forge") && furnace && smith)return assigned;}
        if((worker.role==Village.Role.ELDER || worker.role==Village.Role.MERCHANT || worker.role==Village.Role.BUILDER) && !kind.equals("forge"))return facility(v,kind);
        return null;
    }
    private static boolean make(Village v,Rules rules,Village.Resident r,String item,int target,Set<String> path,int depth){if(depth>12 || !path.add(item))return false;
        for(var craft:recipes(rules,v,item)){
            var station=station(v,r,craft.facility());if(station==null){v.supplyReason=Messages.text("Нужен объект: ")+craft.facility();continue;}
            int times=Math.max(1,Math.min(craft.batch(),(target-v.stock.get(item)+craft.output().get(item)-1)/craft.output().get(item)));
            boolean base=available(v,rules,craft,1);if(!base){var inputs=new LinkedHashMap<>(craft.input());craft.fuel().forEach((k,n)->inputs.merge(k,n,Math::addExact));for(var input:inputs.entrySet()){int keep=craftReserveFuel(craft,input.getKey())?0:SelfSupply.reserve(v,rules,input.getKey());int needed=Math.addExact(input.getValue(),keep);if(v.stock.get(input.getKey())<needed && make(v,rules,r,input.getKey(),needed,new HashSet<>(path),depth+1))return true;}v.supplyReason=Messages.text("Не хватает сырья для ")+item;continue;}
            while(times>1 && !available(v,rules,craft,times))times--;var cost=cost(craft,times);int out=Math.multiplyExact(craft.output().values().stream().mapToInt(Integer::intValue).sum(),times);
            if(v.stock.size()-cost.values().stream().mapToInt(Integer::intValue).sum()+out>v.capacity){v.supplyReason=Messages.text("Склад заполнен");continue;}
            if(craft.toolRole()!=null && !Tools.acquire(v,r,craft.toolRole(),rules)){v.supplyReason=Messages.text("Не хватает инструмента для ")+item;continue;}
            cost.forEach(v.stock::take);final int batch=times;craft.output().forEach((k,n)->{v.stock.add(k,n*batch,v.capacity);v.crafted.merge(k,(long)n*batch,Math::addExact);});if(craft.toolRole()!=null)Tools.wear(r,1);r.economicDay=v.days;r.craftWorkplace=station.id;r.craftDay=v.days;v.supplyReason=Messages.text("Изготавливаем: ")+item;return true;
        }return false;
    }
    private static Map<String,Integer> cost(Rules.Craft c,int times){var result=new LinkedHashMap<String,Integer>();c.input().forEach((k,n)->result.put(k,Math.multiplyExact(n,times)));c.fuel().forEach((k,n)->result.merge(k,n,Math::addExact));return result;}
    private static boolean craftReserveFuel(Rules.Craft c,String item){return c.fuel().containsKey(item);}
    private static boolean available(Village v,Rules rules,Rules.Craft c,int times){var cost=cost(c,times);long nutrition=0;for(var e:cost.entrySet()){if(v.stock.get(e.getKey())<e.getValue() || v.stock.get(e.getKey())-e.getValue()<SelfSupply.reserve(v,rules,e.getKey()) && !craftReserveFuel(c,e.getKey()))return false;nutrition+=(long)e.getValue()*rules.foods.getOrDefault(e.getKey(),0);}long result=c.output().entrySet().stream().mapToLong(e->(long)e.getValue()*times*rules.foods.getOrDefault(e.getKey(),0)).sum();return nutrition==0 || result>nutrition || rules.foodUnits(v)-nutrition>=rules.dailyFood(v)*Math.max(v.laws.foodDays,rules.warningDays);}
}
