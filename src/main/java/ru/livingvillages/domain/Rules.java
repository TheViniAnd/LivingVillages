package ru.livingvillages.domain;

import java.util.*;

public final class Rules {
    public Edition edition=Edition.FREE;
    public boolean foodPlanning;
    public boolean timedCrafting;public long craftIntervalMillis=30000;public int maxCraftPasses=1,craftWorkersPerPass=2;
    public Set<String> derivedPrices=new HashSet<>();
    public int bootstrapStoneReserve=64;
    public double foodCapacityMargin=1.35;public int foodGrowthBuffer=2;
    public Map<String,String> cultureWood=new HashMap<>();
    public Map<String,String> groveKinds=new LinkedHashMap<>();
    public int friendTrust=50,buildTrust=100,renameTrust=500;
    public List<String> villageNames=List.of(),residentNames=List.of(),surnames=List.of();
    public Set<String> legacyResidentNames=new HashSet<>();
    public Map<String,UUID> villageNameOwners=new HashMap<>(),residentNameOwners=new HashMap<>();
    public int maximumReserve=4096,maximumFoodReserveDays=30,securityResidentsPerGuard=8,maximumGuards=4,tradeStockMultiplier=2,tradePriceBonus=5;
    public long treasuryMinimum=64,arrivalMillis,firstArrivalMillis=90000;
    public int arrivalBatch=2,regularResidentsPerGuard,regularGuardLimit=8;
    public double productionLaborMultiplier=1.2;
    public long dayMillis = 1_200_000;
    public int foodPerResident = 4, maxPopulation = 12, maxProjects = 6, dailyTradeTrust = 20;
    public int warningDays = 2, deathDays = 3, immigrationDays = 4;
    public int forgettingGraceDays=14,forgettingPerDay=2,socialDailyTrust=3,socialTrustCap=100,hitPenalty=5,killPenalty=100;
    public int familySize=2,familyStartDay=3,familyIntervalDays=2;
    public int foodHelpTrust=10,contractTrust=15,contractDays=3,foodTargetDays=7;
    public int buildMissionReward=32,repairMissionReward=16,defenseMissionReward=16;
    public double adultHealth=100,childHealth=20,guardHealth=180,healthRecovery=10;
    
    
    
    
    
    
    
    public int homeImmigrationDays=1,minimumBuilders=1,hostilityTrust=50;
    public int childGrowthDays=8,childImmigrationPercent=20;
    public int[] stagePopulation={5,8,16,24};
    public Map<String,Tool> tools=new LinkedHashMap<>(Map.of("FARMER",new Tool("STONE_HOE",64),"LUMBERJACK",new Tool("STONE_AXE",64),"FISHER",new Tool("FISHING_ROD",64),"ARTISAN",new Tool("STONE_PICKAXE",64)));
    {for(String role:List.of("MINER","STONEMASON","SMITH"))tools.put(role,new Tool("STONE_PICKAXE",64));tools.put("HUNTER",new Tool("STONE_AXE",64));}
    public Map<String,Map<String,Integer>> cultureDemand=new HashMap<>();
    public Map<String,List<String>> cultureNames=new HashMap<>(),cultureProjects=new HashMap<>();
    public Map<String,Map<String,Integer>> biomeProduction=new HashMap<>();
    public record Tool(String item,int durability){}
    public double exhaustionDays = 2, workPerSecond = 8;
    public Map<String,Integer> foods = new LinkedHashMap<>();
    public Map<String,Price> prices = new LinkedHashMap<>();
    public Map<String,Recipe> recipes = new LinkedHashMap<>();
    public boolean autonomy,waterEnabled=true;
    public int craftWorkers=2,stockLogs=32,stockStone=64;
    public boolean councilEnabled;
    public int planDays=10,toolReserve=1,toolLeadUses=8,seedReserve=2,fuelReserve=4;
    
    

    public Map<String,Craft> crafts=new LinkedHashMap<>();
    public Map<String,List<Tool>> upgrades=new HashMap<>();
    public record Craft(Map<String,Integer> input,Map<String,Integer> output,Map<String,Integer> fuel,int batch,String facility,String toolRole){}
    public int tradeTrustCeiling=300,tradeTrustPerDay=3;
    public Map<String,Integer> trustCaps=new HashMap<>(Map.of("TRADE",300,"DELIVERY",350,"CONSTRUCTION",350,"RESTORATION",350,"DEFENSE",350,"HUSBANDRY",200,"HELP",350,"SOCIAL",100));
    public Map<String,Integer> trustAction=new HashMap<>(Map.of("TRADE",1,"DELIVERY",4,"CONSTRUCTION",3,"RESTORATION",4,"DEFENSE",2,"HUSBANDRY",2,"HELP",4,"SOCIAL",1));
    public record Price(int base, int target, int batch, int bidCapHundredths) { }
    public double productionRate(Village village,Village.Building building){return biomeProduction.getOrDefault(building.biome==null?village.biome:building.biome,Map.of()).getOrDefault(building.recipe,100)/100.0;}
    public record Recipe(String role, Map<String,Integer> input, Map<String,Integer> output,
                         String condition, int toolWear, int renewDays,String mode,String equipment,int equipmentDurability) {
        public Recipe(String role,Map<String,Integer> input,Map<String,Integer> output,String condition,int toolWear,int renewDays){this(role,input,output,condition,toolWear,renewDays,"standard",null,64);}
    }
    
    
    public long foodUnits(Village v) {
        return foods.entrySet().stream().mapToLong(e -> (long)e.getValue()*v.stock.get(e.getKey())).sum();
    }
    public long dailyFood(Village v) { return (long)v.living()*foodPerResident; }
}
