package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

import java.util.UUID;

/** Earned village actions do not grant control over other players' property or withdrawals. */
public final class TrustAccess {
    private TrustAccess(){}
    public static boolean friend(Village v,UUID actor,Rules r){return !v.abandoned&&v.relation(actor).trust>=r.friendTrust;}
    public static boolean tasks(Village v,UUID actor,Rules r){return !v.abandoned&&v.relation(actor).trust>=r.buildTrust;}
    
    public static void requireFriend(Village v,UUID actor,Rules r){if(!friend(v,actor,r))throw new IllegalArgumentException(Messages.text("Для подарков деревне нужно ")+r.friendTrust+Messages.text(" доверия — Друг деревни. Знакомьтесь с жителями и торгуйте."));}
    public static void requireTasks(Village v,UUID actor,Rules r){if(!tasks(v,actor,r))throw new IllegalArgumentException(Messages.text("Для строительных задач нужно ")+r.buildTrust+Messages.text(" доверия — Союзник деревни."));}
    
    public static String rank(Village v,UUID actor,Rules r){int n=v.relation(actor).trust;if(n>=500)return Messages.text("Почётный житель");if(n>=r.buildTrust)return Messages.text("Союзник деревни");if(n>=r.friendTrust)return Messages.text("Друг деревни");return n<0?Messages.text("Недруг деревни"):Messages.text("Незнакомец");}
}
