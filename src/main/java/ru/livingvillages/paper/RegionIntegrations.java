package ru.livingvillages.paper;

import ru.livingvillages.i18n.Messages;

import org.bukkit.*;
import org.bukkit.plugin.*;
import java.lang.reflect.*;
import java.util.*;

/** Optional public-API bridge. Unknown API or unavailable region data always denies placement. */
public final class RegionIntegrations {
    private final Set<String> covered=new HashSet<>();
    public Set<String> covered(){return Set.copyOf(covered);}
    public RegionIntegrations(LivingVillagesPlugin owner){
        griefPrevention(owner);
        Plugin wg=Bukkit.getPluginManager().getPlugin("WorldGuard");
        if(wg!=null && wg.isEnabled())try{
            ClassLoader loader=wg.getClass().getClassLoader();
            Class<?> root=Class.forName("com.sk89q.worldguard.WorldGuard",true,loader);
            Class<?> region=Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion",true,loader);
            Class<?> cube=Class.forName("com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion",true,loader);
            Class<?> vector=Class.forName("com.sk89q.worldedit.math.BlockVector3",true,loader);
            Class<?> worldType=Class.forName("com.sk89q.worldedit.world.World",true,loader);
            Method adapt=Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter",true,loader).getMethod("adapt",World.class);
            Object instance=root.getMethod("getInstance").invoke(null),platform=root.getMethod("getPlatform").invoke(instance);
            Object container=platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Method get=container.getClass().getMethod("get",worldType),at=vector.getMethod("at",int.class,int.class,int.class),getId=region.getMethod("getId"),getFlags=region.getMethod("getFlags");
            Constructor<?> constructor=cube.getConstructor(String.class,vector,vector);
            PlacementGuard bridge=(world,minX,minZ,maxX,maxZ)->{
                if(!wg.isEnabled())return false;
                try{
                    Object manager=get.invoke(container,adapt.invoke(null,world));if(manager==null)return false;
                    Object probe=constructor.newInstance("__livingvillages_probe",at.invoke(null,minX,world.getMinHeight(),minZ),at.invoke(null,maxX,world.getMaxHeight()-1,maxZ));
                    Object set=manager.getClass().getMethod("getApplicableRegions",region).invoke(manager,probe);
                    for(Object entry:(Iterable<?>)set)if(!getId.invoke(entry).equals("__global__"))return false;
                    Object global=manager.getClass().getMethod("getRegion",String.class).invoke(manager,"__global__");
                    if(global!=null)for(var flag:((Map<?,?>)getFlags.invoke(global)).entrySet()){
                        String name=Objects.toString(flag.getKey().getClass().getMethod("getName").invoke(flag.getKey()));
                        if(Set.of("build","block-place","entry").contains(name) && Objects.toString(flag.getValue()).equals("DENY"))return false;
                    }
                    return true;
                }catch(ReflectiveOperationException | RuntimeException error){return false;}
            };
            Bukkit.getServicesManager().register(PlacementGuard.class,bridge,owner,ServicePriority.Normal);covered.add("WorldGuard");covered.add("WorldEdit");
            owner.getLogger().info(Messages.text("Подключена консервативная проверка регионов WorldGuard через публичный API."));
        }catch(ReflectiveOperationException | LinkageError e){owner.getLogger().warning(Messages.text("WorldGuard API не распознан; размещение останется закрытым: ")+e.getClass().getSimpleName());}
    }
    private void griefPrevention(LivingVillagesPlugin owner){
        Plugin plugin=Bukkit.getPluginManager().getPlugin("GriefPrevention");if(plugin==null || !plugin.isEnabled())return;
        try{
            Object store=plugin.getClass().getField("dataStore").get(plugin);if(store==null)throw new IllegalStateException(Messages.text("Хранилище приватов ещё не готово"));
            Method claims=store.getClass().getMethod("getClaims",int.class,int.class);
            Class<?> claim=Class.forName("me.ryanhamshire.GriefPrevention.Claim",true,plugin.getClass().getClassLoader());
            Method low=claim.getMethod("getLesserBoundaryCorner"),high=claim.getMethod("getGreaterBoundaryCorner");
            PlacementGuard guard=(world,minX,minZ,maxX,maxZ)->{
                if(!plugin.isEnabled())return false;
                try{
                    for(int x=minX>>4;x<=maxX>>4;x++)for(int z=minZ>>4;z<=maxZ>>4;z++){
                        Object value=claims.invoke(store,x,z);if(!(value instanceof Collection<?> regions))return false;
                        for(Object region:regions){Location a=(Location)low.invoke(region),b=(Location)high.invoke(region);
                            if(a.getWorld()==null || b.getWorld()==null)return false;
                            if(a.getWorld().getUID().equals(world.getUID()) && a.getBlockX()<=maxX && b.getBlockX()>=minX && a.getBlockZ()<=maxZ && b.getBlockZ()>=minZ)return false;
                        }
                    }return true;
                }catch(ReflectiveOperationException | RuntimeException error){return false;}
            };
            Bukkit.getServicesManager().register(PlacementGuard.class,guard,owner,ServicePriority.Normal);covered.add("GriefPrevention");
            owner.getLogger().info(Messages.text("Подключена проверка всех пересекающихся приватов GriefPrevention через публичный API."));
        }catch(ReflectiveOperationException | LinkageError | RuntimeException error){owner.getLogger().warning(Messages.text("GriefPrevention API не распознан; размещение останется закрытым: ")+error.getClass().getSimpleName());}
    }
}
