package ru.livingvillages.domain;

public final class Maintenance {
    private Maintenance(){}
    public static void day(Village v,Rules rules){
        for(var b:v.buildings)if(b.complete && b.owner==null && !b.upkeep.isEmpty() && v.days>=b.upkeepDue){
            long foodCost=b.upkeep.entrySet().stream().mapToLong(e->(long)e.getValue()*rules.foods.getOrDefault(e.getKey(),0)).sum();
            boolean affordable=b.upkeep.entrySet().stream().allMatch(e->v.stock.get(e.getKey())>=e.getValue());
            if(foodCost>0 && rules.foodUnits(v)-foodCost<rules.dailyFood(v)*rules.warningDays)affordable=false;
            b.upkeepMissing=!affordable;
            if(affordable){b.upkeep.forEach(v.stock::take);b.upkeepDue=v.days+Math.max(1,b.upkeepDays);}
        }
    }
    public static int reserve(Village v,String item){return v.buildings.stream().filter(b->b.complete && b.owner==null).mapToInt(b->b.upkeep.getOrDefault(item,0)).sum();}
}
