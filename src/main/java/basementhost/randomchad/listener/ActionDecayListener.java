package basementhost.randomchad.listener;

import basementhost.randomchad.manager.ActionDecayManager;
import basementhost.randomchad.manager.ActionDecayManager.ActionType;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;

import java.util.Objects;

// Converts successful game events into configured one-time action costs.
public final class ActionDecayListener implements Listener {
	private final ActionDecayManager actionDecayManager;

	public ActionDecayListener(ActionDecayManager actionDecayManager) {
		this.actionDecayManager = Objects.requireNonNull(actionDecayManager, "actionDecayManager");
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerStatisticIncrement(PlayerStatisticIncrementEvent event) {
		if (event.getStatistic() == Statistic.JUMP) {
			actionDecayManager.applyActionCost(event.getPlayer(), ActionType.JUMPING);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerAttack(EntityDamageByEntityEvent event) {
		if (event.getFinalDamage() <= 0.0 || !(event.getDamager() instanceof Player player)) {
			return;
		}

		actionDecayManager.applyActionCost(player, ActionType.ATTACKING);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerDamage(EntityDamageEvent event) {
		if (event.getCause() == EntityDamageEvent.DamageCause.STARVATION
				|| event.getFinalDamage() <= 0.0
				|| !(event.getEntity() instanceof Player player)) {
			return;
		}

		actionDecayManager.applyActionCost(player, ActionType.TAKING_DAMAGE);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		actionDecayManager.applyActionCost(event.getPlayer(), ActionType.BREAKING_BLOCK);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent event) {
		actionDecayManager.applyActionCost(event.getPlayer(), ActionType.PLACING_BLOCK);
	}
}
