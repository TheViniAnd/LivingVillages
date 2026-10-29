package ru.livingvillages.domain;

import java.util.*;

/** The warehouse remains authoritative; these are floors, never duplicate inventories. */
public final class SelfSupply {
    private SelfSupply(){}
    public static int reserve(Village v,Rules r,String item){
        int keep=Maintenance.reserve(v,item);if(r.foodPlanning&&item.equals("COBBLESTONE")&&!Autonomy.ready(v,"stone_pit")&&!Autonomy.ready(v,"quarry_house"))keep=Math.max(keep,r.bootstrapStoneReserve);if(!r.autonomy)return keep;
        if(item.equals("CHARCOAL") && Assembly.facility(v,"furnace")!=null)keep=Math.max(keep,r.fuelReserve);
        if(item.equals("WHEAT_SEEDS") && v.buildings.stream().anyMatch(b->Jobs.usable(b)&&Objects.toString(b.recipe,"").contains("grain")))keep=Math.max(keep,r.seedReserve);
        if(item.equals(Groves.sapling(Assembly.wood(v,r))) && Autonomy.ready(v,"tree_garden"))keep=Math.max(keep,r.seedReserve);
        if(tools(v,r).containsKey(item))keep=Math.max(keep,r.toolReserve);
        return keep;
    }
    public static LinkedHashMap<String,Integer> tools(Village v,Rules r){
        var result=new LinkedHashMap<String,Integer>();
        for(var e:r.tools.entrySet().stream().sorted(Comparator.comparingInt(e->switch(e.getKey()){case "FARMER"->0;case "LUMBERJACK"->1;case "MINER","ARTISAN"->2;default->3;})).toList()){
            var workers=v.residents.stream().filter(p->p.alive&&v.adult(p)&&p.role.name().equals(e.getKey())).toList();
            boolean job=workers.size()>0 || v.buildings.stream().anyMatch(b->Jobs.usable(b)&&Jobs.required(b,r)!=null&&Jobs.required(b,r).name().equals(e.getKey()));
            if(!job)continue;
            boolean low=workers.stream().anyMatch(p->p.toolKind==null || p.toolUses<=r.toolLeadUses);
            result.merge(e.getValue().item(),Math.max(1,r.toolReserve)+(low?1:0),Math::max);
        }
        return result;
    }
    public static Map<String,Integer> needs(Village v,Rules r){var out=new LinkedHashMap<String,Integer>();
        tools(v,r).forEach((item,n)->{if(v.stock.get(item)<n)out.put(item,n-v.stock.get(item));});
        for(String item:List.of("CHARCOAL","WHEAT_SEEDS",Groves.sapling(Assembly.wood(v,r)))){int n=reserve(v,r,item)-v.stock.get(item);if(n>0)out.put(item,n);}
        return out;
    }
}
