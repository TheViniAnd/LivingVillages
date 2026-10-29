package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;
import java.util.*;

/** This source project contains only Free. These limits are not a DRM boundary. */
public enum Edition {
    FREE;
    public String title(){return "LivingVillages";}
    public int levelLimit(){return 3;}
    public int populationLimit(){return 12;}
    public int radiusLimit(){return 48;}
    public boolean culture(String id){return "polevichi".equals(id);}
    public static final Set<String> BUILDINGS=Set.of("hall","warehouse","garden","road","street_light","tree_garden","tree_garden_oak","camp_workbench","stone_pit","starter_quarry","sand_pit","soil_pit","iron_pit","kiln_house","forge","water_intake","family_home","residence","settler_lodge","relief_garden");
    public boolean building(String kind){return BUILDINGS.contains(kind);}
    public void apply(Rules r){r.edition=this;r.maxPopulation=Math.min(r.maxPopulation,populationLimit());}
    public String incompatible(Village v){
        if(!culture(v.culture)||v.level>levelLimit()||v.living()>populationLimit()||v.radius>radiusLimit())return Messages.text("культура или масштаб деревни");
        if(v.buildings.stream().anyMatch(b->b.owner!=null||b.level>1||!building(b.kind))||v.projects.stream().anyMatch(p->p.owner!=null))return Messages.text("личное имущество или расширенные здания");
        return null;
    }
}
