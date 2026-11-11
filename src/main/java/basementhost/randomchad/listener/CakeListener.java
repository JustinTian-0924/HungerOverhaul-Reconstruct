package basementhost.randomchad.listener;

import basementhost.randomchad.data.FoodSettings;
import basementhost.randomchad.data.FoodConsumptionResult;
import basementhost.randomchad.manager.EatingManager;
import basementhost.randomchad.manager.FoodConsumptionManager;
import basementhost.randomchad.manager.FoodManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Cake;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

// Handles cake slices because placed cake does not trigger an item consume event.
public final class CakeListener implements Listener {
	private final FoodManager foodManager;
	private final FoodConsumptionManager foodConsumptionManager;
	private final EatingManager eatingManager;

	public CakeListener(
			FoodManager foodManager,
			FoodConsumptionManager foodConsumptionManager,
			EatingManager eatingManager
	) {
		this.foodManager = Objects.requireNonNull(foodManager, "foodManager");
		this.foodConsumptionManager = Objects.requireNonNull(foodConsumptionManager, "foodConsumptionManager");
		this.eatingManager = Objects.requireNonNull(eatingManager, "eatingManager");
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onCakeInteract(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK
				|| event.getHand() != EquipmentSlot.HAND
				|| event.useInteractedBlock() == Event.Result.DENY) {
			return;
		}

		Block cakeBlock = event.getClickedBlock();
		if (cakeBlock == null || !isCakeBlock(cakeBlock.getType())) {
			return;
		}

		if (cakeBlock.getType() == Material.CAKE && canPlaceCandle(cakeBlock, event.getPlayer())) {
			return;
		}

		FoodSettings cakeSettings = foodManager.findFoodSettings(Material.CAKE).orElse(null);
		if (cakeSettings == null) {
			return;
		}

		Player player = event.getPlayer();
		if (!foodConsumptionManager.hasAvailableData(player)
				|| !eatingManager.canConsume(player, cakeSettings)) {
			event.setUseInteractedBlock(Event.Result.DENY);
			event.setUseItemInHand(Event.Result.DENY);
			return;
		}

		event.setUseInteractedBlock(Event.Result.DENY);
		event.setUseItemInHand(Event.Result.DENY);
		FoodConsumptionResult result = foodConsumptionManager.applyFood(player, cakeSettings).orElse(null);
		if (result == null) {
			return;
		}
		if (cakeBlock.getType() == Material.CAKE) {
			consumeRegularCakeSlice(cakeBlock);
		} else {
			consumeCandleCakeSlice(cakeBlock);
		}

		player.incrementStatistic(Statistic.CAKE_SLICES_EATEN);
		player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.0F, 1.0F);
		foodConsumptionManager.completeConsumption(player, cakeSettings, result);
	}

	private boolean isCakeBlock(Material material) {
		return material == Material.CAKE || Tag.CANDLE_CAKES.isTagged(material);
	}

	private boolean canPlaceCandle(Block cakeBlock, Player player) {
		if (!(cakeBlock.getBlockData() instanceof Cake cakeData) || cakeData.getBites() != 0) {
			return false;
		}

		return isCandle(player.getInventory().getItemInMainHand())
				|| isCandle(player.getInventory().getItemInOffHand());
	}

	private boolean isCandle(ItemStack item) {
		return item != null && !item.getType().isAir() && Tag.CANDLES.isTagged(item.getType());
	}

	private void consumeRegularCakeSlice(Block cakeBlock) {
		Cake cakeData = (Cake) cakeBlock.getBlockData();
		if (cakeData.getBites() >= cakeData.getMaximumBites()) {
			cakeBlock.setType(Material.AIR, true);
			return;
		}

		cakeData.setBites(cakeData.getBites() + 1);
		cakeBlock.setBlockData(cakeData, true);
	}

	private void consumeCandleCakeSlice(Block cakeBlock) {
		Material candleMaterial = getCandleMaterial(cakeBlock.getType());
		Cake remainingCake = (Cake) Material.CAKE.createBlockData();
		remainingCake.setBites(1);
		cakeBlock.setBlockData(remainingCake, true);
		cakeBlock.getWorld().dropItemNaturally(
				cakeBlock.getLocation().add(0.5, 0.5, 0.5),
				new ItemStack(candleMaterial)
		);
	}

	private Material getCandleMaterial(Material candleCakeMaterial) {
		String candleMaterialName = candleCakeMaterial.name().replaceFirst("_CAKE$", "");
		Material candleMaterial = Material.matchMaterial(candleMaterialName);
		if (candleMaterial == null || !Tag.CANDLES.isTagged(candleMaterial)) {
			throw new IllegalStateException("Could not resolve candle for " + candleCakeMaterial + ".");
		}

		return candleMaterial;
	}
}
