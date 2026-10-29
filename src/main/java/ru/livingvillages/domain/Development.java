package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

public final class Development {
    private Development(){}
    public static String name(int stage){return switch(stage){case 0->Messages.text("Хутор");case 1->Messages.text("Деревня");case 2->Messages.text("Развитая деревня");default->Messages.text("Малое городское поселение");};}
    public static void update(Village v,Rules r){
        long jobs=v.buildings.stream().filter(b->b.complete && !b.damaged && !b.upkeepMissing && b.recipe!=null && b.resourceConfirmed && b.resourceLeft!=0 && v.residents.stream().anyMatch(worker->worker.alive && v.adult(worker) && b.id.equals(worker.workplace))).map(b->b.recipe).distinct().count();
        int stage=0;
        if(r.foodUnits(v)>=r.dailyFood(v)*r.warningDays && v.nutrition>=.999 && v.beds()>=v.living())
            for(int i=1;i<r.stagePopulation.length;i++)if(v.living()>=r.stagePopulation[i] && jobs>=i+1)stage=i;
        Levels.update(v,r);v.stage=stage;v.highestStage=Math.max(v.highestStage,stage);
    }
    public static int availableForBuilding(Village v,Rules rules,String item,boolean essential){
        int keep=essential?Maintenance.reserve(v,item):SelfSupply.reserve(v,rules,item);
        if(!essential)keep=Math.max(keep,v.laws.strategic.getOrDefault(item,0));
        int result=Math.max(0,v.stock.get(item)-keep);
        Integer food=rules.foods.get(item);
        if(food!=null)result=(int)Math.min(result,Math.max(0,(rules.foodUnits(v)-rules.dailyFood(v)*rules.warningDays)/food));
        return result;
    }
}
