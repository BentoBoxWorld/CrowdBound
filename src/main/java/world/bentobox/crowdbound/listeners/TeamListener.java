package world.bentobox.crowdbound.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.util.BoundingBox;

import world.bentobox.bentobox.api.events.team.TeamJoinedEvent;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.crowdbound.CrowdBound;

/**
 * Listens to team changes and adjusts the claim size accordingly
 * @author tastybento
 */
public class TeamListener implements Listener {

   private final CrowdBound addon;


    public TeamListener(CrowdBound addon) {
        this.addon = addon;

    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTeamIncrease(TeamJoinedEvent e) {
        Island claim = e.getIsland();
        if (addon.inWorld(claim.getWorld())) {
            // In our world
            // Try to increase size of this claim
            int size = addon.getSettings().getMemberBonus() * claim.getMemberSet().size();
            BoundingBox bb = BoundingBox.of(claim.getCenter().toVector(), size/2, size/2, size/2);
            // Set to size of team
            
            
        }
        
     }

 
}
