package ru.livingvillages.paper;

import ru.livingvillages.i18n.Messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import java.util.*;

/** One command per line, with permission-filtered pages and safe chat suggestions. */
public final class CommandHelp {
    private CommandHelp(){}
    public record Entry(String command,String description,String permission){}
    private static Entry e(String command,String description){return new Entry(command,description,null);}
    private static Entry a(String command,String description){return new Entry(command,description,"livingvillages.admin");}
    public static final List<Entry> ENTRIES=List.of(
        e("/lv menu","Открытые вами деревни: названия, координаты и меню"),
        e("/lv status","Жители, питание, казна и причины остановки стройки"),
        e("/lv claims","Получить награды и возвращённое имущество"),
        new Entry("/lv bypass on|off","Включить или выключить обход защиты блоков","livingvillages.bypass"),
        a("/lv admin","Открыть панель администратора: доверие, законы, NPC"),
        a("/lv create <культура|auto> [планировка]","Создать поселение возле себя после проверки участка"),
        a("/lv coins <бронзовых> [игрок]","Выдать тестовые монеты; сумма в бронзовых единицах"),
        a("/lv pause","Переключить паузу жизни текущей деревни"),
        a("/lv npc status [UUID деревни]","Показать связи и состояние жителей"),
        a("/lv npc repair [UUID деревни]","Проверить связи и восстановить допустимых живых NPC"),
        a("/lv generation","Показать настройки появления и последний отказ"),
        a("/lv reload","Проверить и применить поддерживаемые настройки"),
        a("/lv diagnostics","Показать деревни, NPC и очереди работ"),
        a("/lv operations","Показать операции, требующие ручной сверки"),
        a("/lv resolve <UUID> commit|cancel","После сверки разрешить спорную операцию"),
        e("/lv help [страница]","Открыть эту справку"));
    public static List<Entry> visible(java.util.function.Predicate<String> permission){return ENTRIES.stream().filter(e->e.permission()==null||permission.test(e.permission())).map(e->new Entry(Messages.text(e.command()),Messages.text(e.description()),e.permission())).toList();}
    public static void show(CommandSender sender,int page){
        var entries=visible(sender::hasPermission);int size=7,pages=(entries.size()+size-1)/size;
        if(page<1||page>pages)throw new IllegalArgumentException(Messages.text("Справка: /lv help 1–")+pages);
        sender.sendMessage(Component.text(Messages.text("━━ LivingVillages · команды ")+page+"/"+pages+" ━━",NamedTextColor.GOLD));
        for(var entry:entries.subList((page-1)*size,Math.min(page*size,entries.size()))){
            var parts=new ArrayList<String>();for(String part:entry.command().split(" ")){if(part.startsWith("<")||part.startsWith("[")||part.contains("|"))break;parts.add(part);}String suggestion=String.join(" ",parts);
            sender.sendMessage(Component.text(entry.command(),NamedTextColor.AQUA)
                .clickEvent(ClickEvent.suggestCommand(suggestion+" ")).hoverEvent(HoverEvent.showText(Component.text(Messages.text("Нажмите, чтобы подставить команду"))))
                .append(Component.text(" — ",NamedTextColor.DARK_GRAY)).append(Component.text(entry.description(),NamedTextColor.WHITE)));
        }
        Component footer=Component.text(Messages.text("В деревне: /lv menu · "),NamedTextColor.GRAY);
        if(page>1)footer=footer.append(Component.text(Messages.text("← Назад  "),NamedTextColor.YELLOW).clickEvent(ClickEvent.runCommand("/lv help "+(page-1))));
        if(page<pages)footer=footer.append(Component.text(Messages.text("Далее → /lv help ")+(page+1),NamedTextColor.YELLOW).clickEvent(ClickEvent.runCommand("/lv help "+(page+1))));
        sender.sendMessage(footer);
    }
}
