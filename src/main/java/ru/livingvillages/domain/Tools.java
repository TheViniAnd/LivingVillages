package ru.livingvillages.domain;

import java.util.*;

/** Tools are withdrawn once; remaining durability follows the resident across compatible jobs. */
public final class Tools {
    private Tools(){}
    public static boolean acquire(Village v,Village.Resident worker,String role,Rules rules){
        var base=rules.tools.get(role);if(base==null)return true;
        if(worker.toolKind!=null && worker.toolUses>0)worker.tools.put(worker.toolKind,worker.toolUses);
        List<Rules.Tool> options=new ArrayList<>(rules.upgrades.getOrDefault(role,List.of()));options.add(base);if(Set.of("FARMER","LUMBERJACK","ARTISAN","MINER","STONEMASON","SMITH").contains(role))options.add(new Rules.Tool(switch(role){case "FARMER"->"WOODEN_HOE";case "LUMBERJACK"->"WOODEN_AXE";default->"WOODEN_PICKAXE";},32));
        for(var tool:options){long uses=worker.tools.getOrDefault(tool.item(),0L);if(uses<=0 && v.stock.get(tool.item())>0){v.stock.take(tool.item(),1);uses=tool.durability();worker.tools.put(tool.item(),uses);}if(uses>0){worker.toolKind=tool.item();worker.toolUses=uses;return true;}}
        return false;
    }
    public static void wear(Village.Resident worker,long amount){worker.toolUses=Math.max(0,worker.toolUses-amount);if(worker.toolKind!=null)worker.tools.put(worker.toolKind,worker.toolUses);}
}
