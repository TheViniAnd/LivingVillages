package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

/** A failed site search is a persisted goal, never a cancelled construction project. */
public final class BuildAgenda {
    private BuildAgenda(){}
    public static String choose(Village v,String proposed){
        if(v.plannedBuilding==null)v.plannedBuilding=proposed;
        if(proposed!=null&&Autonomy.essential(proposed)&&!proposed.equals(v.plannedBuilding))return proposed;
        return v.plannedBuilding;
    }
    public static void request(Village v,String kind){if(v.plannedBuilding!=null&&!kind.equals(v.plannedBuilding))throw new IllegalArgumentException(Messages.text("Уже ищется участок для ")+v.plannedBuilding+Messages.text("; текущая цель сохранена"));v.plannedBuilding=kind;}
    public static void reserved(Village v,String kind){if(kind.equals(v.plannedBuilding)){v.plannedBuilding=null;v.planningReason=null;}}
    public static int priority(String kind){String base=kind.replaceFirst("_u[0-9]+$","");return base.startsWith("tree_garden")?100:base.equals("starter_quarry")||base.equals("stone_pit")?95:base.equals("soil_pit")?94:base.equals("kiln_house")?90:base.equals("family_home")||base.equals("residence")?45:Autonomy.essential(base)?80:0;}
}
