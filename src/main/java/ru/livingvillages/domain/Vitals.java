package ru.livingvillages.domain;

/** Persisted injuries and age; physical NPC unloads never heal a resident. */
public final class Vitals {
    private Vitals(){}
    public static double maximum(Village v,Village.Resident r,Rules rules){return !v.adult(r)?rules.childHealth:r.role==Village.Role.GUARD?rules.guardHealth:rules.adultHealth;}
    public static void reconcile(Village v,Rules rules){for(var r:v.residents){if(!v.adult(r)){r.role=Village.Role.CHILD;r.workplace=null;r.workplacePinned=false;r.craftWorkplace=null;}else if(r.role==Village.Role.CHILD)r.role=Village.Role.BUILDER;}for(var r:v.residents)if(!r.alive){r.health=0;}else{double max=maximum(v,r,rules);if(r.health<0)r.health=max;else if(r.maxHealth>0 && r.maxHealth!=max)r.health=r.health/r.maxHealth*max;r.maxHealth=max;r.health=Math.max(.01,Math.min(max,r.health));}}
    public static void recover(Village v,Rules rules){reconcile(v,rules);if(v.nutrition>=.999 && !v.underAttack)for(var r:v.residents)if(r.alive)r.health=Math.min(r.maxHealth,r.health+rules.healthRecovery);}
}
