package basementhost.randomchad.listener;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

// Shares one main-thread player roster across the high-frequency display tasks.
public final class OnlinePlayerRoster implements Listener {
	private static final Player[] EMPTY = new Player[0];

	private final Server server;
	private final Map<UUID, Player> tracked = new LinkedHashMap<>();
	private Player[] players = EMPTY;
	private boolean enabled;

	public OnlinePlayerRoster(Server server) {
		this.server = Objects.requireNonNull(server, "server");
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}
		this.enabled = enabled;
		tracked.clear();
		players = EMPTY;
		if (enabled) {
			for (Player player : server.getOnlinePlayers()) {
				tracked.put(player.getUniqueId(), player);
			}
			rebuild();
		}
	}

	public void forEachOnlinePlayer(Consumer<? super Player> action) {
		if (!enabled) {
			for (Player player : server.getOnlinePlayers()) {
				action.accept(player);
			}
			return;
		}
		Player[] snapshot = players;
		for (Player player : snapshot) {
			// A synchronous callback may disconnect a player before their turn.
			if (snapshot == players || tracked.get(player.getUniqueId()) == player) {
				action.accept(player);
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		if (enabled && player.isOnline() && tracked.put(player.getUniqueId(), player) != player) {
			rebuild();
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		UUID playerId = player.getUniqueId();
		if (enabled && tracked.get(playerId) == player) {
			tracked.remove(playerId);
			rebuild();
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onRespawn(PlayerRespawnEvent event) {
		Player player = event.getPlayer();
		UUID playerId = player.getUniqueId();
		if (enabled && tracked.containsKey(playerId) && tracked.put(playerId, player) != player) {
			rebuild();
		}
	}

	private void rebuild() {
		players = tracked.isEmpty() ? EMPTY : tracked.values().toArray(Player[]::new);
	}
}
