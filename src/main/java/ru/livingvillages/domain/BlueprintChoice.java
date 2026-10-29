package ru.livingvillages.domain;

import java.util.Set;

/** The Free catalogue supplies its own complete blueprints. */
public final class BlueprintChoice {
    private BlueprintChoice(){}
    public static String choose(Rules rules,String culture,String requested,Set<String> available){
        if(!rules.edition.building(requested)||!available.contains(requested))throw new IllegalArgumentException("Blueprint is not included in Free: "+requested);
        return requested;
    }
}
