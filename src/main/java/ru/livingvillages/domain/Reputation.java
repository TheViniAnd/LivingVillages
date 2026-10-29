package ru.livingvillages.domain;

import java.util.*;

/** Bounded rewards by day, action and kind of help. Ordinary trade cannot reach leadership. */
public final class Reputation {
    private Reputation(){}
    public static void lose(Village.Relation r,int amount){int before=r.trust;r.add(-amount);int remaining=before-r.trust;for(String key:r.contributionTrust.keySet().stream().sorted(Comparator.comparingInt((String k)->r.contributionTrust.get(k)).reversed()).toList()){int n=Math.min(remaining,r.contributionTrust.get(key));r.contributionTrust.put(key,r.contributionTrust.get(key)-n);remaining-=n;if(remaining==0)break;}}
    public static String source(String key){if(key.equals("donation") || key.equals("construction") || key.equals("mission_BUILD"))return "CONSTRUCTION";if(key.equals("restoration") || key.equals("mission_REPAIR"))return "RESTORATION";if(key.equals("defense") || key.equals("mission_DEFENSE"))return "DEFENSE";if(key.equals("delivery"))return "DELIVERY";if(key.equals("husbandry"))return "HUSBANDRY";return "HELP";}
    public static int award(Village v,UUID player,Rules rules,String repeated,int requested){return award(v,player,rules,source(repeated),repeated,requested);}
    public static int award(Village v,UUID player,Rules rules,String source,String repeated,int requested){
        Social.contact(v,player);var r=v.relation(player);if(r.tradeDay!=v.days){r.tradeDay=v.days;r.tradeToday=0;r.tradeTrustToday=0;r.repeatedToday.clear();}if(requested<=0)return 0;
        String key=source+":"+repeated;int repeats=r.repeatedToday.getOrDefault(key,0),wanted=Math.min(requested,rules.trustAction.getOrDefault(source,4));
        int gain=source.equals("TRADE")?wanted/(1+repeats):Math.max(1,wanted/(1+repeats));
        gain=Math.min(gain,Math.max(0,rules.dailyTradeTrust-r.tradeToday));gain=Math.min(gain,Math.max(0,rules.trustCaps.getOrDefault(source,350)-r.contributionTrust.getOrDefault(source,0)));
        if(source.equals("TRADE")){gain=Math.min(gain,Math.max(0,rules.tradeTrustCeiling-r.trust));gain=Math.min(gain,Math.max(0,rules.tradeTrustPerDay-r.tradeTrustToday));}
        if(gain>0){r.add(gain);r.tradeToday+=gain;if(source.equals("TRADE"))r.tradeTrustToday+=gain;r.repeatedToday.merge(key,1,Math::addExact);r.contributionTrust.merge(source,gain,Math::addExact);}return gain;
    }
}
