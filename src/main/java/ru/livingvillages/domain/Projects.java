package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

import java.util.*;

/** Material/labor ledger shared by world application and cancellation. */
public final class Projects {
    private static final ThreadLocal<ReadScope> VIEW=new ThreadLocal<>();
    private record BuildingKey(UUID id){}
    /** Lives for one synchronous server pass, never in a save or across inventory transactions. */
    public static final class ReadScope implements AutoCloseable {
        private final ReadScope previous;private final IdentityHashMap<Village,Map<Object,List<Village.Block>>> cache=new IdentityHashMap<>();
        public long hits,builds;
        private ReadScope(){previous=VIEW.get();VIEW.set(this);}
        public void close(){if(previous==null)VIEW.remove();else VIEW.set(previous);}
    }
    public static ReadScope readScope(){return new ReadScope();}
    static List<Village.Block> memo(Village v,Object key,java.util.function.Supplier<List<Village.Block>> factory){var scope=VIEW.get();if(scope==null)return factory.get();var entries=scope.cache.computeIfAbsent(v,k->new HashMap<>());var result=entries.get(key);if(result!=null){scope.hits++;return result;}scope.builds++;result=List.copyOf(factory.get());entries.put(key,result);return result;}
    public static void invalidate(Village v){for(var scope=VIEW.get();scope!=null;scope=scope.previous)scope.cache.remove(v);}
    public static void requireSlot(Village v,Rules rules,boolean essential){int limit=Math.max(0,rules.maxProjects-(essential?0:1));if(v.projects.stream().filter(Village.Project::active).count()>=limit)throw new IllegalArgumentException(essential?Messages.text("Очередь строительства заполнена"):Messages.text("Одно место в очереди сохраняется для жизненно необходимых общих работ"));}
    private Projects(){}
    /** Clearance must finish before phase-zero posts replace vegetation at the same coordinates. */
    public static Comparator<Village.Block> constructionOrder(){return Comparator.comparingInt((Village.Block c)->c.phase).thenComparingInt(c->c.phase==0&&c.after.equals("minecraft:air")?0:1).thenComparingInt(c->c.y);}
    public static Optional<Village.Project> next(Village v){return v.projects.stream().filter(p->p.state.equals("QUEUED") || p.state.equals("BUILDING")).min(Comparator.<Village.Project>comparingInt(Projects::rank).thenComparing(Comparator.comparingInt((Village.Project p)->p.priority).reversed()));}
    private static int rank(Village.Project p){if(p.starter && p.owner==null){if(p.state.equals("BUILDING") && p.index>0)return -3;if(Objects.toString(p.kind,"").startsWith("hall"))return -2;}return p.essential?-1:p.owner==null?0:1;}
    /** Repairs store only changed cells; overlay every completed plan to retain the whole structure. */
    public static List<Village.Block> expected(Village v,UUID building){
        return memo(v,new BuildingKey(building),()->compose(v,building));
    }
    private static List<Village.Block> compose(Village v,UUID building){
        Map<String,Village.Block> cells=new LinkedHashMap<>();
        for(var p:v.projects)if(p.building.equals(building) && p.state.equals("COMPLETE"))for(var cell:p.blocks)cells.put(cell.x+":"+cell.y+":"+cell.z,cell);
        return new ArrayList<>(cells.values());
    }
    public static void resume(Village.Project p){
        if(!p.state.equals("PAUSED"))throw new IllegalArgumentException(Messages.text("Проект не приостановлен"));
        p.state="QUEUED";p.reason=Messages.text("Повторная проверка участка перед продолжением");
    }
    public static void cancelCommon(Village v,Village.Project p){
        if(p.owner!=null || !p.active() || p.starter)throw new IllegalArgumentException(Messages.text("Начальный или личный проект нельзя отменить этим действием"));
        for(var e:p.escrow.entrySet())if(e.getValue()>0)v.commonReturns.merge(e.getKey(),e.getValue(),Math::addExact);
        p.escrow.clear();p.state="CANCELLED";p.reason=Messages.text("Общий проект отменён; неизрасходованное сохранено для склада");
        var b=p.building(v);boolean old=v.projects.stream().anyMatch(q->q!=p && q.building.equals(b.id) && q.state.equals("COMPLETE"));
        b.complete=old;b.damaged=old || p.index>0;b.damageReason=b.damaged?Messages.text("Отменённая незавершённая стройка"):null;
        if(p.index==0 && !old)v.buildings.remove(b);
    }
    public static void releaseCommonReturns(Village v){
        for(String item:new ArrayList<>(v.commonReturns.keySet())){int n=Math.min(v.commonReturns.get(item),Math.max(0,v.capacity-v.stock.size()));if(n>0){v.stock.add(item,n,v.capacity);int left=v.commonReturns.get(item)-n;if(left==0)v.commonReturns.remove(item);else v.commonReturns.put(item,left);}}
    }
    public static void applied(Village v,Village.Project p){
        validateNext(v,p);
        if(p.index>=p.blocks.size() || !p.active())throw new IllegalArgumentException(Messages.text("Проект завершён"));
        var cell=p.blocks.get(p.index);
        if(cell.material!=null){int n=p.escrow.getOrDefault(cell.material,0);if(n<=0)throw new IllegalArgumentException(Messages.text("Нарушен резерв материалов"));p.escrow.put(cell.material,n-1);}
        if("WATER_BUCKET".equals(cell.material)){v.commonReturns.merge("BUCKET",1,Math::addExact);}
        p.index++;p.credit=Math.max(0,p.credit-1);p.state="BUILDING";
    }
    public static void validateNext(Village v,Village.Project p){
        if(p.index>=p.blocks.size() || !p.active())throw new IllegalArgumentException(Messages.text("Проект завершён"));
        var cell=p.blocks.get(p.index);if(cell.material!=null && p.escrow.getOrDefault(cell.material,0)<=0)throw new IllegalArgumentException(Messages.text("Нарушен резерв материалов"));
        if("WATER_BUCKET".equals(cell.material))Math.addExact(v.commonReturns.getOrDefault("BUCKET",0),1);
    }
    
}
