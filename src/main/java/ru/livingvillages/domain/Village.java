package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

import java.util.*;

/** Persisted aggregate. Only the server thread may mutate it. No Bukkit objects. */
public final class Village {
    public transient Edition edition=Edition.FREE;
    public int format = 15;
    public long craftElapsedMillis,craftRound;
    public UUID id = UUID.randomUUID(), world;
    public String name, culture, biome;
    public String layout="legacy",worldName;
    public String developmentReason = Messages.text("Начальное обустройство");
    public String plannedBuilding,planningReason;
    public Map<String,Integer> siteSearch=new HashMap<>();
    public int x, y, z, radius = 40, capacity = 4096;
    public long lastFamilyPlanDay=-1;
    public long expansionCount;
    public String expansionReason=Messages.text("Расширение пока не требуется");
    public long treasury = 1024, elapsedMillis, days, raidAfterDay = 7;
    public double nutrition = 1;
    public int nutritionCarry;
    public int stage,highestStage;
    public int level=1,highestLevel=1,territoryLevel=1,layoutVariant;
    public long lastImmigrationDay=-1, arrivalElapsedMillis;
    
    
    
    public boolean paused, abandoned;
    public UUID discoverer;
    public Integer mapId;
    public Set<UUID> discovered = new HashSet<>();
    public Map<UUID, Relation> relations = new HashMap<>();
    public Map<UUID, Map<String,Integer>> claims = new HashMap<>();
    public Map<UUID, Long> payouts = new HashMap<>();
    public Map<String,Integer> commonReturns=new TreeMap<>();
    public Stock stock = new Stock();
    public List<Resident> residents = new ArrayList<>();
    public List<Building> buildings = new ArrayList<>();
    public List<Project> projects = new ArrayList<>();
    public List<SupplyTask> tasks = new ArrayList<>();
    
    public List<Mission> missions=new ArrayList<>();
    public int[] mapTerrain=new int[1024];
    public int mapSurveyRadius;
    public Set<String> failedSites=new HashSet<>();
    public Map<String,Long> produced=new TreeMap<>(),crafted=new TreeMap<>();
    public CouncilPlan council=new CouncilPlan();
    
    public static final class CouncilPlan {
        public long startedDay=-1,reviewedDay=-1;public int revision;public String reason=Messages.text("Совет ещё не собрался"),lastState="";
        public List<Goal> goals=new ArrayList<>();public List<String> completed=new ArrayList<>();
    }
    public static final class Goal {public String key,kind,reason;public int target,current;public long startedDay;public boolean complete;}
    
    public String supplyReason=Messages.text("Хозяйство ещё не работало");
    public Laws laws = new Laws();
    public transient boolean underAttack;
    public int living() { return (int) residents.stream().filter(r -> r.alive).count(); }
    public boolean adult(Resident r){return r.matureDay<=days;}
    public Relation relation(UUID player) { return relations.computeIfAbsent(player, k -> {var r=new Relation();r.lastContactDay=days;r.lastDecayDay=days;return r;}); }
    
    public int beds() { return buildings.stream().filter(Housing::usable).mapToInt(b -> b.beds).sum(); }
    public Resident resident(UUID resident) { return residents.stream().filter(r -> r.id.equals(resident)).findFirst().orElse(null); }
    public enum Role { ELDER, MERCHANT, BUILDER, FARMER, LUMBERJACK, FISHER, ARTISAN, GUARD, MINER, STONEMASON, SMITH, HERDER, BEEKEEPER, HUNTER, BAKER, WATER_CARRIER, CHILD }
    public static final class Resident {
        public UUID id = UUID.randomUUID(), entity;
        public String name;
        public Role role;
        public UUID home, workplace,craftWorkplace,family;
        public long craftDay=-1,lastCraftRound=-1;
        public Position position;
        public boolean workplacePinned;
        
        
        
        
        
        
        public boolean alive = true;
        public double hungerDays;
        public long toolUses;
        public String toolKind;
        public long matureDay;
        public double health=-1,maxHealth;
        public long economicDay=-1;
        public Map<String,Long> tools=new HashMap<>();
    }
    
    
    public static final class Position {
        public double x,y,z;public float yaw;
        public Position(){}
        public Position(double x,double y,double z,float yaw){this.x=x;this.y=y;this.z=z;this.yaw=yaw;}
        public boolean valid(){return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Float.isFinite(yaw);}
    }
    public static final class Relation {
        public long lastContactDay=-1,lastDecayDay=-1;
        public boolean aggressor;
        public Map<UUID,Integer> friendship=new HashMap<>();
        public Map<UUID,Long> conversationDays=new HashMap<>(),friendContactDays=new HashMap<>(),friendDecayDays=new HashMap<>();
        public long socialDay=-1;public int socialToday;
        public int trust, tradeToday;
        public long tradeDay = -1;
        public Map<String,Integer> repeatedToday = new HashMap<>();
        public Set<String> deeds = new HashSet<>();
        public boolean finalTask;
        public String playerName;
        public int tradeTrustToday;
        public Map<String,Integer> contributionTrust=new HashMap<>();
        public void add(int amount) { trust = (int)Math.max(-1000L, Math.min(1000L, (long)trust + amount)); }
    }
    public static final class Laws {
        public boolean exportFood = false, immigration = true, expansion = true;
        public int foodDays = 2;
        public String focus = "production";
        public Map<String,Integer> strategic = new HashMap<>(Map.of("COAL",8,"COBBLESTONE",64,"IRON_INGOT",4));
        public Set<String> exportBans=new HashSet<>();
        
    }
    public static final class SupplyTask {
        public UUID id=UUID.randomUUID(),player;
        public String item,state="ACTIVE";
        public int amount,delivered;
        public long reward,paid,expiresDay;
    }
    public static final class Building {
        public UUID id = UUID.randomUUID(), owner,family;
        public int enclosureVersion, designVersion;
        public String kind, recipe,biome;
        public Set<String> footprint=new HashSet<>();
        
        
        public int x, y, z, width, depth, beds, rotation,plotPadding;
        public int workX, workY, workZ;
        public boolean complete, resourceConfirmed, damaged;
        public String damageReason;
        public long resourceLeft = -1, cycles;
        public double productionCredit, cycleReadyDay;
        public int contentVersion,level=1,upkeepDays=7;
        public Map<String,Integer> upkeep=new TreeMap<>();
        public long upkeepDue;
        public boolean upkeepMissing,resourceSurveyed;
        public Set<String> resourceCells=new HashSet<>();
        
        
        public Map<String,Long> equipmentUses=new HashMap<>();
        
        public String wood;
        public int roofVersion,noticeDepth;
        public int entryX,entryY,entryZ;
        public String entryFacing;
        public long plantedDay;
        public List<TreeSite> trees=new ArrayList<>();
        public Map<String,String> treeVisual=new HashMap<>();
    }
    public static final class TreeSite {public int x,y,z;public TreeSite(){}public TreeSite(int x,int y,int z){this.x=x;this.y=y;this.z=z;}}
    
    public static final class Mission {
        public UUID id=UUID.randomUUID(),player,target;
        public String kind,state="ACTIVE";
        public int goal,progress,baseline;
        public long reward,expiresDay;
        public Set<UUID> enemies=new HashSet<>();
    }
    public static final class Block {
        public int x, y, z, phase;
        public boolean floor;
        public String before, after, material;
        public Block() { }
        public Block(int x, int y, int z, int phase, String before, String after, String material) {
            this.x=x; this.y=y; this.z=z; this.phase=phase; this.before=before; this.after=after; this.material=material;
        }
    }
    public static final class Project {
        public UUID id = UUID.randomUUID(), owner, building;
        public String kind, state = "COLLECTING", reason = Messages.text("Ожидает материалы");
        public int index;
        public int priority;
        public boolean essential;
        public double credit;
        public boolean starter;
        public Map<String, Integer> required = new TreeMap<>(), escrow = new TreeMap<>();
        public List<Block> blocks = new ArrayList<>();
        public Set<UUID> contributors = new HashSet<>();
        public Map<UUID,Integer> deliveredBy=new HashMap<>();
        public boolean active() { return !state.equals("COMPLETE") && !state.equals("CANCELLED"); }
        public boolean funded() { return required.entrySet().stream().allMatch(e -> escrow.getOrDefault(e.getKey(),0) >= e.getValue()); }
        public Building building(Village v) { return v.buildings.stream().filter(b -> b.id.equals(building)).findFirst().orElseThrow(); }
    }
}
