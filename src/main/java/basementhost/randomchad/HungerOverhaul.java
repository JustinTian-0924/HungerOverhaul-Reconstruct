package basementhost.randomchad;

import basementhost.randomchad.command.HungerOverhaulCommand;
import basementhost.randomchad.data.DecaySettings;
import basementhost.randomchad.data.DeathLifecycleSettings;
import basementhost.randomchad.data.EatingSettings;
import basementhost.randomchad.data.EatingFeedbackSettings;
import basementhost.randomchad.data.EatingInterruptSettings;
import basementhost.randomchad.data.FoodOverflowPolicy;
import basementhost.randomchad.data.HealthRegenerationSettings;
import basementhost.randomchad.data.LifecycleValueMode;
import basementhost.randomchad.data.LifecycleValueRule;
import basementhost.randomchad.data.LowValueWarningSettings;
import basementhost.randomchad.data.PlayerLifecycleSettings;
import basementhost.randomchad.data.RespawnLifecycleSettings;
import basementhost.randomchad.data.StarvationDamageRule;
import basementhost.randomchad.data.StarvationDamageSettings;
import basementhost.randomchad.data.StorageSettings;
import basementhost.randomchad.data.VanillaHungerSettings;
import basementhost.randomchad.data.WorldChangeLifecycleSettings;
import basementhost.randomchad.lang.LanguageManager;
import basementhost.randomchad.listener.ActionDecayListener;
import basementhost.randomchad.listener.CakeListener;
import basementhost.randomchad.listener.EatingInterruptListener;
import basementhost.randomchad.listener.FoodListener;
import basementhost.randomchad.listener.PlayerDataListener;
import basementhost.randomchad.listener.PlayerLifecycleListener;
import basementhost.randomchad.listener.StatusEffectListener;
import basementhost.randomchad.listener.VanillaHungerListener;
import basementhost.randomchad.manager.ActionDecayManager;
import basementhost.randomchad.manager.EatingManager;
import basementhost.randomchad.manager.FoodConsumptionManager;
import basementhost.randomchad.manager.FoodManager;
import basementhost.randomchad.manager.GroupManager;
import basementhost.randomchad.manager.HealthRegenerationManager;
import basementhost.randomchad.manager.LowValueWarningManager;
import basementhost.randomchad.manager.PlayerDataManager;
import basementhost.randomchad.manager.PlayerDataPersistenceManager;
import basementhost.randomchad.manager.PlayerLifecycleManager;
import basementhost.randomchad.manager.StatusAttributeManager;
import basementhost.randomchad.manager.StatusEffectManager;
import basementhost.randomchad.manager.StarvationDamageManager;
import basementhost.randomchad.manager.ValueDecayManager;
import basementhost.randomchad.manager.VanillaHungerManager;
import basementhost.randomchad.storage.FilePlayerDataStorage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;

public final class HungerOverhaul extends JavaPlugin {
	private LanguageManager languageManager;
	private GroupManager groupManager;
	private FoodManager foodManager;
	private StatusEffectManager statusEffectManager;
	private PlayerDataManager playerDataManager;
	private PlayerDataPersistenceManager persistenceManager;
	private VanillaHungerManager vanillaHungerManager;
	private ValueDecayManager valueDecayManager;
	private ActionDecayManager actionDecayManager;
	private LowValueWarningManager lowValueWarningManager;
	private StatusAttributeManager statusAttributeManager;
	private StarvationDamageManager starvationDamageManager;
	private HealthRegenerationManager healthRegenerationManager;
	private EatingManager eatingManager;
	private PlayerLifecycleManager playerLifecycleManager;

	@Override
	public void onEnable() {
		// This creates config.yml only when the server does not already have one.
		saveDefaultConfig();
		languageManager = new LanguageManager(this);
		reloadConfig();
		languageManager.reload();

		groupManager = new GroupManager(this);
		if (!loadGroupSettingsAtStartup()) {
			return;
		}

		statusEffectManager = new StatusEffectManager(this);
		if (!loadStatusEffectsAtStartup()) {
			return;
		}

		foodManager = new FoodManager(this);
		if (!loadFoodSettingsAtStartup()) {
			return;
		}

		if (!startPlayerDataSystem()) {
			return;
		}

		if (!startEatingSystem()) {
			return;
		}

		if (!startPlayerLifecycleSystem()) {
			return;
		}

		if (!startVanillaHungerSystem()) {
			return;
		}

		if (!startValueDecaySystem()) {
			return;
		}

		if (!startStatusAttributeSystem()) {
			return;
		}

		if (!startLowValueWarningSystem()) {
			return;
		}

		if (!startStarvationDamageSystem()) {
			return;
		}

		if (!startHealthRegenerationSystem()) {
			return;
		}

		registerCommands();
		getComponentLogger().info(languageManager.getMessage("plugin.enabled"));
	}

	@Override
	public void onDisable() {
		if (healthRegenerationManager != null) {
			healthRegenerationManager.shutdown();
		}

		if (eatingManager != null) {
			eatingManager.shutdown();
		}

		if (starvationDamageManager != null) {
			starvationDamageManager.shutdown();
		}

		if (statusAttributeManager != null) {
			statusAttributeManager.shutdown();
		}

		if (lowValueWarningManager != null) {
			lowValueWarningManager.shutdown();
		}

		if (valueDecayManager != null) {
			valueDecayManager.shutdown();
		}

		if (vanillaHungerManager != null) {
			vanillaHungerManager.shutdown();
		}

		if (persistenceManager != null) {
			persistenceManager.shutdown();
		}

		if (languageManager != null) {
			getComponentLogger().info(languageManager.getMessage("plugin.disabled"));
		}
	}

	public LanguageManager getLanguageManager() {
		return languageManager;
	}

	public GroupManager getGroupManager() {
		return groupManager;
	}

	public PlayerDataManager getPlayerDataManager() {
		return playerDataManager;
	}

	public PlayerDataPersistenceManager getPersistenceManager() {
		return persistenceManager;
	}

	public StatusEffectManager getStatusEffectManager() {
		return statusEffectManager;
	}

	public HealthRegenerationManager getHealthRegenerationManager() {
		return healthRegenerationManager;
	}

	public EatingManager getEatingManager() {
		return eatingManager;
	}

	public PlayerLifecycleManager getPlayerLifecycleManager() {
		return playerLifecycleManager;
	}

	public boolean reloadPluginSettings() {
		reloadConfig();
		languageManager.reload();

		try {
			StorageSettings storageSettings = readStorageSettings();
			VanillaHungerSettings vanillaHungerSettings = readVanillaHungerSettings();
			DecaySettings decaySettings = readDecaySettings();
			LowValueWarningSettings warningSettings = readLowValueWarningSettings();
			StarvationDamageSettings starvationSettings = readStarvationDamageSettings();
			HealthRegenerationSettings healthRegenerationSettings = readHealthRegenerationSettings();
			EatingSettings eatingSettings = readEatingSettings();
			PlayerLifecycleSettings playerLifecycleSettings = readPlayerLifecycleSettings();
			foodManager.validateConfiguration();
			groupManager.reload();
			statusEffectManager.reload();
			foodManager.reload();
			persistenceManager.updateSettings(storageSettings);
			refreshOnlinePlayerGroups();
			vanillaHungerManager.updateSettings(vanillaHungerSettings);
			valueDecayManager.updateSettings(decaySettings);
			actionDecayManager.updateSettings(decaySettings);
			lowValueWarningManager.updateSettings(warningSettings);
			starvationDamageManager.updateSettings(starvationSettings);
			healthRegenerationManager.updateSettings(healthRegenerationSettings);
			eatingManager.updateSettings(eatingSettings);
			statusAttributeManager.reload();
			persistenceManager.saveAllLoadedPlayerData();
			playerLifecycleManager.updateSettings(playerLifecycleSettings);
			eatingManager.handleSuccessfulReload();
			return true;
		} catch (RuntimeException exception) {
			getLogger().log(
					Level.SEVERE,
					"Could not reload HungerOverhaul settings. Previous valid runtime settings remain active.",
					exception
			);
			return false;
		}
	}

	private void registerCommands() {
		PluginCommand pluginCommand = Objects.requireNonNull(
				getCommand("hunger"),
				"The hunger command is missing from plugin.yml"
		);
		HungerOverhaulCommand commandHandler = new HungerOverhaulCommand(this);
		pluginCommand.setExecutor(commandHandler);
		pluginCommand.setTabCompleter(commandHandler);
	}

	private boolean loadGroupSettingsAtStartup() {
		try {
			groupManager.reload();
			return true;
		} catch (RuntimeException exception) {
			getLogger().log(
					Level.SEVERE,
					"Could not load group.yml. HungerOverhaul will be disabled.",
					exception
			);
			getServer().getPluginManager().disablePlugin(this);
			return false;
		}
	}

	private boolean loadFoodSettingsAtStartup() {
		try {
			foodManager.reload();
			return true;
		} catch (RuntimeException exception) {
			getLogger().log(
					Level.SEVERE,
					"Could not load foods.yml. HungerOverhaul will be disabled.",
					exception
			);
			getServer().getPluginManager().disablePlugin(this);
			return false;
		}
	}

	private boolean loadStatusEffectsAtStartup() {
		try {
			statusEffectManager.reload();
			return true;
		} catch (RuntimeException exception) {
			getLogger().log(
					Level.SEVERE,
					"Could not load status-effects.yml. HungerOverhaul will be disabled.",
					exception
			);
			getServer().getPluginManager().disablePlugin(this);
			return false;
		}
	}

	private boolean startPlayerDataSystem() {
		try {
			StorageSettings storageSettings = readStorageSettings();
			playerDataManager = new PlayerDataManager(groupManager);
			Path playerDataDirectory = getDataFolder().toPath().resolve("data");
			FilePlayerDataStorage storage = new FilePlayerDataStorage(playerDataDirectory);
			persistenceManager = new PlayerDataPersistenceManager(
					this,
					playerDataManager,
					storage,
					storageSettings
			);
			getServer().getPluginManager().registerEvents(new PlayerDataListener(persistenceManager), this);
			persistenceManager.start();
			return true;
		} catch (RuntimeException exception) {
			getLogger().log(
					Level.SEVERE,
					"Could not start the player data system. HungerOverhaul will be disabled.",
					exception
			);
			getServer().getPluginManager().disablePlugin(this);
			return false;
		}
	}

	private boolean startEatingSystem() {
		try {
			EatingSettings settings = readEatingSettings();
			eatingManager = new EatingManager(this, groupManager, playerDataManager, settings);
			FoodConsumptionManager foodConsumptionManager = new FoodConsumptionManager(this, eatingManager);
			getServer().getPluginManager().registerEvents(
					new FoodListener(foodManager, foodConsumptionManager, eatingManager),
					this
			);
			getServer().getPluginManager().registerEvents(
					new CakeListener(foodManager, foodConsumptionManager, eatingManager),
					this
			);
			getServer().getPluginManager().registerEvents(
					new EatingInterruptListener(eatingManager),
					this
			);
			eatingManager.start();
			return true;
		} catch (RuntimeException exception) {
			getLogger().log(
					Level.SEVERE,
					"Could not start eating restrictions and digestion. HungerOverhaul will be disabled.",
					exception
			);
			getServer().getPluginManager().disablePlugin(this);
			return false;
		}
	}

	private boolean startPlayerLifecycleSystem() {
		try {
			PlayerLifecycleSettings settings = readPlayerLifecycleSettings();
			playerLifecycleManager = new PlayerLifecycleManager(
					groupManager,
					playerDataManager,
					persistenceManager,
					eatingManager,
					settings
			);
			getServer().getPluginManager().registerEvents(
					new PlayerLifecycleListener(playerLifecycleManager),
					this
			);
			return true;
		} catch (RuntimeException exception) {
			getLogger().log(
					Level.SEVERE,
					"Could not start player lifecycle handling. HungerOverhaul will be disabled.",
					exception
			);
			getServer().getPluginManager().disablePlugin(this);
			return false;
		}
	}

	private boolean startVanillaHungerSystem() {
		try {
			VanillaHungerSettings settings = readVanillaHungerSettings();
			vanillaHungerManager = new VanillaHungerManager(this, playerDataManager, settings);
			getServer().getPluginManager().registerEvents(
					new VanillaHungerListener(vanillaHungerManager),
					this
			);
			vanillaHungerManager.start();
			return true;
		} catch (RuntimeException exception) {
			getLogger().log(
					Level.SEVERE,
					"Could not start the vanilla hunger override. HungerOverhaul will be disabled.",
					exception
			);
			getServer().getPluginManager().disablePlugin(this);
			return false;
		}
	}

	private boolean startValueDecaySystem() {
		try {
			DecaySettings settings = readDecaySettings();
			valueDecayManager = new ValueDecayManager(
					this,
					groupManager,
					playerDataManager,
					statusEffectManager,
					settings
			);
			actionDecayManager = new ActionDecayManager(groupManager, playerDataManager, settings);
			getServer().getPluginManager().registerEvents(
					new ActionDecayListener(actionDecayManager),
					this
			);
			valueDecayManager.start();
			return true;
		} catch (RuntimeException exception) {
			getLogger().log(
					Level.SEVERE,
					"Could not start the value decay systems. HungerOverhaul will be disabled.",
					exception
			);
			getServer().getPluginManager().disablePlugin(this);
			return false;
		}
	}

	private boolean startStatusAttributeSystem() {
		try {
			statusAttributeManager = new StatusAttributeManager(
					this,
					playerDataManager,
					statusEffectManager
			);
			getServer().getPluginManager().registerEvents(
					new StatusEffectListener(statusAttributeManager),
					this
			);
			statusAttributeManager.start();
			return true;
		} catch (RuntimeException exception) {
			getLogger().log(
					Level.SEVERE,
					"Could not start status attribute effects. HungerOverhaul will be disabled.",
					exception
			);
			getServer().getPluginManager().disablePlugin(this);
			return false;
		}
	}

	private boolean startLowValueWarningSystem() {
		try {
			LowValueWarningSettings settings = readLowValueWarningSettings();
			lowValueWarningManager = new LowValueWarningManager(this, playerDataManager, settings);
			lowValueWarningManager.start();
			return true;
		} catch (RuntimeException exception) {
			getLogger().log(
					Level.SEVERE,
					"Could not start low-value warnings. HungerOverhaul will be disabled.",
					exception
			);
			getServer().getPluginManager().disablePlugin(this);
			return false;
		}
	}

	private boolean startStarvationDamageSystem() {
		try {
			StarvationDamageSettings settings = readStarvationDamageSettings();
			starvationDamageManager = new StarvationDamageManager(
					this,
					playerDataManager,
					vanillaHungerManager,
					settings
			);
			starvationDamageManager.start();
			return true;
		} catch (RuntimeException exception) {
			getLogger().log(
					Level.SEVERE,
					"Could not start starvation damage. HungerOverhaul will be disabled.",
					exception
			);
			getServer().getPluginManager().disablePlugin(this);
			return false;
		}
	}

	private boolean startHealthRegenerationSystem() {
		try {
			HealthRegenerationSettings settings = readHealthRegenerationSettings();
			healthRegenerationManager = new HealthRegenerationManager(
					this,
					groupManager,
					playerDataManager,
					statusEffectManager,
					settings
			);
			healthRegenerationManager.start();
			return true;
		} catch (RuntimeException exception) {
			getLogger().log(
					Level.SEVERE,
					"Could not start custom health regeneration. HungerOverhaul will be disabled.",
					exception
			);
			getServer().getPluginManager().disablePlugin(this);
			return false;
		}
	}

	private StorageSettings readStorageSettings() {
		boolean autosaveEnabled = readBooleanSetting("storage.autosave.enabled", true);
		long autosaveInterval = readLongSetting("storage.autosave.interval-seconds", 300L);
		long cacheTtl = readLongSetting("storage.cache-ttl-seconds", 300L);

		try {
			return new StorageSettings(autosaveEnabled, autosaveInterval, cacheTtl);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("Invalid storage settings: " + exception.getMessage(), exception);
		}
	}

	private DecaySettings readDecaySettings() {
		boolean enabled = readBooleanSetting("value-decay.enabled", true);
		long intervalTicks = readLongSetting("value-decay.interval-ticks", 20L);
		boolean actionCostsEnabled = readBooleanSetting("value-decay.action-costs.enabled", true);

		try {
			return new DecaySettings(enabled, intervalTicks, actionCostsEnabled);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("Invalid value decay settings: " + exception.getMessage(), exception);
		}
	}

	private LowValueWarningSettings readLowValueWarningSettings() {
		boolean enabled = readBooleanSetting("low-value-warnings.enabled", true);
		long checkIntervalTicks = readLongSetting("low-value-warnings.check-interval-ticks", 20L);
		long repeatIntervalSeconds = readLongSetting("low-value-warnings.repeat-interval-seconds", 30L);
		boolean nutritionEnabled = readBooleanSetting("low-value-warnings.nutrition.enabled", true);
		double nutritionThreshold = readDoubleSetting(
				"low-value-warnings.nutrition.threshold-percentage",
				20.0
		);
		boolean fullnessEnabled = readBooleanSetting("low-value-warnings.fullness.enabled", true);
		double fullnessThreshold = readDoubleSetting(
				"low-value-warnings.fullness.threshold-percentage",
				20.0
		);

		try {
			return new LowValueWarningSettings(
					enabled,
					checkIntervalTicks,
					repeatIntervalSeconds,
					nutritionEnabled,
					nutritionThreshold,
					fullnessEnabled,
					fullnessThreshold
			);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("Invalid low-value warning settings: " + exception.getMessage(), exception);
		}
	}

	private StarvationDamageSettings readStarvationDamageSettings() {
		boolean enabled = readBooleanSetting("starvation-damage.enabled", true);
		long checkIntervalTicks = readLongSetting("starvation-damage.check-interval-ticks", 20L);
		boolean damageInPeaceful = readBooleanSetting("starvation-damage.damage-in-peaceful", false);
		double criticalMaximumPercentage = readDoubleSetting(
				"starvation-damage.critical.maximum-fullness-percentage",
				10.0
		);
		StarvationDamageRule criticalRule = readStarvationDamageRule(
				"starvation-damage.critical",
				200L,
				1.0
		);
		StarvationDamageRule emptyRule = readStarvationDamageRule(
				"starvation-damage.empty",
				40L,
				1.0
		);

		try {
			return new StarvationDamageSettings(
					enabled,
					checkIntervalTicks,
					damageInPeaceful,
					criticalMaximumPercentage,
					criticalRule,
					emptyRule
			);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("Invalid starvation damage settings: " + exception.getMessage(), exception);
		}
	}

	private HealthRegenerationSettings readHealthRegenerationSettings() {
		boolean enabled = readBooleanSetting("health-regeneration.enabled", true);
		long intervalTicks = readLongSetting("health-regeneration.interval-ticks", 200L);
		double minimumChargedHealHp = readDoubleSetting(
				"health-regeneration.minimum-charged-heal-hp",
				0.5
		);

		try {
			return new HealthRegenerationSettings(enabled, intervalTicks, minimumChargedHealHp);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException(
					"Invalid health regeneration settings: " + exception.getMessage(),
					exception
			);
		}
	}

	private PlayerLifecycleSettings readPlayerLifecycleSettings() {
		DeathLifecycleSettings deathSettings = new DeathLifecycleSettings(
				readBooleanSetting("player-lifecycle.death.cancel-eating", true),
				readBooleanSetting("player-lifecycle.death.preserve-digestion-cooldown", true),
				readBooleanSetting("player-lifecycle.death.save-data", true)
		);
		LifecycleValueRule respawnNutritionRule = readLifecycleValueRule(
				"player-lifecycle.respawn.nutrition",
				"minimum-percentage",
				20.0
		);
		LifecycleValueRule respawnFullnessRule = readLifecycleValueRule(
				"player-lifecycle.respawn.fullness",
				"minimum-percentage",
				20.0
		);
		RespawnLifecycleSettings respawnSettings = new RespawnLifecycleSettings(
				respawnNutritionRule,
				respawnFullnessRule,
				readBooleanSetting("player-lifecycle.respawn.affect-frozen-values", false),
				readBooleanSetting("player-lifecycle.respawn.save-data", true)
		);
		LifecycleValueRule worldNutritionRule = readLifecycleValueRule(
				"player-lifecycle.world-change.nutrition",
				"preserve-percentage",
				0.0
		);
		LifecycleValueRule worldFullnessRule = readLifecycleValueRule(
				"player-lifecycle.world-change.fullness",
				"preserve-percentage",
				0.0
		);
		WorldChangeLifecycleSettings worldChangeSettings = new WorldChangeLifecycleSettings(
				readBooleanSetting("player-lifecycle.world-change.cancel-eating", true),
				readBooleanSetting(
						"player-lifecycle.world-change.preserve-digestion-cooldown",
						true
				),
				readBooleanSetting("player-lifecycle.world-change.refresh-group", true),
				worldNutritionRule,
				worldFullnessRule,
				readBooleanSetting("player-lifecycle.world-change.affect-frozen-values", false),
				readBooleanSetting("player-lifecycle.world-change.save-data", true)
		);

		return new PlayerLifecycleSettings(deathSettings, respawnSettings, worldChangeSettings);
	}

	private LifecycleValueRule readLifecycleValueRule(
			String path,
			String fallbackMode,
			double fallbackPercentage
	) {
		String configuredMode = readStringSetting(path + ".mode", fallbackMode);

		try {
			LifecycleValueMode mode = LifecycleValueMode.fromConfigValue(configuredMode);
			Double percentage = mode.requiresPercentage()
					? readDoubleSetting(path + ".percentage", fallbackPercentage)
					: null;
			return new LifecycleValueRule(mode, percentage);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException(
					"Invalid player lifecycle value rule '" + path + "': " + exception.getMessage(),
					exception
			);
		}
	}

	private EatingSettings readEatingSettings() {
		String configuredOverflowPolicy = readStringSetting("eating.overflow-policy", "deny");
		long cooldownTicks = readLongSetting("eating.cooldown-ticks", 40L);
		boolean progressActionBarEnabled = readBooleanSetting(
				"eating.feedback.progress-actionbar",
				true
		);
		long progressUpdateIntervalTicks = readLongSetting(
				"eating.feedback.progress-update-interval-ticks",
				10L
		);
		boolean completionActionBarEnabled = readBooleanSetting(
				"eating.feedback.completion-actionbar",
				true
		);
		boolean interruptionActionBarEnabled = readBooleanSetting(
				"eating.feedback.interruption-actionbar",
				true
		);
		long blockedMessageIntervalTicks = readLongSetting(
				"eating.feedback.blocked-message-interval-ticks",
				40L
		);
		long postConsumptionSilentTicks = readLongSetting(
				"eating.feedback.post-consumption-silent-ticks",
				10L
		);
		boolean interruptOnDamage = readBooleanSetting("eating.interrupt.on-damage", true);
		boolean interruptOnMove = readBooleanSetting("eating.interrupt.on-move", false);
		boolean interruptOnSneakToggle = readBooleanSetting(
				"eating.interrupt.on-sneak-toggle",
				false
		);

		try {
			FoodOverflowPolicy overflowPolicy = FoodOverflowPolicy.fromConfigValue(configuredOverflowPolicy);
			EatingFeedbackSettings feedbackSettings = new EatingFeedbackSettings(
					progressActionBarEnabled,
					progressUpdateIntervalTicks,
					completionActionBarEnabled,
					interruptionActionBarEnabled,
					blockedMessageIntervalTicks,
					postConsumptionSilentTicks
			);
			EatingInterruptSettings interruptSettings = new EatingInterruptSettings(
					interruptOnDamage,
					interruptOnMove,
					interruptOnSneakToggle
			);
			return new EatingSettings(
					overflowPolicy,
					cooldownTicks,
					feedbackSettings,
					interruptSettings
			);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("Invalid eating settings: " + exception.getMessage(), exception);
		}
	}

	private StarvationDamageRule readStarvationDamageRule(
			String path,
			long fallbackIntervalTicks,
			double fallbackDamageHp
	) {
		boolean enabled = readBooleanSetting(path + ".enabled", true);
		long intervalTicks = readLongSetting(path + ".interval-ticks", fallbackIntervalTicks);
		double damageHp = readDoubleSetting(path + ".damage-hp", fallbackDamageHp);

		try {
			return new StarvationDamageRule(enabled, intervalTicks, damageHp);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException(
					"Invalid starvation damage rule '" + path + "': " + exception.getMessage(),
					exception
			);
		}
	}

	private VanillaHungerSettings readVanillaHungerSettings() {
		String configuredSource = readStringSetting("vanilla-hunger.display.source", "fullness");
		int minimumFoodLevel = readIntegerSetting("vanilla-hunger.display.minimum-food-level", 1);
		int maximumFoodLevel = readIntegerSetting("vanilla-hunger.display.maximum-food-level", 20);
		boolean blockNaturalRegeneration = readBooleanSetting(
				"vanilla-hunger.block-natural-regeneration",
				true
		);

		try {
			VanillaHungerSettings.DisplaySource displaySource =
					VanillaHungerSettings.DisplaySource.fromConfigValue(configuredSource);
			return new VanillaHungerSettings(
					displaySource,
					minimumFoodLevel,
					maximumFoodLevel,
					blockNaturalRegeneration
			);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException(
					"Invalid vanilla hunger display settings: " + exception.getMessage(),
					exception
			);
		}
	}

	private boolean readBooleanSetting(String path, boolean fallback) {
		FileConfiguration configuration = getConfig();
		Object configuredValue = configuration.get(path);
		if (configuredValue == null) {
			return fallback;
		}

		if (!(configuredValue instanceof Boolean booleanValue)) {
			throw new IllegalStateException("Configuration value '" + path + "' must be true or false.");
		}

		return booleanValue;
	}

	private String readStringSetting(String path, String fallback) {
		Object configuredValue = getConfig().get(path);
		if (configuredValue == null) {
			return fallback;
		}

		if (!(configuredValue instanceof String stringValue)) {
			throw new IllegalStateException("Configuration value '" + path + "' must be text.");
		}

		return stringValue;
	}

	private int readIntegerSetting(String path, int fallback) {
		Object configuredValue = getConfig().get(path);
		if (configuredValue == null) {
			return fallback;
		}

		if (!(configuredValue instanceof Number number)) {
			throw new IllegalStateException("Configuration value '" + path + "' must be a whole number.");
		}

		try {
			return new BigDecimal(number.toString()).intValueExact();
		} catch (NumberFormatException | ArithmeticException exception) {
			throw new IllegalStateException(
					"Configuration value '" + path + "' must be a whole number within the integer range.",
					exception
			);
		}
	}

	private double readDoubleSetting(String path, double fallback) {
		Object configuredValue = getConfig().get(path);
		if (configuredValue == null) {
			return fallback;
		}

		if (!(configuredValue instanceof Number number)) {
			throw new IllegalStateException("Configuration value '" + path + "' must be a number.");
		}

		return number.doubleValue();
	}

	private long readLongSetting(String path, long fallback) {
		Object configuredValue = getConfig().get(path);
		if (configuredValue == null) {
			return fallback;
		}

		if (!(configuredValue instanceof Number number)) {
			throw new IllegalStateException("Configuration value '" + path + "' must be a whole number.");
		}

		try {
			return new BigDecimal(number.toString()).longValueExact();
		} catch (NumberFormatException | ArithmeticException exception) {
			throw new IllegalStateException(
					"Configuration value '" + path + "' must be a whole number within the long range.",
					exception
			);
		}
	}

	private void refreshOnlinePlayerGroups() {
		for (Player player : getServer().getOnlinePlayers()) {
			playerDataManager.refreshGroupSettings(player);
		}
	}
}
