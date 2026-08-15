package basementhost.randomchad.manager;

import basementhost.randomchad.HungerOverhaul;
import basementhost.randomchad.data.PlayerData;
import basementhost.randomchad.data.StarvationDamageRule;
import basementhost.randomchad.data.StarvationDamageSettings;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

// Applies native starvation damage after continuous time in a low-fullness range.
public final class StarvationDamageManager {
	private final HungerOverhaul plugin;
	private final PlayerDataManager playerDataManager;
	private final VanillaHungerManager vanillaHungerManager;
	private final DamageSource starvationDamageSource;
	private final Map<UUID, DamageProgress> damageProgressByPlayer = new HashMap<>();

	private StarvationDamageSettings settings;
	private BukkitTask damageTask;
	private long currentTaskTick;
	private boolean started;

	public StarvationDamageManager(
			HungerOverhaul plugin,
			PlayerDataManager playerDataManager,
			VanillaHungerManager vanillaHungerManager,
			StarvationDamageSettings settings
	) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.playerDataManager = Objects.requireNonNull(playerDataManager, "playerDataManager");
		this.vanillaHungerManager = Objects.requireNonNull(vanillaHungerManager, "vanillaHungerManager");
		this.settings = Objects.requireNonNull(settings, "settings");
		this.starvationDamageSource = DamageSource.builder(DamageType.STARVE).build();
	}

	public void start() {
		if (started) {
			throw new IllegalStateException("StarvationDamageManager has already started");
		}

		started = true;
		scheduleDamageTask();
	}

	public void updateSettings(StarvationDamageSettings settings) {
		this.settings = Objects.requireNonNull(settings, "settings");
		resetAllProgress();
		if (started) {
			scheduleDamageTask();
		}
	}

	public void shutdown() {
		started = false;
		cancelDamageTask();
		resetAllProgress();
	}

	private void scheduleDamageTask() {
		cancelDamageTask();
		if (!settings.isEnabled()) {
			return;
		}

		long intervalTicks = settings.getCheckIntervalTicks();
		damageTask = plugin.getServer().getScheduler().runTaskTimer(
				plugin,
				this::checkOnlinePlayers,
				intervalTicks,
				intervalTicks
		);
	}

	private void cancelDamageTask() {
		if (damageTask == null) {
			return;
		}

		damageTask.cancel();
		damageTask = null;
	}

	private void checkOnlinePlayers() {
		currentTaskTick = addWithoutOverflow(currentTaskTick, settings.getCheckIntervalTicks());
		Set<UUID> onlinePlayerIds = new HashSet<>();
		for (Player player : plugin.getServer().getOnlinePlayers()) {
			onlinePlayerIds.add(player.getUniqueId());
			checkPlayer(player);
		}

		damageProgressByPlayer.keySet().retainAll(onlinePlayerIds);
	}

	private void checkPlayer(Player player) {
		UUID playerId = player.getUniqueId();
		if (player.isDead()
				|| !isAffectedGameMode(player.getGameMode())
				|| (!settings.isDamageInPeaceful() && player.getWorld().getDifficulty() == Difficulty.PEACEFUL)) {
			damageProgressByPlayer.remove(playerId);
			return;
		}

		PlayerData playerData = playerDataManager.findPlayerData(player).orElse(null);
		if (playerData == null) {
			damageProgressByPlayer.remove(playerId);
			return;
		}

		DamageBand damageBand = selectDamageBand(playerData);
		if (damageBand == DamageBand.NONE) {
			damageProgressByPlayer.remove(playerId);
			return;
		}

		StarvationDamageRule damageRule = getDamageRule(damageBand);
		DamageProgress progress = damageProgressByPlayer.get(playerId);
		if (progress == null || progress.getDamageBand() != damageBand) {
			damageProgressByPlayer.put(
					playerId,
					new DamageProgress(
							damageBand,
							addWithoutOverflow(currentTaskTick, damageRule.getIntervalTicks())
					)
			);
			return;
		}

		if (currentTaskTick < progress.getNextDamageTick()) {
			return;
		}

		vanillaHungerManager.applyPluginStarvationDamage(
				player,
				damageRule.getDamageHp(),
				starvationDamageSource
		);
		progress.setNextDamageTick(addWithoutOverflow(currentTaskTick, damageRule.getIntervalTicks()));
	}

	private DamageBand selectDamageBand(PlayerData playerData) {
		if (playerData.getFullness() <= 0.0) {
			return settings.getEmptyRule().isEnabled() ? DamageBand.EMPTY : DamageBand.NONE;
		}

		if (settings.getCriticalRule().isEnabled()
				&& playerData.getFullnessPercentage() <= settings.getCriticalMaximumPercentage()) {
			return DamageBand.CRITICAL;
		}

		return DamageBand.NONE;
	}

	private StarvationDamageRule getDamageRule(DamageBand damageBand) {
		return damageBand == DamageBand.EMPTY ? settings.getEmptyRule() : settings.getCriticalRule();
	}

	private long addWithoutOverflow(long currentValue, long addedValue) {
		return currentValue > Long.MAX_VALUE - addedValue ? Long.MAX_VALUE : currentValue + addedValue;
	}

	private void resetAllProgress() {
		damageProgressByPlayer.clear();
		currentTaskTick = 0L;
	}

	private boolean isAffectedGameMode(GameMode gameMode) {
		return gameMode == GameMode.SURVIVAL || gameMode == GameMode.ADVENTURE;
	}

	private enum DamageBand {
		NONE,
		CRITICAL,
		EMPTY
	}

	private static final class DamageProgress {
		private final DamageBand damageBand;
		private long nextDamageTick;

		private DamageProgress(DamageBand damageBand, long nextDamageTick) {
			this.damageBand = damageBand;
			this.nextDamageTick = nextDamageTick;
		}

		private DamageBand getDamageBand() {
			return damageBand;
		}

		private long getNextDamageTick() {
			return nextDamageTick;
		}

		private void setNextDamageTick(long nextDamageTick) {
			this.nextDamageTick = nextDamageTick;
		}
	}
}
