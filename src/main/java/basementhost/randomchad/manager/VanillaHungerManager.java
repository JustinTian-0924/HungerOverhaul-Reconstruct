package basementhost.randomchad.manager;

import basementhost.randomchad.HungerOverhaul;
import basementhost.randomchad.data.PlayerData;
import basementhost.randomchad.data.VanillaHungerSettings;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

// Uses the vanilla hunger bar only as a display for HungerOverhaul data.
public final class VanillaHungerManager {
	private static final long DISPLAY_UPDATE_INTERVAL_TICKS = 1L;

	private final HungerOverhaul plugin;
	private final PlayerDataManager playerDataManager;
	private final Set<UUID> allowedPluginStarvationDamage = new HashSet<>();

	private VanillaHungerSettings settings;
	private BukkitTask displayTask;

	public VanillaHungerManager(
			HungerOverhaul plugin,
			PlayerDataManager playerDataManager,
			VanillaHungerSettings settings
	) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.playerDataManager = Objects.requireNonNull(playerDataManager, "playerDataManager");
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	public void start() {
		if (displayTask != null) {
			throw new IllegalStateException("VanillaHungerManager has already started");
		}

		displayTask = plugin.getServer().getScheduler().runTaskTimer(
				plugin,
				this::updateOnlinePlayers,
				0L,
				DISPLAY_UPDATE_INTERVAL_TICKS
		);
	}

	public void updateSettings(VanillaHungerSettings settings) {
		this.settings = Objects.requireNonNull(settings, "settings");
		updateOnlinePlayers();
	}

	public boolean isManaging(Player player) {
		Objects.requireNonNull(player, "player");
		return playerDataManager.hasPlayerData(player);
	}

	public boolean isNaturalRegenerationBlocked() {
		return settings.isNaturalRegenerationBlocked();
	}

	public void applyPluginStarvationDamage(Player player, double amount, DamageSource damageSource) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(damageSource, "damageSource");
		UUID playerId = player.getUniqueId();
		allowedPluginStarvationDamage.add(playerId);
		try {
			player.damage(amount, damageSource);
		} finally {
			allowedPluginStarvationDamage.remove(playerId);
		}
	}

	public boolean isPluginStarvationDamage(Player player) {
		Objects.requireNonNull(player, "player");
		return allowedPluginStarvationDamage.contains(player.getUniqueId());
	}

	public void shutdown() {
		allowedPluginStarvationDamage.clear();
		if (displayTask == null) {
			return;
		}

		displayTask.cancel();
		displayTask = null;
	}

	private void updateOnlinePlayers() {
		for (Player player : plugin.getServer().getOnlinePlayers()) {
			updatePlayer(player);
		}
	}

	private void updatePlayer(Player player) {
		PlayerData playerData = playerDataManager.findPlayerData(player).orElse(null);
		if (playerData == null) {
			return;
		}

		double percentage = switch (settings.getDisplaySource()) {
			case FULLNESS -> playerData.getFullnessPercentage();
			case NUTRITION -> playerData.getNutritionPercentage();
		};
		player.setFoodLevel(mapPercentageToFoodLevel(percentage));
		player.setSaturation(0.0F);
		player.setExhaustion(0.0F);
	}

	private int mapPercentageToFoodLevel(double percentage) {
		double normalizedPercentage = Math.max(0.0, Math.min(100.0, percentage)) / 100.0;
		int minimum = settings.getMinimumFoodLevel();
		int maximum = settings.getMaximumFoodLevel();
		return minimum + (int) Math.round(normalizedPercentage * (maximum - minimum));
	}
}
