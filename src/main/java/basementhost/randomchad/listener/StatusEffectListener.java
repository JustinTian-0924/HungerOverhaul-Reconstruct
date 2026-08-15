package basementhost.randomchad.listener;

import basementhost.randomchad.manager.StatusAttributeManager;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

// Removes plugin-owned attributes at player join/quit/death/change gamemodes
public final class StatusEffectListener implements Listener {
	private final StatusAttributeManager statusAttributeManager;

	public StatusEffectListener(StatusAttributeManager statusAttributeManager) {
		this.statusAttributeManager = Objects.requireNonNull(statusAttributeManager, "statusAttributeManager");
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerJoin(PlayerJoinEvent event) {
		statusAttributeManager.removePlayerEffects(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		statusAttributeManager.removePlayerEffects(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerDeath(PlayerDeathEvent event) {
		statusAttributeManager.removePlayerEffects(event.getEntity());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onGameModeChange(PlayerGameModeChangeEvent event) {
		if (!isAffectedGameMode(event.getNewGameMode())) {
			statusAttributeManager.removePlayerEffects(event.getPlayer());
		}
	}

	private boolean isAffectedGameMode(GameMode gameMode) {
		return gameMode == GameMode.SURVIVAL || gameMode == GameMode.ADVENTURE;
	}
}
