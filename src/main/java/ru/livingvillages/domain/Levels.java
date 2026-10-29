package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

public final class Levels {
    private Levels(){}
    public static final int[] PEOPLE={5,6,8}, BUILDINGS={3,4,5}, JOBS={1,1,2};
    public static void update(Village v,Rules rules){if(v.underAttack || v.abandoned || v.nutrition<.999 || v.beds()<v.living() || rules.foodUnits(v)<rules.dailyFood(v)*Math.max(3,v.laws.foodDays))return;long buildings=v.buildings.stream().filter(b->b.owner==null && b.complete && !b.damaged && !b.upkeepMissing && !"road".equals(b.kind) && !java.util.Objects.toString(b.kind,"").startsWith("street_light")).count();long jobs=v.residents.stream().filter(r->r.alive && v.adult(r) && r.workplace!=null).map(r->r.workplace).distinct().count();for(int i=0;i<rules.edition.levelLimit();i++)if(v.living()>=PEOPLE[i] && buildings>=BUILDINGS[i] && jobs>=JOBS[i])v.level=Math.max(v.level,i+1);v.highestLevel=Math.max(v.highestLevel,v.level);}
    
    public static String next(Village v){int i=Math.min(2,v.level);return v.level>=3?Messages.text("Уровень ")+"3/3":Messages.text("Уровень ")+(i+1)+Messages.text(": жителей ")+PEOPLE[i]+Messages.text(", зданий ")+BUILDINGS[i]+Messages.text(", рабочих мест ")+JOBS[i]+Messages.text(", устойчивое питание");}
}
