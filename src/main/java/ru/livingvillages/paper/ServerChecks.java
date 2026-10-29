package ru.livingvillages.paper;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.*;
import org.bukkit.scheduler.BukkitRunnable;
import ru.livingvillages.domain.*;
import ru.livingvillages.persistence.Database;
import ru.livingvillages.i18n.Messages;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;

/** Opt-in isolated-world checks. This class is excluded from the public plugin JAR. */
final class ServerChecks {
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    static void run(LivingVillagesPlugin p)throws Exception {
        for(int n:new int[]{1,64,4096}){var c=p.coins.coin(n,32);check(p.coins.value(c)==n,"denomination");check(c.isSimilar(p.coins.coin(n,1)),"stackable coins");check(c.getItemMeta().getItemModel()==null,"vanilla item appearance");}
        check(p.coins.value(new ItemStack(Material.SUNFLOWER))==0,"plain sunflower");
        var fake=new ItemStack(Material.SUNFLOWER);var meta=fake.getItemMeta();meta.itemName(net.kyori.adventure.text.Component.text("Gold coin"));fake.setItemMeta(meta);check(p.coins.value(fake)==0,"renamed counterfeit");
        check(p.coins.value(new ItemStack(Material.COPPER_NUGGET))==0&&p.coins.value(new ItemStack(Material.IRON_NUGGET))==0,"plain nuggets");
        var contents=new ItemStack[36];contents[0]=p.coins.coin(4096,1);check(p.coins.total(p.coins.balance(contents,-91))==4005,"exact change");check(p.coins.total(p.coins.balance(p.coins.balance(contents,-91),91))==4096,"conservation");
        for(var culture:p.content.cultures.values())for(var plan:p.content.blueprints.values())for(var cell:plan.cells())Bukkit.createBlockData(p.content.resolve(culture,cell.data()));
        check(p.content.cultures.size()==1&&p.content.blueprints.size()==19,"Free content boundary");
        check(p.edition()==Edition.FREE&&p.content.rules.maxPopulation==12,"Free limits");
        var w=Bukkit.getWorlds().getFirst();w.setDifficulty(Difficulty.PEACEFUL);w.setTime(3000);w.setGameRule(GameRules.ADVANCE_TIME,false);w.setGameRule(GameRules.SPAWN_MOBS,false);
        p.content.config.set("generation.enabled",false);p.content.config.set("development.auto-plan",false);p.content.config.set("development.plan-interval-seconds",1);p.content.config.set("lighting.enabled",false);p.content.config.set("limits.blocks-per-pass",256);p.content.config.set("limits.resource-blocks-per-pass",2048);p.content.config.set("limits.placement-columns-per-pass",1024);p.content.config.set("limits.npc-budget-ms",20);p.content.rules.workPerSecond=128;p.content.rules.dayMillis=10000;p.content.rules.craftIntervalMillis=500;p.content.rules.childImmigrationPercent=0;
        var checkpoint=p.dataDirectory().resolve("free-checkpoint.json");var proof=p.dataDirectory().resolve("free-proof.json");boolean migration=Boolean.getBoolean("livingvillages.test-migration"),resuming=Files.exists(checkpoint),reloading=Files.exists(proof);
        var chunks=new ArrayDeque<int[]>();for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)chunks.add(new int[]{x,z});
        new BukkitRunnable(){int stage=-1,ticks;UUID pending;int initialIndex;long initialDays;String initialLedger;boolean finished;
            void persist(String marker,Path path,Map<String,Object> facts){var v=p.villages.values().iterator().next();v.paused=true;p.transactions.villages.add(v.id);String snapshot=Database.encode(v);p.db.save(snapshot).whenComplete((done,error)->p.sync(()->{p.transactions.villages.remove(v.id);try{if(error!=null)throw new IllegalStateException(error);Files.writeString(path,Database.JSON.toJson(facts));p.getLogger().info(marker);}catch(Exception e){p.getLogger().log(java.util.logging.Level.SEVERE,"SERVER_FREE_FAIL",e);}}));cancel();}
            public void run(){try{
                if(++ticks>9000)throw new AssertionError("timeout stage="+stage+" "+p.villages.values().stream().map(v->v.developmentReason+v.projects.stream().filter(Village.Project::active).map(q->q.kind+":"+q.state+":"+q.reason).toList()).toList());
                if(ticks%400==0)p.getLogger().info("FREE_QA_PROGRESS stage="+stage+" npc="+p.npcs.count());
                if(stage==-1){for(int n=0;n<4&&!chunks.isEmpty();n++){var c=chunks.removeFirst();w.getChunkAt(c[0],c[1]).addPluginChunkTicket(p);}if(!chunks.isEmpty())return;
                    if(migration){check(!p.villages.isEmpty(),"legacy Free save loaded");for(var v:p.villages.values()){var world=Bukkit.getWorld(v.world);check(world!=null,"legacy world identity");for(int x=(v.x-48)>>4;x<=(v.x+48)>>4;x++)for(int z=(v.z-48)>>4;z<=(v.z+48)>>4;z++)world.getChunkAt(x,z).addPluginChunkTicket(p);}stage=10;return;}
                    if(reloading){var v=p.villages.values().iterator().next();check(v.paused&&v.produced.getOrDefault("OAK_LOG",0L)>0,"wood and pause retained");initialDays=v.days;initialLedger=Database.JSON.toJson(v.stock);stage=20;return;}
                    if(resuming){var v=p.villages.values().iterator().next();var data=Database.JSON.fromJson(read(checkpoint),com.google.gson.JsonObject.class);pending=UUID.fromString(data.get("project").getAsString());initialIndex=data.get("index").getAsInt();var q=v.projects.stream().filter(a->a.id.equals(pending)).findFirst().orElseThrow();check(q.index==initialIndex,"saved exact block cursor");v.paused=false;v.laws.immigration=false;p.content.config.set("development.auto-plan",true);stage=2;return;}
                    check(p.villages.isEmpty(),"isolated fresh world");p.worldWorks.createTest(w,0,0,"polevichi",p.getLogger()::info);stage=0;return;}
                if(p.villages.isEmpty()||p.villages.values().stream().anyMatch(p.transactions::busy))return;var v=p.villages.values().iterator().next();
                if(stage==10){if(p.npcs.count()<v.living())return;check(v.living()==5,"legacy five living residents");menus(p,v);persist("SERVER_MIGRATION_PASS",p.dataDirectory().resolve("free-migration-proof.json"),Map.of("legacyFree",true,"village",v.id,"npc",p.npcs.count(),"treasury",v.treasury,"format",v.format));return;}
                if(stage==20){if(ticks<100||p.npcs.count()<v.living())return;check(v.days==initialDays,"paused server time does not advance simulation");check(initialLedger.equals(Database.JSON.toJson(v.stock)),"stocks unchanged while paused");menus(p,v);p.getLogger().info("SERVER_FREE_RELOAD_PASS");cancel();return;}
                v.laws.immigration=false;check(v.living()==5,"five founders alive");check(v.projects.stream().noneMatch(q->q.state.equals("CANCELLED")),"no autonomous cancellation");
                if(stage==0){if(v.projects.stream().anyMatch(Village.Project::active)||p.npcs.count()<5)return;check(v.buildings.stream().filter(b->!b.kind.equals("road")&&b.complete).count()==3,"three initial buildings");menus(p,v);p.content.config.set("development.auto-plan",true);p.content.rules.workPerSecond=8;stage=1;return;}
                if(stage==1){var q=v.projects.stream().filter(a->a.active()&&a.index>0&&a.index<a.blocks.size()).findFirst().orElse(null);if(q==null)return;check(q.owner==null,"communal project");persist("SERVER_FREE_CHECKPOINT",checkpoint,Map.of("project",q.id,"index",q.index,"village",v.id,"treasury",v.treasury));return;}
                if(stage==2){if(v.produced.getOrDefault("OAK_LOG",0L)==0)return;check(v.projects.stream().anyMatch(q->q.id.equals(pending)&&q.state.equals("COMPLETE")),"checkpoint project completed once");check(v.buildings.stream().anyMatch(b->b.complete&&b.kind.startsWith("tree_garden")&&b.resourceConfirmed),"physical working grove");check(v.crafted.getOrDefault("OAK_PLANKS",0L)>0,"ordinary wood crafting");menus(p,v);persist("SERVER_FREE_PASS",proof,Map.of("nativePaper",true,"partialConstructionRestart",true,"physicalGroveAndWoodProduction",true,"plankCrafting",true,"menusAndCoins",true,"connectedClients",false,"acceleratedSimulation",true,"injectedResourcesAfterCreation",false));}
            }catch(Exception|AssertionError error){p.getLogger().log(java.util.logging.Level.SEVERE,"SERVER_FREE_FAIL",error);cancel();}}
        }.runTaskTimer(p,1,1);
    }
    static String read(Path path){try{return Files.readString(path);}catch(Exception e){throw new RuntimeException(e);}}
    static void menus(LivingVillagesPlugin p,Village v){
        var w=Objects.requireNonNull(Bukkit.getWorld(v.world));var driver=new Driver(UUID.nameUUIDFromBytes("free-native-qa".getBytes(java.nio.charset.StandardCharsets.UTF_8)),w,v.x,v.y+1,v.z);var player=driver.player;v.discovered.add(driver.id);v.relation(driver.id).trust=100;
        p.menus.elder(player,v);check(driver.top.getItem(13)!=null,"trade entry");for(int slot:new int[]{11,12,13,14,23,24,25,26}){p.menus.elder(player,v);p.menus.click(driver.click(slot));check(driver.top.getHolder()!=null,"native menu "+slot);check(Arrays.stream(driver.top.getContents()).filter(Objects::nonNull).count()>0,"nonempty menu "+slot);}
        for(String language:List.of("ru","en")){p.content.selectLanguage(language);Messages.use(language);p.menus.elder(player,v);String name=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(driver.top.getItem(10).getItemMeta().itemName());check(language.equals("ru")?name.contains("Состояние"):name.equals("Settlement status"),"language menu: "+name);}
        driver.op=true;p.adminMenus.directory(player,0);check(driver.top.getSize()==54,"admin directory opens");
    }
    static final class Driver {
        final org.bukkit.persistence.PersistentDataContainer pdc=new ItemStack(Material.PAPER).getItemMeta().getPersistentDataContainer();
        final Set<String> permissions=new HashSet<>();
        final UUID id;final World world;final Player player;final PlayerInventory inventory;final Inventory bottom=Bukkit.createInventory(null,36);final List<String> messages=new ArrayList<>();ItemStack[] storage=new ItemStack[36];Inventory top=bottom;InventoryView view;double x,y,z;int saves;boolean sneaking,op;
        Driver(UUID id,World world,double x,double y,double z){this.id=id;this.world=world;this.x=x;this.y=y;this.z=z;
            inventory=(PlayerInventory)Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),new Class<?>[]{PlayerInventory.class},(self,m,args)->switch(m.getName()){case "getItemInMainHand"->storage[0]==null?new ItemStack(Material.AIR):storage[0];case "setItemInMainHand"->{storage[0]=(ItemStack)args[0];yield null;}case "getHeldItemSlot"->0;case "getItem"->storage[(int)args[0]];case "getStorageContents"->Coins.cloneItems(storage);case "setStorageContents"->{storage=Coins.cloneItems((ItemStack[])args[0]);yield null;}default->throw new UnsupportedOperationException("Inventory: "+m.getName());});
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(self,m,args)->switch(m.getName()){case "getPersistentDataContainer"->pdc;case "getUniqueId"->id;case "getName"->"CommunityPlayer";case "getWorld"->world;case "getX"->this.x;case "getY"->this.y;case "getZ"->this.z;case "getLocation"->new Location(world,this.x,this.y,this.z);case "isOnline"->true;case "getOpenInventory"->view;case "getInventory"->inventory;case "getItemOnCursor"->new ItemStack(Material.AIR);case "openInventory"->{top=(Inventory)args[0];yield view;}case "closeInventory"->{top=bottom;yield null;}case "saveData"->{saves++;yield null;}case "sendMessage"->{messages.add(Arrays.toString(args));yield null;}case "hasPermission"->op||permissions.contains(args[0]);case "isSneaking"->sneaking;case "isOp"->op;case "equals"->self==args[0];case "hashCode"->id.hashCode();case "toString"->"CommunityTestPlayer";default->throw new UnsupportedOperationException("Player: "+m.getName());});
            view=(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(self,m,args)->switch(m.getName()){case "getTopInventory"->top;case "getBottomInventory"->bottom;case "getPlayer"->player;case "getType"->InventoryType.CHEST;case "convertSlot"->args[0];case "getInventory"->top;case "getTitle","getOriginalTitle"->"Test";case "countSlots"->90;case "getSlotType"->InventoryType.SlotType.CONTAINER;case "getCursor"->new ItemStack(Material.AIR);default->throw new UnsupportedOperationException("View: "+m.getName());});
        }
        InventoryClickEvent click(int slot){return new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);}
    }
}
