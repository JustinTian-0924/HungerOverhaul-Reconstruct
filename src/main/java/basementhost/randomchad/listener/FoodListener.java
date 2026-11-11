package basementhost.randomchad.listener;

import basementhost.randomchad.data.FoodSettings;
import basementhost.randomchad.data.FoodConsumptionResult;
import basementhost.randomchad.manager.EatingManager;
import basementhost.randomchad.manager.FoodConsumptionManager;
import basementhost.randomchad.manager.FoodManager;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Objects;

// Validates and completes configured food consumption while preserving native item behavior.
public final class FoodListener implements Listener {
	private final FoodManager foodManager;
	private final FoodConsumptionManager foodConsumptionManager;
	private final EatingManager eatingManager;

	public FoodListener(
			FoodManager foodManager,
			FoodConsumptionManager foodConsumptionManager,
			EatingManager eatingManager
	) {
		this.foodManager = Objects.requireNonNull(foodManager, "foodManager");
		this.foodConsumptionManager = Objects.requireNonNull(foodConsumptionManager, "foodConsumptionManager");
		this.eatingManager = Objects.requireNonNull(eatingManager, "eatingManager");
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (!event.getAction().isRightClick()) {
			return;
		}

		FoodSettings foodSettings = foodManager.findFoodSettings(event.getItem()).orElse(null);
		if (foodSettings == null) {
			return;
		}

		Player player = event.getPlayer();
		EquipmentSlot hand = event.getHand();
		if (hand == null
				|| !foodConsumptionManager.hasAvailableData(player)
				|| !eatingManager.canConsume(player, foodSettings)) {
			event.setUseItemInHand(Event.Result.DENY);
			return;
		}

		// A temporary value below 20 lets configured foods start even when the display bar is full.
		if (player.getFoodLevel() >= 20) {
			player.setFoodLevel(19);
			event.setUseItemInHand(Event.Result.ALLOW);
		}

		eatingManager.prepareEatingTime(player, hand, event.getItem(), foodSettings);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onConsumeValidation(PlayerItemConsumeEvent event) {
		FoodSettings foodSettings = foodManager.findFoodSettings(event.getItem()).orElse(null);
		if (foodSettings == null) {
			return;
		}

		if (!foodConsumptionManager.hasAvailableData(event.getPlayer())
				|| !eatingManager.canConsume(event.getPlayer(), foodSettings)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onConsumeCompleted(PlayerItemConsumeEvent event) {
		FoodSettings foodSettings = foodManager.findFoodSettings(event.getItem()).orElse(null);
		if (foodSettings == null) {
			return;
		}

		FoodConsumptionResult result = foodConsumptionManager.applyFood(event.getPlayer(), foodSettings)
				.orElse(null);
		if (result != null) {
			foodConsumptionManager.completeConsumption(event.getPlayer(), foodSettings, result);
		}
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		eatingManager.clearPlayer(event.getPlayer());
	}
}
