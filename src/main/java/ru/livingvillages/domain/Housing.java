package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

import java.util.*;

/** A household owns its public home; the hall remains temporary communal lodging. */
public final class Housing {
    private Housing(){}
    public static boolean usable(Village.Building b){return b.complete && !b.damaged && !b.upkeepMissing && b.owner==null && b.beds>0;}
    public static boolean hall(Village.Building b){return b.kind==null || b.kind.startsWith("hall");}
    public static Map<UUID,Integer> occupancy(Village v){Map<UUID,Integer> counts=new HashMap<>();for(var r:v.residents)if(r.alive && r.home!=null)counts.merge(r.home,1,Integer::sum);return counts;}
    public static void families(Village v,int size){
        for(var r:v.residents)if(r.alive && r.family==null){UUID group=v.residents.stream().filter(p->p.alive && p.family!=null).map(p->p.family).distinct().filter(id->v.residents.stream().filter(p->p.alive && id.equals(p.family)).count()<v.buildings.stream().filter(b->usable(b) && id.equals(b.family)).mapToInt(b->Math.max(size,b.beds)).max().orElse(size)).findFirst().orElse(r.id);r.family=group;}
        var living=new HashSet<UUID>();for(var r:v.residents)if(r.alive)living.add(r.family);for(var b:v.buildings)if(b.owner==null && b.family!=null && !living.contains(b.family))b.family=null;
    }
    public static String familyName(Village v,UUID family){if(family==null)return Messages.text("не назначена");String names=String.join(", ",v.residents.stream().filter(r->r.alive && family.equals(r.family)).map(r->Objects.toString(r.name,Messages.text("Житель"))).toList());return names.isEmpty()?Messages.text("пустой дом"):names;}
    public static UUID vacantHome(Village v){var counts=occupancy(v);return v.buildings.stream().filter(Housing::usable).filter(b->counts.getOrDefault(b.id,0)<b.beds).map(b->b.id).findFirst().orElse(null);}
    public static UUID waitingFamily(Village v){
        return v.residents.stream().filter(r->r.alive && r.family!=null).filter(r->v.buildings.stream().noneMatch(b->usable(b) && !hall(b) && r.family.equals(b.family))).map(r->r.family).distinct().filter(id->v.projects.stream().noneMatch(p->p.active() && id.equals(p.building(v).family))).findFirst().orElse(null);
    }
    public static int reconcile(Village v){return reconcile(v,2);}
    public static int reconcile(Village v,int size){families(v,size);Map<UUID,Integer> used=new HashMap<>();int moved=0;
        var groups=new LinkedHashMap<UUID,List<Village.Resident>>();for(var r:v.residents)if(r.alive)groups.computeIfAbsent(r.family,k->new ArrayList<>()).add(r);
        for(var entry:groups.entrySet()){
            UUID family=entry.getKey();var members=entry.getValue();var house=v.buildings.stream().filter(Housing::usable).filter(b->!hall(b) && family.equals(b.family) && b.beds>=members.size()).findFirst().orElse(null);
            if(house==null)house=v.buildings.stream().filter(Housing::usable).filter(b->!hall(b) && b.family==null && b.beds>=members.size()).findFirst().orElse(null);
            if(house!=null){house.family=family;for(var r:members){if(!house.id.equals(r.home))moved++;r.home=house.id;}used.merge(house.id,members.size(),Integer::sum);continue;}
            for(var r:members){UUID home=v.buildings.stream().filter(Housing::usable).filter(b->hall(b) && used.getOrDefault(b.id,0)<b.beds).map(b->b.id).findFirst().orElse(null);if(!Objects.equals(r.home,home))moved++;r.home=home;if(home!=null)used.merge(home,1,Integer::sum);}
        }
        return moved;
    }
}
