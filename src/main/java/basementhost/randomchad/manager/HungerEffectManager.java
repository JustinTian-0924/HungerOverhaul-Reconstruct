package basementhost.randomchad.manager;

import basementhost.randomchad.data.HungerEffectSettings;
import basementhost.randomchad.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Objects;

// Handles native Hunger only when its tick or the plugin's healing cycle already runs.
public final class HungerEffectManager {
	private final PlayerDataManager playerDataManager;
	private HungerEffectSettings settings;

	public HungerEffectManager(PlayerDataManager playerDataManager, HungerEffectSettings settings) {
		this.playerDataManager = Objects.requireNonNull(playerDataManager, "playerDataManager");
		updateSettings(settings);
	}

	public void updateSettings(HungerEffectSettings settings) {
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	public void applyTick(Player player) {
		if (!settings.hasDrain() || !canAffect(player)) {
			return;
		}
		PotionEffect effect = player.getPotionEffect(PotionEffectType.HUNGER);
		if (effect == null) {
			return;
		}
		PlayerData data = playerDataManager.findPlayerData(player).orElse(null);
		if (data != null) {
			settings.applyDrainTick(data, effect.getAmplifier());
		}
	}

	public double getRegenerationMultiplier(Player player) {
		if (!settings.enabled() || settings.healthRegenerationMultiplier() == 1.0) {
			return 1.0;
		}
		return canAffect(player) && player.hasPotionEffect(PotionEffectType.HUNGER)
				? settings.healthRegenerationMultiplier()
				: 1.0;
	}

	private boolean canAffect(Player player) {
		GameMode gameMode = player.getGameMode();
		return (gameMode == GameMode.SURVIVAL || gameMode == GameMode.ADVENTURE) && !player.isDead();
	}
}
