package ru.livingvillages.paper;

import org.bukkit.World;

/** Optional Bukkit service for region-protection plugins. False vetoes all placement. */
public interface PlacementGuard {
    boolean canReserve(World world,int minX,int minZ,int maxX,int maxZ);
}
