package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

import java.util.*;

/** Personal discovery, independent of trust and territory-management permissions. */
public final class Discoveries {
    private Discoveries(){}
    public static boolean nearby(Village v,UUID world,double x,double z,double distance){return Objects.equals(v.world,world) && Double.isFinite(x) && Double.isFinite(z) && Double.isFinite(distance) && distance>0 && Math.hypot(x-v.x,z-v.z)<=distance;}
    public static boolean record(Village v,UUID player,String name){boolean first=v.discovered.add(player);v.relation(player).playerName=name;if(first && v.discoverer==null)v.discoverer=player;return first;}
    public static String message(Village v,String culture,String world){return Messages.text("Найдена ")+(v.abandoned?Messages.text("заброшенная "):Messages.text("новая "))+Messages.text("деревня «")+v.name+Messages.text("»! Культура: ")+culture+Messages.text(". Координаты: X ")+v.x+", Y "+v.y+", Z "+v.z+Messages.text(". Мир: ")+world+Messages.text(". Открытые деревни: /lv menu.");}
}
