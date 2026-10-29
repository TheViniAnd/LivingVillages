package ru.livingvillages.domain;

import java.util.*;

/** Read-only aggregation. Private escrow and already consumed materials are never public warehouse stock. */
public final class WarehouseView {
    private WarehouseView(){}
    public record Row(String item,int warehouse,int reserved,int pending){public int total(){return Math.addExact(Math.addExact(warehouse,reserved),pending);}}
    public static List<Row> rows(Village v){var reserved=new TreeMap<String,Integer>();for(var p:v.projects)if(p.owner==null&&p.active())p.escrow.forEach((k,n)->{if(n>0)reserved.merge(k,n,Math::addExact);});var keys=new TreeSet<>(v.stock.items.keySet());keys.addAll(reserved.keySet());keys.addAll(v.commonReturns.keySet());return keys.stream().map(k->new Row(k,v.stock.get(k),reserved.getOrDefault(k,0),v.commonReturns.getOrDefault(k,0))).filter(r->r.total()>0).toList();}
}
