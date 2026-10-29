package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

import java.util.*;
import java.util.function.Function;

/** Public village facts; signs never hold a second stock or treasury. */
public final class TownInfo {
    private TownInfo(){}
    public record Card(String id,List<String> lines,boolean warning){
        public Card{lines=lines.stream().map(TownInfo::shortLine).toList();if(lines.size()!=4)throw new IllegalArgumentException(Messages.text("На табличке четыре строки"));}
    }
    public static List<Card> cards(Village v,Rules rules,Function<String,String> itemName,Function<String,String> buildingName){
        return cards(v,rules,itemName,buildingName,0);
    }
    public static List<Card> cards(Village v,Rules rules,Function<String,String> itemName,Function<String,String> buildingName,int materialPage){
        long food=rules.foodUnits(v),daily=rules.dailyFood(v);String days=daily==0?Messages.text("Нет жителей"):String.format(Locale.ROOT,Messages.text("Запас: %.1f сут."),Math.floor(food*10.0/daily)/10);
        var money=Money.split(v.treasury);long workers=v.residents.stream().filter(r->r.alive && r.workplace!=null).count();
        Village.Project project=Projects.next(v).orElseGet(()->v.projects.stream().filter(Village.Project::active).findFirst().orElse(null));
        List<String> construction=project==null?List.of(Messages.text("Строительство"),Messages.text("Нет активных"),Messages.text("проектов"),Messages.text("ПКМ: проекты")):List.of(Messages.text("Строительство"),buildingName.apply(project.kind),project.state.equals("COLLECTING")?Messages.text("Сбор материалов"):project.state.equals("PAUSED")?Messages.text("Приостановлено"):project.index*100/Math.max(1,project.blocks.size())+Messages.text("% · этап ")+(project.index<project.blocks.size()?project.blocks.get(project.index).phase:4),project.reason==null || project.reason.isBlank()?Messages.text("ПКМ: подробнее"):project.reason);
        LinkedHashMap<String,Integer> needs=new LinkedHashMap<>();
        if(food<daily*rules.warningDays && rules.foods.containsKey("CARROT"))needs.put("CARROT",Math.toIntExact((daily*rules.warningDays-food+rules.foods.get("CARROT")-1)/rules.foods.get("CARROT")));
        for(var p:v.projects)if(p.owner==null && p.state.equals("COLLECTING"))for(var e:p.required.entrySet()){int missing=e.getValue()-p.escrow.getOrDefault(e.getKey(),0);if(missing>0)needs.merge(e.getKey(),missing,Math::addExact);}
        for(var b:v.buildings)if(b.upkeepMissing)b.upkeep.forEach((k,n)->needs.merge(k,Math.max(0,n-v.stock.get(k)),Math::max));
        if(needs.isEmpty()){Economy economy=new Economy(rules);for(String item:rules.prices.keySet())if(economy.needed(v,item)>0)needs.put(item,economy.needed(v,item));}
        List<String> requests=new ArrayList<>(List.of(Messages.text("Нужно деревне")));needs.entrySet().stream().filter(e->e.getValue()>0).limit(2).forEach(e->requests.add(itemName.apply(e.getKey())+": "+e.getValue()));while(requests.size()<3)requests.add(requests.size()==1?Messages.text("Нужд сейчас нет"):"");requests.add(Messages.text("ПКМ: поручения"));
        List<Card> result=new ArrayList<>(List.of(new Card("people",List.of(Messages.text("Население"),v.abandoned?Messages.text("Поселение пусто"):Messages.text("Жители: ")+v.living()+"/"+rules.maxPopulation,Messages.text("Мест: ")+v.beds(),Messages.text("Работают: ")+workers),v.abandoned || v.beds()<v.living()),
            new Card("food",List.of(Messages.text("Продовольствие"),days,Messages.text("Сытость: ")+(int)(v.nutrition*100)+"%",food<daily*rules.warningDays?Messages.text("Нужна помощь"):Messages.text("ПКМ: склад")),food<daily*rules.warningDays || v.nutrition<1),
            new Card("treasury",List.of(Messages.text("Казна деревни"),Messages.text("Золото: ")+money.get(4096),Messages.text("Серебро: ")+money.get(64),Messages.text("Бронза: ")+money.get(1)),false),
            new Card("construction",construction,project!=null && (project.state.equals("PAUSED") || project.state.equals("COLLECTING"))),
            new Card("needs",requests,food<daily*rules.warningDays),
            new Card("development",List.of(Messages.text("Уровень ")+v.level+"/3",Messages.text("День: ")+v.days,v.paused?Messages.text("Жизнь на паузе"):v.underAttack?Messages.text("Опасность рядом"):rules.councilEnabled?v.council.reason:v.developmentReason,Messages.text("ПКМ: совет")),v.paused||v.underAttack)));
        Village.Project goal=v.projects.stream().filter(p->p.owner==null && p.active()).min(Comparator.<Village.Project>comparingInt(p->p.essential?0:1).thenComparingInt(p->p.state.equals("BUILDING")?0:1)).orElse(null);
        String planned=goal==null && rules.autonomy?Autonomy.next(v,rules):null;
        String name=goal!=null?buildingName.apply(goal.kind):planned!=null?buildingName.apply(planned):Messages.text("Пока нет проекта");
        if(goal!=null){var household=v.buildings.stream().filter(b->b.id.equals(goal.building) && b.family!=null).findFirst().orElse(null);if(household!=null)name=Messages.text("Дом: ")+Housing.familyName(v,household.family);}
        var title=twoLines(name);String reason=v.abandoned?Messages.text("Деревня пуста"):v.paused?Messages.text("Жизнь на паузе"):v.underAttack?Messages.text("Опасность рядом"):goal==null?planned==null?Messages.text("ПКМ: причины"):Messages.text("Выбираем участок"):goal.state.equals("SURVEY")?Messages.text("Разведка залежи"):goal.state.equals("COLLECTING")?Messages.text("Сбор материалов"):goal.state.equals("PAUSED")?Messages.text("Конфликт блоков"):goal.reason!=null && !goal.reason.isBlank()?goal.reason:Messages.text("Строители работают");
        result.add(new Card("goal",List.of(Messages.text("Следующая цель"),title.get(0),title.get(1),reason),goal==null || !reason.equals(Messages.text("Строители работают"))));
        List<Map.Entry<String,Integer>> missing=new ArrayList<>();
        if(goal!=null)for(var e:goal.required.entrySet()){int count=Math.max(0,e.getValue()-goal.escrow.getOrDefault(e.getKey(),0));if(count>0)missing.add(Map.entry(e.getKey(),count));}
        int pages=Math.max(1,(missing.size()+8)/9),page=Math.floorMod(materialPage,pages);
        for(int board=0;board<3;board++){List<String> lines=new ArrayList<>();lines.add(Messages.text("Нужно ")+(page+1)+"/"+pages+" · "+(board+1));for(int i=0;i<3;i++){int index=page*9+board*3+i;lines.add(index<missing.size()?materialLine(itemName.apply(missing.get(index).getKey()),missing.get(index).getValue()):i==0 && board==0?goal==null?Messages.text("Ожидаем план"):Messages.text("Всё собрано"):"");}result.add(new Card("materials"+(board+1),lines,!missing.isEmpty()));}
        return result;
    }
    private static List<String> twoLines(String text){int cut=Math.min(15,text.codePointCount(0,text.length()));int end=text.offsetByCodePoints(0,cut);if(end<text.length()){int space=text.lastIndexOf(' ',end);if(space>4)end=space;}return List.of(text.substring(0,end).strip(),text.substring(end).strip());}
    static String materialLine(String name,int amount){String suffix=": "+amount;int keep=Math.max(1,15-suffix.length());String label=name.codePointCount(0,name.length())<=keep?name:name.substring(0,name.offsetByCodePoints(0,Math.max(1,keep-1)))+"…";return label+suffix;}
    public static String shortLine(String text){if(text==null)return "";return text.codePointCount(0,text.length())<=15?text:text.substring(0,text.offsetByCodePoints(0,14))+"…";}
}
