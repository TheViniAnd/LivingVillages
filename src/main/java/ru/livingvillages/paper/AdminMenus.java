package ru.livingvillages.paper;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import ru.livingvillages.domain.*;
import ru.livingvillages.persistence.Database;
import ru.livingvillages.i18n.Messages;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Free administrator tools: inspected villages, trust, pause, NPC reconciliation and reload. */
public final class AdminMenus implements Listener {
    private final LivingVillagesPlugin plugin;
    record Input(UUID village,UUID target,long expires){}
    final Map<UUID,Input> inputs=new ConcurrentHashMap<>();
    private static final class Panel implements InventoryHolder {Inventory inventory;Map<Integer,Runnable> actions=new HashMap<>();public Inventory getInventory(){return inventory;}}
    AdminMenus(LivingVillagesPlugin plugin){this.plugin=plugin;}
    private static void require(Player p){if(!p.hasPermission("livingvillages.admin"))throw new IllegalArgumentException(Messages.text("Требуется livingvillages.admin"));}
    private Panel panel(String title){var m=new Panel();m.inventory=Bukkit.createInventory(m,54,TooltipText.title(title));return m;}
    private void button(Panel m,int slot,Material material,String name,List<String> lore,Runnable action){var item=new ItemStack(material);var meta=item.getItemMeta();TooltipText.apply(meta,name,NamedTextColor.GOLD,lore);item.setItemMeta(meta);m.inventory.setItem(slot,item);if(action!=null)m.actions.put(slot,action);}
    private Village current(Player p,UUID id){require(p);var v=plugin.villages.get(id);if(v==null)throw new IllegalArgumentException(Messages.text("Деревня не найдена"));if(plugin.transactions.busy(v)||plugin.transactions.players.contains(p.getUniqueId()))throw new IllegalArgumentException(Messages.text("Изменение ещё сохраняется"));return v;}
    public void directory(Player p,int page){require(p);var rows=new ArrayList<>(plugin.villages.values());int pages=Math.max(1,(rows.size()+44)/45),selected=Math.clamp(page,0,pages-1);var m=panel(Messages.text("Админ · деревни ")+(selected+1)+"/"+pages);int slot=0;for(var v:rows.stream().skip((long)selected*45).limit(45).toList())button(m,slot++,Material.BELL,v.name,List.of(Messages.text("Координаты: ")+v.x+" "+v.z,Messages.text("Мир: ")+v.worldName),()->village(p,v.id));if(selected>0)button(m,48,Material.ARROW,Messages.text("Назад"),List.of(),()->directory(p,selected-1));if(selected+1<pages)button(m,50,Material.ARROW,Messages.text("Далее"),List.of(),()->directory(p,selected+1));p.openInventory(m.inventory);}
    public void village(Player p,UUID id){var v=current(p,id);var m=panel(Messages.text("Админ · ")+v.name);
        button(m,10,Material.BOOK,Messages.text("Состояние деревни"),List.of(Messages.text("Жители: ")+v.living(),Messages.text("Казна: ")+Money.describe(v.treasury),Messages.text("Склад: ")+v.stock.size(),Messages.text("Ремесло: ")+v.supplyReason),null);
        button(m,11,Material.EMERALD,Messages.text("Доверие: себе"),List.of(Messages.text("Сейчас: ")+v.relation(p.getUniqueId()).trust),()->trust(p,id,p.getUniqueId()));
        button(m,12,Material.NAME_TAG,Messages.text("Доверие: другому игроку"),List.of(Messages.text("Игрок в сети или UUID")),()->begin(p,id,null));
        button(m,13,v.paused?Material.LIME_DYE:Material.RED_DYE,v.paused?Messages.text("Продолжить симуляцию"):Messages.text("Поставить на паузу"),List.of(Messages.text("Изменение записывается в журнал")),()->{var next=Database.copy(current(p,id));next.paused=!next.paused;commit(p,next,"ADMIN pause "+next.paused,()->village(p,id));});
        button(m,14,Material.BELL,Messages.text("Проверить связи NPC"),List.of(Messages.text("Только допустимые живые записи")),()->{plugin.npcs.repair(current(p,id));plugin.tell(p,Messages.text("Связи NPC проверены."));});
        button(m,15,Material.COMPARATOR,Messages.text("Перезагрузить настройки"),List.of(Messages.text("Проверка YAML; ошибка сохраняет текущие параметры")),()->plugin.onCommand(p,plugin.getCommand("lv"),"lv",new String[]{"reload"}));
        button(m,49,Material.ARROW,Messages.text("Все деревни"),List.of(),()->directory(p,0));p.openInventory(m.inventory);
    }
    private void trust(Player p,UUID id,UUID target){var v=current(p,id);int old=v.relation(target).trust;var m=panel(Messages.text("Админ · доверие"));button(m,4,Material.PAPER,Messages.text("Доверие: ")+old,List.of("UUID: "+target,Messages.text("Диапазон: -1000…1000")),null);int slot=19;for(int delta:new int[]{-100,-10,10,100}){int value=Math.clamp(old+delta,-1000,1000);button(m,slot++,delta>0?Material.LIME_DYE:Material.RED_DYE,(delta>0?"+":"")+delta,List.of(Messages.text("Будет: ")+value),()->confirmation(p,id,target,old,value));}button(m,31,Material.NAME_TAG,Messages.text("Ввести точное значение"),List.of(Messages.text("Личный ввод в чат и подтверждение")),()->begin(p,id,target));button(m,49,Material.ARROW,Messages.text("Назад"),List.of(),()->village(p,id));p.openInventory(m.inventory);}
    private void begin(Player p,UUID id,UUID target){current(p,id);inputs.put(p.getUniqueId(),new Input(id,target,System.nanoTime()+60_000_000_000L));p.closeInventory();plugin.tell(p,Messages.text(target==null?"Введите имя игрока в сети или UUID. Для отмены: отмена.":"Введите доверие от -1000 до 1000. Для отмены: отмена."));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void chat(AsyncChatEvent event){var input=inputs.remove(event.getPlayer().getUniqueId());if(input==null)return;event.setCancelled(true);String text=PlainTextComponentSerializer.plainText().serialize(event.message()).strip();plugin.sync(()->accept(event.getPlayer(),input,text));}
    private void accept(Player p,Input input,String text){try{var v=current(p,input.village());if(System.nanoTime()>input.expires())throw new IllegalArgumentException(Messages.text("Время ввода истекло"));if(text.equalsIgnoreCase("cancel")||text.equalsIgnoreCase("отмена"))return;if(input.target()==null){var online=Bukkit.getPlayerExact(text);trust(p,v.id,online==null?UUID.fromString(text):online.getUniqueId());}else{int value=Integer.parseInt(text);if(value<-1000||value>1000)throw new IllegalArgumentException(Messages.text("Допустимо -1000…1000"));confirmation(p,v.id,input.target(),v.relation(input.target()).trust,value);}}catch(IllegalArgumentException e){plugin.tell(p,Messages.text("Некорректное значение"));}}
    private void confirmation(Player p,UUID id,UUID target,int old,int value){current(p,id);var m=panel(Messages.text("Подтвердить доверие"));button(m,22,Material.LIME_DYE,Messages.text("Применить"),List.of(Messages.text("Доверие: ")+old+" → "+value,"UUID: "+target),()->{var next=Database.copy(current(p,id));if(next.relation(target).trust!=old)throw new IllegalArgumentException(Messages.text("Доверие изменилось; откройте меню снова"));next.relation(target).trust=value;commit(p,next,"ADMIN trust "+target+" "+old+" -> "+value,()->trust(p,id,target));});button(m,49,Material.RED_DYE,Messages.text("Отмена"),List.of(),()->trust(p,id,target));p.openInventory(m.inventory);}
    private void commit(Player p,Village next,String note,Runnable success){plugin.transactions.run(p,next,Coins.cloneItems(p.getInventory().getStorageContents()),note,success);}
    @EventHandler(priority=EventPriority.HIGHEST)public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Panel panel))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||e.getClick()!=ClickType.LEFT)return;var action=panel.actions.get(e.getRawSlot());if(action!=null)try(var read=Projects.readScope()){require(p);action.run();}catch(IllegalArgumentException denied){plugin.tell(p,denied.getMessage());}}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Panel)e.setCancelled(true);}
    public void pulse(){long now=System.nanoTime();inputs.entrySet().removeIf(e->now>e.getValue().expires());}
    @EventHandler public void quit(PlayerQuitEvent e){inputs.remove(e.getPlayer().getUniqueId());}
}
