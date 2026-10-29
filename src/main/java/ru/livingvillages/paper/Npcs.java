package ru.livingvillages.paper;

import ru.livingvillages.i18n.Messages;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Gate;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.event.world.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.attribute.Attribute;
import ru.livingvillages.domain.*;
import com.destroystokyo.paper.event.entity.EntityPathfindEvent;
import java.util.*;
import static ru.livingvillages.domain.Village.*;

/** Bounded entity views of the saved residents. Vanilla navigation cannot replace our destinations. */
public final class Npcs implements Listener {
    private final LivingVillagesPlugin plugin;
    final NpcSafety safety;private final NpcCollision collision;
    private final NamespacedKey villageKey=new NamespacedKey("livingvillages","village"),residentKey=new NamespacedKey("livingvillages","resident");
    private final Map<UUID,Villager> active=new HashMap<>();
    private final Map<UUID,Long> cooldown=new HashMap<>(),dangerUntil=new HashMap<>(),nextStrike=new HashMap<>();
    private final Map<UUID,Set<UUID>> threats=new HashMap<>();
    private final Map<UUID,String> threatSource=new HashMap<>();
    /** Last observed position only: alarm sharing never follows unseen entities or loads chunks. */
    private final Map<UUID,Location> alarmPoints=new HashMap<>();
    private final Map<UUID,Long> nextLook=new HashMap<>();
    public long replans;
    private final Map<UUID,Long> crowded=new HashMap<>();
    private final Map<UUID,Motion> motions=new HashMap<>();
    private final Map<UUID,String> waiting=new HashMap<>();
    private final Set<UUID> ownPaths=new HashSet<>(),deferred=new LinkedHashSet<>();
    private final Map<String,DoorHold> doors=new LinkedHashMap<>();
    private boolean initialized;private int cursor,motionCursor;
    private final Set<UUID> moving=new LinkedHashSet<>();
    public long constructionClearances;
    private record Detour(Routines.Target target,long until){}
    private final Map<UUID,Detour> constructionDetours=new HashMap<>();
    private final Map<UUID,Long> strandedSince=new HashMap<>();
    public long spawned,recoveries,pathsRequested,vanillaPathsBlocked,rescues,unsafeSteps,unstacked;
    private static final class Motion {Routines.Target target;Location goal,last;long progress,requested,settledUntil;boolean manual;double speed=.7,groundY=Double.NaN;String status=Messages.text("Появляется");List<Location> route=List.of();int node;}
    private record DoorHold(UUID village,int x,int y,int z,long until){}
    private record Waypoint(Location at,boolean doorway){}
    public Npcs(LivingVillagesPlugin plugin){this.plugin=plugin;safety=new NpcSafety(plugin);collision=new NpcCollision();}
    public int count(){active.entrySet().removeIf(e->!e.getValue().isValid());return active.size();}
    public boolean isNpc(Entity e){return e.getPersistentDataContainer().has(residentKey,PersistentDataType.STRING);}
    public UUID villageId(Entity e){String id=e.getPersistentDataContainer().get(villageKey,PersistentDataType.STRING);try{return id==null?null:UUID.fromString(id);}catch(Exception ignored){return null;}}
    public Resident resident(Entity e){Village v=plugin.villages.get(villageId(e));if(v==null)return null;try{return v.resident(UUID.fromString(e.getPersistentDataContainer().get(residentKey,PersistentDataType.STRING)));}catch(Exception ignored){return null;}}
    public boolean danger(Village v){return dangerUntil.getOrDefault(v.id,0L)>System.nanoTime();}
    public String dangerReason(Village v){return danger(v)?threatSource.getOrDefault(v.id,Messages.text("проверяется последнее наблюдение")):Messages.text("Угроз не замечено");}
    public boolean isThreat(Village v,UUID enemy){return threats.getOrDefault(v.id,Set.of()).contains(enemy);}
    public int hostiles(Village v){Set<UUID> ids=threats.computeIfAbsent(v.id,k->new HashSet<>());ids.removeIf(id->{Entity e=Bukkit.getEntity(id);return e==null || !e.isValid() || e.isDead() || !e.getWorld().getUID().equals(v.world) || Math.hypot(e.getX()-v.x,e.getZ()-v.z)>v.radius+16;});return ids.size();}
    public String status(Resident r){Motion m=motions.get(r.id);return waiting.getOrDefault(r.id,m==null?Messages.text("Ожидает загруженную территорию"):m.status);}
    String routeStatus(Resident r){Motion m=motions.get(r.id);return m==null?Messages.text("нет маршрута"):"goal="+(m.goal==null?null:m.goal.toVector())+" node="+m.node+" route="+m.route.stream().skip(m.node).limit(4).map(Location::toVector).toList();}
    Villager view(UUID id){return active.get(id);}
    public List<String> report(Village v){World w=Bukkit.getWorld(v.world);long views=v.residents.stream().filter(r->r.alive && active.containsKey(r.id) && active.get(r.id).isValid()).count();var lines=new ArrayList<String>();lines.add(v.name+Messages.text(": живых ")+v.living()+", NPC "+views);lines.add(w==null?Messages.text("Мир недоступен: ")+Objects.toString(v.worldName,v.world.toString()):Messages.text("Мир: ")+w.getName()+Messages.text("; восстановлений после застревания: ")+recoveries+Messages.text("; выходов из ям: ")+rescues+Messages.text("; разделений толпы: ")+unstacked+Messages.text("; отходов от стройки: ")+constructionClearances);if(danger(v))lines.add(Messages.text("Тревога: ")+threatSource.getOrDefault(v.id,Messages.text("ожидается проверка"))+Messages.text("; ещё ")+Math.max(0,(dangerUntil.get(v.id)-System.nanoTime())/1_000_000_000L)+Messages.text(" с"));for(var r:v.residents)if(r.alive){var npc=active.get(r.id);lines.add(r.name+" · "+plugin.residentRoleName(v,r)+": "+status(r)+(npc==null?"":" ["+npc.getLocation().getBlockX()+", "+npc.getLocation().getBlockY()+", "+npc.getLocation().getBlockZ()+"]"));}return lines;}
    public void repair(Village v){reconcileLoaded();for(var r:v.residents){motions.remove(r.id);waiting.remove(r.id);}}
    public boolean builderPresent(Village v,Project p){var b=p.building(v);return v.residents.stream().filter(r->r.alive && v.adult(r) && (r.role==Role.BUILDER || r.role==Role.ELDER)).map(r->active.get(r.id)).filter(Objects::nonNull).filter(Entity::isValid).filter(e->{var resident=resident(e);var motion=resident==null?null:motions.get(resident.id);return motion!=null && motion.target!=null && (motion.target.activity()==Routines.Activity.BUILD && b.id.equals(motion.target.building()) || constructionDetours.containsKey(resident.id));}).anyMatch(e->e.getWorld().getUID().equals(v.world) && Math.abs(e.getX()-(b.x+b.width/2.0))<=b.width/2.0+8 && Math.abs(e.getZ()-(b.z+b.depth/2.0))<=b.depth/2.0+8);}
    String constructionWait(Village v){boolean rest=false,meeting=false,assigned=false;for(var r:v.residents)if(r.alive && v.adult(r) && (r.role==Role.BUILDER || r.role==Role.ELDER)){var m=motions.get(r.id);if(m!=null && m.target!=null){rest|=m.target.activity()==Routines.Activity.REST;meeting|=m.target.activity()==Routines.Activity.MEET;assigned|=m.target.activity()==Routines.Activity.BUILD;}}return assigned?Messages.text("Строитель идёт к участку; состояние: /lv npc status"):rest?Messages.text("Ночной отдых; обычная стройка продолжится утром"):meeting?Messages.text("Общий сбор; обычная стройка продолжится после него"):Messages.text("Ожидание строителя и загруженного прохода к участку");}
    public void pulse(){
        if(!initialized)return;List<Village> list=new ArrayList<>(plugin.villages.values());if(list.isEmpty())return;
        List<Map.Entry<Village,Resident>> all=new ArrayList<>();for(var v:list)for(var r:v.residents)all.add(Map.entry(v,r));
        int budget=plugin.content.config.getInt("limits.npc-per-pass",16),spawns=plugin.content.config.getInt("limits.npc-spawns-per-pass",1),paths=plugin.content.config.getInt("limits.npc-paths-per-pass",8);
        long deadline=System.nanoTime()+plugin.content.config.getLong("limits.npc-budget-ms",4)*1_000_000;Set<UUID> scanned=new HashSet<>(),prepared=new HashSet<>();
        for(int i=0;i<Math.min(budget,all.size());i++){
            if(i>0 && System.nanoTime()>deadline)break;var entry=all.get(Math.floorMod(cursor++,all.size()));Village v=entry.getKey();Resident r=entry.getValue();Villager npc=active.get(r.id);
            if(!r.alive){if(npc!=null){collision.leave(npc);npc.remove();}active.remove(r.id);motions.remove(r.id);waiting.remove(r.id);constructionDetours.remove(r.id);strandedSince.remove(r.id);continue;}
            if(plugin.transactions.busy(v)){waiting.put(r.id,Messages.text("Операция сохраняется"));continue;}
            if(prepared.add(v.id)){if(v.residents.stream().noneMatch(k->k.alive&&v.adult(k)&&k.role==Role.MERCHANT))Simulation.electReplacement(v,plugin.content.rules);Vitals.reconcile(v,plugin.content.rules);Jobs.reconcile(v,plugin.content.rules);Autonomy.assign(v,plugin.content.rules);}
            World w=Bukkit.getWorld(v.world);if(w==null){waiting.put(r.id,Messages.text("Мир деревни не загружен или отсутствует"));continue;}v.worldName=w.getName();
            if(npc!=null && !npc.isValid()){collision.leave(npc);active.remove(r.id);r.entity=null;npc=null;}
            long slot=System.nanoTime()/(Math.max(2,plugin.content.config.getLong("residents.activity.walk-interval-seconds",4))*1_000_000_000L);
            long workSlot=slot;
            Routines.Target target=Routines.plan(v,r,w.getTime(),slot,workSlot,plugin.content.rules,danger(v));
            if(npc==null){if(spawns<=0){waiting.put(r.id,Messages.text("Ожидает очереди появления"));continue;}Location at=spawnPoint(v,r,w,target);if(at==null){waiting.put(r.id,hasLoadedPlace(v,r,w,target)?Messages.text("Нет безопасного места; проверьте проходы и пол"):Messages.text("Ожидает загрузки дома или рабочего участка"));continue;}
                npc=w.spawn(at,Villager.class,entity->{entity.getPersistentDataContainer().set(villageKey,PersistentDataType.STRING,v.id.toString());entity.getPersistentDataContainer().set(residentKey,PersistentDataType.STRING,r.id.toString());configure(entity,v,r);});active.put(r.id,npc);r.entity=npc.getUniqueId();spawns--;spawned++;motions.remove(r.id);
            }
            waiting.remove(r.id);if(npc.isInsideVehicle())npc.leaveVehicle();if(!npc.getPassengers().isEmpty())npc.eject();if(separate(v,r,npc))continue;if(fallen(v,r,npc) && rescue(v,r,npc))continue;applyVitals(npc,v,r);npc.customName(Component.text(r.name+" · "+(v.adult(r)?plugin.residentRoleName(v,r):Messages.text("ребёнок"))));remember(v,r,npc);
            if(scanned.add(v.id))hostiles(v);Location pos=npc.getLocation();int radius=plugin.content.config.getInt("guards.detection-radius",24);
            LivingEntity enemy=w.getNearbyEntities(pos,radius,plugin.guards.vertical(r),radius).stream().filter(e->e instanceof LivingEntity).map(e->(LivingEntity)e).filter(e->plugin.guards.enemy(v,r,npcView(r),e)).min(Comparator.comparingDouble(e->e.getLocation().distanceSquared(pos))).orElse(null);
            if(enemy!=null){alarmPoints.put(v.id,enemy.getLocation().clone());threatSource.put(v.id,plugin.guards.name(enemy)+Messages.text(" на ")+enemy.getLocation().getBlockX()+", "+enemy.getLocation().getBlockY()+", "+enemy.getLocation().getBlockZ()+Messages.text("; заметил ")+r.name);dangerUntil.put(v.id,System.nanoTime()+plugin.content.config.getLong("guards.alarm-seconds",10)*1_000_000_000L);threats.computeIfAbsent(v.id,k->new HashSet<>()).add(enemy.getUniqueId());}
            if(v.adult(r) && (r.role==Role.GUARD || enemy instanceof Player) && enemy!=null){if(pos.distanceSquared(enemy.getLocation())<(enemy instanceof Creeper?16:7)){hold(npc);motions.computeIfAbsent(r.id,k->new Motion()).status=Messages.text("Защищает жителей");long now=System.nanoTime();if(now>=nextStrike.getOrDefault(r.id,0L)){double damage=plugin.guards.melee(v,r);if(damage>0)enemy.damage(damage,npc);nextStrike.put(r.id,now+plugin.content.config.getLong("guards.attack-millis",1000)*1_000_000L);if(enemy instanceof Creeper){var push=enemy.getLocation().toVector().subtract(pos.toVector()).setY(0);if(push.lengthSquared()>0)enemy.setVelocity(push.normalize().multiply(.7).setY(.2));}}}else paths-=walk(v,r,npc,new Routines.Target(Routines.Activity.PATROL,null,enemy.getLocation().getBlockX(),enemy.getLocation().getBlockY(),enemy.getLocation().getBlockZ()),paths,true);continue;}
            if(enemy==null && v.adult(r) && r.role==Role.GUARD && danger(v)){
                Location observed=alarmPoints.get(v.id);
                if(observed!=null && observed.getWorld().equals(w) && Territory.inside(v,v.world,observed.getX(),observed.getZ())){
                    paths-=walk(v,r,npc,new Routines.Target(Routines.Activity.PATROL,null,observed.getBlockX(),observed.getBlockY(),observed.getBlockZ()),paths,true);continue;
                }
            }
            if(r.role!=Role.GUARD && (danger(v) || target.activity()==Routines.Activity.REST && motions.containsKey(r.id) && motions.get(r.id).target!=null && motions.get(r.id).target.activity()==Routines.Activity.SHELTER))target=emergency(v,r,npc,Routines.plan(v,r,w.getTime(),slot,workSlot,plugin.content.rules,true));
            paths-=walk(v,r,npc,target,paths,false);
        }
        active.entrySet().removeIf(e->!e.getValue().isValid());alarmPoints.keySet().removeIf(id->!plugin.villages.containsKey(id)||dangerUntil.getOrDefault(id,0L)<=System.nanoTime());closeDoors();
    }
    private int walk(Village v,Resident r,Villager npc,Routines.Target target,int pathBudget,boolean combat){
        Motion m=motions.computeIfAbsent(r.id,k->new Motion());World w=npc.getWorld();Location pos=npc.getLocation();long now=System.nanoTime();
        boolean sameJob=m.target!=null && m.target.activity()==target.activity() && Objects.equals(m.target.building(),target.building());
        if(!combat && sameJob && m.goal!=null && (now<m.settledUntil || pos.distanceSquared(m.goal)>arrival(r.id,m.target) && now-m.progress<4_000_000_000L))target=m.target;
        if(!target.equals(m.target) || m.goal==null || !safeFeet(v,w,m.goal)){long previous=m.target!=null && m.target.activity()==target.activity() && Objects.equals(m.target.building(),target.building())?m.progress:now;hold(npc);m.target=target;m.goal=destination(v,w,target,r.id);m.progress=previous;m.requested=0;m.settledUntil=0;m.last=pos.clone();}
        if(m.goal==null){hold(npc);m.status=Routines.label(target.activity())+Messages.text(": участок выгружен или занят");return 0;}
        if(!loadedLine(w,pos,m.goal)){hold(npc);m.status=Messages.text("Маршрут ждёт загруженных чанков");return 0;}
        double distance=pos.distanceSquared(m.goal);if(distance<=arrival(r.id,target)){if(m.settledUntil==0)m.settledUntil=now+1_000_000_000L;hold(npc);m.progress=now;m.last=pos.clone();m.status=Routines.label(target.activity());lookAround(r,npc);return 0;}
        if(m.last==null || Math.pow(m.last.getX()-pos.getX(),2)+Math.pow(m.last.getZ()-pos.getZ(),2)>.09){m.last=pos.clone();m.progress=now;}
        if(now-m.progress>4_000_000_000L){hold(npc);m.requested=0;m.progress=now;replans++;m.status=Messages.text("Проверяет другой проход");}
        openDoors(v,npc);openDestinationGates(v,npc,target.building());if(!Objects.equals(target.building(),r.workplace))openDestinationGates(v,npc,r.workplace);for(var plot:v.buildings)if(plot.complete && plot.owner==null && !plot.kind.equals("road") && Territory.plot(plot,pos.getBlockX(),pos.getBlockZ())){openDestinationGates(v,npc,plot.id);break;}
        if(constructionDetours.containsKey(r.id) && retreatClear(w,pos,m.goal)){m.manual=true;m.route=List.of(m.goal.clone());m.node=0;m.requested=now;m.speed=plugin.content.config.getDouble("residents.routine.walk-speed",.7);moving.add(r.id);m.status=Messages.text("Освобождает место для стройки");return 0;}
        if(pathBudget<=0){m.status=Messages.text("Ожидает очереди маршрутов");return 0;}
        if(m.node<m.route.size() && now-m.requested<3_000_000_000L && (m.manual || npc.getPathfinder().hasPath())){m.status=Messages.text("Идёт: ")+Routines.label(target.activity()).toLowerCase(Locale.ROOT);return 0;}
        if(now-m.requested<1_500_000_000L)return 0;
        m.requested=now;npc.setAI(true);npc.setAware(true);ownPaths.add(npc.getUniqueId());
        try{var path=npc.getPathfinder().findPath(m.goal);pathsRequested++;if(path==null || path.getPoints().isEmpty() || path.getPoints().stream().anyMatch(p->!w.isChunkLoaded(p.getBlockX()>>4,p.getBlockZ()>>4) || !Territory.inside(v,v.world,p.getX(),p.getZ()))){hold(npc);m.status=Messages.text("Проход недоступен; повторная проверка");return 1;}var points=path.getPoints();for(int i=1;i<points.size();i++)if(points.get(i-1).getY()-points.get(i).getY()>plugin.content.config.getDouble("residents.routine.max-step-drop",1.25)){hold(npc);m.status=Messages.text("Маршрут отклонён: опасный спуск");return 1;}m.route=points.stream().map(point->new Location(w,point.getBlockX()+.5,point.getY(),point.getBlockZ()+.5)).toList();m.node=0;m.speed=combat?Math.max(1,plugin.content.config.getDouble("residents.routine.walk-speed",.7)*1.6):plugin.content.config.getDouble("residents.routine.walk-speed",.7);m.manual=false;if(!npc.getPathfinder().moveTo(path,m.speed)){hold(npc);m.status=Messages.text("Не удалось начать маршрут; повторная проверка");return 1;}moving.add(r.id);m.status=Messages.text("Идёт: ")+Routines.label(target.activity()).toLowerCase(Locale.ROOT);return 1;}
        finally{ownPaths.remove(npc.getUniqueId());}
    }
    public void motionPulse(){
        if(!initialized || active.isEmpty())return;var ids=new ArrayList<>(active.keySet());int limit=plugin.content.config.getInt("limits.npc-motion-per-pass",32);long end=System.nanoTime()+plugin.content.config.getLong("limits.npc-motion-budget-ms",2)*1_000_000;
        for(int i=0;i<Math.min(limit,ids.size());i++){if(i>0 && System.nanoTime()>end)break;UUID id=ids.get(Math.floorMod(motionCursor++,ids.size()));Villager npc=active.get(id);if(npc!=null && npc.isValid() && !moving.contains(id)){var resident=resident(npc);if(resident!=null)lookAround(resident,npc);continue;}Motion m=motions.get(id);if(npc==null || !npc.isValid() || m==null || m.goal==null){moving.remove(id);continue;}var v=plugin.villages.get(villageId(npc));if(v==null || plugin.transactions.busy(v)){hold(npc);continue;}Location at=npc.getLocation();if(at.distanceSquared(m.goal)<=arrival(id,m.target)){hold(npc);continue;}
            World w=npc.getWorld();if(!m.manual){var path=npc.getPathfinder().getCurrentPath();if(path==null || path.getNextPoint()==null){moving.remove(id);m.requested=0;continue;}m.node=Math.min(path.getNextPointIndex(),m.route.size()-1);}
            while(m.manual && m.node<m.route.size()){var point=waypoint(w,m.route.get(m.node));Location next=point.at;double dx=next.getX()-at.getX(),dz=next.getZ()-at.getZ();if(dx*dx+dz*dz<(point.doorway?.005:.09) && Math.abs(next.getY()-at.getY())<1.1)m.node++;else break;}
            if(m.node>=m.route.size()){hold(npc);continue;}Location next=waypoint(w,m.route.get(m.node)).at;if(!w.isChunkLoaded(next.getBlockX()>>4,next.getBlockZ()>>4)){hold(npc);m.status=Messages.text("Маршрут ждёт загруженных чанков");continue;}
            double dx=next.getX()-at.getX(),dz=next.getZ()-at.getZ(),length=Math.sqrt(dx*dx+dz*dz);if(length<.01)continue;
            double look=Math.min(.55,length);int aheadX=(int)Math.floor(at.getX()+dx/length*look),aheadZ=(int)Math.floor(at.getZ()+dz/length*look);
            if(!w.isChunkLoaded(aheadX>>4,aheadZ>>4)){hold(npc);m.status=Messages.text("Маршрут ждёт загруженных чанков");continue;}
            double speed=Math.min(length,m.speed*.24),vertical=npc.getVelocity().getY();
            double support=safety.floor(w,at.getX(),at.getY(),at.getZ(),.12),ahead=safety.floor(w,at.getX()+dx/length*.55,at.getY()+1.1,at.getZ()+dz/length*.55,1.25);double rise=ahead-at.getY();
            if(Double.isFinite(support) && Math.abs(at.getY()-support)<.06 && vertical<=0 && rise>.6 && rise<=1.05)vertical=.42;
            double ground=safety.floor(w,at.getX(),at.getY(),at.getZ(),.2);if(Double.isFinite(ground) && (!Double.isFinite(m.groundY) || m.groundY-ground<=plugin.content.config.getDouble("residents.routine.max-step-drop",1.25)))m.groundY=ground;else if(!Double.isFinite(m.groundY))m.groundY=at.getY();
            double advance=Math.min(length,speed*2);if(!safety.step(w,at,dx/length*advance,dz/length*advance,m.groundY)){hold(npc);m.status=Messages.text("Остановился у опасного края; ищет другой путь");unsafeSteps++;continue;}
            if(!m.manual){npc.lookAt(next.getX(),next.getY()+1.4,next.getZ());continue;}
            npc.setRotation((float)Math.toDegrees(Math.atan2(-dx,dz)),0);npc.setVelocity(new org.bukkit.util.Vector(dx/length*speed,vertical,dz/length*speed));
        }
    }
    /** An open door still occupies 3/16 of a block. Align before entering its narrow passage. */
    private Waypoint waypoint(World w,Location node){Location point=node.clone();int x=node.getBlockX(),y=node.getBlockY(),z=node.getBlockZ();for(int[] offset:new int[][]{{0,0},{0,-1},{0,1},{-1,0},{1,0}}){int bx=x+offset[0],bz=z+offset[1];if(!w.isChunkLoaded(bx>>4,bz>>4))continue;Block block=w.getBlockAt(bx,y,bz);if(!(block.getBlockData() instanceof Door door) || !door.isOpen() || door.getHalf()!=org.bukkit.block.data.Bisected.Half.BOTTOM)continue;var box=block.getBoundingBox();if(bx==x && box.getWidthX()<.5){point.setX(box.getCenterX()<bx+.5?(box.getMaxX()+bx+1)/2:(bx+box.getMinX())/2);return new Waypoint(point,true);}if(bz==z && box.getWidthZ()<.5){point.setZ(box.getCenterZ()<bz+.5?(box.getMaxZ()+bz+1)/2:(bz+box.getMinZ())/2);return new Waypoint(point,true);}}return new Waypoint(point,false);}
    private void hold(Villager npc){var record=resident(npc);if(record!=null){moving.remove(record.id);Motion m=motions.get(record.id);if(m!=null){m.route=List.of();m.node=0;}}npc.getPathfinder().stopPathfinding();if(!npc.hasAI())npc.setAI(true);npc.setAware(true);var velocity=npc.getVelocity();if(velocity.getX()!=0 || velocity.getZ()!=0)npc.setVelocity(velocity.setX(0).setZ(0));}
    private Villager npcView(Resident r){return active.get(r.id);}
    private void lookAround(Resident r,Villager npc){long now=System.nanoTime();if(now<nextLook.getOrDefault(r.id,0L))return;nextLook.put(r.id,now+Math.max(250,plugin.content.config.getLong("residents.activity.look-interval-millis",750))*1_000_000L);
        double angle=Math.floorMod(r.id.hashCode()+(int)(now/1_000_000_000L)*47,360)*Math.PI/180;Location at=npc.getLocation();var nearby=npc.getWorld().getNearbyEntities(at,4,2,4).stream().filter(e->e instanceof Player && e.isValid()).findFirst().orElse(null);
        double x=nearby==null?at.getX()+Math.cos(angle)*3:nearby.getX(),z=nearby==null?at.getZ()+Math.sin(angle)*3:nearby.getZ();npc.lookAt(x,at.getY()+1.5,z);float yaw=(float)Math.toDegrees(Math.atan2(at.getX()-x,z-at.getZ()));npc.setRotation(yaw,0);
    }
    private boolean loadedLine(World w,Location a,Location b){int steps=Math.max(Math.abs(a.getBlockX()-b.getBlockX()),Math.abs(a.getBlockZ()-b.getBlockZ()))/8+1;for(int i=0;i<=steps;i++)if(!w.isChunkLoaded((int)Math.floor(a.getX()+(b.getX()-a.getX())*i/steps)>>4,(int)Math.floor(a.getZ()+(b.getZ()-a.getZ())*i/steps)>>4))return false;return true;}
    
    void clearConstruction(Village v,org.bukkit.util.BoundingBox block){
        for(var r:v.residents)if(r.alive){
            var npc=active.get(r.id);if(npc==null || !npc.isValid() || !npc.getBoundingBox().overlaps(block))continue;
            var pending=constructionDetours.get(r.id);if(pending!=null && pending.until>System.nanoTime())continue;
            Location at=npc.getLocation(),free=null;
            search:for(int radius=2;radius<=4;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
                if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
                // A path is 1/16 lower than grass; also sample a lower starting height at ledges.
                for(int dy:new int[]{0,-1,1}){
                    var point=safety.settle(v,npc.getWorld(),new Location(npc.getWorld(),at.getBlockX()+dx+.5,at.getY()+dy,at.getBlockZ()+dz+.5));
                    if(point==null || !available(v,npc.getWorld(),point,r.id,true))continue;
                    var body=new org.bukkit.util.BoundingBox(point.getX()-.3,point.getY(),point.getZ()-.3,point.getX()+.3,point.getY()+1.95,point.getZ()+.3);
                    if(body.overlaps(block) || !retreatClear(npc.getWorld(),at,point))continue;
                    free=point;break search;
                }
            }
            if(free!=null){constructionDetours.put(r.id,new Detour(new Routines.Target(Routines.Activity.IDLE,null,free.getBlockX(),free.getBlockY(),free.getBlockZ()),System.nanoTime()+4_000_000_000L));motions.remove(r.id);constructionClearances++;}
        }
    }
    private boolean retreatClear(World w,Location from,Location to){int steps=(int)Math.ceil(from.distance(to)*4);double previous=from.getY();for(int i=1;i<=steps;i++){var point=from.clone().add(to.toVector().subtract(from.toVector()).multiply((double)i/steps));point.setY(Math.max(from.getY(),to.getY()));if(!safety.clear(w,point))return false;double floor=safety.floor(w,point.getX(),point.getY()+.1,point.getZ(),1.35);if(!Double.isFinite(floor) || Math.abs(floor-previous)>1.25)return false;previous=floor;}return true;}
    private boolean available(Village v,World w,Location p,UUID resident,boolean goals){if(!safeFeet(v,w,p))return false;for(var other:v.residents)if(other.alive && !other.id.equals(resident)){var view=active.get(other.id);if(view!=null && view.isValid()){var at=view.getLocation();if(at.getWorld().equals(w) && Math.abs(at.getY()-p.getY())<1.9 && Math.pow(at.getX()-p.getX(),2)+Math.pow(at.getZ()-p.getZ(),2)<.64)return false;}var motion=motions.get(other.id);if(goals && motion!=null && motion.goal!=null && Math.abs(motion.goal.getY()-p.getY())<1.9 && Math.pow(motion.goal.getX()-p.getX(),2)+Math.pow(motion.goal.getZ()-p.getZ(),2)<.81)return false;}return true;}
    private boolean separate(Village v,Resident r,Villager npc){Location at=npc.getLocation();boolean overlap=v.residents.stream().filter(o->o.alive && !o.id.equals(r.id)).map(o->active.get(o.id)).filter(Objects::nonNull).filter(Entity::isValid).anyMatch(other->Math.abs(other.getY()-at.getY())<2.1 && Math.pow(other.getX()-at.getX(),2)+Math.pow(other.getZ()-at.getZ(),2)<.16);
        if(!overlap){crowded.remove(r.id);return false;}long since=crowded.computeIfAbsent(r.id,k->System.nanoTime());if(System.nanoTime()-since<4_000_000_000L)return false;var free=findFeet(v,npc.getWorld(),at.getBlockX(),at.getBlockY(),at.getBlockZ(),3,r.id);if(free==null)return false;constructionDetours.put(r.id,new Detour(new Routines.Target(Routines.Activity.IDLE,null,free.getBlockX(),free.getBlockY(),free.getBlockZ()),System.nanoTime()+4_000_000_000L));motions.remove(r.id);crowded.remove(r.id);unstacked++;return false;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void mount(EntityMountEvent e){if(isNpc(e.getEntity()) || isNpc(e.getMount()))e.setCancelled(true);}
    private Routines.Target emergency(Village v,Resident r,Villager npc,Routines.Target fallback){if(r.role==Role.GUARD && v.adult(r))return fallback;var current=motions.get(r.id);if(current!=null && current.target!=null && current.target.activity()==Routines.Activity.SHELTER && current.goal!=null && sheltered(v,current.target.building(),current.goal) && safeFeet(v,npc.getWorld(),current.goal))return current.target;
        var houses=v.buildings.stream().filter(b->Shelters.usable(b)).sorted(Comparator.comparingDouble(b->Math.pow(npc.getX()-(b.x+b.width/2.0),2)+Math.pow(npc.getZ()-(b.z+b.depth/2.0),2))).toList();
        for(var b:houses){var cells=Projects.expected(v,b.id);var doors=cells.stream().filter(c->c.after.contains("_door[") && c.after.contains("half=lower")).toList();if(doors.isEmpty() || doors.stream().anyMatch(c->!npc.getWorld().isChunkLoaded(c.x>>4,c.z>>4) || npc.getWorld().getBlockAt(c.x,c.y,c.z).getType()!=Bukkit.createBlockData(c.after).getMaterial()))continue;
            var floors=Shelters.floors(v,b,npc.getX(),npc.getZ(),24);
            for(var cell:floors){if(doors.stream().anyMatch(d->Math.hypot(d.x-cell.x,d.z-cell.z)<1.8))continue;var point=safety.settle(v,npc.getWorld(),new Location(npc.getWorld(),cell.x+.5,cell.y+1,cell.z+.5));if(point!=null && roof(npc.getWorld(),cells,point) && available(v,npc.getWorld(),point,r.id,true) && loadedLine(npc.getWorld(),npc.getLocation(),point))return new Routines.Target(Routines.Activity.SHELTER,b.id,point.getBlockX(),point.getBlockY(),point.getBlockZ());}
        }return fallback;}
    private boolean roof(World world,List<Village.Block> cells,Location at){return cells.stream().anyMatch(c->c.phase==3 && c.x==at.getBlockX() && c.z==at.getBlockZ() && c.y>=at.getY()+1.8 && Bukkit.createBlockData(c.after).getMaterial().isSolid() && world.isChunkLoaded(c.x>>4,c.z>>4) && world.getBlockAt(c.x,c.y,c.z).getType()==Bukkit.createBlockData(c.after).getMaterial());}
    private boolean sheltered(Village v,UUID building,Location at){var b=v.buildings.stream().filter(h->h.id.equals(building) && h.owner==null && h.complete && !h.damaged).findFirst().orElse(null);return b!=null && roof(at.getWorld(),Projects.expected(v,building),at);}
    Routines.Target intention(Resident r){var m=motions.get(r.id);return m==null?null:m.target;}
    boolean inShelter(Village v,Resident r){var npc=active.get(r.id);var m=motions.get(r.id);return npc!=null && m!=null && m.target!=null && m.target.activity()==Routines.Activity.SHELTER && sheltered(v,m.target.building(),npc.getLocation());}
    private double arrival(UUID id,Routines.Target target){if(target!=null && target.activity()==Routines.Activity.PATROL && target.building()!=null)return .25;return constructionDetours.containsKey(id) || target!=null && (target.activity()==Routines.Activity.REST || target.activity()==Routines.Activity.SHELTER || target.activity()==Routines.Activity.WORK || target.activity()==Routines.Activity.TRADE || target.activity()==Routines.Activity.CRAFT || target.activity()==Routines.Activity.TRAIN || target.activity()==Routines.Activity.GARRISON || target.activity()==Routines.Activity.OFFICE)?.25:1.8;}
    private Location destination(Village v,World w,Routines.Target target,UUID resident){
        if(target.building()!=null && (target.activity()==Routines.Activity.REST || target.activity()==Routines.Activity.SHELTER)){var house=v.buildings.stream().filter(b->b.id.equals(target.building()) && Shelters.usable(b) && (target.activity()!=Routines.Activity.REST || b.beds>0)).findFirst().orElse(null);if(house==null)return null;
            for(var cell:Shelters.floors(v,house,target.x()+.5,target.y(),target.z()+.5,32)){var point=safety.settle(v,w,new Location(w,cell.x+.5,cell.y+1,cell.z+.5));if(point!=null && roof(w,Projects.expected(v,house.id),point) && available(v,w,point,resident,true))return point;}return null;
        }
        if(target.activity()==Routines.Activity.BUILD && target.building()!=null){var site=v.buildings.stream().filter(b->b.id.equals(target.building())).findFirst().orElse(null);if(site!=null && !"road".equals(site.kind)){
                if(!"fortification".equals(site.kind)){var npc=active.get(resident);if(npc!=null){int pad=site.plotPadding+1,left=site.x-pad,right=site.x+site.width-1+pad,north=site.z-pad,south=site.z+site.depth-1+pad;int ax=Math.clamp(npc.getLocation().getBlockX(),left,right),az=Math.clamp(npc.getLocation().getBlockZ(),north,south);var approaches=new ArrayList<int[]>(List.of(new int[]{left,az},new int[]{right,az},new int[]{ax,north},new int[]{ax,south}));approaches.sort(Comparator.comparingDouble(a->Math.hypot(a[0]-npc.getX(),a[1]-npc.getZ())));for(var approach:approaches){var point=findFeet(v,w,approach[0],target.y(),approach[1],2,resident,at->!Territory.plot(site,at.getBlockX(),at.getBlockZ()));if(point!=null)return point;}}}
                return findFeet(v,w,target.x(),target.y(),target.z(),4,resident,at->!Territory.plot(site,at.getBlockX(),at.getBlockZ()));
            }}
        if(target.activity()==Routines.Activity.WORK && target.building()!=null){var plot=v.buildings.stream().filter(b->b.id.equals(target.building()) && b.enclosureVersion>0 && Enclosures.needed(b)).findFirst().orElse(null);if(plot!=null){var cells=Enclosures.cells(plot,Projects.expected(v,plot.id));if(!cells.isEmpty()){int minX=cells.stream().mapToInt(Enclosures.Cell::x).min().orElseThrow(),maxX=cells.stream().mapToInt(Enclosures.Cell::x).max().orElseThrow(),minZ=cells.stream().mapToInt(Enclosures.Cell::z).min().orElseThrow(),maxZ=cells.stream().mapToInt(Enclosures.Cell::z).max().orElseThrow();return findFeet(v,w,target.x(),target.y(),target.z(),3,resident,at->at.getX()-.3>=minX+1 && at.getX()+.3<=maxX && at.getZ()-.3>=minZ+1 && at.getZ()+.3<=maxZ);}}}
        if(target.building()!=null && Set.of(Routines.Activity.WORK,Routines.Activity.TRADE,Routines.Activity.CRAFT,Routines.Activity.TRAIN,Routines.Activity.GARRISON).contains(target.activity())){var plot=v.buildings.stream().filter(b->b.id.equals(target.building()) && !Objects.toString(b.kind,"").startsWith("hunting_lodge")).findFirst().orElse(null);if(plot!=null)return findFeet(v,w,target.x(),target.y(),target.z(),3,resident,at->Territory.plot(plot,at.getBlockX(),at.getBlockZ()));}
        return findFeet(v,w,target.x(),target.y(),target.z(),3,resident);
    }
    private boolean safeFeet(Village v,World w,Location p){return safety.stable(v,w,p);}
    private Location findFeet(Village v,World w,int x,int y,int z,int range,UUID resident){return findFeet(v,w,x,y,z,range,resident,p->true);}
    private Location findFeet(Village v,World w,int x,int y,int z,int range,UUID resident,java.util.function.Predicate<Location> allowed){if(!w.isChunkLoaded(x>>4,z>>4))return null;for(int radius=0;radius<=range;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;for(int dy:new int[]{0,1,-1,2,-2,3,-3,4,5}){Location p=safety.settle(v,w,new Location(w,x+dx+.5,y+dy,z+dz+.5));if(p!=null && allowed.test(p) && available(v,w,p,resident,true))return p;}}return null;}
    private boolean fallen(Village v,Resident r,Villager npc){var m=motions.get(r.id);double ground=m!=null && Double.isFinite(m.groundY)?m.groundY:r.position!=null && r.position.valid()?r.position.y:Double.NaN;boolean high=safety.stranded(v,npc.getWorld(),npc.getLocation());if(!high)strandedSince.remove(r.id);long since=high?strandedSince.computeIfAbsent(r.id,k->System.nanoTime()):System.nanoTime();return safety.fallen(v,npc.getWorld(),npc.getLocation(),ground) || high && System.nanoTime()-since>5_000_000_000L;}
    private boolean rescue(Village v,Resident r,Villager npc){World w=npc.getWorld();Location safe=null;if(r.position!=null && r.position.valid()){var old=new Location(w,r.position.x,r.position.y,r.position.z,r.position.yaw,0);if(available(v,w,old,r.id,true))safe=old;}
        if(safe==null){var home=Routines.shelter(v,r);safe=findFeet(v,w,home.x(),home.y(),home.z(),3,r.id);}if(safe==null)for(var house:v.buildings)if(Housing.usable(house)){safe=findFeet(v,w,house.workX,house.workY,house.workZ,3,r.id);if(safe!=null)break;}
        if(safe==null)safe=findFeet(v,w,v.x,v.y+1,v.z-3,3,r.id);
        if(safe==null){waiting.put(r.id,Messages.text("Нет безопасного загруженного места для выхода из ямы"));return false;}
        if(plugin.content.config.getBoolean("debug.server-self-test"))plugin.getLogger().info("NPC_SAFETY_RECOVERY "+r.name+" from="+npc.getLocation().toVector()+" embedded="+safety.embedded(w,npc.getLocation())+" below="+safety.belowGround(v,w,npc.getLocation()));
        if(!npc.teleport(safe))return false;hold(npc);npc.setVelocity(new org.bukkit.util.Vector());npc.setFallDistance(0);motions.remove(r.id);waiting.remove(r.id);rescues++;remember(v,r,npc);return true;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void unsafeDamage(EntityDamageEvent e){if(!isNpc(e.getEntity()) || !(e.getEntity() instanceof Villager npc) || !(e.getCause()==EntityDamageEvent.DamageCause.SUFFOCATION || e.getCause()==EntityDamageEvent.DamageCause.VOID || e.getCause()==EntityDamageEvent.DamageCause.FALL))return;var v=plugin.villages.get(villageId(npc));var r=resident(npc);if(v!=null && r!=null && r.alive && !plugin.transactions.busy(v) && fallen(v,r,npc) && rescue(v,r,npc))e.setCancelled(true);}
    private Location spawnPoint(Village v,Resident r,World w,Routines.Target target){if(r.position!=null && r.position.valid()){var old=new Location(w,r.position.x,r.position.y,r.position.z,r.position.yaw,0);if(available(v,w,old,r.id,true))return old;}
        Location p=findFeet(v,w,target.x(),target.y(),target.z(),3,r.id);if(p!=null)return p;
        var home=Routines.shelter(v,r);p=findFeet(v,w,home.x(),home.y(),home.z(),3,r.id);if(p!=null)return p;
        for(var b:v.buildings)if(Housing.usable(b)){p=findFeet(v,w,b.workX,b.workY,b.workZ,3,r.id);if(p!=null)return p;}
        return findFeet(v,w,v.x,v.y+1,v.z-3,3,r.id);
    }
    private boolean hasLoadedPlace(Village v,Resident r,World w,Routines.Target target){if(w.isChunkLoaded(target.x()>>4,target.z()>>4) || w.isChunkLoaded(v.x>>4,v.z>>4))return true;return v.buildings.stream().anyMatch(b->Housing.usable(b) && w.isChunkLoaded(b.workX>>4,b.workZ>>4));}
    private void remember(Village v,Resident r,Villager npc){Location at=npc.getLocation();if(at.getWorld().getUID().equals(v.world) && safeFeet(v,npc.getWorld(),at))r.position=new Position(at.getX(),at.getY(),at.getZ(),at.getYaw());r.entity=npc.getUniqueId();}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void path(EntityPathfindEvent e){if(isNpc(e.getEntity()) && !ownPaths.contains(e.getEntity().getUniqueId())){e.setCancelled(true);vanillaPathsBlocked++;}}
    @EventHandler(ignoreCancelled=true)public void career(VillagerCareerChangeEvent e){if(isNpc(e.getEntity()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void golem(CreatureSpawnEvent e){if(e.getSpawnReason()==CreatureSpawnEvent.SpawnReason.VILLAGE_DEFENSE && plugin.at(e.getLocation())!=null)e.setCancelled(true);}
    private void openDoors(Village v,Villager npc){Location at=npc.getLocation();World w=npc.getWorld();int opened=0;for(int dx=-1;dx<=1 && opened<2;dx++)for(int dz=-1;dz<=1 && opened<2;dz++)for(int dy=0;dy<2 && opened<2;dy++){int x=at.getBlockX()+dx,y=at.getBlockY()+dy,z=at.getBlockZ()+dz;if(w.isChunkLoaded(x>>4,z>>4) && openPassage(v,w.getBlockAt(x,y,z)))opened++;}}
    private void openDestinationGates(Village v,Villager npc,UUID building){if(building==null)return;var b=v.buildings.stream().filter(h->h.id.equals(building) && h.complete && h.owner==null).findFirst().orElse(null);if(b==null)return;int budget=2;for(var c:Projects.expected(v,b.id))if(c.after.contains("_fence_gate[") && budget-->0 && npc.getWorld().isChunkLoaded(c.x>>4,c.z>>4))openPassage(v,npc.getWorld().getBlockAt(c.x,c.y,c.z));}
    private boolean openPassage(Village v,Block b){var data=b.getBlockData();if(!(data instanceof Door) && !(data instanceof Gate) || data instanceof Door door && door.getHalf()!=org.bukkit.block.data.Bisected.Half.BOTTOM || !(ownedDoor(v,b)))return false;
        if(data instanceof Door door){door.setOpen(true);b.setBlockData(door,false);Block upper=b.getRelative(0,1,0);if(upper.getBlockData() instanceof Door top){top.setOpen(true);upper.setBlockData(top,false);}}
        else if(data instanceof Gate gate){gate.setOpen(true);b.setBlockData(gate,false);}
        Village owner=plugin.at(b.getLocation());UUID doorVillage=owner!=null && ownedDoor(owner,b)?owner.id:v.id;doors.put(doorVillage+":"+b.getX()+":"+b.getY()+":"+b.getZ(),new DoorHold(doorVillage,b.getX(),b.getY(),b.getZ(),System.nanoTime()+(danger(v)?1_000_000_000L:5_000_000_000L)));return true;
    }
    private boolean ownedDoor(Village v,Block block){return v.buildings.stream().filter(b->b.owner==null && b.complete).anyMatch(b->Projects.expected(v,b.id).stream().anyMatch(c->c.x==block.getX() && c.y==block.getY() && c.z==block.getZ() && WorldWorks.matchesExpected(c.after,block.getBlockData().getAsString())));}
    private void closeDoors(){int budget=4;long now=System.nanoTime();for(var it=doors.entrySet().iterator();it.hasNext() && budget-->0;){var hold=it.next().getValue();if(now<hold.until)continue;Village v=plugin.villages.get(hold.village);World w=v==null?null:Bukkit.getWorld(v.world);if(w==null){it.remove();continue;}if(!w.isChunkLoaded(hold.x>>4,hold.z>>4))continue;Block b=w.getBlockAt(hold.x,hold.y,hold.z);if(!ownedDoor(v,b)){it.remove();continue;}if(!w.getNearbyEntities(new Location(w,hold.x+.5,hold.y+1,hold.z+.5),.45,1.3,.45).isEmpty())continue;if(b.getBlockData() instanceof Door door){door.setOpen(false);b.setBlockData(door,false);Block upper=b.getRelative(0,1,0);if(upper.getBlockData() instanceof Door top){top.setOpen(false);upper.setBlockData(top,false);}}else if(b.getBlockData() instanceof Gate gate){gate.setOpen(false);b.setBlockData(gate,false);}it.remove();}}
    private void configure(Villager npc,Village v,Resident r){
        npc.customName(Component.text(r.name+" · "+(v.adult(r)?plugin.residentRoleName(v,r):Messages.text("ребёнок"))));npc.setCustomNameVisible(true);Vitals.reconcile(v,plugin.content.rules);applyVitals(npc,v,r);npc.setAgeLock(true);npc.leaveVehicle();npc.eject();npc.setCollidable(true);collision.join(npc);npc.setBreed(false);npc.setCanPickupItems(false);npc.setRemoveWhenFarAway(false);npc.setPersistent(false);npc.setRecipes(List.of());refreshProfession(npc,v,r);
        npc.setVillagerType(Villager.Type.PLAINS);Bukkit.getMobGoals().removeAllGoals(npc);npc.getPathfinder().stopPathfinding();npc.setAI(true);npc.setAware(true);npc.getPathfinder().setCanOpenDoors(true);npc.getPathfinder().setCanPassDoors(true);npc.getPathfinder().setCanFloat(true);
        for(var key:List.of(org.bukkit.entity.memory.MemoryKey.HOME,org.bukkit.entity.memory.MemoryKey.JOB_SITE,org.bukkit.entity.memory.MemoryKey.POTENTIAL_JOB_SITE,org.bukkit.entity.memory.MemoryKey.MEETING_POINT))npc.setMemory(key,null);
    }
    private void refreshProfession(Villager npc,Village v,Resident r){var profession=switch(Jobs.activity(v,r,plugin.content.rules)){case FARMER->Villager.Profession.FARMER;case LUMBERJACK->Villager.Profession.FLETCHER;case FISHER,WATER_CARRIER->Villager.Profession.FISHERMAN;case ARTISAN,SMITH->Villager.Profession.TOOLSMITH;case MINER,STONEMASON->Villager.Profession.MASON;case HERDER->Villager.Profession.SHEPHERD;case BEEKEEPER,BAKER->Villager.Profession.FARMER;case HUNTER->Villager.Profession.BUTCHER;case GUARD->Villager.Profession.ARMORER;case MERCHANT->Villager.Profession.CARTOGRAPHER;case BUILDER->Villager.Profession.MASON;case CHILD->Villager.Profession.NONE;case ELDER->Villager.Profession.LIBRARIAN;};if(npc.getProfession()!=profession)npc.setProfession(profession);}
    private void applyVitals(Villager npc,Village v,Resident r){refreshProfession(npc,v,r);if(npc.getEquipment()!=null){var weapon=Material.AIR;if(npc.getEquipment().getItemInMainHand().getType()!=weapon)npc.getEquipment().setItemInMainHand(new org.bukkit.inventory.ItemStack(weapon));npc.getEquipment().setItemInMainHandDropChance(0);}var attribute=Objects.requireNonNull(npc.getAttribute(Attribute.MAX_HEALTH));if(attribute.getBaseValue()!=r.maxHealth)attribute.setBaseValue(r.maxHealth);if(v.adult(r)){if(!npc.isAdult())npc.setAdult();}else if(npc.isAdult())npc.setBaby();if(Math.abs(npc.getHealth()-r.health)>.001)npc.setHealth(Math.min(r.maxHealth,r.health));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void injury(EntityDamageEvent e){if(!isNpc(e.getEntity()))return;Village v=plugin.villages.get(villageId(e.getEntity()));Resident r=resident(e.getEntity());if(v!=null && r!=null && r.alive && !plugin.transactions.busy(v)){r.health=Math.max(0,((LivingEntity)e.getEntity()).getHealth()-e.getFinalDamage());plugin.save(v);}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void recovery(EntityRegainHealthEvent e){if(!isNpc(e.getEntity()))return;Village v=plugin.villages.get(villageId(e.getEntity()));Resident r=resident(e.getEntity());if(v==null || r==null)return;if(plugin.transactions.busy(v)){e.setCancelled(true);return;}r.health=Math.min(r.maxHealth,((LivingEntity)e.getEntity()).getHealth()+e.getAmount());plugin.save(v);}
    private final Map<UUID,Long> menuCooldown=new HashMap<>();
    @EventHandler public void dialogueQuit(org.bukkit.event.player.PlayerQuitEvent e){cooldown.remove(e.getPlayer().getUniqueId());menuCooldown.remove(e.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.HIGHEST)public void preciseInteract(PlayerInteractAtEntityEvent e){interact(e);}
    @EventHandler(priority=EventPriority.HIGHEST)public void vanillaMerchant(InventoryOpenEvent e){if(e.getInventory() instanceof MerchantInventory inventory && inventory.getMerchant() instanceof Entity entity && isNpc(entity))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)public void interact(PlayerInteractEntityEvent e) {
        if(!isNpc(e.getRightClicked()))return;e.setCancelled(true);
        if(e.getHand()!=org.bukkit.inventory.EquipmentSlot.HAND)return;
        Village v=plugin.villages.get(villageId(e.getRightClicked()));Resident r=resident(e.getRightClicked());if(v==null || r==null || !r.alive)return;
        Player p=e.getPlayer();
        if(plugin.transactions.players.contains(p.getUniqueId())||plugin.transactions.busy(v)){plugin.tell(p,Messages.text("Ожидайте завершения сохранения."));return;}
        if(!Territory.inside(v,p.getWorld().getUID(),p.getX(),p.getZ())){plugin.tell(p,Messages.text("Поговорите с жителем внутри территории деревни."));return;}plugin.discoverPlayer(p,v);
        if(v.adult(r)&&!p.isSneaking()&&(r.role==Role.ELDER||r.role==Role.MERCHANT)){
            long tick=System.nanoTime();if(tick<menuCooldown.getOrDefault(p.getUniqueId(),0L))return;menuCooldown.put(p.getUniqueId(),tick+250_000_000L);
            plugin.tell(p,r.name+Messages.text(": Добро пожаловать. Для беседы нажмите Shift+ПКМ."));if(r.role==Role.ELDER)plugin.menus.elder(p,v);else plugin.menus.trade(p,v);return;
        }
        long now=System.nanoTime();if(now<cooldown.getOrDefault(p.getUniqueId(),0L))return;cooldown.put(p.getUniqueId(),now+plugin.content.config.getLong("dialogue.cooldown-seconds",4)*1_000_000_000L);var conversation=Social.talk(v,p.getUniqueId(),r.id,"friend",plugin.content.rules);plugin.save(v);
        if(!v.adult(r)){plugin.tell(p,r.name+Messages.text(": Я ещё расту. До взросления примерно ")+Population.maturityMinutes(v,r,plugin.content.rules)+Messages.text(" минут работы деревни."));return;}
        boolean shortage=v.buildings.stream().anyMatch(b->b.upkeepMissing) || v.projects.stream().anyMatch(q->q.state.equals("COLLECTING"));
        String key=danger(v)?"danger":v.nutrition<1?"hunger":v.relation(p.getUniqueId()).trust<0?"hostile":shortage?"shortage":v.projects.stream().anyMatch(Project::active)?"construction":v.relation(p.getUniqueId()).trust>=plugin.content.rules.friendTrust?"friendly":r.role.name();
        List<String> lines=plugin.content.dialogues.getOrDefault(key,plugin.content.dialogues.get(Jobs.activity(v,r,plugin.content.rules).name()));
        plugin.tell(p,r.name+": "+lines.get((int)(System.nanoTime()&Integer.MAX_VALUE)%lines.size())+" "+plugin.content.cultures.get(v.culture).greeting()+Messages.text(" Знакомство: ")+conversation.friendship()+Messages.text("/100 · доверие +")+conversation.trust());
    }
    @EventHandler public void removed(EntityRemoveEvent e){if(isNpc(e.getEntity()))collision.leave(e.getEntity());}
    @EventHandler(priority=EventPriority.MONITOR)public void death(EntityDeathEvent e) {
        if(!isNpc(e.getEntity()))return;Resident r=resident(e.getEntity());Village v=plugin.villages.get(villageId(e.getEntity()));
        e.getDrops().clear();e.setDroppedExp(0);
        collision.leave(e.getEntity());if(r!=null && v!=null){r.alive=false;r.health=0;r.entity=null;active.remove(r.id);v.abandoned=v.living()==0;if(v.abandoned)Missions.abandon(v);UUID killer=e.getEntity().getKiller()==null?e.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent by?Protection.responsible(by.getDamager()):null:e.getEntity().getKiller().getUniqueId();if(killer!=null)Social.harm(v,killer,r.id,plugin.content.rules,true);Simulation.electReplacement(v,plugin.content.rules);Vitals.reconcile(v,plugin.content.rules);plugin.save(v);}
    }
    @EventHandler(ignoreCancelled=true)public void transform(EntityTransformEvent e){if(isNpc(e.getEntity()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void breed(EntityBreedEvent e){if(isNpc(e.getFather())||isNpc(e.getMother()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void acquire(VillagerAcquireTradeEvent e){if(isNpc(e.getEntity()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true)public void replenish(VillagerReplenishTradeEvent e){if(isNpc(e.getEntity()))e.setCancelled(true);}
    @EventHandler public void load(EntitiesLoadEvent e){for(Entity entity:e.getEntities())if(isNpc(entity)){if(initialized)rebind(entity);else deferred.add(entity.getUniqueId());}}
    public void rebind(Entity entity){
        if(!initialized){deferred.add(entity.getUniqueId());return;}
        Resident r=resident(entity);Village v=plugin.villages.get(villageId(entity));
        if(r==null || v==null || !r.alive || !(entity instanceof Villager npc)){collision.leave(entity);entity.remove();return;}
        Villager known=active.get(r.id);if(known!=null && known.isValid() && known!=entity){collision.leave(entity);entity.remove();return;}
        active.put(r.id,npc);r.entity=npc.getUniqueId();configure(npc,v,r);motions.remove(r.id);waiting.remove(r.id);
    }
    @EventHandler public void unload(EntitiesUnloadEvent e){for(Entity entity:e.getEntities())if(isNpc(entity)){Resident r=resident(entity);if(r!=null && active.get(r.id)==entity){Village v=plugin.villages.get(villageId(entity));if(v!=null)remember(v,r,(Villager)entity);active.remove(r.id);r.entity=null;motions.remove(r.id);}collision.leave(entity);entity.remove();}}
    public void reconcileLoaded(){
        initialized=true;for(UUID id:new ArrayList<>(deferred)){Entity entity=Bukkit.getEntity(id);if(entity!=null)rebind(entity);}deferred.clear();
        // Only chunks that intersect a known village; no world-wide entity scan.
        for(Village v:plugin.villages.values()) {
            World w=Bukkit.getWorld(v.world);if(w==null)continue;
            for(int x=(v.x-v.radius)>>4;x<=(v.x+v.radius)>>4;x++)for(int z=(v.z-v.radius)>>4;z<=(v.z+v.radius)>>4;z++)if(w.isChunkLoaded(x,z))for(Entity entity:w.getChunkAt(x,z).getEntities())if(isNpc(entity))rebind(entity);
        }
    }
    public void removeAll(){for(var e:active.values()){var r=resident(e);var v=plugin.villages.get(villageId(e));if(r!=null && v!=null && !plugin.transactions.busy(v)){remember(v,r,e);r.entity=null;}collision.leave(e);e.remove();}collision.close();active.clear();motions.clear();waiting.clear();doors.clear();moving.clear();crowded.clear();strandedSince.clear();constructionDetours.clear();alarmPoints.clear();dangerUntil.clear();threats.clear();threatSource.clear();}
}
