package ru.livingvillages.domain;

import java.util.*;

/** Persisted geometry is planned once; seeds vary whole streets, orientation, gaps and offsets. */
public final class Layouts {
    private Layouts(){}
    public record Plot(String kind,int x,int z,int rotation){}
    public record Shape(String kind,int x,int z,int width,int depth){}
    public static List<Plot> arrange(List<Shape> shapes,UUID seed,int attempt){var random=new Random(seed.getMostSignificantBits()^seed.getLeastSignificantBits()^(long)attempt*0x9e3779b97f4a7c15L);int turns=random.nextInt(4),shiftX=random.nextInt(7)-3,shiftZ=random.nextInt(7)-3;boolean mirror=random.nextBoolean();var result=new ArrayList<Plot>();
        for(var s:shapes){int x=s.x()+random.nextInt(7)-3,z=s.z()+random.nextInt(7)-3,w=s.width(),d=s.depth();if(mirror)x=-x-w;for(int i=0;i<turns;i++){int old=x;x=-z-d;z=old;int swap=w;w=d;d=swap;}result.add(new Plot(s.kind(),x+shiftX,z+shiftZ,turns));}return result;
    }
}
