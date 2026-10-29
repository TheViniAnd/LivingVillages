package ru.livingvillages.domain;

import java.util.*;

/** Effective paid prefix, folding clearance and older renovations at each coordinate. */
public final class PaidProjection {
    private PaidProjection(){}
    public record Cell(int x,int y,int z,String expected,Set<String> recorded){ }
    public static List<Cell> prefix(Village v,Village.Project target){
        var relevant=new LinkedHashSet<String>();for(int i=0;i<Math.min(target.index,target.blocks.size());i++){var b=target.blocks.get(i);relevant.add(key(b));}
        var states=new LinkedHashMap<String,LinkedHashSet<String>>();var cells=new LinkedHashMap<String,Village.Block>();
        for(var p:v.projects){
            int count=p==target?Math.min(p.index,p.blocks.size()):p.state.equals("COMPLETE")?p.blocks.size():0;
            if(p.building.equals(target.building))for(int i=0;i<count;i++){var b=p.blocks.get(i);String k=key(b);if(!relevant.contains(k))continue;var known=states.computeIfAbsent(k,q->new LinkedHashSet<>());known.add(b.before);known.add(b.after);cells.put(k,b);}
            if(p==target)break;
        }
        var out=new ArrayList<Cell>();for(var entry:cells.entrySet()){var b=entry.getValue();out.add(new Cell(b.x,b.y,b.z,b.after,Set.copyOf(states.get(entry.getKey()))));}return List.copyOf(out);
    }
    private static String key(Village.Block b){return b.x+":"+b.y+":"+b.z;}
}
