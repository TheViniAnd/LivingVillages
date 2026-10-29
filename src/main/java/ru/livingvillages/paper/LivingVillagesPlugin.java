package ru.livingvillages.paper;

import ru.livingvillages.i18n.Messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.livingvillages.content.Content;
import ru.livingvillages.domain.*;
import ru.livingvillages.persistence.Database;
import java.util.*;
import java.util.concurrent.*;

public final class LivingVillagesPlugin extends JavaPlugin implements Listener,CommandExecutor,TabCompleter {
    public final Map<UUID,Village> villages=new LinkedHashMap<>();
    public final Coins coins=new Coins();
    public Database db;public Content content;public Economy economy;public Simulation simulation;
    public AdminMenus adminMenus;public WarehouseAccess warehouseAccess;
    public Transactions transactions;public WorldWorks worldWorks;public Npcs npcs;public Guards guards;public Menus menus;
    public RegionIntegrations integrations;public HallDisplays hallDisplays;
    public Cartography cartography;public Protection protection;
    GroveVisuals groveVisuals;
    
    private final Map<UUID,Long> lastStep=new HashMap<>(),warnings=new HashMap<>();
    
    ConstructionRecovery constructionRecovery;
    private Distribution distribution;
    LanguageSupport languages;
    public Edition edition(){return distribution==null?Edition.FREE:distribution.edition;}
    public java.nio.file.Path dataDirectory(){return getDataFolder().getParentFile().toPath().resolve("LivingVillages");}
    private final Set<UUID> saving=new HashSet<>(),saveAgain=new HashSet<>();
    private final Deque<UUID> scheduledSaves=new ArrayDeque<>();private long lastSaveNanos=System.nanoTime();
    private boolean ready,stopping;private int cursor,worldCursor;private long pulseNanos,maxPulseNanos;
    @Override public void onEnable(){
        try{distribution=new Distribution(this);distribution.open();content=new Content(this);Messages.use(content.language);}catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,Messages.text("Некорректные игровые данные: ")+e.getMessage(),e);Bukkit.getPluginManager().disablePlugin(this);return;}
        economy=new Economy(content.rules);simulation=new Simulation(content.rules);db=new Database();
        adminMenus=new AdminMenus(this);warehouseAccess=new WarehouseAccess(this);transactions=new Transactions(this);integrations=new RegionIntegrations(this);worldWorks=new WorldWorks(this);guards=new Guards(this);npcs=new Npcs(this);menus=new Menus(this);hallDisplays=new HallDisplays(this);
        protection=new Protection(this);
        languages=new LanguageSupport(this);constructionRecovery=new ConstructionRecovery(this);cartography=new Cartography(this);groveVisuals=new GroveVisuals(this);
        Objects.requireNonNull(getCommand("lv")).setExecutor(this);getCommand("lv").setTabCompleter(this);
        for(Listener listener:List.of(this,languages,constructionRecovery,worldWorks,npcs,menus,protection,hallDisplays,guards,warehouseAccess,adminMenus))Bukkit.getPluginManager().registerEvents(listener,this);
        db.open(dataDirectory().resolve("livingvillages.db")).thenCompose(loaded->{
            return db.pending(null).thenApply(pending->{for(var op:pending)if(op.aggregate()!=null)Database.decode(op.aggregate());return Map.entry(loaded,pending);});
        }).whenComplete((data,error)->sync(()->{
            if(error!=null){getLogger().severe(Messages.text("Ошибка SQLite: ")+error);Bukkit.getPluginManager().disablePlugin(this);return;}
            if(edition()==Edition.FREE && data.getValue().stream().anyMatch(op->op.aggregate()!=null && edition().incompatible(Database.decode(op.aggregate()))!=null)){getLogger().severe(Messages.text("Free не открывает незавершённые операции Premium. Используйте Premium для восстановления."));Bukkit.getPluginManager().disablePlugin(this);return;}
            for(Village v:data.getKey()){LanguageSupport.reasons(v);String reason=edition().incompatible(v);if(reason!=null){getLogger().severe(Messages.text("Free не открывает сохранения Premium: ")+v.id+" — "+reason+Messages.text(". Данные не изменены."));Bukkit.getPluginManager().disablePlugin(this);return;}v.edition=edition();constructionRecovery.loaded(v);}
            if(edition()==Edition.FREE && data.getKey().stream().collect(java.util.stream.Collectors.groupingBy(v->v.world,java.util.stream.Collectors.counting())).values().stream().anyMatch(n->n>5)){getLogger().severe(Messages.text("Free: больше пяти сохранённых деревень в мире; используйте Premium. Данные не изменены."));Bukkit.getPluginManager().disablePlugin(this);return;}
            try{distribution.record();}catch(Exception e){getLogger().severe(Messages.text("Не сохранён профиль данных: ")+e);Bukkit.getPluginManager().disablePlugin(this);return;}
            Names.seed(data.getKey(),content.rules);
            for(Village v:data.getKey()){
                if(!content.cultures.containsKey(v.culture)){getLogger().severe(Messages.text("Отсутствует сохранённая культура: ")+v.culture);Bukkit.getPluginManager().disablePlugin(this);return;}
                Names.assign(v,content.rules);World loadedWorld=Bukkit.getWorld(v.world);if(loadedWorld!=null)v.worldName=loadedWorld.getName();for(var r:v.residents)r.entity=null;for(var b:v.buildings)content.applyDefinition(v,b);Housing.reconcile(v,content.rules.familySize);Jobs.reconcile(v,content.rules);Vitals.reconcile(v,content.rules);Jobs.ensureBuilders(v,content.rules);villages.put(v.id,v);lastStep.put(v.id,System.nanoTime());
            }
            for(var op:data.getValue()){transactions.unresolved.put(op.id(),op);transactions.reserve(op);if(op.village()!=null)transactions.villages.add(op.village());}
            for(Village v:villages.values())if(!transactions.busy(v))save(v);ready=true;npcs.reconcileLoaded();for(Village v:villages.values()){restoreMap(v);installTownMap(v);World world=Bukkit.getWorld(v.world);if(world!=null)for(var b:v.buildings)if((b.complete || v.projects.stream().anyMatch(p->p.building.equals(b.id) && p.state.equals("SURVEY"))) && !b.resourceSurveyed)b.resourceConfirmed=worldWorks.confirmResource(v,b,world);}for(Player player:Bukkit.getOnlinePlayers()){transactions.recover(player);reloadMaps(player);languages.enqueue(player);}
            Bukkit.getScheduler().runTaskTimer(this,this::pulse,20,content.config.getLong("simulation.pulse-ticks",20));
            Bukkit.getScheduler().runTaskTimer(this,()->npcs.motionPulse(),1,2);
            getLogger().info(Messages.text("LivingVillages готов. Деревень: ")+villages.size()+Messages.text("; незавершённых операций: ")+transactions.unresolved.size());
            if(content.config.getBoolean("debug.server-self-test"))try{var method=Class.forName("ru.livingvillages.paper.ServerChecks",true,getClass().getClassLoader()).getDeclaredMethod("run",LivingVillagesPlugin.class);method.setAccessible(true);method.invoke(null,this);}catch(ClassNotFoundException ignored){getLogger().warning(Messages.text("Проверки разработчика не входят в публичный JAR; параметр debug.server-self-test пропущен."));}catch(ReflectiveOperationException e){getLogger().log(java.util.logging.Level.SEVERE,Messages.text("Не запущены проверки разработчика"),e);}
        }));
    }
    public void sync(Runnable action){if(!stopping && isEnabled())Bukkit.getScheduler().runTask(this,action);}
    private void pulse(){try(var read=Projects.readScope()){pulseBody();geometryCacheHits+=read.hits;geometryCacheBuilds+=read.builds;}}
    public long geometryCacheHits,geometryCacheBuilds;
    private void pulseBody(){
        if(!ready)return;long start=System.nanoTime();List<Village> list=new ArrayList<>(villages.values());
        int budget=content.config.getInt("limits.villages-per-pass",8),remainingBlocks=content.config.getInt("limits.blocks-per-pass",32);
        for(int i=0;i<Math.min(budget,list.size());i++) {
            Village v=list.get(Math.floorMod(cursor++,list.size()));long now=System.nanoTime(),previous=lastStep.getOrDefault(v.id,now);
            if(transactions.busy(v))continue;
            lastStep.put(v.id,now);
            v.underAttack=npcs.danger(v);
            WaterSources.verifyLoaded(this,v);
            simulation.elapse(v,(now-previous)/1_000_000,content.config.getInt("simulation.max-days-pass",1));
            Council.review(v,content.rules);
            worldWorks.inspect(v,4);
            worldWorks.develop(v);
            discover(v);
        }
        
        int worldVisits=Math.min(Math.min(budget,list.size()),remainingBlocks);
        for(int i=0;i<worldVisits;i++){Village v=list.get(Math.floorMod(worldCursor++,list.size()));int quota=(remainingBlocks+worldVisits-i-1)/(worldVisits-i);remainingBlocks-=worldWorks.apply(v,quota);}
        groveVisuals.pulse(remainingBlocks);worldWorks.surveyPulse();worldWorks.resourcePulse();npcs.pulse();hallDisplays.pulse();cartography.pulse();
        adminMenus.pulse();languages.pulse();long saveNow=System.nanoTime();if(saveNow-lastSaveNanos>=content.config.getLong("saving.seconds",30)*1_000_000_000L && scheduledSaves.isEmpty()){lastSaveNanos=saveNow;scheduledSaves.addAll(villages.keySet());}for(int n=0;n<Math.clamp(content.config.getInt("saving.villages-per-pass",4),1,32)&&!scheduledSaves.isEmpty();n++){Village snapshot=villages.get(scheduledSaves.removeFirst());if(snapshot!=null&&!transactions.busy(snapshot))save(snapshot);}
        pulseNanos=System.nanoTime()-start;maxPulseNanos=Math.max(maxPulseNanos,pulseNanos);
    }
    public void announceNearby(Village v){World world=Bukkit.getWorld(v.world);if(world!=null)announceCandidates(v,world.getPlayers());}
    void announceCandidates(Village v,Collection<? extends Player> players){for(Player player:players)if(Discoveries.nearby(v,player.getWorld().getUID(),player.getX(),player.getZ(),content.config.getDouble("discovery.distance",64)))discoverPlayer(player,v);}
    private void discover(Village v){
        World world=Bukkit.getWorld(v.world);if(world==null)return;
        for(Player player:world.getPlayers())if(Math.hypot(player.getLocation().getX()-v.x,player.getLocation().getZ()-v.z)<=content.config.getInt("discovery.distance",64)){
            discoverPlayer(player,v);
            if(content.rules.foodUnits(v)<content.rules.dailyFood(v)*content.rules.warningDays && warnings.getOrDefault(player.getUniqueId(),-1L)!=v.days){warnings.put(player.getUniqueId(),v.days);tell(player,v.name+Messages.text(": продовольствия меньше чем на ")+content.rules.warningDays+Messages.text(" суток. Нужна помощь."));}
        }
    }
    public void discoverPlayer(Player player,Village v){distribution.notice(player);if(transactions.busy(v))return;boolean first=Discoveries.record(v,player.getUniqueId(),player.getName());if(first){tell(player,Discoveries.message(v,content.cultures.get(v.culture).name(),player.getWorld().getName()));save(v);}}
    
    public void save(Village v){
        if(db==null||transactions.busy(v)||stopping)return;
        if(!saving.add(v.id)){saveAgain.add(v.id);return;}
        db.save(Database.encode(v)).whenComplete((done,error)->sync(()->{
            saving.remove(v.id);Village current=villages.get(v.id);
            if(error!=null)getLogger().severe(Messages.text("Сохранение ")+v.id+": "+error);
            if(saveAgain.remove(v.id)&&current!=null&&!transactions.busy(current))save(current);
        }));
    }
    void publish(Village next){LanguageSupport.reasons(next);next.edition=edition();constructionRecovery.loaded(next);Names.assign(next,content.rules);for(var b:next.buildings)content.applyDefinition(next,b);Housing.reconcile(next,content.rules.familySize);Jobs.reconcile(next,content.rules);Vitals.reconcile(next,content.rules);villages.put(next.id,next);}
    @Override public void onDisable(){
        stopping=true;ready=false;
        if(npcs!=null)npcs.removeAll();
        if(db!=null){try{for(Village v:villages.values())if(!transactions.busy(v))try{db.save(Database.encode(v)).orTimeout(10,TimeUnit.SECONDS).join();}catch(Exception error){getLogger().severe(Messages.text("Не записана деревня при остановке ")+v.id+": "+error);}}finally{db.close();}}if(distribution!=null)distribution.close();
    }
    public Village at(Location l){return villages.values().stream().filter(v->Territory.inside(v,l.getWorld().getUID(),l.getX(),l.getZ())).min(Comparator.comparingDouble(v->v.buildings.stream().anyMatch(b->Territory.plot(b,l.getBlockX(),l.getBlockZ()))?-1:Math.hypot(v.x-l.getX(),v.z-l.getZ()))).orElse(null);}
    public Village local(Player p){Village v=at(p.getLocation());if(v==null)throw new IllegalArgumentException(Messages.text("Войдите на территорию деревни. Названия и координаты: /lv menu"));if(transactions.busy(v) || transactions.players.contains(p.getUniqueId()))throw new IllegalArgumentException(Messages.text("Сначала дождитесь завершения операции"));discoverPlayer(p,v);return v;}
    public Village nearest(Player p){return villages.values().stream().filter(v->v.world.equals(p.getWorld().getUID()) && Math.hypot(p.getLocation().getX()-v.x,p.getLocation().getZ()-v.z)<=v.radius+64).min(Comparator.comparingDouble(v->Math.hypot(p.getLocation().getX()-v.x,p.getLocation().getZ()-v.z))).orElseThrow(()->new IllegalArgumentException(Messages.text("Рядом нет поселения")));}
    public void tell(CommandSender who,String text){who.sendMessage(Component.text(Messages.text("[Поселения] "),NamedTextColor.GOLD).append(Component.text(text,NamedTextColor.WHITE)));}
    public void failure(CommandSender who,Throwable e){getLogger().severe(e.toString());tell(who,Messages.text("Ошибка сохранения. Операция заблокирована до сверки; подробности в журнале сервера."));}
    public String roleName(Village.Role role){return switch(role){case ELDER->Messages.text("староста");case MERCHANT->Messages.text("торговец");case BUILDER->Messages.text("строитель");case FARMER->Messages.text("фермер");case LUMBERJACK->Messages.text("лесоруб");case FISHER->Messages.text("рыбак");case ARTISAN->Messages.text("ремесленник");case GUARD->Messages.text("охранник");case MINER->Messages.text("рудокоп");case STONEMASON->Messages.text("камнетёс");case SMITH->Messages.text("кузнец");case HERDER->Messages.text("скотовод");case BEEKEEPER->Messages.text("пасечник");case HUNTER->Messages.text("охотник");case BAKER->Messages.text("пекарь");case WATER_CARRIER->Messages.text("водонос");case CHILD->Messages.text("ребёнок");};}
    public String residentRoleName(Village v,Village.Resident r){if(!v.adult(r))return Messages.text("ребёнок (ещё ")+Population.maturityMinutes(v,r,content.rules)+Messages.text(" мин работы деревни)");var job=Jobs.activity(v,r,content.rules);return job!=r.role && (r.role==Village.Role.ELDER || r.role==Village.Role.MERCHANT)?roleName(r.role)+" / "+roleName(job):roleName(job);}
    @EventHandler public void join(PlayerJoinEvent e){if(ready){transactions.recover(e.getPlayer());reloadMaps(e.getPlayer());languages.enqueue(e.getPlayer());distribution.notice(e.getPlayer());}}
    public void giveMap(Player p,Village v){
        NamespacedKey key=new NamespacedKey("livingvillages","map");
        for(ItemStack item:p.getInventory().getStorageContents())if(item!=null && item.hasItemMeta() && v.id.toString().equals(item.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING))){tell(p,Messages.text("У вас уже есть карта этого поселения."));return;}
        if(p.getInventory().firstEmpty()<0)throw new IllegalArgumentException(Messages.text("Освободите ячейку для карты"));
        MapView view=mapView(v);
        ItemStack map=new ItemStack(Material.FILLED_MAP);MapMeta meta=(MapMeta)map.getItemMeta();meta.setMapView(view);TooltipText.apply(meta,Messages.text("План: ")+v.name,NamedTextColor.WHITE,List.of(Messages.text("Координаты: ")+v.x+" "+v.z));meta.getPersistentDataContainer().set(key,PersistentDataType.STRING,v.id.toString());map.setItemMeta(meta);
        ItemStack[] after=Coins.cloneItems(p.getInventory().getStorageContents());Coins.add(after,map);transactions.run(p,Database.copy(v),after,Messages.text("Выдача карты"),()->tell(p,Messages.text("Карта: красная линия — граница деревни; зелёный — здание; охра — стройка. Фон показывает обследованные окрестности.")));
    }
    private MapView mapView(Village v){
        MapView view=v.mapId==null?null:Bukkit.getMap(v.mapId);
        if(view==null){view=Bukkit.createMap(Objects.requireNonNull(Bukkit.getWorld(v.world)));v.mapId=view.getId();}
        view.setCenterX(v.x);view.setCenterZ(v.z);view.setScale(MapView.Scale.CLOSEST);view.getRenderers().forEach(view::removeRenderer);view.addRenderer(new SettlementMap(this,v.id));return view;
    }
    private void restoreMap(Village v){if(v.mapId!=null && Bukkit.getMap(v.mapId)!=null)mapView(v);}
    public void installTownMap(Village v){
        World w=Bukkit.getWorld(v.world);if(w==null)return;
        var hall=v.buildings.stream().filter(b->b.kind.startsWith("hall") && b.complete).findFirst().orElse(null);if(hall==null)return;
        var anchor=HallDisplays.point(hall,0,1);int x=anchor.x(),y=anchor.y(),z=anchor.z();if(!w.isChunkLoaded(x>>4,z>>4))return;
        NamespacedKey key=new NamespacedKey("livingvillages","town_map");Location l=new Location(w,x+.5,y+.5,z+.5);
        if(w.getNearbyEntities(l,2,2,2).stream().anyMatch(e->v.id.toString().equals(e.getPersistentDataContainer().get(key,PersistentDataType.STRING))))return;
        ItemStack item=new ItemStack(Material.FILLED_MAP);MapMeta meta=(MapMeta)item.getItemMeta();meta.setMapView(mapView(v));meta.itemName(Component.text(Messages.text("План поселения")));item.setItemMeta(meta);
        w.spawn(l,ItemFrame.class,frame->{frame.setFacingDirection(anchor.facing(),true);frame.setFixed(true);frame.setItemDropChance(0);frame.setItem(item);frame.getPersistentDataContainer().set(key,PersistentDataType.STRING,v.id.toString());});
    }
    private void reloadMaps(Player p){
        for(ItemStack item:p.getInventory().getStorageContents())if(item!=null && item.getItemMeta() instanceof MapMeta m && m.hasMapView()){
            String id=m.getPersistentDataContainer().get(new NamespacedKey("livingvillages","map"),PersistentDataType.STRING);
            if(id!=null)try{UUID village=UUID.fromString(id);MapView view=m.getMapView();if(villages.containsKey(village) && view!=null){view.getRenderers().forEach(view::removeRenderer);view.addRenderer(new SettlementMap(this,village));}}catch(IllegalArgumentException ignored){}
        }
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        try{
            if(!ready)throw new IllegalArgumentException(Messages.text("База ещё загружается или плагин остановлен"));
            String sub=args.length==0?"help":args[0].toLowerCase(Locale.ROOT);
            if(sub.equals("edition")){tell(sender,"LivingVillages 0.1. "+Messages.text("Уровни 1–3, до 12 жителей, 5 деревень на мир, Полевичи. Premium: 8 культур, уровни 1–10, личные дома, власть, армии, редкое оружие."));return true;}
            if(sub.equals("help")){if(args.length>2)throw new IllegalArgumentException(Messages.text("/lv help [страница]"));CommandHelp.show(sender,args.length==2?Integer.parseInt(args[1]):1);return true;}
            if(sub.equals("diagnostics")){admin(sender);tell(sender,Messages.text("Деревень: ")+villages.size()+"; NPC: "+npcs.count()+Messages.text("; блоков применено: ")+worldWorks.appliedBlocks+Messages.text("; очередь участков: ")+worldWorks.queueSize()+Messages.text("; таблички обновлены: ")+hallDisplays.updates+Messages.text("; спорных операций: ")+transactions.unresolved.size()+Messages.text("; проход последний/макс мс: ")+String.format(Locale.ROOT,"%.2f/%.2f",pulseNanos/1e6,maxPulseNanos/1e6));tell(sender,Messages.text("Геометрия: повторных чтений из кэша ")+geometryCacheHits+Messages.text("; расчётов ")+geometryCacheBuilds+Messages.text("; запросов маршрутов ")+npcs.pathsRequested);return true;}
            if(sub.equals("generation")){admin(sender);tell(sender,Messages.text("Автопоявление: ")+content.config.getBoolean("generation.enabled")+Messages.text("; миры: ")+content.config.getStringList("generation.worlds")+Messages.text("; шанс: ")+content.config.getDouble("generation.chance")+Messages.text("; кандидатов: ")+worldWorks.automaticCandidates+Messages.text("; очередь: ")+worldWorks.queueSize()+Messages.text("; отказов: ")+worldWorks.placementRejected);tell(sender,Messages.text("Последняя причина: ")+worldWorks.lastPlacementReason+Messages.text(". Автопоявление работает на новых исследуемых чанках. Ручное создание: /lv create auto"));return true;}
            if(sub.equals("reload")){admin(sender);String languageOnly=content.languageOnlyCandidate(this);if(languageOnly!=null){if(!transactions.players.isEmpty()||!transactions.unresolved.isEmpty())throw new IllegalArgumentException(Messages.text("Сначала дождитесь завершения операции"));content.selectLanguage(languageOnly);Messages.use(languageOnly);languages.refresh();tell(sender,Messages.text("Настройки проверены и применены. Существующие чертежи и номиналы сохранены."));return true;}Content next=new Content(this);if(next.config.getLong("simulation.pulse-ticks",20)!=content.config.getLong("simulation.pulse-ticks",20))throw new IllegalArgumentException(Messages.text("Период прохода симуляции меняется только с перезапуском"));if(next.rules.dayMillis!=content.rules.dayMillis)throw new IllegalArgumentException(Messages.text("Длительность дня меняется только с перезапуском"));for(Village v:villages.values())if(!next.cultures.containsKey(v.culture))throw new IllegalArgumentException(Messages.text("Нельзя удалить используемую культуру ")+v.culture);boolean languageChanged=!next.language.equals(content.language);if(languageChanged&&(!transactions.players.isEmpty()||!transactions.unresolved.isEmpty()))throw new IllegalArgumentException(Messages.text("Сначала дождитесь завершения операции"));Names.seed(villages.values(),next.rules);content=next;Messages.use(next.language);if(languageChanged)languages.refresh();economy=new Economy(next.rules);simulation=new Simulation(next.rules);tell(sender,Messages.text("Настройки проверены и применены. Существующие чертежи и номиналы сохранены."));return true;}
            if(sub.equals("operations")){admin(sender);if(transactions.unresolved.isEmpty())tell(sender,Messages.text("Незавершённых операций нет."));for(var op:transactions.unresolved.values())tell(sender,op.id()+Messages.text(" игрок=")+op.player()+Messages.text(" деревня=")+op.village()+" "+op.note());return true;}
            if(sub.equals("coins")){admin(sender);if(args.length<2)throw new IllegalArgumentException(Messages.text("/lv coins <бронзовых> [игрок]"));Player target=args.length>2?Bukkit.getPlayerExact(args[2]):player(sender);if(target==null)throw new IllegalArgumentException(Messages.text("Игрок должен быть в сети"));long amount=Money.checked(Long.parseLong(args[1]));ItemStack[] after=coins.balance(target.getInventory().getStorageContents(),amount);transactions.run(target,null,after,Messages.text("Административная выдача ")+amount,()->tell(target,Messages.text("Выдано: ")+Money.describe(amount)));return true;}
            Player p=player(sender);
            if(sub.equals("admin")){adminMenus.directory(p,0);return true;}
            if(sub.equals("menu")){menus.directory(p,0);return true;}
            if(sub.equals("resolve")){admin(sender);if(args.length!=3 || !List.of("commit","cancel").contains(args[2]))throw new IllegalArgumentException(Messages.text("/lv resolve <операция UUID> commit|cancel после ручной сверки инвентаря"));var op=transactions.unresolved.get(UUID.fromString(args[1]));if(op==null)throw new IllegalArgumentException(Messages.text("Нет такой операции"));transactions.resolve(op,args[2].equals("commit"),p);return true;}
            if(sub.equals("create")){admin(sender);if(args.length>3)throw new IllegalArgumentException(Messages.text("/lv create <культура|auto> [random|")+String.join("|",content.layouts.keySet())+"]");worldWorks.create(p,args.length>=2?args[1]:"auto",args.length==3?args[2]:null);return true;}
            if(sub.equals("npc")){admin(sender);String mode=args.length>1?args[1]:"status";Village village=args.length>2?villages.get(UUID.fromString(args[2])):nearest(p);if(village==null)throw new IllegalArgumentException(Messages.text("Деревня не найдена"));if(!mode.equals("status") && !mode.equals("repair"))throw new IllegalArgumentException(Messages.text("/lv npc status|repair [UUID деревни]"));if(mode.equals("repair")){npcs.repair(village);tell(sender,Messages.text("Связи проверены. Безопасное появление живых жителей выполняется по очереди, без загрузки удалённых чанков."));}for(String line:npcs.report(village))tell(sender,line);return true;}
            if(sub.equals("bypass")){if(args.length==1){tell(p,Messages.text("Обход защиты блоков: ")+(protection.bypass(p)?Messages.text("включён"):Messages.text("выключен"))+". /lv bypass on|off");return true;}if(args.length!=2 || !List.of("on","off").contains(args[1]))throw new IllegalArgumentException("/lv bypass on|off");protection.setBypass(p,args[1].equals("on"));tell(p,Messages.text("Обход защиты блоков: ")+(protection.bypass(p)?Messages.text("включён"):Messages.text("выключен")));return true;}
            Village v=local(p);
            switch(sub){
                case "claims"->menus.claim(p,v);
                case "status"->{tell(p,Messages.text("Ремесло: ")+v.supplyReason+Messages.text("; цикл ")+(content.rules.timedCrafting?content.rules.craftIntervalMillis/1000+Messages.text(" сек."):Messages.text("раз в день"))+Messages.text("; самоснабжение: ")+content.rules.autonomy);tell(p,Messages.text("Пауза: ")+v.paused+Messages.text("; время мира: ")+p.getWorld().getTime()+Messages.text(". Начальные здания строятся без ночного ожидания."));tell(p,v.name+" UUID="+v.id+Messages.text("; жителей=")+v.living()+Messages.text("; питание=")+v.nutrition+Messages.text("; казна=")+Money.describe(v.treasury)+Messages.text("; запас еды=")+content.rules.foodUnits(v));for(var q:v.projects)if(q.active())tell(p,q.kind+" "+q.state+" "+q.index+"/"+q.blocks.size()+" · "+q.reason);}
                case "pause"->{admin(sender);v.paused=!v.paused;save(v);tell(p,Messages.text("Пауза симуляции: ")+v.paused);}
                default->throw new IllegalArgumentException(Messages.text("Неизвестная команда. /lv help"));
            }
        }catch(IllegalArgumentException e){tell(sender,e.getMessage()==null?Messages.text("Некорректный аргумент"):e.getMessage());}catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,Messages.text("Ошибка команды"),e);tell(sender,Messages.text("Ошибка; подробности в журнале сервера."));}return true;
    }
    private static Player player(CommandSender s){if(s instanceof Player p)return p;throw new IllegalArgumentException(Messages.text("Эта команда выполняется игроком"));}
    private static void admin(CommandSender s){if(!s.hasPermission("livingvillages.admin"))throw new IllegalArgumentException(Messages.text("Требуется livingvillages.admin"));}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String alias,String[] args){
 if(args.length==2&&args[0].equalsIgnoreCase("create")&&s.hasPermission("livingvillages.admin"))return List.of("auto","polevichi").stream().filter(k->k.startsWith(args[1])).toList();
 if(args.length==1)return List.of("help","edition","menu","status","claims","admin","create","coins","pause","npc","reload","diagnostics","operations","resolve","generation","bypass").stream().filter(k->k.startsWith(args[0])).toList();return List.of();
}
}
