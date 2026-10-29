package ru.livingvillages.domain;

import java.util.*;

/** Two reserved seed trees yield once per growth cycle. Visuals never award a second harvest. */
public final class Groves {
    private Groves(){}
    public static String sapling(String wood){return wood.equals("MANGROVE")?"MANGROVE_PROPAGULE":wood+"_SAPLING";}
    public static Map<String,String> visual(Village v,Village.Building b,Rules rules){var out=new LinkedHashMap<String,String>();var recipe=rules.recipes.get(b.recipe);boolean mature=recipe!=null && v.days-b.plantedDay>=Math.max(1,recipe.renewDays()-1);String wood=b.wood.toLowerCase(Locale.ROOT),seed=sapling(b.wood).toLowerCase(Locale.ROOT);
        for(var tree:b.trees){for(int dy=0;dy<3;dy++)out.put(tree.x+":"+(tree.y+dy)+":"+tree.z,mature?"minecraft:"+wood+"_log[axis=y]":dy==0?"minecraft:"+seed:"minecraft:air");for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)out.put((tree.x+dx)+":"+(tree.y+3)+":"+(tree.z+dz),mature?"minecraft:"+wood+"_leaves[distance=1,persistent=true]":"minecraft:air");}return out;
    }
}
