package ru.livingvillages.paper;

import ru.livingvillages.i18n.Messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import ru.livingvillages.domain.*;
import ru.livingvillages.persistence.Database;
import java.util.*;
import static ru.livingvillages.domain.Village.*;

public final class Menus implements Listener {
    private final LivingVillagesPlugin plugin;
    
    private final Map<UUID,Long> conversations=new HashMap<>();
    
    private static final class Menu implements InventoryHolder {
        Inventory inventory;UUID village;final Map<Integer,Runnable> actions=new HashMap<>();
        @Override public Inventory getInventory(){return inventory;}
    }
    public Menus(LivingVillagesPlugin plugin){this.plugin=plugin;}
    void languageChanged(){conversations.clear();}
    private String buildingName(String id){var b=plugin.content.blueprints.get(id);return b==null?(id.equals("road")?Messages.text("Дорога"):id.equals("fortification")?Messages.text("Стены, ворота и башни"):Messages.saved(id)):b.name();}
    private Menu menu(String title,Village village){Menu m=new Menu();m.village=village==null?null:village.id;m.inventory=Bukkit.createInventory(m,54,TooltipText.title(title));return m;}
    private void button(Menu m,int slot,Material icon,String name,List<String> lines,Runnable action){
        ItemStack item=new ItemStack(icon);var meta=item.getItemMeta();TooltipText.apply(meta,name,NamedTextColor.YELLOW,lines);item.setItemMeta(meta);m.inventory.setItem(slot,item);if(action!=null)m.actions.put(slot,action);
    }
    private Village current(Player p,UUID id){
        Village v=plugin.villages.get(id);if(v==null)throw new IllegalArgumentException(Messages.text("Деревня не найдена"));
        if(!Territory.access(v,p.getUniqueId(),p.getWorld().getUID(),p.getX(),p.getZ()))throw new IllegalArgumentException(Messages.text("Меню доступно только внутри открытой вами деревни: ")+v.name+" ("+v.x+", "+v.z+")");
        if(plugin.transactions.busy(v) || plugin.transactions.players.contains(p.getUniqueId()))throw new IllegalArgumentException(Messages.text("Операция сохраняется; подождите"));v.relation(p.getUniqueId()).playerName=p.getName();return v;
    }
    public void directory(Player p,int page){
 var villages=Territory.discovered(plugin.villages.values(),p.getUniqueId());int pages=Math.max(1,(villages.size()+44)/45);page=Math.clamp(page,0,pages-1);Menu m=menu(Messages.text("Открытые деревни · ")+(page+1)+"/"+pages,null);int slot=0;
 for(var v:villages.stream().skip((long)page*45).limit(45).toList()){boolean here=Territory.inside(v,p.getWorld().getUID(),p.getX(),p.getZ());button(m,slot++,here?Material.OAK_DOOR:Material.COMPASS,v.name,List.of(plugin.content.cultures.get(v.culture).name(),Messages.text("Координаты: ")+v.x+" "+v.y+" "+v.z,Messages.text("Мир: ")+v.worldName,Messages.text("Ваше доверие: ")+v.relation(p.getUniqueId()).trust,here?Messages.text("Нажмите: открыть меню деревни"):Messages.text("Для входа подойдите на её территорию")),()->elder(p,current(p,v.id)));}
 if(villages.isEmpty())button(m,22,Material.COMPASS,Messages.text("Вы ещё не открыли деревни"),List.of(Messages.text("Исследуйте мир и приблизьтесь к поселению")),null);
 int selected=page;if(page>0)button(m,48,Material.ARROW,Messages.text("Назад"),List.of(),()->directory(p,selected-1));if(page+1<pages)button(m,50,Material.ARROW,Messages.text("Далее"),List.of(),()->directory(p,selected+1));p.openInventory(m.inventory);
}
    private void back(Menu m,Player p,UUID id){button(m,49,Material.ARROW,Messages.text("К старосте"),List.of(),()->elder(p,current(p,id)));}
    @EventHandler(priority=EventPriority.HIGHEST)public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof Menu m))return;
        e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p) || e.getRawSlot()<0 || e.getRawSlot()>=54)return;
        if(m.village!=null)try{current(p,m.village);}catch(IllegalArgumentException denied){p.closeInventory();plugin.tell(p,denied.getMessage());return;}
        if(e.getClick()!=ClickType.LEFT)return;
        Runnable action=m.actions.get(e.getRawSlot());if(action!=null)try(var read=Projects.readScope()){action.run();}catch(IllegalArgumentException ex){plugin.tell(p,ex.getMessage());}
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void open(InventoryOpenEvent e){if(e.getInventory().getHolder() instanceof Menu m && m.village!=null && e.getPlayer() instanceof Player p)try{current(p,m.village);}catch(IllegalArgumentException denied){e.setCancelled(true);plugin.tell(p,denied.getMessage());}}
    private void leave(Player p,Location to){if(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu m && m.village!=null){Village v=plugin.villages.get(m.village);if(v==null || !Territory.inside(v,to.getWorld().getUID(),to.getX(),to.getZ())){p.closeInventory();plugin.tell(p,Messages.text("Вы вышли за границы деревни. Список доступен через /lv menu."));}}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void move(PlayerMoveEvent e){leave(e.getPlayer(),e.getTo());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void teleport(PlayerTeleportEvent e){leave(e.getPlayer(),e.getTo());}
    @EventHandler public void world(PlayerChangedWorldEvent e){leave(e.getPlayer(),e.getPlayer().getLocation());}
    @EventHandler public void quit(PlayerQuitEvent e){conversations.remove(e.getPlayer().getUniqueId());}
    public void board(Player p,Village v,String kind){
        switch(kind){case "treasury"->trade(p,v);case "food"->stock(p,v);case "construction","goal","materials1","materials2","materials3"->projects(p,v);case "needs"->needs(p,v);case "development"->council(p,v);default->elder(p,v);}
    }
    public void elder(Player p,Village v){
 var m=menu(v.name,v);UUID id=v.id;
 button(m,10,Material.BOOK,Messages.text("Состояние поселения"),List.of(Messages.text("Жители: ")+v.living()+"/"+plugin.content.rules.maxPopulation,Messages.text("Уровень: ")+v.level+"/3",Messages.text("Еда: ")+plugin.content.rules.foodUnits(v),Messages.text("Казна: ")+Money.describe(v.treasury),Messages.text("Доверие: ")+v.relation(p.getUniqueId()).trust+" · "+TrustAccess.rank(v,p.getUniqueId(),plugin.content.rules),Messages.text("Развитие: ")+v.developmentReason),null);
 button(m,11,Material.CHEST,Messages.text("Склад"),List.of(Messages.text("Общий учёт ресурсов; сундуки не хранят копию")),()->warehouse(p,current(p,id),0));
 button(m,12,Material.WRITABLE_BOOK,Messages.text("Потребности и поручения"),List.of(plugin.content.cultures.get(v.culture).need()),()->needs(p,current(p,id)));
 button(m,13,Material.EMERALD,Messages.text("Торговля и размен"),List.of(Messages.text("Текущий ассортимент и реальные потребности")),()->trade(p,current(p,id)));
 button(m,14,Material.BRICKS,Messages.text("Строительные проекты"),List.of(Messages.text("Материалы, этапы, причины остановки")),()->projects(p,current(p,id)));
 button(m,16,Material.ENDER_CHEST,Messages.text("Получить возврат"),List.of(Messages.text("Имущество хранится до получения")),()->claim(p,current(p,id)));
 button(m,22,Material.MAP,Messages.text("Карта поселения"),List.of(Messages.text("Красная линия — граница деревни")),()->plugin.giveMap(p,current(p,id)));
 button(m,23,Material.CRAFTING_TABLE,Messages.text("Общее строительство"),List.of(Messages.text("Задачи с ")+plugin.content.rules.buildTrust+Messages.text(" доверия")),()->commonCatalog(p,current(p,id)));
 button(m,24,Material.WRITABLE_BOOK,Messages.text("План совета"),List.of(Messages.text("Цели на несколько дней и причины пересмотра")),()->council(p,current(p,id)));
 button(m,25,Material.ANVIL,Messages.text("Самостоятельное хозяйство"),List.of(Messages.text("Производство, переработка и настоящие дефициты")),()->supplies(p,current(p,id),0));
 button(m,26,Material.FURNACE,Messages.text("Производственные цепочки"),List.of(Messages.text("Рудник → печь → каменные кирпичи")),()->chains(p,current(p,id)));
 button(m,29,Material.HOPPER,Messages.text("Подарить ресурсы"),List.of(Messages.text("С ")+plugin.content.rules.friendTrust+Messages.text(" доверия — Друг деревни")),()->giftCatalog(p,current(p,id),0));
 button(m,31,Material.BOOK,Messages.text("Знакомые жители"),List.of(Messages.text("Для беседы подойдите к жителю")),()->friends(p,current(p,id)));
 button(m,49,Material.NETHER_STAR,"LivingVillages Premium",TooltipText.wrap(Messages.text("Эта возможность доступна в LivingVillages Premium. Хозяйство, торговля и развитие до уровня 3 доступны в бесплатной версии."),220),()->plugin.tell(p,Messages.text("Уровни 1–3, до 12 жителей, 5 деревень на мир, Полевичи. Premium: 8 культур, уровни 1–10, личные дома, власть, армии, редкое оружие.")));
 p.openInventory(m.inventory);
}
    public void warehouse(Player p,Village v,int page){
        var rows=WarehouseView.rows(v);int pages=Math.max(1,(rows.size()+44)/45),index=Math.max(0,Math.min(page,pages-1));Menu m=menu(Messages.text("Ресурсы деревни · ")+(index+1)+"/"+pages,v);int slot=0;
        for(var row:rows.stream().skip((long)index*45).limit(45).toList()){button(m,slot++,Material.valueOf(row.item()),Russian.item(row.item())+": "+row.total(),List.of(Messages.text("На общем складе: ")+row.warehouse(),Messages.text("В резерве общих строек: ")+row.reserved(),Messages.text("Ожидает места на складе: ")+row.pending(),Messages.text("Ведомость: предметы отсюда не забираются")),null);var shown=m.inventory.getItem(slot-1);shown.setAmount(Math.min(shown.getMaxStackSize(),row.total()));}
        if(rows.isEmpty())button(m,22,Material.CHEST,Messages.text("Общие запасы пусты"),List.of(Messages.text("Ресурсы появятся после добычи или поставок")),null);
        if(index>0)button(m,48,Material.ARROW,Messages.text("Назад"),List.of(),()->warehouse(p,current(p,v.id),index-1));if(index+1<pages)button(m,50,Material.ARROW,Messages.text("Далее"),List.of(),()->warehouse(p,current(p,v.id),index+1));
        button(m,47,Material.HOPPER,Messages.text("Подарить ресурсы"),List.of(Messages.text("Доступ с ")+plugin.content.rules.friendTrust+Messages.text(" доверия")),()->giftCatalog(p,current(p,v.id),0));back(m,p,v.id);p.openInventory(m.inventory);
    }
    public void stock(Player p,Village v){warehouse(p,v,0);}
    
    public void trade(Player p,Village v){try(var read=Projects.readScope()){trade(p,v,0);}}
    private void trade(Player p,Village v,int page){
        Menu m=menu(Messages.text("Торговый дом · страница ")+(page+1),v);int slot=0;UUID id=v.id;var demand=plugin.economy.catalog(v);
        var offers=plugin.content.rules.prices.entrySet().stream().filter(e->plugin.economy.exportable(v,e.getKey())>0||demand.get(e.getKey()).count()>0).toList();
        for(var e:offers.stream().skip((long)page*45).limit(45).toList()){
            String item=e.getKey();int needed=demand.get(item).count(),batch=e.getValue().batch(),count=needed>0&&needed<batch?needed:batch;
            button(m,slot++,Material.valueOf(item),Russian.item(item),List.of(Messages.text("Свободно для продажи: ")+plugin.economy.exportable(v,item),Messages.text("Деревне нужно: ")+needed,needed>0?demand.get(item).reason():Messages.text("Только продажа игрокам"),Messages.text("Нажмите: количество, точная цена и доверие")),()->offer(p,current(p,id),item,count));
        }
        if(offers.isEmpty())button(m,22,Material.GRAY_DYE,Messages.text("Сейчас нет торговых предложений"),List.of(Messages.text("Закупаются реальные нужды, продаются свободные товары")),null);
        button(m,47,Material.GOLD_NUGGET,Messages.text("Разменять все монеты"),List.of(Messages.text("Стоимость сохраняется")),()->{Village now=current(p,id);plugin.transactions.run(p,Database.copy(now),plugin.coins.balance(p.getInventory().getStorageContents(),0),Messages.text("Размен монет"),()->plugin.tell(p,Messages.text("Монеты разменяны без изменения суммы.")));});
        if(page>0)button(m,48,Material.ARROW,Messages.text("Назад"),List.of(),()->trade(p,current(p,id),page-1));if(offers.size()>(page+1)*45)button(m,50,Material.ARROW,Messages.text("Далее"),List.of(),()->trade(p,current(p,id),page+1));back(m,p,id);p.openInventory(m.inventory);
    }
    private void offer(Player p,Village v,String item,int count){
        Menu m=menu(Russian.item(item)+" × "+count,v);UUID id=v.id;int stack=Material.valueOf(item).getMaxStackSize(),need=plugin.economy.needed(v,item),batch=plugin.content.rules.prices.get(item).batch();var amounts=new LinkedHashSet<Integer>(List.of(batch,Math.max(1,stack/4),stack,stack*4));if(need>0&&need<=4096)amounts.add(need);int slot=9;
        for(int amount:amounts)if(amount<=4096&&(amount%batch==0||amount==need)){final int selected=amount;button(m,slot++,Material.PAPER,amount==need?Messages.text("Весь дефицит: ")+amount:amount==stack?Messages.text("Стак: ")+stack:Messages.text("Количество: ")+amount,List.of(Messages.text("Выбрано: ")+count),()->offer(p,current(p,id),item,selected));}
        for(boolean buy:new boolean[]{true,false}){
            int pos=buy?20:24;try{
                if(!buy&&need<count)throw new IllegalArgumentException(Messages.text("Текущая потребность: ")+need+". "+Needs.current(v,plugin.content.rules,item).reason());
                if(buy&&plugin.economy.exportable(v,item)<count)throw new IllegalArgumentException(Messages.text("Недостаточно свободного товара; резерв защищён"));
                var q=plugin.economy.quote(v,p.getUniqueId(),item,count,buy);if(!buy&&v.treasury<q.total())throw new IllegalArgumentException(Messages.text("Казна не может оплатить эту партию"));
                button(m,pos,Material.valueOf(item),(buy?Messages.text("Купить за "):Messages.text("Продать за "))+q.total()+Messages.text(" б."),List.of(q.reason(),Messages.text("Доверие за торговлю: до ")+plugin.content.rules.tradeTrustPerDay+Messages.text(" в день"),Messages.text("Цена, потребность и место проверяются повторно")),()->{
                    Village next=Database.copy(current(p,id));int before=next.relation(p.getUniqueId()).trust;plugin.economy.trade(next,p.getUniqueId(),q);int gained=next.relation(p.getUniqueId()).trust-before;ItemStack[] after=Coins.cloneItems(p.getInventory().getStorageContents());
                    if(buy){after=plugin.coins.balance(after,-q.total());Coins.add(after,new ItemStack(Material.valueOf(item),count));}else{Coins.removePlain(after,Material.valueOf(item),count);after=plugin.coins.balance(after,q.total());}
                    plugin.transactions.run(p,next,after,(buy?Messages.text("Покупка "):Messages.text("Продажа "))+item+" × "+count,()->{plugin.tell(p,Messages.text("Обмен завершён: ")+count+" × "+Russian.item(item)+Messages.text(". Доверие: +")+gained+(gained==0?Messages.text(" (повтор товара или достигнут дневной/общий предел торговли)"):""));offer(p,plugin.villages.get(id),item,count);});
                });
            }catch(IllegalArgumentException unavailable){button(m,pos,Material.GRAY_DYE,buy?Messages.text("Покупка недоступна"):Messages.text("Продажа недоступна"),List.of(Objects.toString(unavailable.getMessage(),Messages.text("Выберите другую партию"))),null);}
        }back(m,p,id);p.openInventory(m.inventory);
    }
    private void friends(Player p,Village v){Menu m=menu(Messages.text("Знакомые жители"),v);int slot=0;for(var r:v.residents)if(r.alive && slot<45)button(m,slot++,Material.PAPER,r.name+" · "+plugin.residentRoleName(v,r),List.of(Messages.text("Знакомство: ")+v.relation(p.getUniqueId()).friendship.getOrDefault(r.id,0)+" / 100",Messages.text("Подойдите к жителю для беседы")),()->converse(p,current(p,v.id),r.id));back(m,p,v.id);p.openInventory(m.inventory);}
    public void converse(Player p,Village v,UUID resident){var person=nearResident(p,v,resident);long stamp=System.nanoTime();if(stamp<conversations.getOrDefault(p.getUniqueId(),0L))return;conversations.put(p.getUniqueId(),stamp+plugin.content.config.getLong("dialogue.cooldown-seconds",4)*1_000_000_000L);var reply=Social.talk(v,p.getUniqueId(),resident,"friend",plugin.content.rules);var lines=plugin.content.dialogues.getOrDefault("chat_"+reply.situation(),List.of(Messages.text("Рад, что заглянули.")));plugin.tell(p,person.name+": "+lines.get(Math.floorMod(person.id.hashCode()+(int)v.days,lines.size()))+Messages.text(" Знакомство: ")+reply.friendship()+"/100");plugin.save(v);p.closeInventory();}
    private Village.Resident nearResident(Player p,Village v,UUID id){var r=v.resident(id);var entity=r==null || r.entity==null?null:Bukkit.getEntity(r.entity);if(r==null || !r.alive || entity==null || !entity.getWorld().equals(p.getWorld()) || entity.getLocation().distanceSquared(p.getLocation())>36)throw new IllegalArgumentException(Messages.text("Подойдите к этому жителю на расстояние до 6 блоков"));return r;}
    private void needs(Player p,Village v){needs(p,v,0);}
    private void needs(Player p,Village v,int page){
        Menu m=menu(Messages.text("Поручения из реальных нужд"),v);UUID id=v.id;var demand=plugin.economy.catalog(v);int slot=0;
        for(String item:plugin.content.rules.prices.keySet().stream().filter(item->demand.get(item).count()>=plugin.content.rules.prices.get(item).batch()&&!plugin.villages.values().stream().anyMatch(other->Missions.active(other,p.getUniqueId()))).skip((long)page*36).limit(36).toList()) {
            int need=demand.get(item).count();if(need==0)continue;int count=plugin.content.rules.prices.get(item).batch();
            var bid=plugin.economy.displayQuote(v,p.getUniqueId(),item,count,false,demand.get(item).count());
            button(m,slot++,Material.valueOf(item),Messages.text("Доставить ")+Russian.item(item),List.of(Messages.text("Нужно ещё: ")+need,Messages.text("Оплата партии ")+count+": "+bid.total()+Messages.text(" б."),v.treasury>=bid.total()?Messages.text("Оплата из текущей казны"):Messages.text("Сейчас казна не может оплатить"),Messages.text("Нажмите: принять поручение с резервом оплаты")),()->{
                Missions.requireAvailable(plugin.villages.values(),p.getUniqueId());Village next=Database.copy(current(p,id));Contracts.issue(next,p.getUniqueId(),item,count,plugin.economy);
                plugin.transactions.run(p,next,Coins.cloneItems(p.getInventory().getStorageContents()),Messages.text("Выдано поручение ")+item,()->needs(p,plugin.villages.get(id)));
            });
            if(slot>=36)break;
        }
        if(plugin.villages.values().stream().anyMatch(other->Missions.active(other,p.getUniqueId())))button(m,4,Material.BOOK,Messages.text("У вас одно активное поручение"),List.of(Messages.text("Завершите или отмените его перед следующим"),Messages.text("Управление — в меню поручений той деревни")),null);
        button(m,44,Material.STONE_HOE,Messages.text("Обслуживание инструментов"),List.of(Messages.text("Мотыга — фермеру, топор — лесорубу"),Messages.text("Удочка — рыбаку, кирка — ремесленнику"),Messages.text("Замена расходуется из общих запасов")),null);
        for(var task:v.tasks)if(task.state.equals("ACTIVE") && task.player.equals(p.getUniqueId())){
            button(m,38,Material.WRITABLE_BOOK,Messages.text("Сдать поручение: ")+Russian.item(task.item),List.of(task.delivered+" / "+task.amount,Messages.text("Оплата зарезервирована: ")+task.reward+Messages.text(" б."),Messages.text("Срок: до дня ")+task.expiresDay,Messages.text("Материалы принимаются частями")),()->{
                Village next=Database.copy(current(p,id));var t=next.tasks.stream().filter(q->q.id.equals(task.id)).findFirst().orElseThrow();
                ItemStack[] after=Coins.cloneItems(p.getInventory().getStorageContents());Material mat=Material.valueOf(t.item);int n=Math.min(t.amount-t.delivered,Arrays.stream(after).filter(i->Coins.plain(i,mat)).mapToInt(ItemStack::getAmount).sum());
                if(n==0)throw new IllegalArgumentException(Messages.text("Нет нужного товара в инвентаре"));Coins.removePlain(after,mat,n);Contracts.deliver(next,t,n,plugin.economy);
                plugin.transactions.run(p,next,after,Messages.text("Поставка по поручению ")+t.id,()->plugin.tell(p,Messages.text("Поставка принята. Оплата доступна в меню получения.")));
            });
            button(m,39,Material.RED_DYE,Messages.text("Отменить поручение"),List.of(Messages.text("Оплата сданной части остаётся у вас"),Messages.text("Неиспользованный бюджет возвращается деревне")),()->{
                Village next=Database.copy(current(p,id));var t=next.tasks.stream().filter(q->q.id.equals(task.id)).findFirst().orElseThrow();Contracts.cancel(next,t);
                plugin.transactions.run(p,next,Coins.cloneItems(p.getInventory().getStorageContents()),Messages.text("Отмена поручения ")+t.id,()->plugin.tell(p,Messages.text("Поручение отменено.")));
            });
        }
        button(m,40,Material.IRON_SWORD,Messages.text("Стройка, восстановление и защита"),List.of(Messages.text("Поручения с оплатой из зарезервированной казны")),()->missions(p,current(p,id)));
        button(m,42,Material.BRICKS,Messages.text("Нужды строительных проектов"),List.of(Messages.text("Точные материалы действующих общих строек")),()->projects(p,current(p,id)));
        if(page>0)button(m,48,Material.ARROW,Messages.text("Назад"),List.of(),()->needs(p,current(p,id),page-1));if(plugin.content.rules.prices.keySet().stream().filter(item->demand.get(item).count()>0).count()>(long)(page+1)*36)button(m,50,Material.ARROW,Messages.text("Далее"),List.of(),()->needs(p,current(p,id),page+1));
        back(m,p,id);p.openInventory(m.inventory);
    }
    private String missionName(String kind){return switch(kind){case "BUILD"->Messages.text("Помощь общей стройке");case "REPAIR"->Messages.text("Восстановление здания");default->Messages.text("Защита деревни");};}
    private void missions(Player p,Village v){
        Menu m=menu(Messages.text("Общественные поручения"),v);int slot=0;boolean available=plugin.villages.values().stream().noneMatch(other->Missions.active(other,p.getUniqueId()));
        for(var q:v.projects)if(available&&q.owner==null && !q.starter && q.state.equals("COLLECTING") && slot<30&&v.missions.stream().noneMatch(done->done.player.equals(p.getUniqueId())&&done.state.equals("COMPLETE")&&q.id.equals(done.target))){int missing=q.required.entrySet().stream().mapToInt(e->Math.max(0,e.getValue()-q.escrow.getOrDefault(e.getKey(),0))).sum();if(missing==0)continue;int goal=Math.min(missing,Math.min(16,Math.max(4,missing/16))),reward=plugin.content.rules.buildMissionReward;
            button(m,slot++,Material.BRICKS,Messages.text("Помочь: ")+buildingName(q.kind),List.of(Messages.text("Передать не менее ")+goal+Messages.text(" нужных материалов"),Messages.text("Награда после передачи требуемого вклада"),Messages.text("Награда: ")+reward+Messages.text(" бронзовых"),Messages.text("Общие пожертвования не возвращаются при отмене")),()->acceptMission(p,current(p,v.id),"BUILD",q.id,reward,goal));}
        for(var b:v.buildings)if(available&&b.owner==null && b.damaged && v.projects.stream().noneMatch(q->q.building.equals(b.id) && q.active()) && slot<35){int reward=plugin.content.rules.repairMissionReward;button(m,slot++,Material.ANVIL,Messages.text("Восстановить: ")+buildingName(b.kind),List.of(Messages.text("Начать реальный ремонт или передать его материалы"),Messages.text("Награда после завершения: ")+reward+Messages.text(" бронзовых")),()->acceptMission(p,current(p,v.id),"REPAIR",b.id,reward,1));}
        if(available&&plugin.npcs.hostiles(v)>0){int reward=plugin.content.rules.defenseMissionReward,goal=Math.min(3,Math.max(1,v.living()/8));button(m,35,Material.IRON_SWORD,Messages.text("Защитить жителей"),List.of(Messages.text("Уничтожить нападающих: ")+goal,Messages.text("Учитываются обнаруженные жителями враги"),Messages.text("Награда: ")+reward+Messages.text(" бронзовых")),()->acceptMission(p,current(p,v.id),"DEFENSE",null,reward,goal));}
        for(var mission:v.missions)if(mission.state.equals("ACTIVE") && mission.player.equals(p.getUniqueId())){
            button(m,38,Material.WRITABLE_BOOK,missionName(mission.kind),List.of(Messages.text("Прогресс: ")+mission.progress+" / "+mission.goal,Messages.text("Зарезервировано: ")+mission.reward+Messages.text(" бронзовых"),Messages.text("Срок: до дня ")+mission.expiresDay,Messages.text("Нажмите: проверить выполнение")),()->{Village next=Database.copy(current(p,v.id));Missions.check(next,plugin.content.rules);plugin.transactions.run(p,next,Coins.cloneItems(p.getInventory().getStorageContents()),Messages.text("Проверка поручения"),()->missions(p,plugin.villages.get(v.id)));});
            button(m,39,Material.RED_DYE,Messages.text("Отменить поручение"),List.of(Messages.text("Бюджет вернётся деревне"),Messages.text("Подаренные общему проекту материалы останутся в нём")),()->{Village next=Database.copy(current(p,v.id));var goal=next.missions.stream().filter(q->q.id.equals(mission.id)).findFirst().orElseThrow();if(!p.getUniqueId().equals(goal.player))throw new IllegalArgumentException(Messages.text("Чужое поручение"));Missions.cancel(next,goal);plugin.transactions.run(p,next,Coins.cloneItems(p.getInventory().getStorageContents()),Messages.text("Отмена общественного поручения"),()->missions(p,plugin.villages.get(v.id)));});
            if(mission.kind.equals("BUILD"))button(m,40,Material.CHEST,Messages.text("Передать материалы проекта"),List.of(Messages.text("Только действительный дефицит")),()->project(p,current(p,v.id),mission.target));
            if(mission.kind.equals("REPAIR"))button(m,40,Material.ANVIL,Messages.text("Перейти к ремонту"),List.of(),()->buildingDetail(p,current(p,v.id),mission.target));
        }
        button(m,44,Material.ENDER_CHEST,Messages.text("Получить заработанное"),List.of(Messages.text("Награды поступают в сохранённую выдачу")),()->claim(p,current(p,v.id)));back(m,p,v.id);p.openInventory(m.inventory);
    }
    private void acceptMission(Player p,Village v,String kind,UUID target,int quotedReward,int quotedGoal){
        Missions.requireAvailable(plugin.villages.values(),p.getUniqueId());Village next=Database.copy(v);var mission=Missions.issue(next,p.getUniqueId(),kind,target,plugin.content.rules,plugin.npcs.hostiles(v)>0);if(mission.reward!=quotedReward || mission.goal!=quotedGoal)throw new IllegalArgumentException(Messages.text("Условия изменились; откройте поручения снова"));
        plugin.transactions.run(p,next,Coins.cloneItems(p.getInventory().getStorageContents()),Messages.text("Поручение ")+kind,()->{plugin.tell(p,Messages.text("Поручение принято. Награда зарезервирована; получите её после выполнения."));missions(p,plugin.villages.get(v.id));});
    }
    
    private void manageProject(Player player,Village village,UUID id,boolean cancel){
        if(!player.hasPermission("livingvillages.admin"))throw new IllegalArgumentException(Messages.text("Требуется livingvillages.admin"));Village next=Database.copy(village);Project p=next.projects.stream().filter(q->q.id.equals(id)).findFirst().orElseThrow();
        if(p.owner!=null || !p.active())throw new IllegalArgumentException(Messages.text("Управление доступно только действующим общим проектам"));
        if(cancel)Projects.cancelCommon(next,p);else p.priority=(p.priority+1)%3;
        plugin.transactions.run(player,next,Coins.cloneItems(player.getInventory().getStorageContents()),cancel?Messages.text("Отмена общего проекта"):Messages.text("Приоритет проекта"),()->projects(player,plugin.villages.get(next.id)));
    }
    
    
    
    public void projects(Player p,Village v){projects(p,v,0);}
    private void projects(Player p,Village v,int page){
        Menu m=menu(Messages.text("Стройки · страница ")+(page+1),v);int slot=0;
        for(Project project:v.projects.reversed().stream().skip((long)page*45).limit(45).toList())button(m,slot++,Material.BRICKS,buildingName(project.kind)+" · "+Russian.state(project.state),List.of(Messages.text("Этап/блок: ")+project.index+" / "+project.blocks.size(),project.reason==null?"":project.reason,"ID: "+project.id.toString().substring(0,8),project.owner==null?Messages.text("Общий проект"):Messages.text("Заказчик: ")+project.owner),()->project(p,current(p,v.id),project.id));
        if(page>0)button(m,48,Material.ARROW,Messages.text("Назад"),List.of(),()->projects(p,current(p,v.id),page-1));
        if(v.projects.size()>(page+1)*45)button(m,50,Material.ARROW,Messages.text("Далее"),List.of(),()->projects(p,current(p,v.id),page+1));
        back(m,p,v.id);p.openInventory(m.inventory);
    }
    private void project(Player p,Village v,UUID projectId){
        Project q=v.projects.stream().filter(k->k.id.equals(projectId)).findFirst().orElseThrow();Menu m=menu(Messages.text("Материалы · ")+buildingName(q.kind),v);int slot=0;
        for(var e:q.required.entrySet())if(slot<40)button(m,slot++,Material.valueOf(e.getKey()),Russian.item(e.getKey()),List.of(Messages.text("Всего по чертежу: ")+e.getValue(),Messages.text("Остаток резерва: ")+q.escrow.getOrDefault(e.getKey(),0)),null);
        if((p.getUniqueId().equals(q.owner) || q.owner==null) && q.active() && !Set.of("SURVEY","SITE_BLOCKED").contains(q.state)) {
            button(m,45,Material.CHEST,Messages.text("Передать материалы из инвентаря"),List.of(Messages.text("Только недостающие, без переименования"),q.owner==null&&Missions.contributionAllowance(v,p.getUniqueId(),q,plugin.content.rules)>0?Messages.text("Можно передать вклад по принятому поручению"):q.owner==null?Messages.text("Подарок: от ")+plugin.content.rules.friendTrust+Messages.text(" доверия"):Messages.text("Можно передавать частями")),()->deposit(p,current(p,v.id),projectId));
        }
        if(q.owner==null && q.active() && p.hasPermission("livingvillages.admin")){
            button(m,46,Material.COMPARATOR,Messages.text("Приоритет: ")+q.priority,List.of(Messages.text("Переключить 0 → 1 → 2 → 0"),Messages.text("Жизненно необходимые проекты остаются первыми")),()->manageProject(p,current(p,v.id),projectId,false));
            if(!q.starter)button(m,47,Material.RED_DYE,Messages.text("Отменить общий проект"),List.of(Messages.text("Неизрасходованное вернётся на склад"),Messages.text("При полном складе хранится отдельно до освобождения места")),()->manageProject(p,current(p,v.id),projectId,true));
        }
        if(q.state.equals("PAUSED") && (p.getUniqueId().equals(q.owner) || q.owner==null && p.hasPermission("livingvillages.admin")))button(m,48,Material.LIME_DYE,Messages.text("Повторить проверку и продолжить"),List.of(Messages.text("Сначала устраните указанную причину"),Messages.text("Посторонние блоки не будут снесены")),()->{
            Village next=Database.copy(current(p,v.id));Project project=next.projects.stream().filter(k->k.id.equals(projectId)).findFirst().orElseThrow();
            if(!p.getUniqueId().equals(project.owner) && !(project.owner==null && p.hasPermission("livingvillages.admin")))throw new IllegalArgumentException(Messages.text("Нет права продолжить проект"));Projects.resume(project);
            plugin.transactions.run(p,next,Coins.cloneItems(p.getInventory().getStorageContents()),Messages.text("Продолжение проекта ")+projectId,()->projects(p,plugin.villages.get(next.id)));
        });
        back(m,p,v.id);p.openInventory(m.inventory);
    }
    
    private void deposit(Player p,Village v,UUID id){
        Village next=Database.copy(v);Project q=next.projects.stream().filter(k->k.id.equals(id)).findFirst().orElseThrow();
        if(q.owner!=null && !p.getUniqueId().equals(q.owner))throw new IllegalArgumentException(Messages.text("Чужой заказ"));
        int allowance=q.owner==null?Missions.contributionAllowance(next,p.getUniqueId(),q,plugin.content.rules):Integer.MAX_VALUE;if(allowance==0)TrustAccess.requireFriend(next,p.getUniqueId(),plugin.content.rules);
        if(!q.state.equals("COLLECTING"))throw new IllegalArgumentException(Messages.text("Материалы уже собраны"));
        ItemStack[] after=Coins.cloneItems(p.getInventory().getStorageContents());int total=0;
        for(var e:q.required.entrySet()) {
            Material material=Material.valueOf(e.getKey());int available=Arrays.stream(after).filter(i->Coins.plain(i,material)).mapToInt(ItemStack::getAmount).sum();
            int n=Math.min(allowance-total,Math.min(available,e.getValue()-q.escrow.getOrDefault(e.getKey(),0)));
            if(n>0){Coins.removePlain(after,material,n);q.escrow.merge(e.getKey(),n,Integer::sum);total+=n;}
        }
        if(total==0)throw new IllegalArgumentException(Messages.text("В инвентаре нет недостающих материалов"));
        if(!Set.of("SURVEY","SITE_BLOCKED").contains(q.state) && q.funded()){q.state="QUEUED";q.reason=Messages.text("Очередь строителей");}
        if(q.owner==null){q.contributors.add(p.getUniqueId());q.deliveredBy.merge(p.getUniqueId(),total,Math::addExact);next.relation(p.getUniqueId()).deeds.add("construction");Reputation.award(next,p.getUniqueId(),plugin.content.rules,"donation",Math.min(10,total/8));}
        Missions.check(next,plugin.content.rules);
        plugin.transactions.run(p,next,after,Messages.text("Материалы заказа ")+id,()->projects(p,plugin.villages.get(next.id)));
    }
    
    
    public void claim(Player p,Village v){
        Village next=Database.copy(v);Map<String,Integer> items=next.claims.getOrDefault(p.getUniqueId(),Map.of());long money=next.payouts.getOrDefault(p.getUniqueId(),0L);
        if(items.isEmpty() && money==0)throw new IllegalArgumentException(Messages.text("Нет имущества к получению"));
        ItemStack[] after=plugin.coins.balance(p.getInventory().getStorageContents(),money);int delivered=0;
        // Partial saved delivery fits even large refunds; failed stack remains in the durable claim.
        Map<String,Integer> left=new TreeMap<>(items);
        for(var e:items.entrySet()) {
            int count=e.getValue();while(count>0){int n=Math.min(Material.valueOf(e.getKey()).getMaxStackSize(),count);ItemStack[] trial=Coins.cloneItems(after);try{Coins.add(trial,new ItemStack(Material.valueOf(e.getKey()),n));}catch(IllegalArgumentException full){break;}after=trial;count-=n;delivered+=n;}
            if(count==0)left.remove(e.getKey());else left.put(e.getKey(),count);
        }
        if(delivered==0 && money==0)throw new IllegalArgumentException(Messages.text("Освободите место в инвентаре"));
        next.claims.put(p.getUniqueId(),left);next.payouts.remove(p.getUniqueId());
        plugin.transactions.run(p,next,after,Messages.text("Получение возврата"),()->plugin.tell(p,Messages.text("Выдана доступная часть возврата. Остаток хранится в деревне.")));
    }
    private void chains(Player p,Village v){
        var r=plugin.content.rules;var m=menu(Messages.text("Производственные цепочки"),v);
        long farmers=v.residents.stream().filter(w->w.alive&&v.adult(w)&&w.workplace!=null&&v.buildings.stream().anyMatch(b->b.id.equals(w.workplace)&&FoodPlan.provider(b,r))).count();
        button(m,10,Material.CARROT,Messages.text("Питание и поля"),List.of(Messages.text("Потребление: ")+r.dailyFood(v)+Messages.text(" ед./сутки"),Messages.text("Работают: ")+farmers+Messages.text("; выработка: ")+Math.round(FoodPlan.capacity(v,r,false)),Messages.text("С учётом вакансий: ")+Math.round(FoodPlan.capacity(v,r,true)),Messages.text("План с запасом: ")+Math.round(FoodPlan.target(v,r)),Messages.text("Еда на складе: ")+r.foodUnits(v)+Messages.text(" ед."),FoodPlan.pending(v,r)?Messages.text("Хозяйство уже в строительной очереди"):Messages.text("Новая ферма планируется по прогнозу")),()->commonCatalog(p,current(p,v.id)));
        button(m,12,Material.OAK_LOG,Messages.text("Дерево и столярное дело"),List.of(Messages.text("Делянки: ")+v.buildings.stream().filter(b->Jobs.usable(b)&&b.kind.startsWith("tree_garden")).count(),Messages.text("Основная древесина: ")+Assembly.wood(v),Messages.text("Лестниц изготовлено: ")+v.crafted.entrySet().stream().filter(e->e.getKey().endsWith("_STAIRS")).mapToLong(Map.Entry::getValue).sum(),Messages.text("Факелов изготовлено: ")+v.crafted.getOrDefault("TORCH",0L)),()->supplies(p,current(p,v.id),0));
        button(m,14,Material.STONE_BRICKS,Messages.text("Камень и печи"),List.of(Messages.text("Печь: ")+(Assembly.facility(v,"furnace")==null?Messages.text("нужен готовый объект"):Messages.text("работает")),Messages.text("Верстак: ")+(Assembly.facility(v,"bench")==null?Messages.text("нужен готовый объект"):Messages.text("работает")),Messages.text("Камня обожжено: ")+v.crafted.getOrDefault("STONE",0L),Messages.text("Кирпичей изготовлено: ")+v.crafted.getOrDefault("STONE_BRICKS",0L),Messages.text("Древесного угля: ")+v.stock.get("CHARCOAL"),supplyText(v.supplyReason)),()->materialNeeds(p,current(p,v.id),0));
        button(m,22,Material.CLOCK,Messages.text("Ремесло: ")+(r.timedCrafting?Messages.text("короткие циклы"):Messages.text("раз в день")),List.of(r.timedCrafting?Messages.text("До следующей попытки: ")+Math.max(0,(r.craftIntervalMillis-v.craftElapsedMillis+999)/1000)+Messages.text(" сек."):Messages.text("Экономический день: ")+r.dayMillis/1000+Messages.text(" сек."),Messages.text("Мастеров за проход: ")+(r.timedCrafting?r.craftWorkersPerPass:r.craftWorkers),Messages.text("Самоснабжение: ")+r.autonomy,Messages.text("Нужны сырьё, рабочий объект и взрослый мастер"),supplyText(v.supplyReason)),null);
        button(m,16,Material.PAPER,Messages.text("Все этапы снабжения"),List.of(Messages.text("Промежуточные изделия и конечные материалы"),Messages.text("Запасы уже учтены; резервы защищены")),()->materialNeeds(p,current(p,v.id),0));back(m,p,v.id);p.openInventory(m.inventory);
    }
    private void materialNeeds(Player p,Village v,int page){var entries=new ArrayList<>(Assembly.materialPlan(v,plugin.content.rules,false).entrySet());var m=menu(Messages.text("Сырьё и полуфабрикаты · ")+(page+1),v);int slot=0;for(var e:entries.stream().skip((long)page*45).limit(45).toList())button(m,slot++,Material.valueOf(e.getKey()),Russian.item(e.getKey()),List.of(Messages.text("Нужно дополнительно: ")+e.getValue(),Messages.text("На складе: ")+v.stock.get(e.getKey()),Messages.text("Защищённый запас: ")+SelfSupply.reserve(v,plugin.content.rules,e.getKey())),null);if(page>0)button(m,48,Material.ARROW,Messages.text("Назад"),List.of(),()->materialNeeds(p,current(p,v.id),page-1));if(entries.size()>(page+1)*45)button(m,50,Material.ARROW,Messages.text("Далее"),List.of(),()->materialNeeds(p,current(p,v.id),page+1));back(m,p,v.id);p.openInventory(m.inventory);}
    public void council(Player p,Village v){Council.review(v,plugin.content.rules);Menu m=menu(Messages.text("Совет · план развития"),v);button(m,4,Material.LECTERN,Messages.text("План на ")+plugin.content.rules.planDays+Messages.text(" дней"),List.of(Messages.text("Пересмотр: ")+v.council.reason,Messages.text("День плана: ")+Math.max(0,v.days-v.council.startedDay)+Messages.text("; версия ")+v.council.revision,Messages.text("Текущий выпуск еды: ")+String.format(java.util.Locale.ROOT,"%.1f",Council.foodPerDay(v,plugin.content.rules))+Messages.text(" ед./день"),Messages.text("Нужно жителям: ")+plugin.content.rules.dailyFood(v)+Messages.text(" ед./день"),Messages.text("Оценка выпуска не гарантирует будущий урожай")),null);int slot=9;for(var g:v.council.goals){String name=g.kind==null?g.key.equals("food")?Messages.text("Продовольственный запас"):Messages.text("Инструменты и семена"):plugin.content.blueprints.containsKey(g.kind)?plugin.content.blueprints.get(g.kind).name():g.kind;button(m,slot++,g.complete?Material.LIME_DYE:Material.WRITABLE_BOOK,name,List.of(Messages.text("Готовность: ")+g.current+" / "+g.target,supplyText(g.reason),g.complete?Messages.text("Цель выполнена"):Messages.text("Материалы и участки проверяются при создании проекта")),null);}button(m,30,Material.ANVIL,Messages.text("Подробности снабжения"),List.of(Messages.text("Необходимые ресурсы и производственные цепочки")),()->supplies(p,current(p,v.id),0));back(m,p,v.id);p.openInventory(m.inventory);}
    
    private String supplyText(String message){String result=message;var items=new HashSet<String>();items.addAll(plugin.content.rules.prices.keySet());for(var c:plugin.content.rules.crafts.values()){items.addAll(c.input().keySet());items.addAll(c.output().keySet());}for(String item:items.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList())result=result.replace(item,Russian.item(item));return result.replace("forge",Messages.text("кузница")).replace("furnace",Messages.text("печь")).replace("bench",Messages.text("верстак")).replace("hand",Messages.text("рабочее место"));}
    private void supplies(Player p,Village v,int page){Menu m=menu(Messages.text("Хозяйство · ")+(page+1),v);button(m,4,Material.CRAFTING_TABLE,Messages.text("Снабжение деревни"),List.of(supplyText(v.supplyReason),Messages.text("Одна производственная работа на жителя за день"),Messages.text("Резервы строек в переработку не поступают"),Messages.text("Пустые месторождения не возобновляются")),null);var needs=new ArrayList<>(Assembly.rawDemand(v,plugin.content.rules).entrySet());int slot=9;for(var e:needs.stream().skip((long)page*27).limit(27).toList())button(m,slot++,Material.valueOf(e.getKey()),Russian.item(e.getKey()),List.of(Messages.text("Не хватает сырья: ")+e.getValue(),Messages.text("В свободном складе: ")+v.stock.get(e.getKey()),Messages.text("Можно продать нужное или передать общей стройке")),null);if(page>0)button(m,48,Material.ARROW,Messages.text("Назад"),List.of(),()->supplies(p,current(p,v.id),page-1));if(needs.size()>(page+1)*27)button(m,50,Material.ARROW,Messages.text("Далее"),List.of(),()->supplies(p,current(p,v.id),page+1));back(m,p,v.id);p.openInventory(m.inventory);}
    private void commonCatalog(Player p,Village v){commonCatalog(p,v,0);}
    private void commonCatalog(Player p,Village v,int page){
 var kinds=plugin.content.blueprints.keySet().stream().filter(k->!Set.of("hall","warehouse","street_light").contains(k)).toList();var m=menu(Messages.text("Общее строительство"),v);int slot=0;
 for(String kind:kinds){var b=plugin.content.blueprints.get(kind);button(m,slot++,Material.BRICKS,b.name(),List.of(Messages.text("Жильё: ")+b.beds(),Messages.text("Материалы передаются через проекты"),Messages.text("Задачи с ")+plugin.content.rules.buildTrust+Messages.text(" доверия")),()->commonProject(p,current(p,v.id),kind));}
 button(m,37,Material.ANVIL,Messages.text("Здания, обслуживание и ремонт"),List.of(Messages.text("Рабочие места и причины остановки")),()->buildingList(p,current(p,v.id),0));back(m,p,v.id);p.openInventory(m.inventory);
}
    
    private void buildingList(Player p,Village v,int page){
        Menu m=menu(Messages.text("Здания · страница ")+(page+1),v);var buildings=v.buildings.stream().filter(b->b.owner==null && !b.kind.equals("road")).toList();int slot=0;
        for(var b:buildings.stream().skip((long)page*45).limit(45).toList())button(m,slot++,b.damaged?Material.ANVIL:Material.BRICKS,buildingName(b.kind),List.of(b.complete?Messages.text("Завершено"):Messages.text("Строится"),Messages.text("Уровень: ")+b.level,b.upkeepMissing?Messages.text("Нужен материал обслуживания"):Messages.text("Обслуживание: ")+b.upkeep,b.damaged?Objects.toString(b.damageReason,Messages.text("Нужен ремонт")):b.resourceLeft==0?Messages.text("Рабочий ресурс исчерпан"):b.resourceConfirmed?Messages.text("Ресурс подтверждён"):Messages.text("Рабочий ресурс не подтверждён")),()->buildingDetail(p,current(p,v.id),b.id));
        if(page>0)button(m,48,Material.ARROW,Messages.text("Назад"),List.of(),()->buildingList(p,current(p,v.id),page-1));if(buildings.size()>(page+1)*45)button(m,50,Material.ARROW,Messages.text("Далее"),List.of(),()->buildingList(p,current(p,v.id),page+1));back(m,p,v.id);p.openInventory(m.inventory);
    }
    private void buildingDetail(Player p,Village v,UUID id){
        var b=v.buildings.stream().filter(q->q.id.equals(id)).findFirst().orElseThrow();Menu m=menu(buildingName(b.kind),v);
        button(m,4,Material.BRICKS,Messages.text("Состояние объекта"),List.of(Messages.text("Уровень: ")+b.level,Messages.text("Обслуживание: ")+b.upkeep+" / "+b.upkeepDays+Messages.text(" сут."),Messages.text("Следующий срок: ")+b.upkeepDue,b.resourceLeft<0?Messages.text("Возобновляемый/перерабатывающий объект"):Messages.text("Конечный запас: ")+b.resourceLeft+Messages.text(" партий")),null);
        if(b.family!=null)button(m,12,Material.OAK_DOOR,Messages.text("Дом семьи"),List.of(Housing.familyName(v,b.family),Messages.text("Семья переезжает после завершения строительства")),null);
        if(Enclosures.needed(b))button(m,14,Material.OAK_FENCE,b.enclosureVersion==1?Messages.text("Участок огорожен"):Messages.text("Ограда ещё не завершена"),List.of(Objects.toString(b.damageReason,Messages.text("Материалы и ход работ — в проектах"))),null);
        var required=Jobs.required(b,plugin.content.rules);if(required!=null)button(m,13,Material.STONE_HOE,Messages.text("Рабочее место: ")+plugin.roleName(required),List.of(v.residents.stream().filter(r->r.alive && b.id.equals(r.workplace)).map(r->r.name+" · "+plugin.residentRoleName(v,r)).findFirst().orElse(Messages.text("Вакансия: нужен новый работник"))),null);
        if(b.damaged)button(m,19,Material.ANVIL,Messages.text("Начать восстановление"),List.of(Objects.toString(b.damageReason,Messages.text("Повреждение"))),()->plugin.worldWorks.repair(p,current(p,v.id),id));
        back(m,p,v.id);p.openInventory(m.inventory);
    }
    
    private void commonProject(Player p,Village v,String kind){
        TrustAccess.requireTasks(v,p.getUniqueId(),plugin.content.rules);
        if(kind.equals("workshop") && v.buildings.stream().anyMatch(b->b.kind.startsWith("workshop")))throw new IllegalArgumentException(Messages.text("Мастерская уже существует или заказана"));
        Village next=Database.copy(v);Project q;
        try{q=plugin.worldWorks.planNearby(next,kind);}catch(WorldWorks.SitePending searching){BuildAgenda.request(next,kind);next.planningReason=searching.getMessage();next.developmentReason=searching.getMessage();plugin.transactions.run(p,next,Coins.cloneItems(p.getInventory().getStorageContents()),Messages.text("Сохранена общая строительная цель ")+kind,()->plugin.tell(p,Messages.text("Цель сохранена. Деревня проверит следующие участки; причина видна в состоянии поселения.")));return;}
        BuildAgenda.reserved(next,kind);
        if(!Set.of("SURVEY","SITE_BLOCKED").contains(q.state))for(var e:q.required.entrySet()){int n=Math.min(e.getValue(),Development.availableForBuilding(next,plugin.content.rules,e.getKey(),q.essential));if(n>0){next.stock.take(e.getKey(),n);q.escrow.put(e.getKey(),n);}}
        if(!Set.of("SURVEY","SITE_BLOCKED").contains(q.state) && q.funded()){q.state="QUEUED";q.reason=Messages.text("Очередь строителей");}
        plugin.transactions.run(p,next,Coins.cloneItems(p.getInventory().getStorageContents()),Messages.text("Общий проект ")+kind,()->plugin.tell(p,Messages.text("Проект зарегистрирован. Недостающие материалы можно передавать через меню проектов.")));
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    void giftCatalog(Player p,Village v,int page){
        TrustAccess.requireFriend(v,p.getUniqueId(),plugin.content.rules);var items=new TreeSet<String>();
        for(var item:p.getInventory().getStorageContents())if(item!=null&&Coins.plain(item,item.getType())&&!item.getType().isAir())items.add(item.getType().name());
        var list=items.stream().toList();var m=menu(Messages.text("Подарить ресурсы · ")+(page+1),v);int slot=0;
        for(String item:list.stream().skip((long)page*45).limit(45).toList())button(m,slot++,Material.valueOf(item),Russian.item(item),List.of(Messages.text("На складе: ")+v.stock.get(item),Messages.text("Текущая потребность: ")+plugin.economy.needed(v,item),Messages.text("Без оплаты; доверие только за нужные поставки")),()->giftOffer(p,current(p,v.id),item));
        if(list.isEmpty())button(m,22,Material.BARRIER,Messages.text("Нет обычных ресурсов для передачи"),List.of(Messages.text("Монеты и особые предметы сюда не принимаются")),null);
        if(page>0)button(m,48,Material.ARROW,Messages.text("Назад"),List.of(),()->giftCatalog(p,current(p,v.id),page-1));if(list.size()>(page+1)*45)button(m,50,Material.ARROW,Messages.text("Далее"),List.of(),()->giftCatalog(p,current(p,v.id),page+1));back(m,p,v.id);p.openInventory(m.inventory);
    }
    private void giftOffer(Player p,Village v,String item){TrustAccess.requireFriend(v,p.getUniqueId(),plugin.content.rules);var m=menu(Messages.text("Подарок: ")+Russian.item(item),v);int slot=20;for(int n:new int[]{1,16,64})button(m,slot++,Material.HOPPER,Messages.text("Передать ")+n,List.of(Messages.text("Подарок в общий склад"),Messages.text("Проверка места и доверия до списания")),()->giftTransfer(p,current(p,v.id),item,n));back(m,p,v.id);p.openInventory(m.inventory);}
    void giftTransfer(Player p,Village v,String item,int count){
        var next=Database.copy(v);int needed=plugin.economy.needed(next,item);CommunalStore.gift(next,p.getUniqueId(),item,count,plugin.content.rules);
        var after=Coins.cloneItems(p.getInventory().getStorageContents());Coins.removePlain(after,Material.valueOf(item),count);
        int useful=Math.min(count,needed);if(useful>0){next.relation(p.getUniqueId()).deeds.add("construction");Reputation.award(next,p.getUniqueId(),plugin.content.rules,"donation",Math.min(3,useful/8));}
        plugin.transactions.run(p,next,after,Messages.text("Подарок деревне: ")+item+" × "+count,()->{plugin.tell(p,Messages.text("Подарок принят в общий склад."));giftCatalog(p,plugin.villages.get(v.id),0);});
    }
    
    
    
    
    
    
    
    
}
