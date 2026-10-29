package ru.livingvillages.paper;

import ru.livingvillages.i18n.Messages;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.world.ChunkLoadEvent;
import ru.livingvillages.content.Content;
import ru.livingvillages.domain.*;
import ru.livingvillages.persistence.Database;
import java.util.*;
import java.util.function.Consumer;
import static ru.livingvillages.domain.Village.*;

public final class WorldWorks implements Listener {
    
    private final LivingVillagesPlugin plugin;
    private final Deque<Survey> surveys=new ArrayDeque<>();
    private final Set<String> fresh=new LinkedHashSet<>();
    private final Set<UUID> freshPlans=new HashSet<>();
    private final Map<UUID,Village> creationReservations=new HashMap<>();
    private final Terrain terrain;
    private final StreetWorks lighting;
    private final Map<UUID,Long> nextRepair=new HashMap<>(),nextEnclosure=new HashMap<>(),nextWaterSearch=new HashMap<>();
    public long placementRejected,automaticCandidates;
    public String lastPlacementReason=Messages.text("Пока нет отказов");
    
    
    private final NamespacedKey newLandKey=new NamespacedKey("livingvillages","new_land");
    private final Set<UUID> pendingCreation=new HashSet<>();
    private final Map<UUID,Integer> inspections=new HashMap<>();
    private final Map<UUID,Integer> inspectionProjects=new HashMap<>();
    private final Map<UUID,Long> nextDevelopment=new HashMap<>();
    private final Random random=new Random();
    public long appliedBlocks, surveyNanos;
    public WorldWorks(LivingVillagesPlugin plugin){this.plugin=plugin;terrain=new Terrain(plugin);lighting=new StreetWorks(plugin,this);}
    public int queueSize(){return surveys.size();}
    void holdClaim(Village v){creationReservations.put(v.id,v);}
    void releaseClaim(UUID id){creationReservations.remove(id);}
    boolean reservedAt(Location at){return creationReservations.values().stream().anyMatch(v->Territory.inside(v,at.getWorld().getUID(),at.getX(),at.getZ()));}
    void createTest(World world,int x,int z,String culture,Consumer<String> reply){createTest(world,x,z,culture,"square",reply);}
    void createTest(World world,int x,int z,String culture,String layout,Consumer<String> reply){Survey survey=new Survey(world,x,z,culture,true,reply);survey.layoutChoice=layout;surveys.addFirst(survey);}
    private String chunkKey(World w,int x,int z){return w.getUID()+":"+x+":"+z;}
    @EventHandler(ignoreCancelled=true) public void generated(ChunkLoadEvent event) {
        if(!event.isNewChunk())return;
        var c=plugin.content.config;
        World w=event.getWorld();
        event.getChunk().getPersistentDataContainer().set(newLandKey,org.bukkit.persistence.PersistentDataType.BYTE,(byte)2);
        fresh.add(chunkKey(w,event.getChunk().getX(),event.getChunk().getZ()));if(fresh.size()>8192)fresh.remove(fresh.iterator().next());
        if(!c.getBoolean("generation.enabled",true) || !c.getStringList("generation.worlds").contains(w.getName()) || surveys.size()>=c.getInt("limits.candidates",64))return;
        if(random.nextDouble()>=c.getDouble("generation.chance",.015))return;
        int x=event.getChunk().getX()*16+8,z=event.getChunk().getZ()*16+8;
        automaticCandidates++;surveys.add(new Survey(w,x,z,null,false,msg->{}));
    }
    public void create(Player player,String culture){create(player,culture,null);}
    public void create(Player player,String culture,String layout) {
        if(layout!=null && !layout.equals("random") && !plugin.content.layouts.containsKey(layout))throw new IllegalArgumentException(Messages.text("Неизвестная планировка: ")+layout);
        if("auto".equals(culture))culture=null;
        if(culture!=null && !plugin.content.cultures.containsKey(culture))throw new IllegalArgumentException(Messages.text("Неизвестная культура: ")+culture);
        if(surveys.size()>=plugin.content.config.getInt("limits.candidates",64))throw new IllegalArgumentException(Messages.text("Очередь проверки занята"));
        if(!pendingCreation.add(player.getUniqueId()))throw new IllegalArgumentException(Messages.text("Ваш участок уже проверяется"));
        Location l=player.getLocation();
        Survey survey=new Survey(player.getWorld(),l.getBlockX(),l.getBlockZ(),culture,true,msg->{pendingCreation.remove(player.getUniqueId());plugin.tell(player,msg);});survey.layoutChoice=layout;surveys.addFirst(survey);
        plugin.tell(player,Messages.text("Проверяю территорию по частям. Оставайтесь рядом, чтобы чанки были загружены."));
    }
    
    
    
    private boolean freshChunk(World w,int x,int z){return fresh.contains(chunkKey(w,x,z)) || w.isChunkLoaded(x,z) && Byte.valueOf((byte)2).equals(w.getChunkAt(x,z).getPersistentDataContainer().get(newLandKey,org.bukkit.persistence.PersistentDataType.BYTE));}
    void markEdited(org.bukkit.block.Block block){World world=block.getWorld();int x=block.getX()>>4,z=block.getZ()>>4;fresh.remove(chunkKey(world,x,z));if(world.isChunkLoaded(x,z))world.getChunkAt(x,z).getPersistentDataContainer().remove(newLandKey);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void playerPlace(org.bukkit.event.block.BlockPlaceEvent e){markEdited(e.getBlockPlaced());if(e instanceof org.bukkit.event.block.BlockMultiPlaceEvent multi)multi.getReplacedBlockStates().forEach(b->markEdited(b.getBlock()));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void playerBreak(org.bukkit.event.block.BlockBreakEvent e){markEdited(e.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void bucketIn(org.bukkit.event.player.PlayerBucketEmptyEvent e){markEdited(e.getBlock().getRelative(e.getBlockFace()));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void bucketOut(org.bukkit.event.player.PlayerBucketFillEvent e){markEdited(e.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void alteredByExplosion(org.bukkit.event.entity.EntityExplodeEvent e){e.blockList().forEach(this::markEdited);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void alteredByBlockExplosion(org.bukkit.event.block.BlockExplodeEvent e){e.blockList().forEach(this::markEdited);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void absorbed(org.bukkit.event.block.SpongeAbsorbEvent e){e.getBlocks().forEach(b->markEdited(b.getBlock()));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void portalLand(org.bukkit.event.world.PortalCreateEvent e){e.getBlocks().forEach(b->markEdited(b.getBlock()));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void pushed(org.bukkit.event.block.BlockPistonExtendEvent e){markEdited(e.getBlock());e.getBlocks().forEach(b->{markEdited(b);markEdited(b.getRelative(e.getDirection()));});}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void pulled(org.bukkit.event.block.BlockPistonRetractEvent e){markEdited(e.getBlock());e.getBlocks().forEach(b->{markEdited(b);markEdited(b.getRelative(e.getDirection()));});}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void fertilizedLand(org.bukkit.event.block.BlockFertilizeEvent e){e.getBlocks().forEach(b->markEdited(b.getBlock()));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void grownLand(org.bukkit.event.world.StructureGrowEvent e){e.getBlocks().forEach(b->markEdited(b.getBlock()));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void entityLand(org.bukkit.event.entity.EntityChangeBlockEvent e){if(e.getEntity() instanceof Player)markEdited(e.getBlock());}
    
    private static final class ChunksPending extends IllegalArgumentException { ChunksPending(){super(Messages.text("Ожидание загрузки соседних чанков при исследовании"));} }
    public void surveyPulse() {
        long start=System.nanoTime();int budget=plugin.content.config.getInt("limits.placement-columns-per-pass",128),jobs=Math.min(surveys.size(),4);
        while(jobs-->0 && budget>0){
            Survey s=surveys.remove();
            try{
                if(System.nanoTime()>s.deadline)throw new IllegalArgumentException(Messages.text("Не дождались соседних чанков; подойдите ближе к центру участка и повторите"));
                s.begin();
                while(budget>0 && s.column<s.size()){s.checkColumn();budget--;}
                if(s.column>=s.size())s.finish();else surveys.add(s);
            }catch(ChunksPending pending){s.column=0;surveys.add(s);lastPlacementReason=pending.getMessage();}
            catch(IllegalArgumentException e){placementRejected++;lastPlacementReason=e.getMessage();s.reply.accept(Messages.text("Размещение отклонено: ")+e.getMessage());}
        }
        surveyNanos=System.nanoTime()-start;
    }
    private final class Survey {
 final World world;final int x,z,radius,widthChunks,depthChunks,minChunkX,minChunkZ;final boolean manual;final Consumer<String> reply;String culture,layoutChoice;int column;
 final long deadline=System.nanoTime()+plugin.content.config.getLong("generation.candidate-wait-seconds",300)*1_000_000_000L;
 Survey(World world,int x,int z,String culture,boolean manual,Consumer<String> reply){this.world=world;this.x=x;this.z=z;this.culture=culture;this.manual=manual;this.reply=reply;radius=Math.min(48,plugin.content.config.getInt("generation.initial-radius",40));minChunkX=(x-radius)>>4;minChunkZ=(z-radius)>>4;widthChunks=((x+radius)>>4)-minChunkX+1;depthChunks=((z+radius)>>4)-minChunkZ+1;}
 int size(){return widthChunks*depthChunks;}
void begin(){
            if(world.getEnvironment()!=World.Environment.NORMAL)throw new IllegalArgumentException(Messages.text("Нужен обычный мир"));
            if(!manual && world.getSpawnLocation().distanceSquared(new Location(world,x,world.getSpawnLocation().getY(),z))<Math.pow(plugin.content.config.getInt("generation.spawn-distance",256),2))throw new IllegalArgumentException(Messages.text("Близко к спавну"));
            List<Village> occupied=new ArrayList<>(plugin.villages.values());occupied.addAll(creationReservations.values());
            int configuredDistance=plugin.content.config.getInt("generation.minimum-distance",0);
            for(Village v:occupied)if(v.world.equals(world.getUID()) && configuredDistance>0 && Math.hypot(x-v.x,z-v.z)<configuredDistance)throw new IllegalArgumentException(Messages.text("Расстояние ограничено настройкой сервера"));
            checkGuards(world,x-radius,z-radius,x+radius,z+radius);
        }
void checkColumn(){
            int cx=minChunkX+column%widthChunks,cz=minChunkZ+column/widthChunks;
            if(!world.isChunkLoaded(cx,cz))throw new ChunksPending();
            if(!manual && !freshChunk(world,cx,cz))throw new IllegalArgumentException(Messages.text("Автоматическое размещение разрешено только на подтверждённой новой земле"));
            int bx=Math.max(x-radius,cx*16),bz=Math.max(z-radius,cz*16),ex=Math.min(x+radius,cx*16+15),ez=Math.min(z+radius,cz*16+15);
            if(!world.getWorldBorder().isInside(new Location(world,bx,world.getSeaLevel(),bz)) || !world.getWorldBorder().isInside(new Location(world,ex,world.getSeaLevel(),ez)))throw new IllegalArgumentException(Messages.text("Граница мира"));
            column++;
        }
void finish(){
            begin();
            if(!manual)for(int cx=(x-radius)>>4;cx<=(x+radius)>>4;cx++)for(int cz=(z-radius)>>4;cz<=(z+radius)>>4;cz++)if(!freshChunk(world,cx,cz))throw new IllegalArgumentException(Messages.text("Земля изменена во время проверки; автоматическое расширение отменено"));
            int ground=terrain.surface(world,x,z);String biome=world.getBiome(x,ground,z).getKey().getKey().toUpperCase(Locale.ROOT);
            List<String> allowed=plugin.content.config.getStringList("generation.allowed-biomes");
            if(!allowed.isEmpty() && !allowed.contains(biome))throw new IllegalArgumentException(Messages.text("Биом не разрешён"));
            if(culture==null)culture=plugin.content.cultures.values().stream().filter(c->plugin.edition().culture(c.id()) && c.biomes().contains(biome)).map(Content.Culture::id).findFirst().orElseThrow(()->new IllegalArgumentException(Messages.text("Нет подходящей культуры")));
            Village v=new Village();v.world=world.getUID();v.worldName=world.getName();v.x=x;v.y=ground;v.z=z;v.radius=radius;v.biome=biome;v.culture=culture;v.edition=plugin.edition();if(!v.edition.culture(culture))throw new IllegalArgumentException(Messages.text("Эта культура доступна в LivingVillages Premium; в бесплатной версии выберите Полевичей"));if(v.edition==Edition.FREE && plugin.villages.values().stream().filter(t->t.world.equals(v.world)).count()+creationReservations.values().stream().filter(t->t.world.equals(v.world)).count()>=5)throw new IllegalArgumentException(Messages.text("В бесплатной версии — до пяти деревень на мир"));
            var c=plugin.content.cultures.get(culture);v.name=Names.village(v,plugin.content.rules);v.capacity=plugin.content.config.getInt("storage.capacity",4096);
            long starterFood=5L*plugin.content.rules.foodPerResident*plugin.content.config.getInt("nutrition.initial-days",7),units=plugin.content.rules.foods.get("CARROT");
            v.stock.add("CARROT",Math.toIntExact((starterFood+units-1)/units),v.capacity);
            v.stock.add("WHITE_WOOL",18,v.capacity);v.stock.add("STONE_HOE",4,v.capacity);v.stock.add("OAK_SAPLING",8,v.capacity);v.stock.add("COBBLESTONE",64,v.capacity);
            if(plugin.content.rules.autonomy){String seed=c.palette().get("sapling");if(seed!=null)seed=seed.toUpperCase(Locale.ROOT);if(seed!=null && !seed.equals("OAK_SAPLING"))v.stock.add(seed,4,v.capacity);v.stock.add("DIRT",16,v.capacity);v.stock.add("STONE_AXE",1,v.capacity);v.stock.add("STONE_PICKAXE",1,v.capacity);v.stock.add("WHEAT_SEEDS",16,v.capacity);}
            c.initialGoods().forEach((item,n)->v.stock.add(item,n,v.capacity));
            int initialLogs=plugin.content.config.getInt("autonomy.initial-local-logs",64);if(initialLogs>0)v.stock.add(plugin.content.rules.cultureWood.get(culture)+"_LOG",initialLogs,v.capacity);
            Project hall=null,warehouse=null,garden=null;
            String requested=layoutChoice==null?plugin.content.config.getString("generation.layout","random"):layoutChoice;
            List<Content.StartLayout> choices=requested.equals("random")?new ArrayList<>(plugin.content.layouts.values()):new ArrayList<>(List.of(Objects.requireNonNull(plugin.content.layouts.get(requested),Messages.text("Неизвестная планировка"))));
            if(requested.equals("random"))Collections.rotate(choices,Math.floorMod(v.id.hashCode(),choices.size()));
            String failure=Messages.text("Нет подходящей планировки");boolean placed=false;
            if(!manual)freshPlans.add(v.id);
            try{for(var layout:choices){for(int attempt=0;attempt<16 && !placed;attempt++){v.buildings.clear();v.projects.clear();try{var shapes=layout.plots().stream().map(plot->{var bp=plugin.content.blueprint(v,variant(v,plot.kind()));return new Layouts.Shape(plot.kind(),plot.x(),plot.z(),bp.width(),bp.depth());}).toList();var arranged=layoutChoice==null || layoutChoice.equals("random")?Layouts.arrange(shapes,v.id,attempt):layout.plots().stream().map(plot->new Layouts.Plot(plot.kind(),plot.x(),plot.z(),0)).toList();for(var plot:arranged)plan(v,plot.kind(),x+plot.x(),z+plot.z(),null,true,plot.rotation());planRoad(v);v.layout=layout.id();v.layoutVariant=attempt+1;placed=true;}catch(IllegalArgumentException conflict){failure=conflict.getMessage();}}if(placed)break;}}finally{freshPlans.remove(v.id);}
            if(!placed)throw new IllegalArgumentException(failure);
            hall=v.projects.stream().filter(p->p.kind.startsWith("hall")).findFirst().orElseThrow();warehouse=v.projects.stream().filter(p->p.kind.startsWith("warehouse")).findFirst().orElseThrow();garden=v.projects.stream().filter(p->p.kind.startsWith("garden")).findFirst().orElseThrow();
            Role[] roles={Role.ELDER,Role.MERCHANT,Role.FARMER,Role.BUILDER,Role.GUARD};
            for(int i=0;i<roles.length;i++){Resident r=new Resident();r.name=Names.resident(v,plugin.content.rules,r.id);r.role=roles[i];r.home=hall.building;if(r.role==Role.FARMER)r.workplace=garden.building;if(r.role==Role.MERCHANT)r.workplace=warehouse.building;v.residents.add(r);}
            // Persist the full reserved plan BEFORE touching the world.
            plugin.transactions.villages.add(v.id);
            holdClaim(v);
            plugin.db.save(Database.encode(v)).whenComplete((done,error)->plugin.sync(()->{
                creationReservations.remove(v.id);
                plugin.transactions.villages.remove(v.id);
                if(error!=null){plugin.getLogger().severe(Messages.text("Создание не сохранено: ")+error);reply.accept(Messages.text("Ошибка сохранения; мир не менялся."));return;}
                plugin.villages.put(v.id,v);plugin.announceNearby(v);reply.accept(Messages.text("Поселение создано: ")+v.name+" · "+plugin.content.layouts.get(v.layout).name()+Messages.text(". Здания появятся постепенно. UUID ")+v.id);
            }));
        }
}
    public void checkGuards(World w,int minX,int minZ,int maxX,int maxZ) {
        var guards=Bukkit.getServicesManager().getRegistrations(PlacementGuard.class);
        for(var guard:guards)if(!guard.getProvider().canReserve(w,minX,minZ,maxX,maxZ))throw new IllegalArgumentException(Messages.text("Региональная защита запретила размещение"));
        if(plugin.content.config.getBoolean("generation.deny-with-unknown-plugins",true)) {
            var allowed=plugin.content.config.getStringList("generation.placement-allow-plugins");
            for(var p:Bukkit.getPluginManager().getPlugins())if(p!=plugin && p.isEnabled() && !allowed.contains(p.getName()) && !plugin.integrations.covered().contains(p.getName()) && guards.stream().noneMatch(g->g.getPlugin().equals(p)))throw new IllegalArgumentException(Messages.text("Нет адаптера защиты для набора плагинов. Проверьте placement-allow-plugins: ")+p.getName());
        }
    }
    void checkForeign(Village v,int x,int z,int ex,int ez){var others=new ArrayList<>(plugin.villages.values());others.addAll(creationReservations.values());for(var other:others)if(!other.id.equals(v.id) && other.world.equals(v.world)){for(var b:other.buildings)if(!"road".equals(b.kind) && Territory.intersects(b,x,z,ex,ez,0))throw new IllegalArgumentException(Messages.text("На участке постройка соседней деревни"));if(other.buildings.stream().flatMap(b->b.resourceCells.stream()).anyMatch(cell->{String[] c=cell.split(":");int bx=Integer.parseInt(c[0]),bz=Integer.parseInt(c[2]);return bx>=x&&bx<=ex&&bz>=z&&bz<=ez;}))throw new IllegalArgumentException(Messages.text("На участке ресурс соседней деревни"));if(other.projects.stream().filter(p->!p.state.equals("CANCELLED")).flatMap(p->p.blocks.stream()).anyMatch(c->c.phase==5 && c.x>=x && c.x<=ex && c.z>=z && c.z<=ez))throw new IllegalArgumentException(Messages.text("На участке дорога соседней деревни"));}}
    public String variant(Village v,String base) {
        return BlueprintChoice.choose(plugin.content.rules,v.culture,base,plugin.content.blueprints.keySet());
    }
    public Project plan(Village v,String kind,int x,int z,UUID owner,boolean starter) {
        return plan(v,kind,x,z,owner,starter,0);
    }
    public Project plan(Village v,String kind,int x,int z,UUID owner,boolean starter,int rotation) {
        return planInternal(v,kind,x,z,owner,starter,rotation,Map.of(),!kind.startsWith("street_light"),null);
    }
    Project light(Village v,int x,int z,boolean starter){return planInternal(v,"street_light",x,z,null,starter,0,Map.of(),false,null);}
    private Project planInternal(Village v,String kind,int x,int z,UUID owner,boolean starter,int rotation,Map<String,String> trusted,boolean road,Integer baseOverride) {
        if(!plugin.edition().building(kind)||owner!=null)throw new IllegalArgumentException(Messages.text("Эта возможность доступна в LivingVillages Premium. Хозяйство, торговля и развитие до уровня 3 доступны в бесплатной версии."));
        if(v.abandoned)throw new IllegalArgumentException(Messages.text("Поселение заброшено; новые стройки недоступны"));
        if(rotation<0 || rotation>3)throw new IllegalArgumentException(Messages.text("Поворот: 0, 90, 180, 270 градусов"));
        if(!starter)Projects.requireSlot(v,plugin.content.rules,owner==null && (kind.equals("garden") || Autonomy.essential(kind)));
        String key=variant(v,kind);var blueprint=plugin.content.blueprint(v,key);
        if(blueprint==null)throw new IllegalArgumentException(Messages.text("Нет чертежа ")+key);
        if(rotation!=0){var source=blueprint;List<Content.Cell> cells=new ArrayList<>();for(var cell:source.cells()){
            int rx=switch(rotation){case 1->source.depth()-1-cell.z();case 2->source.width()-1-cell.x();default->cell.z();};
            int rz=switch(rotation){case 1->cell.x();case 2->source.depth()-1-cell.z();default->source.width()-1-cell.x();};
            cells.add(new Content.Cell(rx,cell.y(),rz,cell.phase(),cell.data(),cell.floor()));
        }Content.Entrance e=source.entrance();if(e!=null){int ex=switch(rotation){case 1->source.depth()-1-e.z();case 2->source.width()-1-e.x();default->e.z();},ez=switch(rotation){case 1->e.x();case 2->source.depth()-1-e.z();default->source.width()-1-e.x();};var directions=List.of("NORTH","EAST","SOUTH","WEST");e=new Content.Entrance(ex,ez,directions.get((directions.indexOf(e.facing())+rotation)%4));}blueprint=new Content.Blueprint(source.id(),source.name(),rotation%2==0?source.width():source.depth(),rotation%2==0?source.depth():source.width(),source.beds(),source.level(),source.recipe(),cells,source.upkeep(),source.upkeepDays(),source.unlockStage(),e,source.groundLayer(),source.closedGables());}
        if(!starter && v.highestStage<blueprint.unlockStage())throw new IllegalArgumentException(Messages.text("Нужна ступень: ")+Development.name(blueprint.unlockStage()));
        final int width=blueprint.width(),depth=blueprint.depth();
        final int padding=0,minX=x-padding,minZ=z-padding,maxX=x+width+padding,maxZ=z+depth+padding;
        World w=Bukkit.getWorld(v.world);if(w==null)throw new IllegalArgumentException(Messages.text("Мир не загружен"));
        if(minX<v.x-v.radius || maxX>v.x+v.radius+1 || minZ<v.z-v.radius || maxZ>v.z+v.radius+1)throw new IllegalArgumentException(Messages.text("Участок с двором выходит за границы деревни"));
        checkGuards(w,minX,minZ,maxX-1,maxZ-1);
        checkForeign(v,minX,minZ,maxX-1,maxZ-1);
        checkFresh(v,w,minX,minZ,width+padding*2,depth+padding*2);
        int base=baseOverride==null?terrain.base(w,x,z,width,depth,key.endsWith("_pier") && !kind.startsWith("tree_garden"),kind.startsWith("tree_garden"))-blueprint.groundLayer():baseOverride;
        if(padding>0){terrain.base(w,minX,minZ,width+padding*2,depth+padding*2,key.endsWith("_pier"));terrain.structures(w,minX,base,minZ,width+padding*2,8,depth+padding*2);}
        int roof=blueprint.cells().stream().mapToInt(Content.Cell::y).max().orElse(0);if(base<w.getMinHeight() || base+roof>=w.getMaxHeight())throw new IllegalArgumentException(Messages.text("Высота здания выходит за границы мира"));terrain.structures(w,x,base,z,width,roof+1,depth);
        for(Building b:v.buildings)if(!b.kind.equals("road") && Territory.intersects(b,minX,minZ,maxX-1,maxZ-1,2))throw new IllegalArgumentException(Messages.text("Пересечение с зарезервированным участком"));
        if(v.projects.stream().filter(q->!q.state.equals("CANCELLED")).flatMap(q->q.blocks.stream().filter(cell->cell.phase==5 || q.kind.equals("road"))).anyMatch(cell-> cell.x>=minX && cell.x<maxX && cell.z>=minZ && cell.z<maxZ))throw new IllegalArgumentException(Messages.text("Участок пересекает дорогу"));
        if(v.buildings.stream().flatMap(existing->existing.resourceCells.stream()).anyMatch(cell->{String[] c=cell.split(":");int bx=Integer.parseInt(c[0]),bz=Integer.parseInt(c[2]);return bx>=minX && bx<maxX && bz>=minZ && bz<maxZ;}))throw new IllegalArgumentException(Messages.text("Участок занят производственной делянкой"));
        Project p=new Project();p.kind=key;p.owner=null;p.starter=starter;p.essential=owner==null && (kind.equals("garden")||Autonomy.essential(kind));
        Building b=new Building();b.kind=key;b.owner=null;b.x=x;b.z=z;b.y=base;b.width=blueprint.width();b.depth=blueprint.depth();b.beds=blueprint.beds();b.rotation=rotation;b.level=blueprint.level();b.upkeep.putAll(blueprint.upkeep());b.upkeepDays=blueprint.upkeepDays();b.upkeepDue=v.days+b.upkeepDays;b.contentVersion=3;b.designVersion=plugin.content.designVersion(v,key);
        b.plotPadding=padding;b.roofVersion=1;if(kind.startsWith("hall"))b.noticeDepth=(rotation%2==0?b.depth:b.width)-(key.endsWith("_pier")?3:0);
        if(blueprint.entrance()!=null){b.entryX=x+blueprint.entrance().x();b.entryY=base+(key.endsWith("_pier") && !kind.startsWith("tree_garden")?4:1);b.entryZ=z+blueprint.entrance().z();b.entryFacing=blueprint.entrance().facing();}
        b.recipe=blueprint.recipe();if(kind.startsWith("workshop"))b.recipe=plugin.content.cultures.get(v.culture).specialty();
        if(kind.equals("boatyard"))b.recipe=plugin.content.cultures.get(v.culture).palette().get("planks").replace("_planks","_boats");
        b.biome=w.getBiome(x+width/2,base,z+depth/2).getKey().getKey().toUpperCase(Locale.ROOT);
        b.workX=x+2;b.workY=base+(key.endsWith("_pier")?4:2);b.workZ=z+blueprint.depth()-2-(key.endsWith("_pier")&&!kind.startsWith("tree_garden")?3:0);
        if(!blueprint.cells().isEmpty()){
            Map<String,String> space=new HashMap<>();for(var c:blueprint.cells())space.put(c.x()+":"+c.y()+":"+c.z(),c.data());
            var point=blueprint.cells().stream().filter(c->c.floor()&&!c.data().equals("air")&&!c.data().startsWith("water")&&!c.data().startsWith("lava")&&"air".equals(space.get(c.x()+":"+(c.y()+1)+":"+c.z()))&&"air".equals(space.get(c.x()+":"+(c.y()+2)+":"+c.z())))
                .min(Comparator.comparingInt(Content.Cell::y).thenComparingInt(c->Math.abs(c.x()-width/2)+Math.abs(c.z()-depth/2))).orElse(null);
            if(point!=null){b.workX=x+point.x();b.workY=base+point.y()+1;b.workZ=z+point.z();}
        }
        p.building=b.id;
        var culture=plugin.content.cultures.get(v.culture);
        if((Autonomy.CORE.contains(kind.replaceAll("_u[0-9]+$",""))||kind.startsWith("tree_garden"))){String wood=plugin.content.rules.groveKinds.containsKey(kind.replaceAll("_u[0-9]+$",""))?plugin.content.rules.groveKinds.get(kind.replaceAll("_u[0-9]+$","")):culture.palette().get("planks").toUpperCase(Locale.ROOT).replace("_PLANKS","");if(kind.equals("tree_garden")){String preferred=wood;if(v.stock.get(Groves.sapling(preferred))<2)wood=List.of("OAK","SPRUCE","ACACIA","MANGROVE","BIRCH").stream().filter(k->v.stock.get(Groves.sapling(k))>=2).findFirst().orElse(preferred);}else if(!kind.startsWith("tree_garden")&&v.buildings.stream().anyMatch(g->g.kind.startsWith("tree_garden")))wood=Assembly.wood(v,plugin.content.rules);b.wood=wood;var palette=new HashMap<>(culture.palette());palette.put("log",wood+"_LOG");palette.put("wall",wood+"_PLANKS");palette.put("planks",wood+"_PLANKS");palette.put("roof",wood+"_SLAB");palette.put("stairs",wood+"_STAIRS");palette.put("fence",wood+"_FENCE");palette.put("door",wood+"_DOOR");palette.put("gate",wood+"_FENCE_GATE");palette.put("sapling",Groves.sapling(wood));culture=new Content.Culture(culture.id(),culture.name(),culture.biomes(),culture.names(),palette,culture.specialty(),culture.greeting(),culture.need(),culture.initialGoods());if(kind.startsWith("tree_garden")){b.recipe="grove_"+wood.toLowerCase(Locale.ROOT);b.workY=base+1;}}
        Set<String> leveled=kind.startsWith("tree_garden")?terrain.levelNursery(w,x,z,width,depth,base,p.blocks):Set.of();
        terrain.foundations(w,x,base,z,blueprint,culture,key.endsWith("_pier")&&!kind.startsWith("tree_garden"),p.blocks);
        for(var cell:blueprint.cells()) {
            int bx=x+cell.x(),by=base+cell.y(),bz=z+cell.z();
            if(!w.isChunkLoaded(bx>>4,bz>>4))throw new IllegalArgumentException(Messages.text("Участок не загружен"));
            Block existing=w.getBlockAt(bx,by,bz);var target=Bukkit.createBlockData(plugin.content.resolve(culture,cell.data()));
            target.rotate(switch(rotation){case 1->org.bukkit.block.structure.StructureRotation.CLOCKWISE_90;case 2->org.bukkit.block.structure.StructureRotation.CLOCKWISE_180;case 3->org.bukkit.block.structure.StructureRotation.COUNTERCLOCKWISE_90;default->org.bukkit.block.structure.StructureRotation.NONE;});String after=target.getAsString();
            if(!leveled.contains(bx+":"+by+":"+bz) && !matchesExpected(trusted.get(bx+":"+by+":"+bz),existing.getBlockData().getAsString()) && !replaceable(existing.getType()) && !(key.endsWith("_pier") && existing.getType()==Material.WATER) && !(cell.y()<=1 && natural(existing.getType())))throw new IllegalArgumentException(Messages.text("Посторонний блок на участке: ")+existing.getType());
            if(!trusted.isEmpty() && target.getMaterial().isAir() && existing.getState(false) instanceof org.bukkit.block.Sign && matchesExpected(trusted.get(bx+":"+by+":"+bz),existing.getBlockData().getAsString()))after=existing.getBlockData().getAsString();
            String material=costMaterial(Bukkit.createBlockData(after).getMaterial());
            if(Set.of(Material.DIRT,Material.FARMLAND,Material.DIRT_PATH).contains(target.getMaterial())&&Set.of(Material.DIRT,Material.GRASS_BLOCK,Material.COARSE_DIRT,Material.ROOTED_DIRT,Material.PODZOL).contains(existing.getType()))material=null;
            if(kind.startsWith("tree_garden") && target.getMaterial()==Material.DIRT && Set.of(Material.DIRT,Material.GRASS_BLOCK,Material.PODZOL,Material.ROOTED_DIRT,Material.MUD).contains(existing.getType()))material=null;
            if(kind.startsWith("tree_garden") && target.getMaterial().name().equals(Groves.sapling(b.wood)))b.trees.add(new TreeSite(bx,by,bz));
            if(target instanceof org.bukkit.block.data.type.Door door && door.getHalf()==org.bukkit.block.data.Bisected.Half.TOP)material=null;
            if(target instanceof org.bukkit.block.data.type.Bed bed && bed.getPart()==org.bukkit.block.data.type.Bed.Part.HEAD)material=null;
            if(!trusted.isEmpty() && matchesExpected(after,existing.getBlockData().getAsString()))material=null;
            var planned=new Village.Block(bx,by,bz,cell.phase(),leveled.contains(bx+":"+by+":"+bz)?"minecraft:air":existing.getBlockData().getAsString(),after,material);planned.floor=cell.floor();p.blocks.add(planned);
            if(material!=null)p.required.merge(material,1,Math::addExact);
        }
        for(int bx=x;bx<x+width;bx++)for(int bz=z;bz<z+depth;bz++)terrain.clearColumn(w,bx,bz,base,base+roof,p.blocks,trusted);
        p.blocks.sort(Projects.constructionOrder());
        if(!starter && road)attachRoad(v,b,p,w,p.blocks);
        if(starter){p.escrow.putAll(p.required);p.state="QUEUED";p.reason=Messages.text("Начальные припасы переселенцев");}
        if(kind.startsWith("garden_")||kind.startsWith("relief_garden"))b.enclosureVersion=1;
        if(p.blocks.stream().anyMatch(c->c.after.contains("_fence_gate[")))b.enclosureVersion=1;
        if(Enclosures.needed(b)){var perimeter=Enclosures.cells(b,p.blocks);if(!perimeter.isEmpty() && perimeter.stream().allMatch(c->p.blocks.stream().anyMatch(cell->cell.x==c.x() && cell.y==c.y() && cell.z==c.z() && (c.gate()?cell.after.contains("_fence_gate["):cell.after.contains("_fence[")))))b.enclosureVersion=1;}
        v.buildings.add(b);v.projects.add(p);
        var production=plugin.content.rules.recipes.get(b.recipe);if(!starter && owner==null && production!=null && Set.of("stone","iron","clay","sand","dirt","water_source").contains(production.condition())){p.state="SURVEY";p.reason=Messages.text("Разведка настоящего месторождения; нужны загруженные окрестности");confirmResource(v,b,w);}
        return p;
    }
    private void checkFresh(Village v,World world,int x,int z,int width,int depth){
        if(!freshPlans.contains(v.id))return;
        for(int cx=x>>4;cx<=(x+width-1)>>4;cx++)for(int cz=z>>4;cz<=(z+depth-1)>>4;cz++)if(!freshChunk(world,cx,cz))throw new IllegalArgumentException(Messages.text("Автоматическая стройка разрешена только в новых чанках; для старой территории используйте /lv create auto"));
    }
    private Map<String,Integer> roadLevels(Village v,Project current){
        Map<String,Integer> levels=new HashMap<>();List<Project> projects=new ArrayList<>(v.projects);if(!projects.contains(current))projects.add(current);
        for(Project p:projects)if(!p.state.equals("CANCELLED"))for(var cell:p.blocks)if((cell.phase==5 || p.kind.equals("road")) && (cell.after.equals("minecraft:gravel") || cell.after.equals("minecraft:cobblestone") || cell.after.equals("minecraft:dirt_path") || cell.after.equals("minecraft:mangrove_planks")))levels.put(cell.x+":"+cell.z,cell.y);
        return levels;
    }
    private void attachRoad(Village v,Building building,Project project,World world,List<Village.Block> structure){
        String failure=Messages.text("Нет свободного соединения с дорогой");
        var door=Entrances.road(building,structure);if(door==null && building.entryFacing==null)throw new IllegalArgumentException(Messages.text("Чертёж без входа"));
        for(int trunk:new int[]{v.z-3,v.z-2,v.z,v.z+3,v.z-8})for(int side:new int[]{building.x-2,building.x+building.width+1}){
            LinkedHashMap<String,int[]> path=new LinkedHashMap<>();
            var facing=door==null?org.bukkit.block.BlockFace.valueOf(building.entryFacing):((org.bukkit.block.data.Directional)Bukkit.createBlockData(door.after)).getFacing();int startX=door==null?building.entryX:door.x,startZ=door==null?building.entryZ:door.z,entryY=door==null?building.entryY:door.y;
            switch(facing){case SOUTH->startZ=building.z+building.depth;case NORTH->startZ=building.z-1;case EAST->startX=building.x+building.width;case WEST->startX=building.x-1;default->throw new IllegalArgumentException(Messages.text("Направление входа"));}
            line(path,startX,startZ,side,startZ);line(path,side,startZ,side,trunk);line(path,side,trunk,v.x,trunk);line(path,v.x,trunk,v.x,v.z-3);
            try{
                List<int[]> points=new ArrayList<>(path.values());Map<String,Integer> known=roadLevels(v,project);int[] ground=new int[points.size()],height=new int[points.size()];Map<Integer,Integer> fixed=new HashMap<>();
                for(int i=0;i<points.size();i++){
                    int x=points.get(i)[0],z=points.get(i)[1];
                    if(Math.abs(x-v.x)>v.radius || Math.abs(z-v.z)>v.radius)throw new IllegalArgumentException(Messages.text("Дорога выходит за территорию"));
                    checkFresh(v,world,x,z,1,1);checkForeign(v,x,z,x,z);
                    if(x>=building.x && x<building.x+building.width && z>=building.z && z<building.z+building.depth || v.buildings.stream().anyMatch(b->!b.kind.equals("road") && Territory.plot(b,x,z)))throw new IllegalArgumentException(Messages.text("Дорога пересекает здание или личный двор"));
                    ground[i]=terrain.surface(world,x,z);Material material=world.getBlockAt(x,ground[i],z).getType();
                    Integer saved=known.get(x+":"+z);if(saved!=null){height[i]=saved;fixed.put(i,saved);}
                    else{if(material==Material.WATER){if(!world.getBlockAt(x,ground[i]-3,z).getType().isSolid())throw new IllegalArgumentException(Messages.text("Слишком глубокая вода на пути дороги"));}else if(!natural(material))throw new IllegalArgumentException(Messages.text("Посторонняя постройка на пути дороги: ")+material);height[i]=ground[i]+(plugin.content.config.getBoolean("construction.natural-paths",true) && Set.of(Material.GRASS_BLOCK,Material.DIRT,Material.COARSE_DIRT,Material.PODZOL,Material.ROOTED_DIRT).contains(material)?0:1);}
                }
                int approach=entryY-1;if(building.kind.endsWith("_pier"))approach=structure.stream().filter(c->c.phase==1 && c.after.contains("_stairs[")).mapToInt(c->c.y).min().orElse(approach);
                fixed.put(0,approach);height[0]=Math.max(height[0],approach);
                for(int i=1;i<height.length;i++)height[i]=Math.max(height[i],height[i-1]-1);
                for(int i=height.length-2;i>=0;i--)height[i]=Math.max(height[i],height[i+1]-1);
                for(var anchor:fixed.entrySet())if(height[anchor.getKey()]!=anchor.getValue())throw new IllegalArgumentException(Messages.text("Перепад высот не позволяет соединить вход и дорогу"));
                List<Village.Block> cells=new ArrayList<>();
                for(int i=0;i<points.size();i++){
                    int x=points.get(i)[0],z=points.get(i)[1],y=height[i];if(known.containsKey(x+":"+z))continue;
                    if(y-ground[i]>plugin.content.config.getInt("generation.max-height-difference",2)+2)throw new IllegalArgumentException(Messages.text("Слишком высокий настил дороги"));
                    terrain.structures(world,x,y,z,1,3,1);
                    for(int by=ground[i]+1;by<y;by++){var block=world.getBlockAt(x,by,z);cells.add(new Village.Block(x,by,z,0,block.getBlockData().getAsString(),"minecraft:cobblestone","COBBLESTONE"));}
                    var block=world.getBlockAt(x,y,z);boolean earth=plugin.content.config.getBoolean("construction.natural-paths",true) && y==ground[i] && Set.of(Material.GRASS_BLOCK,Material.DIRT,Material.COARSE_DIRT,Material.PODZOL,Material.ROOTED_DIRT).contains(block.getType());if(!replaceable(block.getType()) && !earth)throw new IllegalArgumentException(Messages.text("Посторонний блок на дороге: ")+block.getType());
                    String material=earth?"DIRT_PATH":world.getBlockAt(x,ground[i],z).getType()==Material.WATER?"MANGROVE_PLANKS":"COBBLESTONE";
                    cells.add(new Village.Block(x,y,z,5,block.getBlockData().getAsString(),"minecraft:"+material.toLowerCase(Locale.ROOT),earth?null:material));terrain.clearColumn(world,x,z,y,y+2,cells);
                }
                project.blocks.addAll(cells);for(var cell:cells)if(cell.material!=null)project.required.merge(cell.material,1,Math::addExact);
                project.blocks.sort(Projects.constructionOrder());return;
            }catch(IllegalArgumentException error){failure=error.getMessage();}
        }throw new IllegalArgumentException(failure);
    }
    private static void line(Map<String,int[]> path,int x1,int z1,int x2,int z2){
        int steps=Math.abs(x2-x1)+Math.abs(z2-z1);
        if(steps>512)throw new IllegalArgumentException(Messages.text("Дорога слишком длинная"));
        int x=x1,z=z1;for(int i=0;i<=steps;i++){path.putIfAbsent(x+":"+z,new int[]{x,z});if(x!=x2)x+=Integer.signum(x2-x);else if(z!=z2)z+=Integer.signum(z2-z);}
    }
    static final class SitePending extends IllegalArgumentException {SitePending(String reason){super(reason);}}
    public Project planNearby(Village v,String kind){
        Projects.requireSlot(v,plugin.content.rules,kind.equals("garden") || Autonomy.essential(kind));
        var shape=plugin.content.blueprint(v,variant(v,kind));List<int[]> candidates=new ArrayList<>();
        for(int dx=-v.radius+2;dx<=v.radius-shape.width()-2;dx+=4)for(int dz=-v.radius+2;dz<=v.radius-shape.depth()-2;dz+=4)candidates.add(new int[]{v.x+dx,v.z+dz});
        candidates.sort(Comparator.<int[]>comparingInt(c->Math.abs(c[0]+shape.width()/2-v.x)+Math.abs(c[1]+shape.depth()/2-v.z)).thenComparingInt(c->c[0]).thenComparingInt(c->c[1]));
        if(candidates.isEmpty())throw new IllegalArgumentException(Messages.text("Здание не помещается на текущей территории"));
        int cursor=Math.floorMod(v.siteSearch.getOrDefault(kind,0),candidates.size()),budget=Math.clamp(plugin.content.config.getInt("limits.building-sites-per-pass",8),1,16);String reason=Messages.text("Нет свободного участка");
        for(int n=0;n<Math.min(budget,candidates.size());n++){var point=candidates.get((cursor+n)%candidates.size());v.siteSearch.put(kind,(cursor+n+1)%candidates.size());if(v.failedSites.contains(kind+":"+point[0]+":"+point[1]))continue;
            int buildings=v.buildings.size(),projects=v.projects.size();try{var project=plan(v,kind,point[0],point[1],null,false);project.priority=BuildAgenda.priority(kind);return project;}catch(IllegalArgumentException blocked){reason=blocked.getMessage();if(v.buildings.size()>buildings)v.buildings.subList(buildings,v.buildings.size()).clear();if(v.projects.size()>projects)v.projects.subList(projects,v.projects.size()).clear();}
        }throw new SitePending(Messages.text("Поиск участка для «")+projectName(variant(v,kind))+Messages.text("» продолжается. Последняя причина: ")+reason);
    }
    
    
    
    
    
    
    
    
    
    
    private String projectName(String kind){var definition=plugin.content.blueprints.get(kind);return definition==null?kind:definition.name();}
    private final Map<UUID,Long> nextSiteRetry=new HashMap<>();
    private void relocateSites(Village v){
        var pending=v.projects.stream().filter(p->p.state.equals("SITE_BLOCKED")&&p.owner==null&&p.index==0&&p.escrow.values().stream().noneMatch(n->n>0)).findFirst().orElse(null);if(pending==null||System.nanoTime()<nextSiteRetry.getOrDefault(pending.id,0L))return;
        nextSiteRetry.put(pending.id,System.nanoTime()+3_000_000_000L);var old=pending.building(v);int pi=v.projects.indexOf(pending),bi=v.buildings.indexOf(old);v.projects.remove(pending);v.buildings.remove(old);
        try{var replacement=planNearby(v,pending.kind);replacement.id=pending.id;replacement.priority=pending.priority;replacement.contributors.addAll(pending.contributors);v.developmentReason=Messages.text("Цель сохранена; проверяем другой участок: ")+projectName(replacement.kind);plugin.save(v);}
        catch(IllegalArgumentException blocked){v.projects.add(Math.min(pi,v.projects.size()),pending);v.buildings.add(Math.min(bi,v.buildings.size()),old);pending.reason=Messages.text("Ресурс не найден на прошлом участке. ")+blocked.getMessage();v.developmentReason=pending.reason;}
    }
    public void develop(Village v){
        Projects.releaseCommonReturns(v);
        var waterWorld=Bukkit.getWorld(v.world);if(waterWorld!=null)for(var b:v.buildings)if((b.complete || v.projects.stream().anyMatch(project->project.building.equals(b.id) && project.active())) && "water_supply".equals(b.recipe) && !b.resourceConfirmed && System.nanoTime()>=nextWaterSearch.getOrDefault(b.id,0L)){
            nextWaterSearch.put(b.id,System.nanoTime()+plugin.content.config.getLong("water.source-recheck-seconds",30)*1_000_000_000L);b.resourceSurveyed=false;b.resourceCells.clear();confirmResource(v,b,waterWorld);
        }
        if(v.paused || v.abandoned)return;
        relocateSites(v);Levels.update(v,plugin.content.rules);Jobs.ensureBuilders(v,plugin.content.rules);
        World loadedWorld=Bukkit.getWorld(v.world);if(loadedWorld!=null)for(var planned:v.buildings)if(!planned.resourceSurveyed && (planned.complete && "water_supply".equals(planned.recipe) || v.projects.stream().anyMatch(p->p.building.equals(planned.id) && p.state.equals("SURVEY"))))confirmResource(v,planned,loadedWorld);
        if(plugin.npcs.danger(v)){v.developmentReason=Messages.text("Тревога: ")+plugin.npcs.dangerReason(v);return;}
        if(plugin.content.config.getBoolean("development.auto-plan",true) && plugin.content.config.getBoolean("development.auto-repair",true) && System.nanoTime()>=nextRepair.getOrDefault(v.id,0L)){
            nextRepair.put(v.id,System.nanoTime()+plugin.content.config.getLong("development.plan-interval-seconds",60)*1_000_000_000L);
            for(var broken:Repairs.needed(v,plugin.content.rules)){try{var repair=planRepair(v,broken.id);repair.priority=Repairs.priority(broken,plugin.content.rules);v.developmentReason=Messages.text("Восстановление: ")+projectName(broken.kind);plugin.save(v);break;}catch(IllegalArgumentException blocked){v.developmentReason=Messages.text("Ремонт: ")+blocked.getMessage();}}
        }
        for(Project p:v.projects.stream().filter(p->p.owner==null && p.state.equals("COLLECTING")).sorted(Comparator.<Project>comparingInt(p->p.essential?0:1).thenComparing(Comparator.comparingInt((Project p)->p.priority).reversed())).toList()){
            for(var e:p.required.entrySet()){int missing=e.getValue()-p.escrow.getOrDefault(e.getKey(),0),n=Math.min(missing,Development.availableForBuilding(v,plugin.content.rules,e.getKey(),p.essential));if(n>0){v.stock.take(e.getKey(),n);p.escrow.merge(e.getKey(),n,Integer::sum);}}
            if(p.funded()){p.state="QUEUED";p.reason=Messages.text("Материалы собраны");}else{p.reason=Messages.text("Не хватает материалов; передайте через меню проекта");v.developmentReason=Messages.text("Нужны материалы: ")+projectName(p.kind);}
        }
        if(v.paused || v.abandoned || v.projects.stream().anyMatch(p->p.starter && p.active()))return;
        if(!plugin.content.config.getBoolean("development.auto-plan",true)&&v.plannedBuilding==null){v.developmentReason=Messages.text("Автоматическое планирование отключено; общие проекты доступны в меню");return;}
        if(v.nutrition<1 || plugin.content.rules.foodUnits(v)<plugin.content.rules.dailyFood(v)*3){
            v.developmentReason=Messages.text("Нужно восстановить питание и запас на три дня");
            String vital=plugin.content.rules.autonomy && v.nutrition>=.75?Autonomy.foodRecovery(v,plugin.content.rules):null;
            if(vital!=null && System.nanoTime()>=nextDevelopment.getOrDefault(v.id,0L)){
                nextDevelopment.put(v.id,System.nanoTime()+plugin.content.config.getLong("development.plan-interval-seconds",60)*1_000_000_000L);
                try{var recovery=planNearby(v,vital);recovery.essential=true;recovery.priority=100;v.developmentReason=Messages.text("Восстановление питания: ")+projectName(recovery.kind);plugin.save(v);}catch(IllegalArgumentException blocked){v.developmentReason=Messages.text("Восстановление питания: ")+blocked.getMessage();}
            }return;
        }
        String plannedFood=FoodPlan.next(v,plugin.content.rules);
        if(plugin.content.rules.councilEnabled&&plannedFood!=null&&System.nanoTime()>=nextDevelopment.getOrDefault(v.id,0L)){
            nextDevelopment.put(v.id,System.nanoTime()+plugin.content.config.getLong("development.plan-interval-seconds",60)*1_000_000_000L);
            try{var foodProject=planNearby(v,plannedFood);foodProject.essential=true;foodProject.priority=95;v.developmentReason=Messages.text("Совет: выпуск еды отстаёт от потребления");plugin.save(v);}catch(IllegalArgumentException blocked){v.developmentReason=Messages.text("План питания: ")+blocked.getMessage();}return;
        }
        enclose(v);
        cropAccess(v);
        lighting.develop(v);
        if(v.projects.stream().anyMatch(p->p.owner==null && p.active()) && (!plugin.content.rules.autonomy || Autonomy.next(v,plugin.content.rules)==null || !Autonomy.essential(Autonomy.next(v,plugin.content.rules)))){v.developmentReason=Messages.text("Завершение текущих общих проектов");return;}
        String supplier=plugin.content.rules.autonomy?Council.nextBuilding(v,plugin.content.rules):null;
        if(System.nanoTime()<nextDevelopment.getOrDefault(v.id,0L))return;
        nextDevelopment.put(v.id,System.nanoTime()+plugin.content.config.getLong("development.plan-interval-seconds",60)*1_000_000_000L);
        boolean spare=v.residents.stream().anyMatch(r->r.alive && v.adult(r) && r.role==Role.BUILDER && r.workplace==null);
        String kind=plugin.content.rules.autonomy?BuildAgenda.choose(v,supplier):v.living()>=v.beds() && v.living()<plugin.content.rules.maxPopulation?"residence":spare && v.buildings.stream().noneMatch(block->block.kind.startsWith("workshop"))?"workshop":null;
        if(kind==null){v.developmentReason=Messages.text("Жилья достаточно; расширение пока не требуется");return;}
        if(v.projects.stream().anyMatch(p->p.active() && p.kind.equals(variant(v,kind)))){v.developmentReason=Messages.text("Завершение проекта: ")+projectName(variant(v,kind));BuildAgenda.reserved(v,kind);return;}
        try{Project p=planNearby(v,kind);BuildAgenda.reserved(v,kind);if(kind.equals("family_home") || kind.equals("residence") && Housing.waitingFamily(v)!=null){p.building(v).family=Housing.waitingFamily(v);p.priority=45;v.lastFamilyPlanDay=v.days;}v.developmentReason=Messages.text("Сбор материалов: ")+projectName(p.kind);plugin.save(v);}catch(IllegalArgumentException e){v.planningReason=e.getMessage();v.developmentReason=e.getMessage();nextDevelopment.put(v.id,System.nanoTime()+Math.min(3,plugin.content.config.getLong("development.plan-interval-seconds",60))*1_000_000_000L);}
    }
    private void enclose(Village v){
        if(System.nanoTime()<nextEnclosure.getOrDefault(v.id,0L) || v.projects.stream().anyMatch(p->p.active() && Messages.startsWith(p.kind,"Ограда")))return;
        nextEnclosure.put(v.id,System.nanoTime()+plugin.content.config.getLong("development.plan-interval-seconds",60)*1_000_000_000L);
        for(var b:v.buildings)if(Enclosures.needed(b) && b.complete && !b.damaged && b.enclosureVersion==0 && v.projects.stream().noneMatch(p->p.active() && p.building.equals(b.id))){try{enclosure(v,b);plugin.save(v);break;}catch(IllegalArgumentException denied){b.damageReason=Messages.text("Ограда: ")+denied.getMessage();}}
    }
    private void cropAccess(Village v){
        if(v.projects.stream().anyMatch(p->p.active() && Messages.startsWith(p.kind,"Проход на огород")))return;
        for(var b:v.buildings)if(b.owner==null && b.complete && !b.damaged && v.projects.stream().noneMatch(p->p.active() && p.building.equals(b.id)) && !CropAccess.cells(b,Projects.expected(v,b.id)).isEmpty()){
            try{cropAccess(v,b);plugin.save(v);}catch(IllegalArgumentException denied){v.developmentReason=Messages.text("Проход на огород: ")+denied.getMessage();}break;
        }
    }
    Project cropAccess(Village v,Building b){
        Projects.requireSlot(v,plugin.content.rules,false);World w=Objects.requireNonNull(Bukkit.getWorld(v.world));var expected=Projects.expected(v,b.id);var palette=plugin.content.cultures.get(v.culture).palette();var p=new Project();p.kind=Messages.text("Проход на огород · ")+projectName(b.kind);p.building=b.id;p.priority=35;
        for(var cell:CropAccess.cells(b,expected)){
            if(!w.isChunkLoaded(cell.x()>>4,cell.z()>>4))throw new IllegalArgumentException(Messages.text("Дождитесь загрузки участка"));checkGuards(w,cell.x(),cell.z(),cell.x(),cell.z());Block actual=w.getBlockAt(cell.x(),cell.y(),cell.z());var old=expected.stream().filter(c->c.x==cell.x() && c.y==cell.y() && c.z==cell.z()).findFirst().orElse(null);
            if(actual.getState(false) instanceof org.bukkit.block.TileState || (old==null?!actual.getType().isAir():!matchesExpected(old.after,actual.getBlockData().getAsString())))throw new IllegalArgumentException(Messages.text("Посторонний блок в проходе; сохранён без изменений"));
            String after=Bukkit.createBlockData(switch(cell.part()){case "door-lower"->palette.get("door")+"[facing="+cell.facing()+",half=lower]";case "door-upper"->palette.get("door")+"[facing="+cell.facing()+",half=upper]";case "gate"->palette.get("gate")+"[facing="+cell.facing()+"]";default->"air";}).getAsString();
            if(matchesExpected(after,actual.getBlockData().getAsString()))continue;String material=cell.part().equals("door-upper") || cell.part().equals("air")?null:costMaterial(Bukkit.createBlockData(after).getMaterial());p.blocks.add(new Village.Block(cell.x(),cell.y(),cell.z(),2,actual.getBlockData().getAsString(),after,material));if(material!=null)p.required.merge(material,1,Math::addExact);
        }
        if(p.blocks.isEmpty())throw new IllegalArgumentException(Messages.text("Проход уже готов"));
        for(var e:p.required.entrySet()){int n=Math.min(e.getValue(),Development.availableForBuilding(v,plugin.content.rules,e.getKey(),false));if(n>0){v.stock.take(e.getKey(),n);p.escrow.put(e.getKey(),n);}}
        if(p.funded())p.state="QUEUED";p.reason=Messages.text("Дверь и калитка между домом и огородом");v.projects.add(p);return p;
    }
    Project enclosure(Village v,Building b){
        Projects.requireSlot(v,plugin.content.rules,false);World w=Objects.requireNonNull(Bukkit.getWorld(v.world));var expected=Projects.expected(v,b.id);var palette=plugin.content.cultures.get(v.culture).palette();var p=new Project();p.kind=Messages.text("Ограда · ")+projectName(b.kind);p.building=b.id;p.priority=20;
        for(var c:Enclosures.cells(b,expected)){
            if(!w.isChunkLoaded(c.x()>>4,c.z()>>4))throw new IllegalArgumentException(Messages.text("Дождитесь загрузки участка"));checkGuards(w,c.x(),c.z(),c.x(),c.z());Block actual=w.getBlockAt(c.x(),c.y(),c.z());String after=Bukkit.createBlockData(c.gate()?palette.get("gate")+"[facing="+c.facing().toLowerCase(Locale.ROOT)+"]":palette.get("fence")).getAsString();
            if(matchesExpected(after,actual.getBlockData().getAsString()))continue;var old=expected.stream().filter(e->e.x==c.x() && e.y==c.y() && e.z==c.z()).findFirst().orElse(null);
            if(!replaceable(actual.getType()) && (old==null || !matchesExpected(old.after,actual.getBlockData().getAsString())))throw new IllegalArgumentException(Messages.text("Посторонний блок на месте ограды"));
            if(actual.getState(false) instanceof org.bukkit.block.TileState)throw new IllegalArgumentException(Messages.text("Хранилище нельзя заменить оградой"));String material=costMaterial(Bukkit.createBlockData(after).getMaterial());p.blocks.add(new Village.Block(c.x(),c.y(),c.z(),2,actual.getBlockData().getAsString(),after,material));p.required.merge(material,1,Math::addExact);
        }
        if(p.blocks.isEmpty()){b.enclosureVersion=1;return null;}
        for(var e:p.required.entrySet()){int n=Math.min(e.getValue(),Development.availableForBuilding(v,plugin.content.rules,e.getKey(),false));if(n>0){v.stock.take(e.getKey(),n);p.escrow.put(e.getKey(),n);}}
        if(p.funded())p.state="QUEUED";p.reason=Messages.text("Ограждение рабочего участка; нужны материалы");v.projects.add(p);return p;
    }
    private void planRoad(Village v) {
        World world=Objects.requireNonNull(Bukkit.getWorld(v.world));Project road=new Project();road.kind="road";road.starter=true;road.state="QUEUED";
        Building b=new Building();b.kind="road";b.x=v.x;b.z=v.z-3;b.y=v.y;b.width=1;b.depth=1;road.building=b.id;
        var sources=v.projects.stream().filter(p->!p.kind.equals("road")).toList();v.buildings.add(b);v.projects.add(road);
        for(Project source:sources)attachRoad(v,source.building(v),road,world,source.blocks);
        lighting.starter(v,road);
        if(!road.blocks.isEmpty()){b.x=road.blocks.stream().mapToInt(c->c.x).min().orElse(v.x);b.z=road.blocks.stream().mapToInt(c->c.z).min().orElse(v.z);b.width=road.blocks.stream().mapToInt(c->c.x).max().orElse(v.x)-b.x+1;b.depth=road.blocks.stream().mapToInt(c->c.z).max().orElse(v.z)-b.z+1;}
        road.escrow.putAll(road.required);
    }
    public int apply(Village v,int budget) {
        long before=appliedBlocks;
        int recovered=plugin.constructionRecovery.pulse(v,Math.max(0,budget));budget-=recovered;
        if(budget<=0)return recovered;
        if(v.paused || plugin.transactions.busy(v)||plugin.constructionRecovery.pending(v))return recovered;
        World w=Bukkit.getWorld(v.world);if(w==null)return recovered;
        Project p=Projects.next(v).orElse(null);
        if(p==null)return recovered;
        if(v.nutrition<.75){p.reason=Messages.text("Не хватает питания");return recovered;}
        if(plugin.npcs.danger(v)){p.reason=Messages.text("Опасность: ")+plugin.npcs.dangerReason(v);return recovered;}
        if(v.residents.stream().noneMatch(r->r.alive && v.adult(r) && (r.role==Role.BUILDER || r.role==Role.ELDER))){p.reason=Messages.text("Нет взрослого строителя");return recovered;}
        if(!plugin.npcs.builderPresent(v,p)){p.reason=plugin.npcs.constructionWait(v);return recovered;}
        if("water_supply".equals(p.building(v).recipe) && !p.building(v).resourceConfirmed){p.reason=Messages.text("Водозабор ожидает подтверждения источника");return recovered;}
        p.reason="";Map<String,String> fencePlan=null;
        while(budget-->0 && p.credit>=1 && p.index<p.blocks.size()) {
            var cell=p.blocks.get(p.index);
            if(!w.isChunkLoaded(cell.x>>4,cell.z>>4)){p.reason=Messages.text("Участок выгружен; труд накоплен");break;}
            Block block=w.getBlockAt(cell.x,cell.y,cell.z);String actual=block.getBlockData().getAsString();
            boolean plantsChanged=replaceable(Bukkit.createBlockData(cell.before).getMaterial()) && replaceable(block.getType());
            if(!plantsChanged && !matchesExpected(cell.before,actual) && !matchesExpected(cell.after,actual)){p.state="PAUSED";p.reason=Messages.text("Изменён блок ")+cell.x+" "+cell.y+" "+cell.z;break;}
            try{checkGuards(w,cell.x,cell.z,cell.x,cell.z);}catch(IllegalArgumentException e){p.reason=e.getMessage();break;}
            var nextData=Bukkit.createBlockData(cell.after);boolean change=!matchesExpected(cell.after,actual);if(FenceConnections.supported(nextData)){if(fencePlan==null)fencePlan=FenceConnections.plan(p.blocks);nextData=FenceConnections.shape(block,nextData,fencePlan);change=!nextData.equals(block.getBlockData());}Material nextMaterial=nextData.getMaterial();if(nextMaterial.isSolid() && change){double top=nextMaterial.name().endsWith("_FENCE") || nextMaterial.name().endsWith("_WALL")?1.5:1;var occupied=new org.bukkit.util.BoundingBox(cell.x,cell.y,cell.z,cell.x+1,cell.y+top,cell.z+1);boolean body=w.getNearbyEntities(new Location(w,cell.x+.5,cell.y+.5,cell.z+.5),1,2,1).stream().anyMatch(e->e instanceof org.bukkit.entity.LivingEntity && e.getBoundingBox().overlaps(occupied));if(body){plugin.npcs.clearConstruction(v,occupied);p.reason=Messages.text("Житель или игрок освобождает место для блока");break;}}
            if(cell.material!=null && p.escrow.getOrDefault(cell.material,0)<=0){p.reason=Messages.text("Нарушен резерв материалов");break;}
            try{Projects.validateNext(v,p);}catch(IllegalArgumentException e){p.reason=e.getMessage();break;}
            if(change)block.setBlockData(nextData,false);
            Projects.applied(v,p);appliedBlocks++;
        }
        if(p.index==p.blocks.size()) {
            Projects.invalidate(v);
            p.state="COMPLETE";p.reason=Messages.text("Завершён");Building b=p.building(v);b.complete=true;b.damaged=false;b.damageReason=null;Missions.check(v,plugin.content.rules);
            if(!Messages.startsWith(p.kind,"Ограда") && !p.kind.contains("_u") && !Messages.startsWith(p.kind,"Перестройка") && plugin.content.rules.recipes.containsKey(b.recipe) && plugin.content.rules.recipes.get(b.recipe).condition().equals("grove")){b.plantedDay=v.days;b.cycles=v.days+plugin.content.rules.recipes.get(b.recipe).renewDays();b.cycleReadyDay=b.cycles;}
            if(Messages.startsWith(p.kind,"Ограда"))b.enclosureVersion=1;else b.resourceConfirmed=confirmResource(v,b,w);
            Housing.reconcile(v);
            if(Messages.startsWith(p.kind,"Ремонт"))for(UUID contributor:p.contributors){v.relation(contributor).deeds.add("restoration");Reputation.award(v,contributor,plugin.content.rules,"restoration",10);}
            if(b.kind.startsWith("hall"))plugin.installTownMap(v);
            Jobs.reconcile(v,plugin.content.rules);Jobs.fill(v,plugin.content.rules);
            Missions.check(v,plugin.content.rules);
            plugin.save(v);
        }
        return recovered+Math.toIntExact(appliedBlocks-before);
    }
    public void inspect(Village v,int budget){
        World w=Bukkit.getWorld(v.world);if(w==null || plugin.transactions.busy(v))return;
        Set<UUID> seen=new HashSet<>();List<Project> completed=v.projects.reversed().stream().filter(p->p.state.equals("COMPLETE") && seen.add(p.building)).toList();
        if(completed.isEmpty())return;
        int which=inspectionProjects.merge(v.id,1,Integer::sum);
        for(Project p:List.of(completed.get(Math.floorMod(which,completed.size())))){
            Building b=p.building(v);if(b.owner!=null || b.damaged || !b.complete || v.projects.stream().anyMatch(q->q.active() && q.building.equals(b.id)))continue;
            List<Village.Block> expected=Projects.expected(v,b.id);int index=inspections.getOrDefault(b.id,0);
            while(budget-->0 && !expected.isEmpty()){
                var cell=expected.get(Math.floorMod(index++,expected.size()));
                if(Bukkit.createBlockData(cell.after).getMaterial().isAir() || !w.isChunkLoaded(cell.x>>4,cell.z>>4))continue;
                if(GroveVisuals.managed(b,w.getBlockAt(cell.x,cell.y,cell.z)))continue;
                Material expectedMaterial=Bukkit.createBlockData(cell.after).getMaterial();
                var actualBlock=w.getBlockAt(cell.x,cell.y,cell.z);if(actualBlock.getType()==expectedMaterial && FenceConnections.supported(actualBlock.getBlockData())){var shape=FenceConnections.shape(actualBlock,actualBlock.getBlockData(),Map.of());if(!shape.equals(actualBlock.getBlockData())){var box=new org.bukkit.util.BoundingBox(cell.x,cell.y,cell.z,cell.x+1,cell.y+1.5,cell.z+1);boolean occupied=w.getNearbyEntities(new Location(w,cell.x+.5,cell.y+.5,cell.z+.5),1,2,1).stream().anyMatch(e->e instanceof org.bukkit.entity.LivingEntity && e.getBoundingBox().overlaps(box));if(!occupied)actualBlock.setBlockData(shape,false);}}
                if(actualBlock.getType()!=expectedMaterial){b.damaged=true;b.damageReason=Messages.text("Повреждение ")+cell.x+" "+cell.y+" "+cell.z;break;}
            }inspections.put(b.id,index);if(budget<=0)break;
        }
    }
    public void repair(Player player,Village original,UUID buildingId){
        if(!Missions.repairAllowed(original,player.getUniqueId(),buildingId))TrustAccess.requireTasks(original,player.getUniqueId(),plugin.content.rules);
        Village next=Database.copy(original);Project repair=planRepair(next,buildingId);repair.contributors.add(player.getUniqueId());
        plugin.transactions.run(player,next,Coins.cloneItems(player.getInventory().getStorageContents()),Messages.text("Ремонт ")+buildingId,()->plugin.tell(player,Messages.text("Ремонт зарезервирован; материалы можно передать через проекты.")));
    }
    Project planRepair(Village next,UUID buildingId){
        Building b=next.buildings.stream().filter(k->k.id.equals(buildingId)).findFirst().orElseThrow();
        if(b.owner!=null || !b.damaged)throw new IllegalArgumentException(Messages.text("Этот объект не требует общего ремонта"));
        Projects.requireSlot(next,plugin.content.rules,true);
        if(next.projects.stream().anyMatch(p->p.building.equals(b.id) && p.active()))throw new IllegalArgumentException(Messages.text("У объекта уже есть незавершённый проект"));
        World w=Bukkit.getWorld(next.world);
        Project repair=new Project();repair.essential=true;repair.kind=Messages.text("Ремонт ")+projectName(b.kind);repair.priority=Repairs.priority(b,plugin.content.rules);repair.building=b.id;
        for(var cell:Projects.expected(next,b.id)){
            if(!w.isChunkLoaded(cell.x>>4,cell.z>>4))throw new IllegalArgumentException(Messages.text("Для осмотра загрузите участок"));
            Block current=w.getBlockAt(cell.x,cell.y,cell.z);if(plugin.hallDisplays.managed(next,current) || GroveVisuals.managed(b,current))continue;if(current.getType()==Bukkit.createBlockData(cell.after).getMaterial())continue;
            if(!replaceable(current.getType()))throw new IllegalArgumentException(Messages.text("На месте повреждения посторонний блок; ремонт остановлен"));
            var data=Bukkit.createBlockData(cell.after);String material=costMaterial(data.getMaterial());
            if(data instanceof org.bukkit.block.data.type.Door door && door.getHalf()==org.bukkit.block.data.Bisected.Half.TOP || data instanceof org.bukkit.block.data.type.Bed bed && bed.getPart()==org.bukkit.block.data.type.Bed.Part.HEAD)material=null;
            var replacement=new Village.Block(cell.x,cell.y,cell.z,cell.phase,current.getBlockData().getAsString(),cell.after,material);replacement.floor=cell.floor;repair.blocks.add(replacement);if(material!=null)repair.required.merge(material,1,Integer::sum);
        }
        if(repair.blocks.isEmpty())throw new IllegalArgumentException(Messages.text("Повреждений не найдено"));
        for(var e:repair.required.entrySet()){int n=Math.min(e.getValue(),Development.availableForBuilding(next,plugin.content.rules,e.getKey(),true));if(n>0){next.stock.take(e.getKey(),n);repair.escrow.put(e.getKey(),n);}}
        if(repair.funded()){repair.state="QUEUED";repair.reason=Messages.text("Очередь ремонта");}
        next.projects.add(repair);b.complete=false;Housing.reconcile(next);return repair;
    }
    private final Deque<ResourceSurvey> resourceSurveys=new ArrayDeque<>();
    private final Set<UUID> surveyingResources=new HashSet<>();
    private record ResourceSurvey(UUID village,UUID building,String condition,int minX,int maxX,int minZ,int maxZ,int minY,int maxY,int index,Set<String> found){
        ResourceSurvey next(int cursor){return new ResourceSurvey(village,building,condition,minX,maxX,minZ,maxZ,minY,maxY,cursor,found);}
    }
    public boolean confirmResource(Village v,Building b,World w) {
        var recipe=plugin.content.rules.recipes.get(b.recipe);if(recipe==null)return false;
        if(recipe.condition().equals("land")){b.resourceSurveyed=true;return true;}
        if(recipe.condition().equals("grove")){b.resourceSurveyed=true;return b.trees.size()>=2 && b.trees.stream().allMatch(t->w.isChunkLoaded(t.x>>4,t.z>>4) && Set.of(Material.DIRT,Material.GRASS_BLOCK,Material.PODZOL,Material.ROOTED_DIRT).contains(w.getBlockAt(t.x,t.y-1,t.z).getType()));}
        if(b.resourceSurveyed)return b.resourceConfirmed;
        if(surveyingResources.add(b.id)){
            boolean garden=Set.of("irrigated","flowers").contains(recipe.condition());
            int distance=recipe.condition().equals("water_source")?plugin.content.config.getInt("water.source-search-radius",16):10;
            int minX=garden?b.x:b.x-distance,maxX=garden?b.x+b.width-1:b.x+distance;
            int minZ=garden?b.z:b.z-distance,maxZ=garden?b.z+b.depth-1:b.z+distance;
            resourceSurveys.add(new ResourceSurvey(v.id,b.id,recipe.condition(),minX,maxX,minZ,maxZ,Math.max(w.getMinHeight(),b.y-(recipe.condition().equals("water_source")?4:plugin.content.config.getInt("autonomy.prospect-depth",24))),Math.min(w.getMaxHeight()-1,b.y+5),0,new HashSet<>()));
        }
        return false;
    }
    public void resourcePulse(){
        int budget=plugin.content.config.getInt("limits.resource-blocks-per-pass",128),turns=Math.min(resourceSurveys.size(),plugin.content.config.getInt("limits.resource-jobs-per-pass",16));
        while(budget>0 && turns-->0 && !resourceSurveys.isEmpty()){
            var job=resourceSurveys.remove();Village v=plugin.villages.get(job.village());
            if(v==null){surveyingResources.remove(job.building());continue;}
            if(plugin.transactions.busy(v)){resourceSurveys.add(job);continue;}
            Building b=v.buildings.stream().filter(k->k.id.equals(job.building())).findFirst().orElse(null);
            if(b==null || !b.complete && v.projects.stream().noneMatch(p->p.building.equals(b.id) && (p.state.equals("SURVEY") || job.condition().equals("water_source") && p.active()))){surveyingResources.remove(job.building());continue;}
            World w=Bukkit.getWorld(v.world);if(w==null || plugin.transactions.busy(v)){resourceSurveys.add(job);continue;}
            int width=job.maxX()-job.minX()+1,depth=job.maxZ()-job.minZ()+1,height=job.maxY()-job.minY()+1,index=job.index(),size=width*depth*height;
            Set<String> other=new HashSet<>();for(var village:plugin.villages.values())if(village.world.equals(v.world) && village.x+village.radius>=job.minX() && village.x-village.radius<=job.maxX() && village.z+village.radius>=job.minZ() && village.z-village.radius<=job.maxZ())for(Building existing:village.buildings)if(!existing.id.equals(b.id))other.addAll(existing.resourceCells);
            while(budget>0 && index<size){
                int x=job.minX()+index%width,z=job.minZ()+index/width%depth,y=job.minY()+index/(width*depth);
                if(!w.isChunkLoaded(x>>4,z>>4))break;
                index++;budget--;if(Math.abs(x-v.x)>v.radius || Math.abs(z-v.z)>v.radius)continue;
                String key=x+":"+y+":"+z;if(other.contains(key))continue;
                boolean natural=!Set.of("irrigated","water","flowers").contains(job.condition());
                if(natural && v.buildings.stream().anyMatch(existing->!existing.kind.equals("road") && Territory.plot(existing,x,z)))continue;
                if(job.condition().equals("water_source")){var square=WaterSources.square(plugin,v,w,x,y,z);if(!square.isEmpty() && Collections.disjoint(square,other)){job.found().addAll(square);index=size;}continue;}
                Material material=w.getBlockAt(x,y,z).getType();boolean matches=switch(job.condition()){
                    case "irrigated","water"->material==Material.WATER;
                    case "forest"->material.name().endsWith("_LOG");
                    case "flowers"->Tag.FLOWERS.isTagged(material);
                    case "dirt"->Set.of(Material.DIRT,Material.GRASS_BLOCK,Material.COARSE_DIRT,Material.ROOTED_DIRT).contains(material);
                    case "stone"->material==Material.STONE || material==Material.ANDESITE || material==Material.GRANITE;
                    case "iron"->material==Material.IRON_ORE || material==Material.DEEPSLATE_IRON_ORE;
                    case "clay"->material==Material.CLAY;
                    case "sand"->material==Material.SAND || material==Material.RED_SAND;
                    default->false;
                };
                if(matches)job.found().add(key);
            }
            if(index<size){resourceSurveys.add(job.next(index));continue;}
            job.found().removeAll(other);var recipe=plugin.content.rules.recipes.get(b.recipe);
            int batch=recipe==null?1:recipe.output().values().stream().mapToInt(Integer::intValue).sum();if(job.condition().equals("clay"))batch=Math.max(1,(batch+3)/4);
            boolean finite=Set.of("stone","iron","clay","sand","dirt").contains(job.condition());
            int required=finite?batch:Set.of("forest","flowers").contains(job.condition())?8:job.condition().equals("water")?16:1;
            b.resourceConfirmed=job.found().size()>=required;b.resourceSurveyed=true;
            if(finite)b.resourceLeft=job.found().size()/Math.max(1,batch);
            if(b.resourceConfirmed)b.resourceCells.addAll(job.found());
            var pending=v.projects.stream().filter(p->p.building.equals(b.id) && p.state.equals("SURVEY")).findFirst().orElse(null);
            if(pending!=null){if(b.resourceConfirmed){pending.state="COLLECTING";pending.reason=job.condition().equals("water_source")?Messages.text("Возобновляемый источник воды подтверждён"):Messages.text("Месторождение подтверждено: ")+b.resourceLeft+Messages.text(" партий");}else{v.failedSites.add(b.kind.replace("_terrace","").replace("_pier","")+":"+b.x+":"+b.z);pending.state="SITE_BLOCKED";pending.reason=Messages.text("Ресурс не найден: цель сохранена, ищем другой участок; материалы не потрачены");v.developmentReason=pending.reason;}}
            surveyingResources.remove(b.id);plugin.save(v);
        }
    }
    public static String costMaterial(Material m) {
        if(m.isAir())return null;
        return switch(m){case WATER->"WATER_BUCKET";case FARMLAND,DIRT_PATH,GRASS_BLOCK->"DIRT";case CARROTS->"CARROT";case POTATOES->"POTATO";case BEETROOTS->"BEETROOT_SEEDS";case WHEAT->"WHEAT_SEEDS";case SWEET_BERRY_BUSH->"SWEET_BERRIES";case WALL_TORCH->"TORCH";default->m.name().endsWith("_WALL_SIGN")?m.name().replace("_WALL_SIGN","_SIGN"):m.name();};
    }
    static boolean matchesExpected(String expected,String actual){
        if(expected==null)return false;if(expected.equals(actual))return true;
        var left=Bukkit.createBlockData(expected);var right=Bukkit.createBlockData(actual);if(left.getMaterial()!=right.getMaterial())return false;
        if(left instanceof org.bukkit.block.data.type.Leaves a && right instanceof org.bukkit.block.data.type.Leaves b){b.setDistance(a.getDistance());return left.equals(right);}
        if(left instanceof org.bukkit.block.data.type.Wall a && right instanceof org.bukkit.block.data.type.Wall b){for(var side:List.of(org.bukkit.block.BlockFace.NORTH,org.bukkit.block.BlockFace.SOUTH,org.bukkit.block.BlockFace.EAST,org.bukkit.block.BlockFace.WEST))b.setHeight(side,a.getHeight(side));b.setUp(a.isUp());return left.equals(right);}
        if(FenceConnections.supported(left)&&left instanceof org.bukkit.block.data.MultipleFacing a&&right instanceof org.bukkit.block.data.MultipleFacing b){for(var face:a.getAllowedFaces())b.setFace(face,a.hasFace(face));return left.equals(right);}
        if(left instanceof org.bukkit.block.data.type.Farmland a && right instanceof org.bukkit.block.data.type.Farmland b){b.setMoisture(a.getMoisture());return left.equals(right);}
        if(left instanceof org.bukkit.block.data.type.Fence a && right instanceof org.bukkit.block.data.type.Fence b){for(var face:a.getAllowedFaces())b.setFace(face,a.hasFace(face));return left.equals(right);}
        if(left instanceof org.bukkit.block.data.type.Door a && right instanceof org.bukkit.block.data.type.Door b){b.setOpen(a.isOpen());b.setPowered(a.isPowered());return left.equals(right);}
        if(left instanceof org.bukkit.block.data.type.Gate a && right instanceof org.bukkit.block.data.type.Gate b){b.setOpen(a.isOpen());b.setPowered(a.isPowered());return left.equals(right);}return false;
    }
    private static boolean natural(Material m){return Terrain.ground(m);}
    private boolean replaceable(Material m){return terrain.replaceable(m);}
}
