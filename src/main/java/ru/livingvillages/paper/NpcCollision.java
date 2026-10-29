package ru.livingvillages.paper;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.scoreboard.Team;

/** Keep NPCs pickable by projectiles; the team rule prevents bodily pushing without invulnerability. */
final class NpcCollision {
    private Team team;
    NpcCollision(){ensure();}
    private Team ensure(){var board=Bukkit.getScoreboardManager().getMainScoreboard();var existing=board.getTeam("livingv_npc");team=existing==null?board.registerNewTeam("livingv_npc"):existing;team.setOption(Team.Option.COLLISION_RULE,Team.OptionStatus.NEVER);team.setAllowFriendlyFire(true);return team;}
    void join(Entity entity){ensure().addEntity(entity);}
    void leave(Entity entity){var current=Bukkit.getScoreboardManager().getMainScoreboard().getTeam("livingv_npc");if(current!=null)current.removeEntity(entity); }
    void close(){var current=Bukkit.getScoreboardManager().getMainScoreboard().getTeam("livingv_npc");if(current!=null && current.getEntries().isEmpty())current.unregister();}
}
