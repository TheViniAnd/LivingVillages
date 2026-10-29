package ru.livingvillages.domain;

import java.util.*;

/** Adds tradable crafted materials without replacing configured prices. */
public final class TradeCatalog {
    private TradeCatalog(){}
    public static void extend(Rules rules){
        for(int pass=0;pass<20;pass++){boolean changed=false;for(var c:rules.crafts.values()){
            if(c.input().keySet().stream().anyMatch(k->!rules.prices.containsKey(k))||c.fuel().keySet().stream().anyMatch(k->!rules.prices.containsKey(k)))continue;
            long cost=0;for(var e:c.input().entrySet())cost=Math.addExact(cost,(long)e.getValue()*rules.prices.get(e.getKey()).base()*100);
            for(var e:c.fuel().entrySet())cost=Math.addExact(cost,((long)e.getValue()*rules.prices.get(e.getKey()).base()*100+c.batch()-1)/c.batch());
            for(var out:c.output().entrySet())if(!rules.prices.containsKey(out.getKey())){int base=Math.toIntExact(Math.max(1,(cost+out.getValue()*100L-1)/(out.getValue()*100L)));rules.prices.put(out.getKey(),new Rules.Price(base,32,base<4?8:1,Math.toIntExact(Math.min(Integer.MAX_VALUE,Math.max(1,base*40L)))));rules.derivedPrices.add(out.getKey());changed=true;}
        }if(!changed)break;}
    }
    public static int constructionMissing(Village v,String item){int n=0;for(var p:v.projects)if(p.owner==null&&p.state.equals("COLLECTING"))n=Math.addExact(n,Math.max(0,p.required.getOrDefault(item,0)-p.escrow.getOrDefault(item,0)));return Math.max(0,n-v.stock.get(item));}
}
