package ru.livingvillages.paper;

import ru.livingvillages.i18n.Messages;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.livingvillages.domain.*;
import ru.livingvillages.persistence.Database;
import java.util.*;

public final class Transactions {
    private final LivingVillagesPlugin plugin;
    public final Set<UUID> players=new HashSet<>(), villages=new HashSet<>();
    private final Set<UUID> inflight=new HashSet<>();
    public final Map<UUID,Database.Operation> unresolved=new HashMap<>();
    public Transactions(LivingVillagesPlugin plugin) {this.plugin=plugin;}
    public boolean busy(Village v) {return villages.contains(v.id);}
    public void run(Player player,Village next,ItemStack[] after,String note,Runnable success) {
        if(players.contains(player.getUniqueId()) || next!=null && busy(next))throw new IllegalArgumentException(Messages.text("Предыдущая операция ещё сохраняется"));
        if(next!=null && !Territory.access(next,player.getUniqueId(),player.getWorld().getUID(),player.getX(),player.getZ()))throw new IllegalArgumentException(Messages.text("Операции деревни доступны только на её территории после открытия"));
        if(!player.getItemOnCursor().getType().isAir())throw new IllegalArgumentException(Messages.text("Сначала уберите предмет с курсора"));
        if(next!=null)Names.assign(next,plugin.content.rules);
        String before=Coins.encode(player.getInventory().getStorageContents()),encodedAfter=Coins.encode(after);
        var op=new Database.Operation(UUID.randomUUID(),player.getUniqueId(),next==null?null:next.id,before,encodedAfter,next==null?null:Database.encode(next),"PREPARED",note);
        players.add(op.player());inflight.add(op.player());if(next!=null)villages.add(next.id);reserve(op);
        player.closeInventory();
        plugin.db.prepare(op).whenComplete((nothing,error)->plugin.sync(()->{
            if(error!=null){unlock(op);plugin.failure(player,error);return;}
            unresolved.put(op.id(),op);
            if(!player.isOnline() || next!=null && !Territory.access(next,player.getUniqueId(),player.getWorld().getUID(),player.getX(),player.getZ())){
                plugin.db.finish(op,false).whenComplete((done,problem)->plugin.sync(()->{if(problem!=null){inflight.remove(op.player());plugin.failure(player,problem);return;}unresolved.remove(op.id());unlock(op);}));return;
            }
            if(!before.equals(Coins.encode(player.getInventory().getStorageContents()))) {
                quarantine(op,player,Messages.text("Инвентарь изменился до выдачи; операция сохранена для проверки"));return;
            }
            try{player.getInventory().setStorageContents(after);player.saveData();}
            catch(Exception e){quarantine(op,player,Messages.text("Выдача прервана; требуется сверка"));return;}
            plugin.db.finish(op,true).whenComplete((done,problem)->plugin.sync(()->{
                if(problem!=null){inflight.remove(op.player());plugin.failure(player,problem);return;}
                if(!Boolean.TRUE.equals(done)){quarantine(op,player,Messages.text("Операция была отменена до подтверждения выдачи; требуется сверка"));return;}
                if(next!=null)plugin.publish(next);
                unresolved.remove(op.id());unlock(op);if(player.isOnline())success.run();
            }));
        }));
    }
    void reserve(Database.Operation op){if(op.aggregate()==null||op.village()==null)return;var target=Database.decode(op.aggregate());var current=plugin.villages.get(op.village());if(current!=null&&target.radius>current.radius)plugin.worldWorks.holdClaim(target);}
    public void recover(Player player) {
        if(inflight.contains(player.getUniqueId()))return;
        players.add(player.getUniqueId());inflight.add(player.getUniqueId());
        plugin.db.pending(player.getUniqueId()).whenComplete((pending,error)->plugin.sync(()->{
            if(error!=null){inflight.remove(player.getUniqueId());plugin.failure(player,error);return;}
            if(pending.isEmpty()){players.remove(player.getUniqueId());inflight.remove(player.getUniqueId());return;}
            for(var op:pending) {
                unresolved.put(op.id(),op);if(op.village()!=null)villages.add(op.village());reserve(op);
                String current=Coins.encode(player.getInventory().getStorageContents());
                if(op.state().equals("QUARANTINE") || !current.equals(op.before()) && !current.equals(op.after())) {
                    quarantine(op,player,Messages.text("Обнаружена неоднозначная операция ")+op.id()+Messages.text(". Обратитесь к администратору."));continue;
                }
                boolean commit=current.equals(op.after());
                plugin.db.finish(op,commit).whenComplete((done,problem)->plugin.sync(()->{
                    if(problem!=null){inflight.remove(op.player());plugin.failure(player,problem);return;}
                    if(Boolean.TRUE.equals(done) && op.aggregate()!=null) {Village next=Database.decode(op.aggregate());plugin.publish(next);}
                    unresolved.remove(op.id());unlock(op);plugin.tell(player,commit?Messages.text("Незавершённая операция восстановлена."):Messages.text("Неприменённая операция отменена."));
                }));
            }
        }));
    }
    public void resolve(Database.Operation op,boolean commit,Player admin) {
        if(inflight.contains(op.player()))throw new IllegalArgumentException(Messages.text("Операция ещё выполняется. Дождитесь завершения."));
        inflight.add(op.player());
        plugin.db.finish(op,commit,admin.getUniqueId()).whenComplete((done,error)->plugin.sync(()->{
            if(error!=null){inflight.remove(op.player());plugin.failure(admin,error);return;}
            if(Boolean.TRUE.equals(done) && op.aggregate()!=null){Village next=Database.decode(op.aggregate());plugin.publish(next);}
            unresolved.remove(op.id());unlock(op);plugin.tell(admin,Messages.text("Решение записано. Инвентарь автоматически не изменялся."));
        }));
    }
    private void quarantine(Database.Operation op,Player p,String reason) {inflight.remove(op.player());plugin.db.quarantine(op.id());plugin.tell(p,reason);plugin.getLogger().severe(reason+" op="+op.id());}
    private void unlock(Database.Operation op){inflight.remove(op.player());if(unresolved.values().stream().noneMatch(other->other.player().equals(op.player())))players.remove(op.player());if(op.village()!=null && unresolved.values().stream().noneMatch(other->op.village().equals(other.village()))){villages.remove(op.village());plugin.worldWorks.releaseClaim(op.village());}}
}
