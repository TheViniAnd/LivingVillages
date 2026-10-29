package ru.livingvillages;
import ru.livingvillages.domain.*;
final class TestDemand {static Village.Project add(Village v,String item,int amount){var b=new Village.Building();b.kind="foundation";v.buildings.add(b);var p=new Village.Project();p.building=b.id;p.kind="foundation";p.required.put(item,amount);v.projects.add(p);return p;}}
