package ru.livingvillages.domain;

import java.util.*;

public final class Territory {
    private Territory(){}
    public static boolean inside(Village v,UUID world,double x,double z){return Objects.equals(v.world,world) && Math.floor(x)>=v.x-v.radius && Math.floor(x)<=v.x+v.radius && Math.floor(z)>=v.z-v.radius && Math.floor(z)<=v.z+v.radius;}
    public static boolean access(Village v,UUID player,UUID world,double x,double z){return v.discovered.contains(player) && inside(v,world,x,z);}
    public static List<Village> discovered(Collection<Village> villages,UUID player){return villages.stream().filter(v->v.discovered.contains(player)).sorted(Comparator.comparing((Village v)->Objects.toString(v.name,"")).thenComparing(v->v.id)).toList();}
    public static boolean intersects(Village.Building b,int minX,int minZ,int maxX,int maxZ,int padding){if(b.footprint.isEmpty())return minX<=b.x+b.width+b.plotPadding+padding-1 && maxX>=b.x-b.plotPadding-padding && minZ<=b.z+b.depth+b.plotPadding+padding-1 && maxZ>=b.z-b.plotPadding-padding;return b.footprint.stream().anyMatch(key->{var c=key.split(":");int x=Integer.parseInt(c[0]),z=Integer.parseInt(c[1]);return x>=minX-padding&&x<=maxX+padding&&z>=minZ-padding&&z<=maxZ+padding;});}
    public static boolean plot(Village.Building b,int x,int z){return b.footprint.isEmpty()? x>=b.x-b.plotPadding && x<b.x+b.width+b.plotPadding && z>=b.z-b.plotPadding && z<b.z+b.depth+b.plotPadding:b.footprint.contains(x+":"+z);}
}
