package basementhost.randomchad.listener;

import basementhost.randomchad.manager.PlayerLifecycleManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Objects;

// Connects death, respawn, and world changes to player lifecycle rules.
public final class PlayerLifecycleListener implements Listener {
	private final PlayerLifecycleManager lifecycleManager;

	public PlayerLifecycleListener(PlayerLifecycleManager lifecycleManager) {
		this.lifecycleManager = Objects.requireNonNull(lifecycleManager, "lifecycleManager");
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerDeath(PlayerDeathEvent event) {
		lifecycleManager.handleDeath(event.getEntity());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerRespawn(PlayerRespawnEvent event) {
		lifecycleManager.handleRespawn(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
		lifecycleManager.handleWorldChange(event.getPlayer());
	}
}
