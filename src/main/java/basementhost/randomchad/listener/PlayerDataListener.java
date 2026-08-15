package basementhost.randomchad.listener;

import basementhost.randomchad.manager.PlayerDataPersistenceManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

// Connects player join and quit events to data storage
public final class PlayerDataListener implements Listener {
	private final PlayerDataPersistenceManager persistenceManager;

	public PlayerDataListener(PlayerDataPersistenceManager persistenceManager) {
		this.persistenceManager = Objects.requireNonNull(persistenceManager, "persistenceManager");
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		persistenceManager.loadPlayerData(event.getPlayer());
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		persistenceManager.saveAndCachePlayerData(event.getPlayer());
	}
}
