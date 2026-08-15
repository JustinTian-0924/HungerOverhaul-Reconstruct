package basementhost.randomchad.manager;

import basementhost.randomchad.HungerOverhaul;
import basementhost.randomchad.data.PlayerData;
import basementhost.randomchad.data.StatusEffectDefinition;
import basementhost.randomchad.data.StatusEffectsConfiguration;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

// Applies temporary direct attributes from the player's current value ranges.
public final class StatusAttributeManager {
	private static final double MODIFIER_TOLERANCE = 0.0000001;

	private final HungerOverhaul plugin;
	private final PlayerDataManager playerDataManager;
	private final StatusEffectManager statusEffectManager;
	private final NamespacedKey movementSpeedKey;
	private final NamespacedKey blockBreakSpeedKey;
	private final NamespacedKey attackDamageKey;

	private BukkitTask updateTask;
	private boolean started;

	public StatusAttributeManager(
			HungerOverhaul plugin,
			PlayerDataManager playerDataManager,
			StatusEffectManager statusEffectManager
	) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.playerDataManager = Objects.requireNonNull(playerDataManager, "playerDataManager");
		this.statusEffectManager = Objects.requireNonNull(statusEffectManager, "statusEffectManager");
		this.movementSpeedKey = new NamespacedKey(plugin, "status_movement_speed");
		this.blockBreakSpeedKey = new NamespacedKey(plugin, "status_block_break_speed");
		this.attackDamageKey = new NamespacedKey(plugin, "status_attack_damage");
	}

	public void start() {
		if (started) {
			throw new IllegalStateException("StatusAttributeManager has already started");
		}

		started = true;
		refreshOnlinePlayers();
		scheduleUpdateTask();
	}

	public void reload() {
		if (!started) {
			return;
		}

		refreshOnlinePlayers();
		scheduleUpdateTask();
	}

	public void shutdown() {
		started = false;
		cancelUpdateTask();
		removeOnlinePlayerEffects();
	}

	public void refreshPlayer(Player player) {
		Objects.requireNonNull(player, "player");
		StatusEffectsConfiguration configuration = statusEffectManager.getConfiguration();
		if (!configuration.isEnabled()
				|| player.isDead()
				|| !isAffectedGameMode(player.getGameMode())) {
			removePlayerEffects(player);
			return;
		}

		PlayerData playerData = playerDataManager.findPlayerData(player).orElse(null);
		if (playerData == null) {
			removePlayerEffects(player);
			return;
		}

		StatusEffectDefinition fullnessEffect = configuration.findFullnessEffect(
				playerData.getFullnessPercentage()
		);
		StatusEffectDefinition nutritionEffect = configuration.findNutritionEffect(
				playerData.getNutritionPercentage()
		);
		applyModifier(
				player,
				Attribute.MOVEMENT_SPEED,
				movementSpeedKey,
				configuration.clampAttributeModifier(
						fullnessEffect.getMovementSpeedModifier() + nutritionEffect.getMovementSpeedModifier()
				)
		);
		applyModifier(
				player,
				Attribute.BLOCK_BREAK_SPEED,
				blockBreakSpeedKey,
				configuration.clampAttributeModifier(
						fullnessEffect.getBlockBreakSpeedModifier() + nutritionEffect.getBlockBreakSpeedModifier()
				)
		);
		applyModifier(
				player,
				Attribute.ATTACK_DAMAGE,
				attackDamageKey,
				configuration.clampAttributeModifier(
						fullnessEffect.getAttackDamageModifier() + nutritionEffect.getAttackDamageModifier()
				)
		);
	}

	public void removePlayerEffects(Player player) {
		Objects.requireNonNull(player, "player");
		removeModifier(player, Attribute.MOVEMENT_SPEED, movementSpeedKey);
		removeModifier(player, Attribute.BLOCK_BREAK_SPEED, blockBreakSpeedKey);
		removeModifier(player, Attribute.ATTACK_DAMAGE, attackDamageKey);
	}

	private void scheduleUpdateTask() {
		cancelUpdateTask();
		StatusEffectsConfiguration configuration = statusEffectManager.getConfiguration();
		if (!configuration.isEnabled()) {
			removeOnlinePlayerEffects();
			return;
		}

		long intervalTicks = configuration.getUpdateIntervalTicks();
		updateTask = plugin.getServer().getScheduler().runTaskTimer(
				plugin,
				this::refreshOnlinePlayers,
				intervalTicks,
				intervalTicks
		);
	}

	private void cancelUpdateTask() {
		if (updateTask == null) {
			return;
		}

		updateTask.cancel();
		updateTask = null;
	}

	private void refreshOnlinePlayers() {
		for (Player player : plugin.getServer().getOnlinePlayers()) {
			refreshPlayer(player);
		}
	}

	private void removeOnlinePlayerEffects() {
		for (Player player : plugin.getServer().getOnlinePlayers()) {
			removePlayerEffects(player);
		}
	}

	private void applyModifier(Player player, Attribute attribute, NamespacedKey key, double amount) {
		AttributeInstance attributeInstance = player.getAttribute(attribute);
		if (attributeInstance == null) {
			return;
		}

		AttributeModifier existingModifier = attributeInstance.getModifier(key);
		if (Math.abs(amount) <= MODIFIER_TOLERANCE) {
			if (existingModifier != null) {
				attributeInstance.removeModifier(key);
			}
			return;
		}

		if (existingModifier != null
				&& existingModifier.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1
				&& Math.abs(existingModifier.getAmount() - amount) <= MODIFIER_TOLERANCE) {
			return;
		}

		if (existingModifier != null) {
			attributeInstance.removeModifier(key);
		}

		AttributeModifier modifier = new AttributeModifier(
				key,
				amount,
				AttributeModifier.Operation.MULTIPLY_SCALAR_1
		);
		attributeInstance.addTransientModifier(modifier);
	}

	private void removeModifier(Player player, Attribute attribute, NamespacedKey key) {
		AttributeInstance attributeInstance = player.getAttribute(attribute);
		if (attributeInstance != null && attributeInstance.getModifier(key) != null) {
			attributeInstance.removeModifier(key);
		}
	}

	private boolean isAffectedGameMode(GameMode gameMode) {
		return gameMode == GameMode.SURVIVAL || gameMode == GameMode.ADVENTURE;
	}
}
