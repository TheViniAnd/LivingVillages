package ru.livingvillages.paper;

import ru.livingvillages.i18n.Messages;

import ru.livingvillages.domain.Edition;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.Properties;

/** Shared storage identity, exclusive ownership and gentle private Free notices. */
final class Distribution implements AutoCloseable {
    private final LivingVillagesPlugin plugin;
    final Edition edition;
    private FileChannel channel;private FileLock lock;
    Distribution(LivingVillagesPlugin plugin){this.plugin=plugin;edition=Edition.FREE;}
    void open() throws Exception {
        String other="LivingVillagesPremium";
        if(Bukkit.getPluginManager().getPlugin(other)!=null)throw new IllegalStateException(Messages.text("Установлены обе версии LivingVillages. Оставьте один JAR и перезапустите сервер."));
        Files.createDirectories(plugin.dataDirectory());channel=FileChannel.open(plugin.dataDirectory().resolve("storage.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);lock=channel.tryLock();if(lock==null)throw new IllegalStateException(Messages.text("Папка LivingVillages уже открыта другим сервером"));
        Path state=plugin.dataDirectory().resolve("edition-state.txt");if(edition==Edition.FREE&&Files.exists(plugin.dataDirectory().resolve("livingvillages.db"))&&(!Files.exists(state)||!Files.readString(state).trim().equals("FREE")))throw new IllegalStateException(Messages.text("Существующая база не помечена как Free. Используйте Premium для прежних сохранений или отдельную чистую папку. Файлы не изменены."));if(edition==Edition.FREE&&Files.exists(state)&&Files.readString(state).trim().equals("PREMIUM"))throw new IllegalStateException(Messages.text("Это сохранения Premium. Бесплатная версия их не изменяет. Используйте Premium или отдельную чистую папку LivingVillages."));
        if(edition==Edition.FREE&&!Files.exists(plugin.dataDirectory().resolve("livingvillages.db"))&&!Files.exists(state))record();
    }
    void record() throws Exception {Path tmp=plugin.dataDirectory().resolve("edition-state.tmp"),out=plugin.dataDirectory().resolve("edition-state.txt");Files.writeString(tmp,edition.name()+"\n");Files.move(tmp,out,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
    void notice(Player p){if(edition!=Edition.FREE||!plugin.content.config.getBoolean("edition.reminders-enabled",true))return;long now=System.currentTimeMillis(),interval=Math.max(900,Math.min(86400,plugin.content.config.getLong("edition.reminder-seconds",7200)))*1000;var key=new NamespacedKey("livingvillages","premium_notice_at");long previous=p.getPersistentDataContainer().getOrDefault(key,PersistentDataType.LONG,0L);if(now>=previous&&now-previous<interval)return;p.getPersistentDataContainer().set(key,PersistentDataType.LONG,now);plugin.tell(p,Messages.text("LivingVillages: развитие до уровня 3, 12 жителей и 5 деревень на мир. В Premium — 8 культур, 10 уровней, власть, личные дома и армии. /lv edition"));}
    @Override public void close(){try{if(lock!=null&&lock.isValid())lock.release();if(channel!=null)channel.close();}catch(java.io.IOException e){plugin.getLogger().severe(Messages.text("Не закрыта блокировка данных: ")+e);}}
}
