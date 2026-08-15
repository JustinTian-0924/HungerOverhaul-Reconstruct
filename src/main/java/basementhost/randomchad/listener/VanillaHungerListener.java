package basementhost.randomchad.listener;

import basementhost.randomchad.manager.VanillaHungerManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;

import java.util.Objects;

// Prevents vanilla hunger mechanics from changing managed players.
public final class VanillaHungerListener implements Listener {
	private final VanillaHungerManager vanillaHungerManager;

	public VanillaHungerListener(VanillaHungerManager vanillaHungerManager) {
		this.vanillaHungerManager = Objects.requireNonNull(vanillaHungerManager, "vanillaHungerManager");
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onFoodLevelChange(FoodLevelChangeEvent event) {
		if (event.getEntity() instanceof Player player && vanillaHungerManager.isManaging(player)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onExhaustion(EntityExhaustionEvent event) {
		if (event.getEntity() instanceof Player player && vanillaHungerManager.isManaging(player)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onStarvationDamage(EntityDamageEvent event) {
		if (event.getCause() == EntityDamageEvent.DamageCause.STARVATION
				&& event.getEntity() instanceof Player player
				&& vanillaHungerManager.isManaging(player)
				&& !vanillaHungerManager.isPluginStarvationDamage(player)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onNaturalRegeneration(EntityRegainHealthEvent event) {
		if (vanillaHungerManager.isNaturalRegenerationBlocked()
				&& event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED
				&& event.getEntity() instanceof Player player
				&& vanillaHungerManager.isManaging(player)) {
			event.setCancelled(true);
		}
	}
}
