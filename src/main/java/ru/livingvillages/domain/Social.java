package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

import java.util.*;

/** Personal NPC familiarity is separate from village trust; neither rewards repeated clicks. */
public final class Social {
    private Social(){}
    public record Reply(int friendship,int trust,String situation){}
    public static void contact(Village v,UUID player){var r=v.relation(player);r.lastContactDay=v.days;r.lastDecayDay=v.days;}
    public static void contact(Village v,UUID player,UUID person){contact(v,player);var r=v.relation(player);r.friendContactDays.put(person,v.days);r.friendDecayDays.put(person,v.days);}
    public static void day(Village v,Rules rules){for(var r:v.relations.values()){
        if(r.lastContactDay<0){r.lastContactDay=v.days;r.lastDecayDay=v.days;continue;}
        long elapsed=Math.max(0,v.days-Math.max(r.lastDecayDay,r.lastContactDay+rules.forgettingGraceDays));
        if(elapsed>0){int loss=(int)Math.min(1000,elapsed*rules.forgettingPerDay);if(r.trust>0)Reputation.lose(r,Math.min(r.trust,loss));}
        for(UUID id:new ArrayList<>(r.friendship.keySet())){long contact=r.friendContactDays.getOrDefault(id,r.conversationDays.getOrDefault(id,r.lastContactDay)),last=r.friendDecayDays.getOrDefault(id,contact),missing=Math.max(0,v.days-Math.max(last,contact+rules.forgettingGraceDays));if(missing>0)r.friendship.put(id,Math.max(0,r.friendship.get(id)-(int)Math.min(100,missing)));r.friendDecayDays.put(id,v.days);}
        r.lastDecayDay=v.days;
    }}
    public static void harm(Village v,UUID player,UUID resident,Rules rules,boolean kill){contact(v,player);var r=v.relation(player);r.aggressor=true;Reputation.lose(r,kill?rules.killPenalty:rules.hitPenalty);r.friendship.computeIfPresent(resident,(id,n)->Math.max(0,n-(kill?100:15)));}
    public static Reply talk(Village v,UUID player,UUID person,String topic,Rules rules){
        var resident=v.resident(person);if(resident==null || !resident.alive)throw new IllegalArgumentException(Messages.text("Этот житель недоступен"));
        if(!Set.of("work","village","friend").contains(topic))throw new IllegalArgumentException(Messages.text("Неизвестная тема"));
        var r=v.relation(player);contact(v,player,person);int awarded=0;
        if(r.conversationDays.getOrDefault(person,-1L)!=v.days){
            r.conversationDays.put(person,v.days);r.friendship.merge(person,2,(a,b)->Math.min(100,a+b));
            if(r.socialDay!=v.days){r.socialDay=v.days;r.socialToday=0;}
            if(r.socialToday<rules.socialDailyTrust){awarded=Reputation.award(v,player,rules,"SOCIAL",person.toString(),1);r.socialToday+=awarded;}
        }
        String situation=v.underAttack?"danger":v.nutrition<1?"hunger":r.trust<0?"hostile":topic.equals("work")?"work":topic.equals("village")?"village":r.friendship.getOrDefault(person,0)>=30?"close":"friend";
        return new Reply(r.friendship.getOrDefault(person,0),awarded,situation);
    }
}
