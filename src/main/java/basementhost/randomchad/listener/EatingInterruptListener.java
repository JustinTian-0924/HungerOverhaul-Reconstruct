package basementhost.randomchad.listener;

import basementhost.randomchad.data.EatingInterruptReason;
import basementhost.randomchad.manager.EatingManager;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.Objects;

// Converts player actions into configurable eating interruption reasons.
public final class EatingInterruptListener implements Listener {
	private final EatingManager eatingManager;

	public EatingInterruptListener(EatingManager eatingManager) {
		this.eatingManager = Objects.requireNonNull(eatingManager, "eatingManager");
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onDamage(EntityDamageEvent event) {
		if (event.getEntity() instanceof Player player && event.getFinalDamage() > 0.0) {
			eatingManager.interruptIfConfigured(player, EatingInterruptReason.DAMAGE);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onMove(PlayerMoveEvent event) {
		if (changedBlock(event.getFrom(), event.getTo())) {
			eatingManager.interruptIfConfigured(event.getPlayer(), EatingInterruptReason.MOVE);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onSneakToggle(PlayerToggleSneakEvent event) {
		eatingManager.interruptIfConfigured(event.getPlayer(), EatingInterruptReason.SNEAK_TOGGLE);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHeldItemChange(PlayerItemHeldEvent event) {
		eatingManager.interruptNativeUse(event.getPlayer(), EatingInterruptReason.ITEM_SWITCH);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onSwapHands(PlayerSwapHandItemsEvent event) {
		eatingManager.interruptNativeUse(event.getPlayer(), EatingInterruptReason.ITEM_SWITCH);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onStopUsingItem(PlayerStopUsingItemEvent event) {
		eatingManager.handleStoppedUsingItem(event.getPlayer());
	}

	private boolean changedBlock(Location from, Location to) {
		return !from.getWorld().equals(to.getWorld())
				|| from.getBlockX() != to.getBlockX()
				|| from.getBlockY() != to.getBlockY()
				|| from.getBlockZ() != to.getBlockZ();
	}
}
