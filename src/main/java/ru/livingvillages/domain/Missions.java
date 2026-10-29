package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

import java.util.*;

/** Bounded real objectives with an up-front treasury reservation and durable player payout. */
public final class Missions {
    private Missions(){}
    public static boolean active(Village v,UUID player){return v.tasks.stream().anyMatch(t->t.state.equals("ACTIVE") && player.equals(t.player)) || v.missions.stream().anyMatch(m->m.state.equals("ACTIVE") && player.equals(m.player));}
    public static void requireAvailable(Collection<Village> villages,UUID player){if(villages.stream().anyMatch(v->active(v,player)))throw new IllegalArgumentException(Messages.text("У вас уже есть поручение. Завершите или отмените его, затем возьмите следующее."));}
    public static int contributionAllowance(Village v,UUID player,Village.Project p,Rules rules){
        if(TrustAccess.friend(v,player,rules))return Integer.MAX_VALUE;
        return v.missions.stream().filter(m->m.player.equals(player)&&m.state.equals("ACTIVE")&&v.days<m.expiresDay&&((m.kind.equals("BUILD")&&p.id.equals(m.target))||(m.kind.equals("REPAIR")&&p.building.equals(m.target)))).mapToInt(m->m.kind.equals("BUILD")?Math.max(0,m.goal-Math.max(0,p.deliveredBy.getOrDefault(player,0)-m.baseline)):Integer.MAX_VALUE).max().orElse(0);
    }
    public static boolean repairAllowed(Village v,UUID player,UUID building){return v.missions.stream().anyMatch(m->m.player.equals(player)&&m.state.equals("ACTIVE")&&m.kind.equals("REPAIR")&&building.equals(m.target)&&v.days<m.expiresDay);}
    public static Village.Mission issue(Village v,UUID player,String kind,UUID target,Rules rules,boolean threatPresent){
        if(v.abandoned || active(v,player))throw new IllegalArgumentException(Messages.text("Сначала завершите или отмените текущее поручение"));
        var m=new Village.Mission();m.player=player;m.kind=kind;m.target=target;m.expiresDay=v.days+rules.contractDays;
        switch(kind){
            case "BUILD"->{var p=v.projects.stream().filter(q->q.id.equals(target)).findFirst().orElseThrow(()->new IllegalArgumentException(Messages.text("Проект не найден")));if(v.missions.stream().anyMatch(old->old.player.equals(player)&&old.kind.equals("BUILD")&&target.equals(old.target)&&old.state.equals("COMPLETE")))throw new IllegalArgumentException(Messages.text("Вы уже выполнили помощь этому проекту"));if(p.owner!=null || p.starter || !p.state.equals("COLLECTING") || p.funded())throw new IllegalArgumentException(Messages.text("Помощь этому проекту уже не требуется"));int missing=p.required.entrySet().stream().mapToInt(e->Math.max(0,e.getValue()-p.escrow.getOrDefault(e.getKey(),0))).sum();if(missing==0)throw new IllegalArgumentException(Messages.text("Материалы уже собраны"));m.goal=Math.min(missing,Math.min(16,Math.max(4,missing/16)));m.baseline=p.deliveredBy.getOrDefault(player,0);m.reward=rules.buildMissionReward;}
            case "REPAIR"->{var b=v.buildings.stream().filter(q->q.id.equals(target)).findFirst().orElseThrow(()->new IllegalArgumentException(Messages.text("Здание не найдено")));if(b.owner!=null || !b.damaged || v.projects.stream().anyMatch(p->p.building.equals(target) && p.active()))throw new IllegalArgumentException(Messages.text("Восстановление этого объекта уже выполняется или не нужно"));m.goal=1;m.baseline=v.projects.size();m.reward=rules.repairMissionReward;}
            case "DEFENSE"->{if(!threatPresent)throw new IllegalArgumentException(Messages.text("Сейчас нет подтверждённой угрозы деревне"));m.goal=Math.min(3,Math.max(1,v.living()/8));m.reward=rules.defenseMissionReward;}
            default->throw new IllegalArgumentException(Messages.text("Неизвестное поручение"));
        }
        if(v.treasury<m.reward)throw new IllegalArgumentException(Messages.text("В казне недостаточно денег для награды"));v.treasury-=m.reward;v.missions.add(m);return m;
    }
    public static void check(Village v,Rules rules){
        for(var m:v.missions)if(m.state.equals("ACTIVE")){
            if(v.abandoned || v.days>=m.expiresDay){cancel(v,m);continue;}
            if(m.kind.equals("BUILD")){
                var p=v.projects.stream().filter(q->q.id.equals(m.target)).findFirst().orElse(null);
                if(p==null || p.state.equals("CANCELLED")){cancel(v,m);continue;}
                m.progress=Math.min(m.goal,Math.max(0,p.deliveredBy.getOrDefault(m.player,0)-m.baseline));if(m.progress>=m.goal)complete(v,m,rules);
            }else if(m.kind.equals("REPAIR")){
                var b=v.buildings.stream().filter(q->q.id.equals(m.target)).findFirst().orElse(null);if(b==null){cancel(v,m);continue;}
                if(b.complete && !b.damaged){boolean helped=v.projects.stream().skip(m.baseline).anyMatch(p->p.building.equals(b.id) && Messages.startsWith(p.kind,"Ремонт") && p.state.equals("COMPLETE") && p.contributors.contains(m.player));if(helped){m.progress=1;complete(v,m,rules);}else cancel(v,m);}
            }
        }
    }
    public static void defeat(Village v,UUID player,UUID enemy,Rules rules,boolean tracked){
        if(!tracked)return;for(var m:v.missions)if(m.state.equals("ACTIVE") && m.kind.equals("DEFENSE") && player.equals(m.player) && m.enemies.add(enemy)){m.progress++;if(m.progress>=m.goal)complete(v,m,rules);}
    }
    public static void cancel(Village v,Village.Mission m){if(!m.state.equals("ACTIVE"))throw new IllegalArgumentException(Messages.text("Поручение уже закрыто"));v.treasury=Money.add(v.treasury,m.reward);m.state="CANCELLED";}
    private static void complete(Village v,Village.Mission m,Rules rules){long payout=Math.addExact(v.payouts.getOrDefault(m.player,0L),m.reward);v.payouts.put(m.player,payout);m.state="COMPLETE";var relation=v.relation(m.player);relation.deeds.add("contract");relation.deeds.add(m.kind.equals("DEFENSE")?"defense":m.kind.equals("REPAIR")?"restoration":"construction");Reputation.award(v,m.player,rules,"mission_"+m.kind,rules.contractTrust);}
    public static void abandon(Village v){for(var t:v.tasks)if(t.state.equals("ACTIVE"))Contracts.cancel(v,t);for(var m:v.missions)if(m.state.equals("ACTIVE"))cancel(v,m);}
}
