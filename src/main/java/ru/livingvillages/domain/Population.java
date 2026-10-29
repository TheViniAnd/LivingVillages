package ru.livingvillages.domain;

public final class Population {
    private Population(){}
    public static long maturityMinutes(Village v,Village.Resident r,Rules rules){long left=Math.max(0,(r.matureDay-v.days)*rules.dayMillis-v.elapsedMillis);return (left+59999)/60000;}
    public static boolean vacancies(Village v,Rules rules){return Jobs.builders(v)<rules.minimumBuilders || v.buildings.stream().anyMatch(b->Jobs.usable(b) && Jobs.required(b,rules)!=null && v.residents.stream().noneMatch(r->r.alive && v.adult(r) && b.id.equals(r.workplace)));}
    public static boolean arrivalDue(Village v,Rules rules){boolean house=v.buildings.stream().anyMatch(b->Housing.usable(b) && !Housing.hall(b));int interval=house && v.beds()>v.living()?Math.min(rules.immigrationDays,rules.homeImmigrationDays):rules.immigrationDays;return v.lastImmigrationDay<0?v.days%interval==0:v.days-v.lastImmigrationDay>=interval;}
    public static int arrive(Village v,Rules rules,int maximum){int added=0;
        for(int n=0;n<maximum;n++){
            if(v.abandoned || v.paused || v.underAttack || !v.laws.immigration || v.nutrition<.999 || rules.foodUnits(v)<(rules.dailyFood(v)+rules.foodPerResident)*Math.max(3,v.laws.foodDays) || v.living()>=Math.min(rules.maxPopulation,v.beds())||!FoodPlan.canFeedArrival(v,rules,1))break;
            var r=new Village.Resident();r.name=Names.resident(v,rules,r.id);boolean guard=v.residents.stream().filter(k->k.alive&&v.adult(k)&&k.role==Village.Role.GUARD).count()<Focus.guards(v,rules);r.role=guard?Village.Role.GUARD:Village.Role.BUILDER;
            if(!guard && Jobs.builders(v)>=rules.minimumBuilders && !vacancies(v,rules) && v.residents.stream().filter(person->person.alive&&v.adult(person)).count()>=2 && Math.floorMod(r.id.hashCode(),100)<rules.childImmigrationPercent){r.matureDay=v.days+rules.childGrowthDays;r.role=Village.Role.CHILD;}
            r.home=Housing.vacantHome(v);if(r.home==null)break;v.residents.add(r);v.lastImmigrationDay=v.days;Housing.reconcile(v,rules.familySize);Jobs.reconcile(v,rules);Autonomy.assign(v,rules);Vitals.reconcile(v,rules);added++;
        }return added;
    }
    public static void elapse(Village v,Rules rules,long millis){if(rules.arrivalMillis<=0 || v.paused || v.abandoned)return;long interval=v.lastImmigrationDay<0?rules.firstArrivalMillis:rules.arrivalMillis;v.arrivalElapsedMillis=Math.min(interval,Math.addExact(v.arrivalElapsedMillis,Math.max(0,millis)));if(v.arrivalElapsedMillis>=interval && arrive(v,rules,rules.arrivalBatch)>0)v.arrivalElapsedMillis=0;}
    public static boolean hostile(Village v,java.util.UUID player,Rules rules){var r=v.relations.get(player);return r!=null && r.aggressor && r.trust<rules.hostilityTrust;}
}
