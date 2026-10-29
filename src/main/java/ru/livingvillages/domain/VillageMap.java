package ru.livingvillages.domain;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;

/** A map of saved territory and surveyed terrain; never reads or loads the world. */
public final class VillageMap {
    public static final Color BORDER=new Color(209,41,39),BUILDING=new Color(94,130,70),CONSTRUCTION=new Color(237,171,45),ROAD=new Color(169,151,112),UNKNOWN=new Color(210,208,188);
    private VillageMap(){}
    public static int extent(Village v){return v.radius+12;}
    public static int pixel(Village v,int coordinate,boolean x){return (int)Math.floor((coordinate-(x?v.x:v.z)+extent(v))*128.0/(extent(v)*2+1));}
    public static int terrainCoordinate(Village v,int cell,boolean x){return (x?v.x:v.z)-extent(v)+(int)Math.floor((cell+.5)*(extent(v)*2+1)/32.0);}
    public static long signature(Village v){long n=Objects.hash(v.x,v.z,v.radius,Arrays.hashCode(v.mapTerrain));for(var b:v.buildings)n=31*n+Objects.hash(b.id,b.x,b.z,b.width,b.depth,b.plotPadding,b.complete,b.damaged,b.owner);for(var p:v.projects)n=31*n+Objects.hash(p.id,p.state,p.index);return n;}
    public static BufferedImage render(Village v){
        BufferedImage image=new BufferedImage(128,128,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setColor(UNKNOWN);g.fillRect(0,0,128,128);
        if(v.mapTerrain!=null)for(int index=0;index<Math.min(1024,v.mapTerrain.length);index++)if(v.mapTerrain[index]!=0){g.setColor(new Color(v.mapTerrain[index]));g.fillRect(index%32*4,index/32*4,4,4);}
        for(var p:v.projects)if(!p.state.equals("CANCELLED"))for(int index=0;index<p.blocks.size();index++){var cell=p.blocks.get(index);if((cell.phase==5 || p.kind.equals("road")) && !cell.after.equals("minecraft:air")){int x=pixel(v,cell.x,true),z=pixel(v,cell.z,false);g.setColor(index<p.index?ROAD:CONSTRUCTION);g.fillRect(x,z,Math.max(1,pixel(v,cell.x+1,true)-x),Math.max(1,pixel(v,cell.z+1,false)-z));}}
        for(var b:v.buildings)if(!"road".equals(b.kind)){if(!b.footprint.isEmpty()){g.setColor(b.damaged?BORDER:!b.complete?CONSTRUCTION:new Color(72,76,86));for(String key:b.footprint){var c=key.split(":");int bx=Integer.parseInt(c[0]),bz=Integer.parseInt(c[1]),px=pixel(v,bx,true),pz=pixel(v,bz,false);g.fillRect(px,pz,Math.max(1,pixel(v,bx+1,true)-px),Math.max(1,pixel(v,bz+1,false)-pz));}continue;}int x=pixel(v,b.x,true),z=pixel(v,b.z,false),w=Math.max(2,pixel(v,b.x+b.width,true)-x),h=Math.max(2,pixel(v,b.z+b.depth,false)-z);g.setColor(!b.complete?CONSTRUCTION:BUILDING);g.fillRect(x,z,w,h);g.setColor(b.damaged?BORDER:new Color(47,43,37));g.drawRect(x,z,w,h);if(b.kind!=null && b.kind.startsWith("hall")){g.setColor(Color.WHITE);g.fillRect(x+w/2,z+1,1,Math.max(1,h-2));g.fillRect(x+1,z+h/2,Math.max(1,w-2),1);}}
        int minX=pixel(v,v.x-v.radius,true),minZ=pixel(v,v.z-v.radius,false),maxX=pixel(v,v.x+v.radius+1,true),maxZ=pixel(v,v.z+v.radius+1,false);g.setColor(new Color(70,20,17));g.drawRect(minX-1,minZ-1,maxX-minX+2,maxZ-minZ+2);g.setColor(BORDER);g.drawRect(minX,minZ,maxX-minX,maxZ-minZ);
        g.setColor(Color.WHITE);g.drawLine(122,3,122,10);g.drawLine(122,3,119,6);g.drawLine(122,3,125,6);g.dispose();return image;
    }
}
