package ru.livingvillages.domain;

import java.util.*;

/** Detects only absent triangular end walls between paired roof slopes. No world access. */
public final class Gables {
    private Gables(){}
    public record Cell(int x,int y,int z,int phase,String data){}
    private static String key(int x,int y,int z){return x+":"+y+":"+z;}
    public static List<Cell> missing(List<Cell> cells,int rotation){
        Map<String,Cell> expected=new HashMap<>();Map<Integer,List<Cell>> levels=new TreeMap<>();
        for(var c:cells){expected.put(key(c.x,c.y,c.z),c);if(c.phase==3 && (c.data.startsWith("$stairs[") || c.data.contains("_stairs[")))levels.computeIfAbsent(c.y,k->new ArrayList<>()).add(c);}
        if(levels.isEmpty())return List.of();boolean acrossX=rotation%2==0;int baseY=levels.keySet().iterator().next();List<Cell> result=new ArrayList<>();
        for(var entry:levels.entrySet()){
            var roof=entry.getValue();int lo=roof.stream().mapToInt(c->acrossX?c.x:c.z).min().orElse(0),hi=roof.stream().mapToInt(c->acrossX?c.x:c.z).max().orElse(0);
            int front=roof.stream().mapToInt(c->acrossX?c.z:c.x).min().orElse(0),back=roof.stream().mapToInt(c->acrossX?c.z:c.x).max().orElse(0);
            if(hi-lo<2)continue;
            for(int end:new int[]{front,back}){var header=expected.get(acrossX?key((lo+hi)/2,baseY-1,end):key(end,baseY-1,(lo+hi)/2));if(header==null || air(header.data) || header.data.contains("lantern") || header.data.contains("glass"))continue;
                for(int a=lo+1;a<hi;a++){int x=acrossX?a:end,z=acrossX?end:a;Cell old=expected.get(key(x,entry.getKey(),z));if(old==null || air(old.data)){Cell fill=new Cell(x,entry.getKey(),z,3,header.data);result.add(fill);expected.put(key(x,fill.y,z),fill);}}
            }
        }return result;
    }
    private static boolean air(String data){return data.equals("air") || data.equals("minecraft:air");}
}
