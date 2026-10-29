package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

import java.util.*;

/** A rolling, persisted agenda. Completion is measured from real stock and completed buildings. */
public final class Council {
    private Council(){}
    public static void review(Village v,Rules r){
        if(!r.councilEnabled)return;var plan=v.council;
        boolean urgent=v.underAttack||v.nutrition<.999||r.foodUnits(v)<r.dailyFood(v)*3;
        String cause=v.abandoned?Messages.text("Поселение заброшено"):v.underAttack?Messages.text("Укрытие и безопасность"):urgent?Messages.text("Восстановить питание"):Messages.text("Планомерное развитие");
        String state=v.living()+":"+v.beds()+":"+v.stock.items.hashCode()+":"+v.projects.stream().map(p->p.id+"/"+p.state).toList();
        if(plan.reviewedDay==v.days && cause.equals(plan.reason)&&state.equals(plan.lastState))return;plan.lastState=state;
        if(plan.startedDay<0 || v.days-plan.startedDay>=r.planDays){plan.startedDay=v.days;plan.revision++;}
        plan.reviewedDay=v.days;plan.reason=cause;
        for(var old:plan.goals)if(done(v,r,old)&&!old.complete){old.complete=true;plan.completed.add(Messages.text("День ")+v.days+": "+old.key);}
        while(plan.completed.size()>12)plan.completed.removeFirst();
        var goals=new ArrayList<Village.Goal>();
        add(v,goals,"food",null,Math.toIntExact(r.dailyFood(v)*r.foodTargetDays),Math.toIntExact(Math.min(Integer.MAX_VALUE,r.foodUnits(v))),urgent?Messages.text("Запас еды обязателен до новых жителей"):Messages.text("Пища для жителей и будущих работ"),plan.goals);
        int shortage=SelfSupply.needs(v,r).values().stream().mapToInt(Integer::intValue).sum();
        add(v,goals,"tools",null,1,shortage==0?1:0,shortage==0?Messages.text("Инструменты и семенной запас готовы"):Messages.text("Нужны запасные инструменты, семена и топливо"),plan.goals);
        String next=choice(v,r);
        var active=v.projects.stream().filter(p->p.owner==null&&p.active()).sorted(Comparator.comparingInt(p->p.essential?0:1)).findFirst().orElse(null);
        if(active!=null){var b=active.building(v);add(v,goals,"build:"+b.id,active.kind,1,b.complete?1:0,active.reason,plan.goals);}
        if(next!=null && (active==null||!active.kind.startsWith(next)))add(v,goals,"build:"+next,next,completed(v,next)+1,completed(v,next),Messages.text("Следующий объект; участок ещё не зарезервирован"),plan.goals);
        if(v.beds()<r.maxPopulation && v.beds()<=v.living()+2 && (next==null||!next.equals("family_home")))add(v,goals,"housing","family_home",v.living()+4,v.beds(),Messages.text("Жильё для следующей семьи"),plan.goals);
        if(!Autonomy.ready(v,"forge") && !"forge".equals(next))add(v,goals,"industry","forge",1,0,Messages.text("После дерева, камня, топлива и подтверждённого железа"),plan.goals);
        for(var old:plan.goals)if(old.kind!=null&&!old.complete&&!done(v,r,old)&&goals.stream().noneMatch(g->g.kind!=null&&g.kind.equals(old.kind))&&goals.size()<12){old.reason=Messages.startsWith(old.reason,"Цель сохранена:")?old.reason:Messages.text("Цель сохранена: ")+old.reason;goals.add(old);}
        if(v.plannedBuilding!=null&&goals.stream().noneMatch(g->v.plannedBuilding.equals(g.kind)))add(v,goals,"site:"+v.plannedBuilding,v.plannedBuilding,1,0,v.planningReason==null?Messages.text("Поиск проверенного участка"):v.planningReason,plan.goals);
        plan.goals=goals;
    }
    public static String nextBuilding(Village v,Rules r){if(!r.councilEnabled)return Autonomy.next(v,r);review(v,r);return choice(v,r);}
    private static String choice(Village v,Rules r){
        String farm=FoodPlan.next(v,r);if(r.foodPlanning)return farm!=null?farm:Autonomy.next(v,r);
        boolean gardenPending=v.projects.stream().anyMatch(p->p.owner==null&&p.active()&&p.kind.startsWith("garden"));
        boolean foodVacancy=v.buildings.stream().anyMatch(b->Jobs.usable(b)&&r.recipes.containsKey(b.recipe)&&r.recipes.get(b.recipe).output().keySet().stream().anyMatch(r.foods::containsKey)&&v.residents.stream().noneMatch(w->w.alive&&v.adult(w)&&b.id.equals(w.workplace)));
        if(!gardenPending&&!foodVacancy&&foodPerDay(v,r)<r.dailyFood(v)*1.2&&Autonomy.ready(v,"tree_garden")&&(Autonomy.ready(v,"stone_pit")||Autonomy.ready(v,"quarry_house")))return "garden";
        return Autonomy.next(v,r);
    }
    private static int completed(Village v,String kind){return (int)v.buildings.stream().filter(b->b.owner==null&&b.complete&&!b.damaged&&b.kind.startsWith(kind)).count();}
    private static boolean done(Village v,Rules r,Village.Goal g){return switch(g.key){case "food"->r.foodUnits(v)>=g.target;case "tools"->SelfSupply.needs(v,r).isEmpty();case "housing"->v.beds()>=g.target;case "industry"->Autonomy.ready(v,"forge");default->{var project=v.projects.stream().filter(p->g.key.equals("build:"+p.building)).findFirst().orElse(null);yield project!=null?project.state.equals("COMPLETE"):g.kind!=null&&completed(v,g.kind)>=g.target;}};}
    private static void add(Village v,List<Village.Goal> out,String key,String kind,int target,int current,String reason,List<Village.Goal> old){var goal=new Village.Goal();goal.key=key;goal.kind=kind;goal.target=target;goal.current=current;goal.reason=Objects.toString(reason,"");goal.startedDay=old.stream().filter(g->g.key.equals(key)).mapToLong(g->g.startedDay).findFirst().orElse(v.days);goal.complete=current>=target;out.add(goal);}
    public static double foodPerDay(Village v,Rules r){if(r.foodPlanning)return FoodPlan.capacity(v,r,false);double n=0;var used=new HashSet<UUID>();for(var b:v.buildings){var recipe=r.recipes.get(b.recipe);if(!Jobs.usable(b)||!b.resourceConfirmed||b.resourceLeft==0||recipe==null||!recipe.input().isEmpty())continue;var worker=v.residents.stream().filter(p->p.alive&&v.adult(p)&&b.id.equals(p.workplace)&&Autonomy.producer(v,p,recipe)&&!used.contains(p.id)).findFirst().orElse(null);if(worker==null)continue;used.add(worker.id);n+=recipe.output().entrySet().stream().mapToLong(e->(long)e.getValue()*r.foods.getOrDefault(e.getKey(),0)).sum()*b.level*r.productionRate(v,b)/recipe.renewDays();}return n;}
}
