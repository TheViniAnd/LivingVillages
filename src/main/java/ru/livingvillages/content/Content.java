package ru.livingvillages.content;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.livingvillages.paper.LivingVillagesPlugin;
import ru.livingvillages.domain.*;
import ru.livingvillages.i18n.Messages;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;

/** Free content loader: one culture, the communal catalogue and ordinary vanilla crafting. */
public final class Content {
    public final Rules rules=new Rules();
    public String language;
    private final String languageConfigSignature;
    private final Map<String,String> contentSignatures=new LinkedHashMap<>();
    private static final List<String> FILES=List.of("config.yml","cultures.yml","buildings.yml","dialogues.yml","economy.yml","layouts.yml","names.yml");
    public final Map<String,Culture> cultures=new LinkedHashMap<>();
    public final Map<String,Blueprint> blueprints=new LinkedHashMap<>();
    public final Map<String,StartLayout> layouts=new LinkedHashMap<>();
    public final Map<String,List<String>> dialogues=new HashMap<>();
    public final Map<String,Integer> designVersions=new HashMap<>();
    private final Map<String,Blueprint> blueprintCache=new HashMap<>();
    public final YamlConfiguration config;
public record Plot(String kind,int x,int z){}
public record StartLayout(String id,String name,List<Plot> plots){}
public record Culture(String id,String name,List<String> biomes,List<String> names,Map<String,String> palette,String specialty,String greeting,String need,Map<String,Integer> initialGoods) { }
public record Cell(int x,int y,int z,int phase,String data,boolean floor) {
        public Cell(int x,int y,int z,int phase,String data){this(x,y,z,phase,data,false);}
    }
public record Entrance(int x,int z,String facing){}
public record Blueprint(String id,String name,int width,int depth,int beds,int level,String recipe,List<Cell> cells,Map<String,Integer> upkeep,int upkeepDays,int unlockStage,Entrance entrance,int groundLayer,boolean closedGables) { }
    private static void install(LivingVillagesPlugin p,String name){try(var in=p.getResource(name)){if(in==null)fail(Messages.text("Нет ресурса ")+name);Files.createDirectories(p.dataDirectory());Files.copy(in,p.dataDirectory().resolve(name));}catch(IOException e){throw new IllegalStateException(Messages.text("Не записан ")+name,e);}}
    public Content(LivingVillagesPlugin p){
        for(String name:FILES)if(!Files.exists(p.dataDirectory().resolve(name)))install(p,name);
        config=read(p,"config.yml");language=Messages.validate(config.getString("language","en"));languageConfigSignature=configSignature(config);
        try(var lang=Messages.preview(language)){
        rules.foodPerResident=positive(config,"nutrition.per-resident",rules.foodPerResident);
        rules.deathDays=positive(config,"nutrition.death-deficit-days",rules.deathDays);
        rules.warningDays=positive(config,"nutrition.warning-days",rules.warningDays);
        rules.foodTargetDays=positive(config,"nutrition.target-days",rules.foodTargetDays);
        rules.maxPopulation=positive(config,"population.maximum",rules.maxPopulation);
        rules.arrivalBatch=positive(config,"population.arrival.batch",rules.arrivalBatch);
        rules.regularResidentsPerGuard=positive(config,"guards.residents-per-guard",rules.regularResidentsPerGuard);
        rules.regularGuardLimit=positive(config,"guards.population-cap",rules.regularGuardLimit);
        rules.homeImmigrationDays=positive(config,"population.home-immigration-days",rules.homeImmigrationDays);
        rules.minimumBuilders=positive(config,"population.minimum-adult-builders",rules.minimumBuilders);
        rules.immigrationDays=positive(config,"population.immigration-days",rules.immigrationDays);
        rules.childGrowthDays=positive(config,"population.child-growth-days",rules.childGrowthDays);
        rules.maxProjects=positive(config,"construction.max-projects",rules.maxProjects);
        rules.forgettingGraceDays=positive(config,"reputation.forgetting-grace-days",rules.forgettingGraceDays);
        rules.forgettingPerDay=positive(config,"reputation.forgetting-per-day",rules.forgettingPerDay);
        rules.socialDailyTrust=positive(config,"reputation.conversation-per-day",rules.socialDailyTrust);
        rules.socialTrustCap=positive(config,"reputation.conversation-cap",rules.socialTrustCap);
        rules.hitPenalty=positive(config,"reputation.resident-hit-penalty",rules.hitPenalty);
        rules.killPenalty=positive(config,"reputation.resident-kill-penalty",rules.killPenalty);
        rules.familySize=positive(config,"housing.family-size",rules.familySize);
        rules.familyStartDay=positive(config,"housing.family-start-day",rules.familyStartDay);
        rules.familyIntervalDays=positive(config,"housing.family-interval-days",rules.familyIntervalDays);
        rules.friendTrust=positive(config,"reputation.access.friend",rules.friendTrust);
        rules.buildTrust=positive(config,"reputation.access.construction",rules.buildTrust);
        rules.craftWorkersPerPass=positive(config,"autonomy.crafting.workers-per-pass",rules.craftWorkersPerPass);
        rules.maxCraftPasses=positive(config,"autonomy.crafting.max-passes",rules.maxCraftPasses);
        rules.craftWorkers=positive(config,"autonomy.craft-workers-per-day",rules.craftWorkers);
        rules.stockLogs=positive(config,"autonomy.logs-reserve",rules.stockLogs);
        rules.stockStone=positive(config,"autonomy.stone-reserve",rules.stockStone);
        rules.planDays=positive(config,"council.horizon-days",rules.planDays);
        rules.toolReserve=positive(config,"council.tool-reserve",rules.toolReserve);
        rules.toolLeadUses=positive(config,"council.tool-lead-uses",rules.toolLeadUses);
        rules.seedReserve=positive(config,"council.seed-reserve",rules.seedReserve);
        rules.fuelReserve=positive(config,"council.fuel-reserve",rules.fuelReserve);
        rules.dailyTradeTrust=positive(config,"reputation.daily-positive-limit",rules.dailyTradeTrust);
        rules.foodHelpTrust=positive(config,"reputation.food-help-trust",rules.foodHelpTrust);
        rules.contractTrust=positive(config,"reputation.contract-trust",rules.contractTrust);
        rules.contractDays=positive(config,"contracts.duration-days",rules.contractDays);
        rules.buildMissionReward=positive(config,"missions.build-reward",rules.buildMissionReward);
        rules.repairMissionReward=positive(config,"missions.repair-reward",rules.repairMissionReward);
        rules.defenseMissionReward=positive(config,"missions.defense-reward",rules.defenseMissionReward);
        rules.tradeTrustCeiling=positive(config,"reputation.trade-ceiling",rules.tradeTrustCeiling);
        rules.tradeTrustPerDay=positive(config,"reputation.trade-per-day",rules.tradeTrustPerDay);
        rules.bootstrapStoneReserve=positive(config,"autonomy.bootstrap-stone-reserve",rules.bootstrapStoneReserve);
        rules.maxPopulation=Math.min(12,rules.maxPopulation);rules.dayMillis=positive(config,"simulation.day-seconds",1200)*1000L;
        rules.arrivalMillis=positive(config,"population.arrival.interval-seconds",240)*1000L;rules.firstArrivalMillis=positive(config,"population.arrival.first-seconds",90)*1000L;rules.craftIntervalMillis=positive(config,"autonomy.crafting.interval-seconds",30)*1000L;
        rules.childImmigrationPercent=config.getInt("population.child-immigration-percent",20);rules.hostilityTrust=config.getInt("reputation.hostility-below",50);rules.exhaustionDays=config.getDouble("nutrition.exhaustion-days",2);rules.foodCapacityMargin=config.getDouble("food-planning.capacity-margin",1.35);rules.foodGrowthBuffer=config.getInt("food-planning.growth-buffer",2);
        rules.adultHealth=health(config,"residents.health.adult",100);rules.childHealth=health(config,"residents.health.child",20);rules.guardHealth=health(config,"residents.health.guard",180);rules.healthRecovery=config.getDouble("residents.health.recovery-per-day",10);
        rules.workPerSecond=positive(config,"construction.labor-blocks-second",8);rules.foodPlanning=config.getBoolean("food-planning.enabled",true);rules.autonomy=config.getBoolean("autonomy.enabled",true);rules.timedCrafting=config.getBoolean("autonomy.crafting.enabled",true);rules.councilEnabled=config.getBoolean("council.enabled",true);rules.waterEnabled=config.getBoolean("water.enabled",true);
        if(rules.craftWorkersPerPass>8||rules.maxCraftPasses>4||rules.maxProjects>32||rules.arrivalBatch>8||rules.familySize>4||rules.socialTrustCap>300||rules.tradeTrustCeiling>=1000||rules.childImmigrationPercent<0||rules.childImmigrationPercent>100||rules.arrivalMillis<30000||rules.craftIntervalMillis<1000||!Double.isFinite(rules.foodCapacityMargin)||rules.foodCapacityMargin<1||rules.foodCapacityMargin>3||!Double.isFinite(rules.exhaustionDays)||rules.exhaustionDays<1||!Double.isFinite(rules.healthRecovery)||rules.healthRecovery<0)fail(Messages.text("Слишком большие лимиты совета и самоснабжения"));
        if(rules.friendTrust>=rules.buildTrust||rules.buildTrust>=1000)fail(Messages.text("Пороги доверия: 0 < друг < строительство < 1000"));
        int radius=positive(config,"generation.initial-radius",40);if(radius<32||radius>48)fail(Messages.text("Некорректные радиусы территории"));config.set("generation.max-radius",48);
        double chance=config.getDouble("generation.chance",.015);if(!Double.isFinite(chance)||chance<0||chance>1)fail(Messages.text("generation.chance: требуется 0..1"));
        for(String key:List.of("simulation.pulse-ticks","simulation.max-days-pass","limits.blocks-per-pass","limits.placement-columns-per-pass","limits.villages-per-pass","residents.navigation.path-requests-per-pass","residents.routine.max-npcs-pass","lighting.blocks-per-pass"))positive(config,key,8);
        for(String role:rules.tools.keySet().toArray(String[]::new)){var tool=rules.tools.get(role);String item=config.getString("tools."+role+".material",tool.item());material(item);rules.tools.put(role,new Rules.Tool(item,positive(config,"tools."+role+".durability",tool.durability())));}
        for(String role:List.of("FARMER","LUMBERJACK","ARTISAN","MINER","STONEMASON","SMITH"))rules.upgrades.put(role,List.of(new Rules.Tool(switch(role){case "FARMER"->"IRON_HOE";case "LUMBERJACK"->"IRON_AXE";default->"IRON_PICKAXE";},192)));
        var names=read(p,"names.yml");rules.villageNames=names.getStringList("villages");rules.residentNames=names.getStringList("residents");rules.surnames=names.getStringList("surnames");rules.legacyResidentNames.addAll(names.getStringList("legacy-residents"));
        for(var values:List.of(rules.villageNames,rules.residentNames,rules.surnames)){if(values.size()<5||values.stream().map(Names::key).distinct().count()!=values.size())fail(Messages.text("Нужно пять имён: ")+"names.yml");for(String name:values)Names.checked(name);}
        var culturesData=read(p,"cultures.yml");var c=section(culturesData,"polevichi");var palette=new LinkedHashMap<String,String>();section(c,"palette").getValues(false).forEach((key,value)->{material(value.toString());palette.put(key,value.toString().toLowerCase(Locale.ROOT));});
        cultures.put("polevichi",new Culture("polevichi",Messages.text(c.getString("name")),c.getStringList("biomes"),c.getStringList("names"),palette,c.getString("specialty"),Messages.text(c.getString("greeting")),Messages.text(c.getString("need")),quantities(c,"initial-goods")));
        rules.cultureWood.put("polevichi","OAK");rules.groveKinds.put("tree_garden_oak","OAK");rules.cultureDemand.put("polevichi",quantities(c,"demand"));rules.cultureNames.put("polevichi",c.getStringList("names"));rules.cultureProjects.put("polevichi",c.getStringList("development").stream().filter(rules.edition::building).toList());
        var plans=read(p,"buildings.yml");
        for(String key:plans.getKeys(false))if(Edition.BUILDINGS.contains(key)){
            var b=section(plans,key);var symbols=section(b,"symbols").getValues(false);var walkable=new HashSet<>(b.getStringList("walkable-symbols"));var cells=new ArrayList<Cell>();int y=0,width=0,depth=0;
            for(var layer:b.getMapList("layers")){int z=0,phase=((Number)layer.get("phase")).intValue();if(phase<0||phase>5)fail(Messages.text("Некорректный уровень: ")+key);
                for(Object row:(List<?>)layer.get("rows")){String line=row.toString();width=Math.max(width,line.length());for(int x=0;x<line.length();x++){String symbol=String.valueOf(line.charAt(x));if(symbol.equals(" "))continue;Object value=symbols.get(symbol);if(!(value instanceof String))fail(Messages.text("Неизвестный символ ")+symbol+Messages.text(" в ")+key);cells.add(new Cell(x,y,z,phase,value.toString(),Boolean.TRUE.equals(layer.get("floor"))||walkable.contains(symbol)));}z++;}depth=Math.max(depth,z);y++;}
            if(b.getBoolean("closed-gables",false))for(var fill:Gables.missing(cells.stream().map(cell->new Gables.Cell(cell.x(),cell.y(),cell.z(),cell.phase(),cell.data())).toList(),0))cells.add(new Cell(fill.x(),fill.y(),fill.z(),fill.phase(),fill.data()));
            cells.sort(Comparator.comparingInt(Cell::phase).thenComparingInt(Cell::y));if(cells.isEmpty()||cells.size()>12000||width>64||depth>64||b.getInt("beds")<0||b.getInt("level",1)!=1)fail(Messages.text("Пустой/слишком большой чертёж ")+key);
            Entrance entrance=null;if(b.isConfigurationSection("entrance")){entrance=new Entrance(b.getInt("entrance.x"),b.getInt("entrance.z"),b.getString("entrance.facing"));if(entrance.x()<0||entrance.x()>=width||entrance.z()<0||entrance.z()>=depth||!Set.of("NORTH","SOUTH","EAST","WEST").contains(entrance.facing()))fail(Messages.text("Некорректный открытый вход: ")+key);}
            int ground=b.getInt("ground-layer",0);if(ground<0||ground>3)fail(Messages.text("ground-layer: требуется 0..3 в ")+key);
            blueprints.put(key,new Blueprint(key,Messages.text(b.getString("name",key)),width,depth,b.getInt("beds"),1,b.getString("recipe"),List.copyOf(cells),quantities(b,"maintenance"),positive(b,"maintenance-days",7),b.getInt("unlock-stage",0),entrance,ground,b.getBoolean("closed-gables",false)));designVersions.put(key,b.getInt("design-version",1));
        }
        for(String required:List.of("hall","warehouse","garden","tree_garden","starter_quarry","soil_pit","relief_garden","camp_workbench","family_home"))if(!blueprints.containsKey(required))fail(Messages.text("Нет чертежа ")+required);
        var economy=read(p,"economy.yml");section(economy,"food").getValues(false).forEach((item,value)->{material(item);int n=((Number)value).intValue();if(n<=0)fail(Messages.text("Питательность ")+item);rules.foods.put(item,n);});
        for(String item:section(economy,"prices").getKeys(false)){material(item);var price=section(economy,"prices."+item);rules.prices.put(item,new Rules.Price(positive(price,"base",1),positive(price,"target",64),positive(price,"batch",1),positive(price,"bid-cap-hundredths",100000)));}
        var produced=blueprints.values().stream().map(Blueprint::recipe).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        for(String key:section(economy,"recipes").getKeys(false))if(produced.contains(key)){var recipe=section(economy,"recipes."+key);String mode=recipe.getString("mode","standard");if(!mode.equals("standard"))fail(Messages.text("Неизвестный вид производства: ")+key);String role=recipe.getString("role");Village.Role.valueOf(role);rules.recipes.put(key,new Rules.Recipe(role,quantities(recipe,"input"),quantities(recipe,"output"),recipe.getString("condition","land"),positive(recipe,"tool-wear",1),positive(recipe,"renew-days",1),mode,recipe.getString("equipment"),positive(recipe,"equipment-durability",64)));}
        if(economy.isConfigurationSection("crafting"))for(String key:section(economy,"crafting").getKeys(false)){var craft=section(economy,"crafting."+key);String station=craft.getString("facility","bench"),role=craft.getString("tool-role");if(!Set.of("hand","bench","furnace","forge").contains(station))continue;if(role!=null&&!rules.tools.containsKey(role))continue;var input=quantities(craft,"input");var output=quantities(craft,"output");if(input.isEmpty()||output.isEmpty())fail(Messages.text("Ремесло требует сырьё и результат: ")+key);rules.crafts.put(key,new Rules.Craft(input,output,quantities(craft,"fuel"),positive(craft,"batch",4),station,role));}
        TradeCatalog.extend(rules);if(economy.isConfigurationSection("biome-production"))for(String biome:section(economy,"biome-production").getKeys(false)){var rates=new HashMap<String,Integer>();for(var entry:section(economy,"biome-production."+biome).getValues(false).entrySet())if(rules.recipes.containsKey(entry.getKey())){int rate=((Number)entry.getValue()).intValue();if(rate<0||rate>200)fail(Messages.text("Некорректная производительность биома ")+biome);rates.put(entry.getKey(),rate);}rules.biomeProduction.put(biome,rates);}
        var layoutData=read(p,"layouts.yml");for(String key:layoutData.getKeys(false)){var layout=section(layoutData,key);var plots=new ArrayList<Plot>();for(var row:layout.getMapList("plots")){String kind=Objects.toString(row.get("kind"));if(!blueprints.containsKey(kind))fail(Messages.text("Некорректный участок layouts.yml: ")+key);plots.add(new Plot(kind,((Number)row.get("x")).intValue(),((Number)row.get("z")).intValue()));}if(plots.size()!=3)fail(Messages.text("Нужны ратуша, амбар и огород в ")+key);layouts.put(key,new StartLayout(key,Messages.text(layout.getString("name",key)),List.copyOf(plots)));}
        var text=read(p,"dialogues.yml");for(String key:text.getKeys(false))dialogues.put(key,text.getStringList(key).stream().map(Messages::text).toList());
        for(String role:List.of("ELDER","MERCHANT","BUILDER","FARMER","LUMBERJACK","FISHER","ARTISAN","GUARD","MINER","STONEMASON","SMITH","BAKER","WATER_CARRIER","CHILD"))if(dialogues.getOrDefault(role,List.of()).size()<4)fail(Messages.text("Нужно четыре реплики: ")+role);
        for(var culture:cultures.values()){var checked=new HashSet<String>();for(var b:blueprints.values())for(var cell:b.cells())if(checked.add(cell.data()))resolve(culture,cell.data());}
        for(var b:blueprints.values())if(b.recipe()!=null&&!rules.recipes.containsKey(b.recipe()))fail(Messages.text("Нет рецепта: ")+b.recipe());
        rules.edition.apply(rules);for(String file:FILES)if(!file.equals("config.yml"))contentSignatures.put(file,fileSignature(p.dataDirectory().resolve(file)));
        }
    }
    private static YamlConfiguration read(LivingVillagesPlugin p,String name){try(var in=Files.newBufferedReader(p.dataDirectory().resolve(name),StandardCharsets.UTF_8);var defaults=new InputStreamReader(Objects.requireNonNull(p.getResource(name)),StandardCharsets.UTF_8)){return YamlDefaults.load(in,defaults);}catch(Exception error){throw new IllegalArgumentException(Messages.text("Ошибка YAML: ")+name+": "+error.getMessage(),error);}}
    public String languageOnlyCandidate(LivingVillagesPlugin p){var next=read(p,"config.yml");String selected=Messages.validate(next.getString("language","en"));if(selected.equals(language)||!languageConfigSignature.equals(configSignature(next)))return null;for(var entry:contentSignatures.entrySet())if(entry.getValue()==null||!entry.getValue().equals(fileSignature(p.dataDirectory().resolve(entry.getKey()))))return null;return selected;}
    public void selectLanguage(String next){try(var preview=Messages.preview(next)){cultures.replaceAll((id,c)->new Culture(c.id(),Messages.text(c.name()),c.biomes(),c.names(),c.palette(),c.specialty(),Messages.text(c.greeting()),Messages.text(c.need()),c.initialGoods()));blueprints.replaceAll((id,b)->new Blueprint(b.id(),Messages.text(b.name()),b.width(),b.depth(),b.beds(),b.level(),b.recipe(),b.cells(),b.upkeep(),b.upkeepDays(),b.unlockStage(),b.entrance(),b.groundLayer(),b.closedGables()));layouts.replaceAll((id,l)->new StartLayout(l.id(),Messages.text(l.name()),l.plots()));dialogues.replaceAll((key,list)->list.stream().map(Messages::text).toList());}language=next;config.set("language",next);blueprintCache.clear();}
    
    public int designVersion(Village v,String key){return Math.max(designVersions.getOrDefault(key,1),Roofing.VERSION);}
    public Blueprint blueprint(Village v,String key){return blueprintCache.computeIfAbsent(key,id->{var b=blueprints.get(id);return b==null?null:Roofing.apply(b);});}
    public void applyDefinition(Village v,Village.Building b){if(b.contentVersion>=3)return;var def=blueprints.get(b.kind);if(def!=null&&b.contentVersion<2){b.level=1;b.upkeep.putAll(def.upkeep());b.upkeepDays=def.upkeepDays();b.upkeepDue=v.days+b.upkeepDays;}if(b.resourceLeft>=0)b.resourceSurveyed=true;b.contentVersion=3;}
public String resolve(Culture c,String data) {
        if(data.contains("$roof_block"))data=data.replace("$roof_block",Roofing.solid(c.palette().get("roof")));
        for(var e:c.palette().entrySet()) data=data.replace("$"+e.getKey(),e.getValue());
        String base=data.split("\\[")[0].replace("minecraft:","");material(base);
        if(data.contains("$")) fail(Messages.text("Неразрешённая палитра: ")+data);
        return data.toLowerCase(Locale.ROOT);
    }
private static int positive(ConfigurationSection c,String key,int fallback) {int n=c.getInt(key,fallback);if(n<=0)fail(Messages.text("Требуется положительное число: ")+key);return n;}
private static double health(ConfigurationSection c,String key,double fallback){double n=c.getDouble(key,fallback);if(!Double.isFinite(n) || n<=0 || n>1024)fail(key+Messages.text(": требуется число больше 0 и не больше 1024"));return n;}
private static Map<String,Integer> quantities(ConfigurationSection c,String path) {
        Map<String,Integer> out=new LinkedHashMap<>();if(!c.isConfigurationSection(path))return out;
        section(c,path).getValues(false).forEach((k,v)->{material(k);int n=((Number)v).intValue();if(n<=0)fail(Messages.text("Количество ")+k);out.put(k,n);});return out;
    }
private static ConfigurationSection section(ConfigurationSection c,String key) {var s=c.getConfigurationSection(key);if(s==null)fail(Messages.text("Отсутствует раздел: ")+key);return s;}
private static void material(String key) {if(Material.matchMaterial(key)==null)fail(Messages.text("Неизвестный материал: ")+key);}
private static void fail(String message) {throw new IllegalArgumentException(message);}
private static Object canonical(Object value){
        if(value instanceof ConfigurationSection section)return canonical(section.getValues(false));
        if(value instanceof Map<?,?> map){var result=new TreeMap<String,Object>();map.forEach((key,item)->result.put(key.toString(),canonical(item)));return result;}
        if(value instanceof List<?> list)return list.stream().map(Content::canonical).toList();return value;
    }
static String configSignature(YamlConfiguration source){var values=new TreeMap<String,Object>(source.getValues(false));values.remove("language");return new com.google.gson.Gson().toJson(canonical(values));}
private static String fileSignature(java.nio.file.Path path){
        try{var digest=java.security.MessageDigest.getInstance("SHA-256");try(var input=new java.security.DigestInputStream(Files.newInputStream(path),digest)){input.transferTo(java.io.OutputStream.nullOutputStream());}return HexFormat.of().formatHex(digest.digest());}
        catch(Exception error){return null;}
    }
}
