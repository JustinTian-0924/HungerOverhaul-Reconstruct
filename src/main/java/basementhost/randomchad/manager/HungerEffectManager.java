package basementhost.randomchad.manager;

import basementhost.randomchad.data.HungerEffectSettings;
import basementhost.randomchad.data.PlayerData;
import basementhost.randomchad.data.StatusEffectDefinition;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Objects;

// Handles native Hunger only when its tick or the plugin's healing cycle already runs.
public final class HungerEffectManager {
	private final PlayerDataManager playerDataManager;
	private final StatusEffectManager statusEffectManager;
	private HungerEffectSettings settings;

	public HungerEffectManager(PlayerDataManager playerDataManager, StatusEffectManager statusEffectManager, HungerEffectSettings settings) {
		this.playerDataManager = Objects.requireNonNull(playerDataManager, "playerDataManager");
		this.statusEffectManager = Objects.requireNonNull(statusEffectManager, "statusEffectManager");
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
		if (data == null) {
			return;
		}

		double fullnessMultiplier = 1.0;
		double nutritionMultiplier = 1.0;
		if (settings.applyStatusDecayMultiplier()) {
			if (settings.drainsFullness(data)) {
				fullnessMultiplier = decayMultiplier(statusEffectManager.getFullnessEffect(data.getFullnessPercentage()));
			}
			if (settings.drainsNutrition(data)) {
				nutritionMultiplier = decayMultiplier(statusEffectManager.getNutritionEffect(data.getNutritionPercentage()));
			}
		}
		settings.applyDrainTick(data, effect.getAmplifier(), fullnessMultiplier, nutritionMultiplier);
	}

	private static double decayMultiplier(StatusEffectDefinition effect) {
		return effect == null ? 1.0 : effect.getDecayMultiplier();
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
