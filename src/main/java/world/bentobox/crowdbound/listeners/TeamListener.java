package world.bentobox.crowdbound.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import world.bentobox.bentobox.api.events.team.TeamJoinedEvent;
import world.bentobox.bentobox.api.events.team.TeamKickEvent;
import world.bentobox.bentobox.api.events.team.TeamLeaveEvent;
import world.bentobox.bentobox.api.user.User;
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
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        // Check if player is in the world
        if (!addon.inWorld(player.getWorld())) {
            return;
        }
        // Check if the player has a claim
        addon.getIslands().getIslands(player.getWorld(), player.getUniqueId()).forEach(this::resize);
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent e) {
        Player player = e.getPlayer();
        // Check if player is in the world
        if (!addon.inWorld(e.getTo())) {
            return;
        }
        // Check if the player has a claim
        addon.getIslands().getIslands(e.getTo().getWorld(), player.getUniqueId()).forEach(this::resize);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTeamJoin(TeamJoinedEvent e) {
        resize(e.getIsland());
    }
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTeamKick(TeamKickEvent e) {
        resize(e.getIsland());
    }
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTeamLeave(TeamLeaveEvent e) {
        resize(e.getIsland());
    }

    private void resize(Island claim) {
        if (addon.inWorld(claim.getWorld())) {
            // In our world
            // Resize this claim
            int size = addon.getSettings().getMemberBonus() * claim.getMemberSet().size();
            int oldSize = claim.getProtectionRange();
            // Set to size of team
            claim.setRange(size);
            claim.setProtectionRange(size); // This should trigger an update to the border viewed via the event
            if (size == oldSize) {
                return;
            }
            // Determine the message key suffix based on whether the team size increased or decreased
            final String suffix = size > oldSize ? "increase" : "decrease";           

            // Notify players
            if (claim.isOwned()) {
                // Tell owner
                User.getInstance(claim.getOwner()).sendMessage("crowdbound.claim.team-" + suffix + "-owner");
            }

            // Tell players on the claim (excluding the owner, if one exists)
            claim.getPlayersOnIsland().stream()
            .filter(p -> claim.getOwner() == null || !p.getUniqueId().equals(claim.getOwner()))
            .map(User::getInstance)
            .forEach(u -> u.sendMessage("crowdbound.claim.team-" + suffix));
            
            
        }
        
    }

}

