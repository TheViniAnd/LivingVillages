package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

import java.util.*;

public final class Economy {
    public record Quote(String item, int count, boolean playerBuys, long total, String reason) { }
    private final Rules rules;
    public Economy(Rules rules) { this.rules = rules; TradeCatalog.extend(rules); }
    public Rules rules(){return rules;}
    public int needed(Village v, String item) {
        int held=v.tasks.stream().filter(t->t.state.equals("ACTIVE") && t.item.equals(item)).mapToInt(t->t.amount-t.delivered).sum();
        return Math.max(0,rawNeeded(v,item)-held);
    }
    public int rawNeeded(Village v,String item){
        return rules.prices.containsKey(item)?Needs.current(v,rules,item).count():0;
    }
    public Map<String,Needs.Need> catalog(Village v){var result=new LinkedHashMap<String,Needs.Need>();var held=new HashMap<String,Integer>();for(var t:v.tasks)if(t.state.equals("ACTIVE"))held.merge(t.item,t.amount-t.delivered,Math::addExact);Needs.catalog(v,rules).forEach((item,need)->result.put(item,new Needs.Need(Math.max(0,need.count()-held.getOrDefault(item,0)),need.reason())));return Collections.unmodifiableMap(result);}
    public int exportable(Village v, String item) {
        int count = Math.max(0,v.stock.get(item)-v.laws.strategic.getOrDefault(item,0));
        if(v.laws.exportBans.contains(item))return 0;
        count=Math.max(0,count-SelfSupply.reserve(v,rules,item));
        Integer units = rules.foods.get(item);
        if(units != null) {
            if(!v.laws.exportFood) return 0;
            count = Math.min(count, (int)Math.max(0,(rules.foodUnits(v)-rules.dailyFood(v)*Math.max(v.laws.foodDays,rules.warningDays))/units));
        }
        return count;
    }
    public Quote quote(Village v, UUID player, String item, int count, boolean buy) {
        return displayQuote(v,player,item,count,buy,buy?0:needed(v,item));
    }
    /** Menu estimate only. trade() always obtains a fresh quote and checks live stock/treasury. */
    public Quote displayQuote(Village v,UUID player,String item,int count,boolean buy,int currentNeed){
        Rules.Price p = rules.prices.get(item);
        if(p == null || count <= 0 || count > 4096) throw new IllegalArgumentException(Messages.text("Неверная партия товара"));
        boolean exactRemainder=!buy&&count==currentNeed;
        boolean finishing=exactRemainder&&count<p.batch()&&TradeCatalog.constructionMissing(v,item)>=count;
        if(count%p.batch()!=0&&!exactRemainder)throw new IllegalArgumentException(Messages.text("Обычная партия: ")+p.batch()+Messages.text(". Точный остаток текущей потребности принимается отдельно."));
        boolean food = rules.foods.containsKey(item);
        double relative = v.stock.get(item)/(double)Math.max(1,p.target());
        int factor = relative < .25 ? 150 : relative > 1.5 ? 70 : 100;
        boolean local = v.buildings.stream().filter(b -> b.complete && !b.damaged && !b.upkeepMissing && b.resourceConfirmed && v.residents.stream().anyMatch(r->r.alive && v.adult(r) && b.id.equals(r.workplace))).anyMatch(b -> {
            var recipe=rules.recipes.get(b.recipe); return recipe!=null && recipe.output().containsKey(item);
        });
        if(local) factor -= 15;
        String reason=(local?Messages.text("местный товар; "):"")+(relative < .25 ? Messages.text("дефицит") : relative > 1.5 ? Messages.text("избыток") : Messages.text("обычный запас"));
        if(food && v.nutrition<1 && rules.foodUnits(v)<rules.dailyFood(v)*rules.warningDays){factor+=20;reason+=Messages.text("; срочно нужна еда");}
        if(count/p.batch()>=4){factor+=buy && relative<1?5:-5;reason+=Messages.text("; объём партии");}
        int trust = v.relation(player).trust;
        if(buy && trust>=300) { factor-=Math.min(10,trust/100); reason+=Messages.text("; скидка за доверие"); }
        factor = Math.max(50,Math.min(200,factor));
        long hundredths = (long)p.base()*factor;
        if(!buy){long bid=hundredths*60/100,cap=safeBidCap(item);if(bid>cap)reason+=Messages.text("; предел обратной продажи");hundredths=Math.min(bid,cap);}
        long total = buy ? (hundredths*count+99)/100 : hundredths*count/100;
        if(total==0&&finishing){total=1;reason+=Messages.text("; точная завершающая поставка в общую стройку");}
        if(total<=0) throw new IllegalArgumentException(Messages.text("Партия слишком мала для оплаты"));
        return new Quote(item,count,buy,Money.checked(total),reason);
    }
    public long safeBidCap(String item){
        Rules.Price price=rules.prices.get(item);if(price==null)return 0;
        long raw=switch(item){
            case "IRON_SWORD"->minAsk("IRON_INGOT")*2+minLogAsk()/8;
            case "BOW"->minAsk("STRING")*3+minLogAsk()*3/8;
            case "ARROW"->(minAsk("FLINT")+minAsk("FEATHER")+minLogAsk()/8)/4;
            case "WHITE_BED"->minAsk("WHITE_WOOL")*3+minLogAsk()*3/4;
            case "BUCKET"->minAsk("IRON_INGOT")*3;
            case "WATER_BUCKET"->minAsk("BUCKET");
            case "BREAD"->minAsk("WHEAT")*3;
            case "GLASS"->minAsk("SAND")+minAsk("COAL")/8;
            case "BRICK"->minAsk("CLAY_BALL")+minAsk("COAL")/8;
            case "CHARCOAL"->minAsk("OAK_LOG")+minAsk("COAL")/8;
            case "OAK_BOAT","SPRUCE_BOAT","BIRCH_BOAT","ACACIA_BOAT","MANGROVE_BOAT"->minLogAsk()*5/4;
            case "COOKED_BEEF"->minAsk("BEEF")+minAsk("COAL")/8;
            case "COOKED_COD"->minAsk("COD")+minAsk("COAL")/8;
            case "GLASS_BOTTLE"->minAsk("GLASS");
            case "PAPER"->minAsk("SUGAR_CANE");
            case "SHEARS"->minAsk("IRON_INGOT")*2;
            case "IRON_INGOT"->minAsk("RAW_IRON")+minAsk("COAL")/8;
            case "STONE_HOE"->minAsk("COBBLESTONE")*2+minLogAsk()/4;
            case "STONE_AXE","STONE_PICKAXE"->minAsk("COBBLESTONE")*3+minLogAsk()/4;
            default->Long.MAX_VALUE/100;
        };
        long cap=Math.min(price.bidCapHundredths(),Math.min(raw*80/100,(long)price.base()*40));
        for(var craft:rules.crafts.values())if(craft.output().containsKey(item)){
            long cost=0;for(var e:craft.input().entrySet())cost=Math.addExact(cost,minAsk(e.getKey())*e.getValue());for(var e:craft.fuel().entrySet())cost=Math.addExact(cost,minAsk(e.getKey())*e.getValue()/craft.batch());
            cap=Math.min(cap,cost/craft.output().get(item)*80/100);
        }return cap;
    }
    private long minAsk(String item){var price=rules.prices.get(item);return price==null?1_000_000_000L:(long)price.base()*50;}
    private long minLogAsk(){return rules.prices.keySet().stream().filter(k->k.endsWith("_LOG")).mapToLong(this::minAsk).min().orElse(1_000_000_000L);}
    /** Called on a private copy of the aggregate, under the village lock. */
    public void trade(Village v, UUID player, Quote q) {
        if(v.abandoned || v.residents.stream().noneMatch(r->r.alive && v.adult(r) && (r.role==Village.Role.MERCHANT || r.role==Village.Role.ELDER)))throw new IllegalArgumentException(Messages.text("Нет живого торговца или старосты для сделки"));
        Social.contact(v,player);
        if(!q.playerBuys&&needed(v,q.item)<q.count)throw new IllegalArgumentException(Messages.text("Эта поставка уже не нужна; предложение изменилось"));
        Quote current=quote(v,player,q.item,q.count,q.playerBuys);
        if(current.total!=q.total) throw new IllegalArgumentException(Messages.text("Цена изменилась: откройте предложение заново"));
        if(q.playerBuys) {
            if(exportable(v,q.item)<q.count) throw new IllegalArgumentException(Messages.text("Товар отсутствует или защищён резервом"));
            v.treasury=Money.add(v.treasury,q.total);
            v.stock.take(q.item,q.count);
        } else {
            if(needed(v,q.item)<q.count) throw new IllegalArgumentException(Messages.text("Эта поставка уже не нужна"));
            if(v.treasury<q.total) throw new IllegalArgumentException(Messages.text("В казне недостаточно монет"));
            v.stock.add(q.item,q.count,v.capacity);
            v.treasury-=q.total;
            var rel=v.relation(player);
            int gain=Math.max(1,q.count);
            rel.deeds.add("trade");
            if(rules.foods.containsKey(q.item) && v.nutrition<1) { rel.deeds.add("food"); gain+=Math.min(rules.foodHelpTrust,q.count); }

        }
        v.relation(player).deeds.add("trade");Reputation.award(v,player,rules,"TRADE",q.item,1);
    }
}
