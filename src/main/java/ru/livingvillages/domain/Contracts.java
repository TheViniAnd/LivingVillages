package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

import java.util.*;

/** Requests reserve a real budget and demand. Rewards are saved claims, not invented coins. */
public final class Contracts {
    private Contracts(){}
    public static Village.SupplyTask issue(Village v,UUID player,String item,int amount,Economy economy){
        if(Missions.active(v,player))throw new IllegalArgumentException(Messages.text("Сначала завершите или отмените текущее поручение"));
        if(economy.needed(v,item)<amount)throw new IllegalArgumentException(Messages.text("Такая поставка уже не нужна"));
        long reward=economy.quote(v,player,item,amount,false).total();
        if(v.treasury<reward)throw new IllegalArgumentException(Messages.text("Нет бюджета на поручение"));
        var t=new Village.SupplyTask();t.player=player;t.item=item;t.amount=amount;t.reward=reward;t.expiresDay=v.days+economy.rules().contractDays;
        v.treasury-=reward;v.tasks.add(t);return t;
    }
    public static void deliver(Village v,Village.SupplyTask task,int count,Economy economy){
        if(!task.state.equals("ACTIVE") || count<=0 || count>task.amount-task.delivered)throw new IllegalArgumentException(Messages.text("Поручение недоступно"));
        // A budgeted accepted contract is a binding purchase, even if autonomous supply catches up.
        v.stock.add(task.item,count,v.capacity);task.delivered+=count;
        if(economy.rules().foods.containsKey(task.item) && v.nutrition<1)v.relation(task.player).deeds.add("food");
        long earned=task.reward*task.delivered/task.amount;
        v.payouts.merge(task.player,earned-task.paid,Math::addExact);task.paid=earned;
        if(task.delivered==task.amount){task.state="COMPLETE";v.relation(task.player).deeds.add("contract");Reputation.award(v,task.player,economy.rules(),"DELIVERY",task.item,economy.rules().contractTrust);}
    }
    public static void cancel(Village v,Village.SupplyTask task){
        if(!task.state.equals("ACTIVE"))throw new IllegalArgumentException(Messages.text("Поручение уже закрыто"));
        v.treasury=Money.add(v.treasury,task.reward-task.paid);task.state="CANCELLED";
    }
    public static void expire(Village v){for(var t:v.tasks)if(t.state.equals("ACTIVE") && t.expiresDay<=v.days)cancel(v,t);}
}
