package basementhost.randomchad;

import basementhost.randomchad.command.HungerOverhaulCommand;
import basementhost.randomchad.compat.placeholderapi.PlaceholderApiHook;
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
import basementhost.randomchad.data.PerformanceSettings;
import basementhost.randomchad.data.RespawnLifecycleSettings;
import basementhost.randomchad.data.StarvationDamageRule;
import basementhost.randomchad.data.StarvationDamageSettings;
import basementhost.randomchad.data.StatusBarSettings;
import basementhost.randomchad.data.StatusEffectsConfiguration;
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
import basementhost.randomchad.manager.StatusBarManager;
import basementhost.randomchad.manager.StatusEffectManager;
import basementhost.randomchad.manager.StarvationDamageManager;
import basementhost.randomchad.manager.ValueDecayManager;
import basementhost.randomchad.manager.VanillaHungerManager;
import basementhost.randomchad.storage.FilePlayerDataStorage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;

public final class HungerOverhaul extends JavaPlugin {
	private FileConfiguration activeConfiguration;
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
	private StatusBarManager statusBarManager;
	private PlayerLifecycleManager playerLifecycleManager;
	private PlaceholderApiHook placeholderApiHook;
	private PerformanceSettings performanceSettings = PerformanceSettings.DEFAULT;

	@Override
	public void onEnable() {
		// This creates config.yml only when the server does not already have one.
		saveDefaultConfig();
		languageManager = new LanguageManager(this);
		reloadConfig();
		languageManager.loadInitialConfiguration();

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
		startPlaceholderApiHook();
		getComponentLogger().info(languageManager.getMessage("plugin.enabled"));
	}

	@Override
	public void onDisable() {
		stopPlaceholderApiHook();

		if (healthRegenerationManager != null) {
			healthRegenerationManager.shutdown();
		}

		if (eatingManager != null) {
			eatingManager.shutdown();
		}

		if (statusBarManager != null) {
			statusBarManager.shutdown();
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

	public FoodManager getFoodManager() {
		return foodManager;
	}

	public PlayerLifecycleManager getPlayerLifecycleManager() {
		return playerLifecycleManager;
	}

	@Override
	public FileConfiguration getConfig() {
		return activeConfiguration == null ? super.getConfig() : activeConfiguration;
	}

	@Override
	public void reloadConfig() {
		super.reloadConfig();
		activeConfiguration = super.getConfig();
	}

	public ReloadResult reloadPluginSettings() {
		PreparedReload prepared;
		try {
			prepared = prepareReload();
		} catch (RuntimeException exception) {
			getLogger().log(
					Level.WARNING,
					"Rejected HungerOverhaul configuration reload. The active settings were kept.",
					exception
			);
			return new ReloadResult(ReloadResult.Status.REJECTED, describeFailure(exception));
		}

		try {
			applyReload(prepared);
		} catch (RuntimeException exception) {
			getLogger().log(Level.SEVERE, "Could not activate validated HungerOverhaul settings.", exception);
			return new ReloadResult(ReloadResult.Status.ACTIVATION_FAILED, describeFailure(exception));
		}
		return new ReloadResult(ReloadResult.Status.SUCCESS, "");
	}

	// Reads each file once and leaves active settings and scheduled work untouched.
	private PreparedReload prepareReload() {
		MainSettings main = readMainSettings();
		return new PreparedReload(
				main,
				groupManager.readConfiguration(),
				statusEffectManager.readConfiguration(),
				foodManager.readConfiguration(),
				languageManager.readConfiguration(main.language())
		);
	}

	private MainSettings readMainSettings() {
		YamlConfiguration configuration = new YamlConfiguration();
		try {
			configuration.load(new File(getDataFolder(), "config.yml"));
		} catch (IOException | InvalidConfigurationException exception) {
			throw new IllegalStateException("Could not load config.yml.", exception);
		}

		try (Reader resource = getTextResource("config.yml")) {
			if (resource == null) {
				throw new IOException("Bundled resource is missing.");
			}
			YamlConfiguration defaults = new YamlConfiguration();
			defaults.load(resource);
			configuration.setDefaults(defaults);
		} catch (IOException | InvalidConfigurationException exception) {
			throw new IllegalStateException("Could not load the bundled defaults for config.yml.", exception);
		}

		try {
			return new MainSettings(
					configuration,
					readStringSetting(configuration, "language", "en_us"),
					readStorageSettings(configuration),
					readVanillaHungerSettings(configuration),
					readDecaySettings(configuration),
					readLowValueWarningSettings(configuration),
					readStarvationDamageSettings(configuration),
					readHealthRegenerationSettings(configuration),
					readEatingSettings(configuration),
					readStatusBarSettings(configuration),
					readPlayerLifecycleSettings(configuration),
					readPerformanceSettings(configuration)
			);
		} catch (IllegalStateException exception) {
			throw new IllegalStateException("Invalid config.yml: " + exception.getMessage(), exception);
		}
	}

	private void applyReload(PreparedReload prepared) {
		MainSettings main = prepared.main();
		activeConfiguration = main.configuration();
		groupManager.applyConfiguration(prepared.groups());
		statusEffectManager.applyConfiguration(prepared.statusEffects());
		foodManager.applyConfiguration(prepared.foods());
		languageManager.applyConfiguration(prepared.language());
		performanceSettings = main.performance();

		persistenceManager.updateSettings(main.storage());
		refreshOnlinePlayerGroups();
		vanillaHungerManager.updateSettings(main.vanillaHunger());
		valueDecayManager.updateSettings(main.decay());
		actionDecayManager.updateSettings(main.decay());
		lowValueWarningManager.updateSettings(main.lowValueWarnings());
		starvationDamageManager.updateSettings(main.starvationDamage());
		healthRegenerationManager.updateSettings(main.healthRegeneration());
		eatingManager.updateSettings(main.eating());
		eatingManager.updatePerformanceSettings(performanceSettings);
		statusBarManager.updateSettings(main.statusBar(), main.eating().getFeedbackSettings().isActionBarEnabled(),
				performanceSettings.cacheStatusbarMessages());
		playerLifecycleManager.updateSettings(main.playerLifecycle());
		statusAttributeManager.reload();
		persistenceManager.saveAllLoadedPlayerData();
		eatingManager.handleSuccessfulReload();
		if (placeholderApiHook != null) {
			placeholderApiHook.updatePerformanceSettings(performanceSettings);
		}
	}

	private static String describeFailure(RuntimeException exception) {
		return Objects.toString(exception.getMessage(), exception.getClass().getSimpleName());
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

	private void startPlaceholderApiHook() {
		if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			return;
		}

		try {
			placeholderApiHook = new PlaceholderApiHook(this, performanceSettings);
			placeholderApiHook.start();
		} catch (RuntimeException | LinkageError exception) {
			getLogger().log(Level.WARNING, "Could not enable the optional PlaceholderAPI integration.", exception);
			stopPlaceholderApiHook();
		}
	}

	private void stopPlaceholderApiHook() {
		if (placeholderApiHook == null) {
			return;
		}

		try {
			placeholderApiHook.stop();
		} catch (RuntimeException | LinkageError exception) {
			getLogger().log(Level.WARNING, "Could not finish PlaceholderAPI integration cleanup.", exception);
		} finally {
			placeholderApiHook = null;
		}
	}

	private boolean loadGroupSettingsAtStartup() {
		try {
			groupManager.loadInitialConfiguration();
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
			foodManager.loadInitialConfiguration();
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
			statusEffectManager.loadInitialConfiguration();
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
			StorageSettings storageSettings = readStorageSettings(getConfig());
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
			performanceSettings = readPerformanceSettings(getConfig());
			EatingSettings settings = readEatingSettings(getConfig());
			StatusBarSettings statusBarSettings = readStatusBarSettings(getConfig());
			statusBarManager = new StatusBarManager(this, playerDataManager);
			eatingManager = new EatingManager(this, groupManager, playerDataManager, statusBarManager, settings);
			eatingManager.updatePerformanceSettings(performanceSettings);
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
			statusBarManager.updateSettings(statusBarSettings, settings.getFeedbackSettings().isActionBarEnabled(),
					performanceSettings.cacheStatusbarMessages());
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
			PlayerLifecycleSettings settings = readPlayerLifecycleSettings(getConfig());
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
			VanillaHungerSettings settings = readVanillaHungerSettings(getConfig());
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
			DecaySettings settings = readDecaySettings(getConfig());
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
			LowValueWarningSettings settings = readLowValueWarningSettings(getConfig());
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
			StarvationDamageSettings settings = readStarvationDamageSettings(getConfig());
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
			HealthRegenerationSettings settings = readHealthRegenerationSettings(getConfig());
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

	private StorageSettings readStorageSettings(FileConfiguration configuration) {
		boolean autosaveEnabled = readBooleanSetting(configuration, "storage.autosave.enabled", true);
		long autosaveInterval = readLongSetting(configuration, "storage.autosave.interval-seconds", 300L);
		long cacheTtl = readLongSetting(configuration, "storage.cache-ttl-seconds", 300L);

		try {
			return new StorageSettings(autosaveEnabled, autosaveInterval, cacheTtl);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("Invalid storage settings: " + exception.getMessage(), exception);
		}
	}

	private DecaySettings readDecaySettings(FileConfiguration configuration) {
		boolean enabled = readBooleanSetting(configuration, "value-decay.enabled", true);
		long intervalTicks = readLongSetting(configuration, "value-decay.interval-ticks", 20L);
		boolean actionCostsEnabled = readBooleanSetting(configuration, "value-decay.action-costs.enabled", true);

		try {
			return new DecaySettings(enabled, intervalTicks, actionCostsEnabled);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("Invalid value decay settings: " + exception.getMessage(), exception);
		}
	}

	private LowValueWarningSettings readLowValueWarningSettings(FileConfiguration configuration) {
		boolean enabled = readBooleanSetting(configuration, "low-value-warnings.enabled", true);
		long checkIntervalTicks = readLongSetting(configuration, "low-value-warnings.check-interval-ticks", 20L);
		long repeatIntervalSeconds = readLongSetting(configuration, "low-value-warnings.repeat-interval-seconds", 30L);
		boolean nutritionEnabled = readBooleanSetting(configuration, "low-value-warnings.nutrition.enabled", true);
		double nutritionThreshold = readDoubleSetting(
				configuration,
				"low-value-warnings.nutrition.threshold-percentage",
				20.0
		);
		boolean fullnessEnabled = readBooleanSetting(configuration, "low-value-warnings.fullness.enabled", true);
		double fullnessThreshold = readDoubleSetting(
				configuration,
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

	private StarvationDamageSettings readStarvationDamageSettings(FileConfiguration configuration) {
		boolean enabled = readBooleanSetting(configuration, "starvation-damage.enabled", true);
		long checkIntervalTicks = readLongSetting(configuration, "starvation-damage.check-interval-ticks", 20L);
		boolean damageInPeaceful = readBooleanSetting(configuration, "starvation-damage.damage-in-peaceful", false);
		double criticalMaximumPercentage = readDoubleSetting(
				configuration,
				"starvation-damage.critical.maximum-fullness-percentage",
				10.0
		);
		StarvationDamageRule criticalRule = readStarvationDamageRule(
				configuration,
				"starvation-damage.critical",
				200L,
				1.0
		);
		StarvationDamageRule emptyRule = readStarvationDamageRule(
				configuration,
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

	private HealthRegenerationSettings readHealthRegenerationSettings(FileConfiguration configuration) {
		boolean enabled = readBooleanSetting(configuration, "health-regeneration.enabled", true);
		long intervalTicks = readLongSetting(configuration, "health-regeneration.interval-ticks", 200L);
		double minimumChargedHealHp = readDoubleSetting(
				configuration,
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

	private PlayerLifecycleSettings readPlayerLifecycleSettings(FileConfiguration configuration) {
		DeathLifecycleSettings deathSettings = new DeathLifecycleSettings(
				readBooleanSetting(configuration, "player-lifecycle.death.cancel-eating", true),
				readBooleanSetting(configuration, "player-lifecycle.death.preserve-digestion-cooldown", true),
				readBooleanSetting(configuration, "player-lifecycle.death.save-data", true)
		);
		LifecycleValueRule respawnNutritionRule = readLifecycleValueRule(
				configuration,
				"player-lifecycle.respawn.nutrition",
				"minimum-percentage",
				20.0
		);
		LifecycleValueRule respawnFullnessRule = readLifecycleValueRule(
				configuration,
				"player-lifecycle.respawn.fullness",
				"minimum-percentage",
				20.0
		);
		RespawnLifecycleSettings respawnSettings = new RespawnLifecycleSettings(
				respawnNutritionRule,
				respawnFullnessRule,
				readBooleanSetting(configuration, "player-lifecycle.respawn.affect-frozen-values", false),
				readBooleanSetting(configuration, "player-lifecycle.respawn.save-data", true)
		);
		LifecycleValueRule worldNutritionRule = readLifecycleValueRule(
				configuration,
				"player-lifecycle.world-change.nutrition",
				"preserve-percentage",
				0.0
		);
		LifecycleValueRule worldFullnessRule = readLifecycleValueRule(
				configuration,
				"player-lifecycle.world-change.fullness",
				"preserve-percentage",
				0.0
		);
		WorldChangeLifecycleSettings worldChangeSettings = new WorldChangeLifecycleSettings(
				readBooleanSetting(configuration, "player-lifecycle.world-change.cancel-eating", true),
				readBooleanSetting(
						configuration,
						"player-lifecycle.world-change.preserve-digestion-cooldown",
						true
				),
				readBooleanSetting(configuration, "player-lifecycle.world-change.refresh-group", true),
				worldNutritionRule,
				worldFullnessRule,
				readBooleanSetting(configuration, "player-lifecycle.world-change.affect-frozen-values", false),
				readBooleanSetting(configuration, "player-lifecycle.world-change.save-data", true)
		);

		return new PlayerLifecycleSettings(deathSettings, respawnSettings, worldChangeSettings);
	}

	private LifecycleValueRule readLifecycleValueRule(
			FileConfiguration configuration,
			String path,
			String fallbackMode,
			double fallbackPercentage
	) {
		String configuredMode = readStringSetting(configuration, path + ".mode", fallbackMode);

		try {
			LifecycleValueMode mode = LifecycleValueMode.fromConfigValue(configuredMode);
			Double percentage = mode.requiresPercentage()
					? readDoubleSetting(configuration, path + ".percentage", fallbackPercentage)
					: null;
			return new LifecycleValueRule(mode, percentage);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException(
					"Invalid player lifecycle value rule '" + path + "': " + exception.getMessage(),
					exception
			);
		}
	}

	private PerformanceSettings readPerformanceSettings(FileConfiguration configuration) {
		return new PerformanceSettings(
				readBooleanSetting(configuration, "performance.skip-idle-eating-checks", true),
				readBooleanSetting(configuration, "performance.cache-statusbar-messages", true),
				readBooleanSetting(configuration, "performance.reuse-placeholder-snapshots", true),
				readBooleanSetting(configuration, "performance.cache-placeholder-status-names", true)
		);
	}

	private StatusBarSettings readStatusBarSettings(FileConfiguration configuration) {
		boolean enabled = readBooleanSetting(configuration, "actionbar.enabled", true);
		int updateIntervalTicks = readIntegerSetting(configuration, "actionbar.update-interval-ticks", 20);
		try {
			return new StatusBarSettings(enabled, updateIntervalTicks);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException(exception.getMessage(), exception);
		}
	}

	private EatingSettings readEatingSettings(FileConfiguration configuration) {
		String configuredOverflowPolicy = readStringSetting(configuration, "eating.overflow-policy", "deny");
		long cooldownTicks = readLongSetting(configuration, "eating.cooldown-ticks", 40L);
		boolean feedbackActionBarEnabled = readBooleanSetting(configuration, "eating.feedback.enabled", true);
		boolean progressActionBarEnabled = readBooleanSetting(
				configuration,
				"eating.feedback.progress-actionbar",
				true
		);
		long progressUpdateIntervalTicks = readLongSetting(
				configuration,
				"eating.feedback.progress-update-interval-ticks",
				10L
		);
		boolean completionActionBarEnabled = readBooleanSetting(
				configuration,
				"eating.feedback.completion-actionbar",
				true
		);
		boolean interruptionActionBarEnabled = readBooleanSetting(
				configuration,
				"eating.feedback.interruption-actionbar",
				true
		);
		int resultHoldTicks = readIntegerSetting(configuration, "eating.feedback.result-hold-ticks", 40);
		boolean cooldownActionBarEnabled = readBooleanSetting(configuration, "eating.feedback.cooldown-actionbar", false);
		int cooldownMessageDelayTicks = readIntegerSetting(configuration, "eating.feedback.cooldown-message-delay-ticks", 10);
		long blockedMessageIntervalTicks = readLongSetting(
				configuration,
				"eating.feedback.blocked-message-interval-ticks",
				40L
		);
		long postConsumptionSilentTicks = readLongSetting(
				configuration,
				"eating.feedback.post-consumption-silent-ticks",
				10L
		);
		boolean interruptOnDamage = readBooleanSetting(configuration, "eating.interrupt.on-damage", true);
		boolean interruptOnMove = readBooleanSetting(configuration, "eating.interrupt.on-move", false);
		boolean interruptOnSneakToggle = readBooleanSetting(
				configuration,
				"eating.interrupt.on-sneak-toggle",
				false
		);

		try {
			FoodOverflowPolicy overflowPolicy = FoodOverflowPolicy.fromConfigValue(configuredOverflowPolicy);
			EatingFeedbackSettings feedbackSettings = new EatingFeedbackSettings(
					feedbackActionBarEnabled,
					progressActionBarEnabled,
					progressUpdateIntervalTicks,
					completionActionBarEnabled,
					interruptionActionBarEnabled,
					resultHoldTicks,
					cooldownActionBarEnabled,
					cooldownMessageDelayTicks,
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
			FileConfiguration configuration,
			String path,
			long fallbackIntervalTicks,
			double fallbackDamageHp
	) {
		boolean enabled = readBooleanSetting(configuration, path + ".enabled", true);
		long intervalTicks = readLongSetting(configuration, path + ".interval-ticks", fallbackIntervalTicks);
		double damageHp = readDoubleSetting(configuration, path + ".damage-hp", fallbackDamageHp);

		try {
			return new StarvationDamageRule(enabled, intervalTicks, damageHp);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException(
					"Invalid starvation damage rule '" + path + "': " + exception.getMessage(),
					exception
			);
		}
	}

	private VanillaHungerSettings readVanillaHungerSettings(FileConfiguration configuration) {
		String configuredSource = readStringSetting(configuration, "vanilla-hunger.display.source", "fullness");
		int minimumFoodLevel = readIntegerSetting(configuration, "vanilla-hunger.display.minimum-food-level", 1);
		int maximumFoodLevel = readIntegerSetting(configuration, "vanilla-hunger.display.maximum-food-level", 20);
		boolean blockNaturalRegeneration = readBooleanSetting(
				configuration,
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

	private boolean readBooleanSetting(FileConfiguration configuration, String path, boolean fallback) {
		Object configuredValue = readConfiguredValue(configuration, path);
		if (configuredValue == null) {
			return fallback;
		}

		if (!(configuredValue instanceof Boolean booleanValue)) {
			throw new IllegalStateException("Configuration value '" + path + "' must be true or false.");
		}

		return booleanValue;
	}

	private String readStringSetting(FileConfiguration configuration, String path, String fallback) {
		Object configuredValue = readConfiguredValue(configuration, path);
		if (configuredValue == null) {
			return fallback;
		}

		if (!(configuredValue instanceof String stringValue)) {
			throw new IllegalStateException("Configuration value '" + path + "' must be text.");
		}

		return stringValue;
	}

	private int readIntegerSetting(FileConfiguration configuration, String path, int fallback) {
		Object configuredValue = readConfiguredValue(configuration, path);
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

	private double readDoubleSetting(FileConfiguration configuration, String path, double fallback) {
		Object configuredValue = readConfiguredValue(configuration, path);
		if (configuredValue == null) {
			return fallback;
		}

		if (!(configuredValue instanceof Number number)) {
			throw new IllegalStateException("Configuration value '" + path + "' must be a number.");
		}

		return number.doubleValue();
	}

	private long readLongSetting(FileConfiguration configuration, String path, long fallback) {
		Object configuredValue = readConfiguredValue(configuration, path);
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

	private Object readConfiguredValue(FileConfiguration configuration, String path) {
		int separator = path.indexOf('.');
		while (separator >= 0) {
			String sectionPath = path.substring(0, separator);
			Object section = configuration.get(sectionPath);
			if (section != null && !(section instanceof ConfigurationSection)) {
				throw new IllegalStateException("Configuration value '" + sectionPath + "' must be a section.");
			}
			separator = path.indexOf('.', separator + 1);
		}
		return configuration.get(path);
	}

	private void refreshOnlinePlayerGroups() {
		for (Player player : getServer().getOnlinePlayers()) {
			playerDataManager.refreshGroupSettings(player);
		}
	}

	public record ReloadResult(Status status, String reason) {
		public enum Status {
			SUCCESS,
			REJECTED,
			ACTIVATION_FAILED
		}
	}

	private record PreparedReload(
			MainSettings main,
			GroupManager.ParsedGroups groups,
			StatusEffectsConfiguration statusEffects,
			FoodManager.ParsedFoodConfiguration foods,
			LanguageManager.LanguageConfiguration language
	) {
	}

	private record MainSettings(
			FileConfiguration configuration,
			String language,
			StorageSettings storage,
			VanillaHungerSettings vanillaHunger,
			DecaySettings decay,
			LowValueWarningSettings lowValueWarnings,
			StarvationDamageSettings starvationDamage,
			HealthRegenerationSettings healthRegeneration,
			EatingSettings eating,
			StatusBarSettings statusBar,
			PlayerLifecycleSettings playerLifecycle,
			PerformanceSettings performance
	) {
	}
}
