package basementhost.randomchad.manager;

import basementhost.randomchad.HungerOverhaul;
import basementhost.randomchad.data.PlayerData;
import basementhost.randomchad.data.StorageSettings;
import basementhost.randomchad.data.StoredPlayerData;
import basementhost.randomchad.storage.CorruptPlayerDataException;
import basementhost.randomchad.storage.PlayerDataStorage;
import basementhost.randomchad.storage.PlayerDataStorageException;
import basementhost.randomchad.storage.ReadOnlyPlayerDataQueryResult;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

// Coordinates asynchronous file access, autosaves, and offline memory expiration.
public final class PlayerDataPersistenceManager {
	private static final long TICKS_PER_SECOND = 20L;
	private static final long SHUTDOWN_WAIT_SECONDS = 10L;

	private final HungerOverhaul plugin;
	private final PlayerDataManager playerDataManager;
	private final PlayerDataStorage storage;
	private final ExecutorService storageExecutor;
	private final Set<UUID> loadingPlayerIds = new HashSet<>();
	private final Map<UUID, String> lastKnownNames = new HashMap<>();
	private final Map<UUID, Long> offlineSince = new HashMap<>();
	private final Map<UUID, BukkitTask> unloadTasks = new HashMap<>();

	private StorageSettings settings;
	private BukkitTask autosaveTask;
	private boolean started;
	private boolean stopping;

	public PlayerDataPersistenceManager(
			HungerOverhaul plugin,
			PlayerDataManager playerDataManager,
			PlayerDataStorage storage,
			StorageSettings settings
	) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.playerDataManager = Objects.requireNonNull(playerDataManager, "playerDataManager");
		this.storage = Objects.requireNonNull(storage, "storage");
		this.settings = Objects.requireNonNull(settings, "settings");
		this.storageExecutor = Executors.newSingleThreadExecutor(task -> {
			Thread storageThread = new Thread(task, "HungerOverhaul-PlayerData");
			storageThread.setDaemon(true);
			return storageThread;
		});
	}

	public void start() {
		if (started) {
			throw new IllegalStateException("PlayerDataPersistenceManager has already started");
		}

		if (stopping) {
			throw new IllegalStateException("PlayerDataPersistenceManager has already stopped");
		}

		started = true;
		scheduleAutosave();
		for (Player player : plugin.getServer().getOnlinePlayers()) {
			loadPlayerData(player);
		}
	}

	public void updateSettings(StorageSettings settings) {
		this.settings = Objects.requireNonNull(settings, "settings");
		if (!started || stopping) {
			return;
		}

		scheduleAutosave();
		rescheduleOfflineUnloads();
	}

	public void loadPlayerData(Player player) {
		Objects.requireNonNull(player, "player");
		if (stopping) {
			return;
		}

		UUID playerId = player.getUniqueId();
		lastKnownNames.put(playerId, player.getName());
		cancelUnload(playerId);
		offlineSince.remove(playerId);

		if (playerDataManager.hasPlayerData(playerId)) {
			playerDataManager.refreshGroupSettings(player);
			return;
		}

		if (!loadingPlayerIds.add(playerId)) {
			return;
		}

		submitStorageTask(() -> loadPlayerDataFromStorage(playerId));
	}

	public void saveAndCachePlayerData(Player player) {
		Objects.requireNonNull(player, "player");
		UUID playerId = player.getUniqueId();
		long quitTime = System.currentTimeMillis();
		lastKnownNames.put(playerId, player.getName());

		playerDataManager.findPlayerData(playerId).ifPresent(playerData ->
				submitSave(playerId, createSnapshot(playerData, player.getName(), quitTime))
		);

		offlineSince.put(playerId, quitTime);
		scheduleUnload(playerId, quitTime);
	}

	// Queues a save while keeping the player's data loaded in memory.
	public boolean savePlayerData(Player player) {
		Objects.requireNonNull(player, "player");
		PlayerData playerData = playerDataManager.findPlayerData(player).orElse(null);
		if (playerData == null) {
			return false;
		}

		UUID playerId = player.getUniqueId();
		lastKnownNames.put(playerId, player.getName());
		submitSave(playerId, createSnapshot(playerData, player.getName(), System.currentTimeMillis()));
		return true;
	}

	public void saveAllLoadedPlayerData() {
		if (stopping) {
			return;
		}

		for (Map.Entry<UUID, StoredPlayerData> entry : createAllSnapshots().entrySet()) {
			submitSave(entry.getKey(), entry.getValue());
		}
	}

	public boolean isPlayerDataLoading(UUID playerId) {
		return loadingPlayerIds.contains(Objects.requireNonNull(playerId, "playerId"));
	}

	// Queries an existing file on the storage thread and never loads it into the live cache.
	public void queryPlayerDataReadOnly(
			UUID playerId,
			Consumer<ReadOnlyPlayerDataQueryResult> resultHandler
	) {
		Objects.requireNonNull(playerId, "playerId");
		Objects.requireNonNull(resultHandler, "resultHandler");
		if (stopping) {
			resultHandler.accept(ReadOnlyPlayerDataQueryResult.of(
					ReadOnlyPlayerDataQueryResult.Status.UNAVAILABLE
			));
			return;
		}

		boolean queued = submitStorageTask(() -> queryPlayerDataFileReadOnly(playerId, resultHandler));
		if (!queued) {
			resultHandler.accept(ReadOnlyPlayerDataQueryResult.of(
					ReadOnlyPlayerDataQueryResult.Status.UNAVAILABLE
			));
		}
	}

	// Uses the saved last-known name when Paper no longer has the player in its fast profile cache.
	public void queryPlayerDataReadOnly(
			String playerName,
			Consumer<ReadOnlyPlayerDataQueryResult> resultHandler
	) {
		Objects.requireNonNull(playerName, "playerName");
		Objects.requireNonNull(resultHandler, "resultHandler");
		if (stopping) {
			resultHandler.accept(ReadOnlyPlayerDataQueryResult.of(
					ReadOnlyPlayerDataQueryResult.Status.UNAVAILABLE
			));
			return;
		}

		boolean queued = submitStorageTask(() -> queryPlayerDataFileReadOnly(playerName, resultHandler));
		if (!queued) {
			resultHandler.accept(ReadOnlyPlayerDataQueryResult.of(
					ReadOnlyPlayerDataQueryResult.Status.UNAVAILABLE
			));
		}
	}

	public void shutdown() {
		if (stopping) {
			return;
		}

		stopping = true;
		cancelAutosave();
		cancelAllUnloadTasks();

		Map<UUID, StoredPlayerData> finalSnapshots = createAllSnapshots();
		for (Map.Entry<UUID, StoredPlayerData> entry : finalSnapshots.entrySet()) {
			submitFinalSave(entry.getKey(), entry.getValue());
		}

		storageExecutor.shutdown();
		waitForStorageShutdown();
		loadingPlayerIds.clear();
		lastKnownNames.clear();
		offlineSince.clear();
		playerDataManager.unloadAllPlayerData();
	}

	private void loadPlayerDataFromStorage(UUID playerId) {
		try {
			Optional<StoredPlayerData> storedPlayerData = storage.load(playerId);
			scheduleLoadedDataApplication(playerId, storedPlayerData.orElse(null), false);
		} catch (CorruptPlayerDataException exception) {
			plugin.getLogger().log(
					Level.WARNING,
					"Player data for " + playerId + " is corrupt and will be replaced with default data.",
					exception
			);
			scheduleLoadedDataApplication(playerId, null, true);
		} catch (PlayerDataStorageException exception) {
			plugin.getLogger().log(Level.SEVERE, "Could not load player data for " + playerId + ".", exception);
			scheduleLoadFailure(playerId);
		}
	}

	private void queryPlayerDataFileReadOnly(
			UUID playerId,
			Consumer<ReadOnlyPlayerDataQueryResult> resultHandler
	) {
		ReadOnlyPlayerDataQueryResult result;
		try {
			Optional<StoredPlayerData> storedPlayerData = storage.loadReadOnly(playerId);
			result = storedPlayerData
					.map(playerData -> ReadOnlyPlayerDataQueryResult.found(playerId, playerData))
					.orElseGet(() -> ReadOnlyPlayerDataQueryResult.of(
							ReadOnlyPlayerDataQueryResult.Status.NOT_FOUND
					));
		} catch (CorruptPlayerDataException exception) {
			plugin.getLogger().log(
					Level.WARNING,
					"Read-only query found corrupt player data for " + playerId + ". The file was not changed.",
					exception
			);
			result = ReadOnlyPlayerDataQueryResult.of(ReadOnlyPlayerDataQueryResult.Status.CORRUPT);
		} catch (PlayerDataStorageException exception) {
			plugin.getLogger().log(
					Level.WARNING,
					"Could not complete read-only player data query for " + playerId + ".",
					exception
			);
			result = ReadOnlyPlayerDataQueryResult.of(ReadOnlyPlayerDataQueryResult.Status.FAILED);
		}

		ReadOnlyPlayerDataQueryResult completedResult = result;
		runOnServerThread(() -> resultHandler.accept(completedResult));
	}

	private void queryPlayerDataFileReadOnly(
			String playerName,
			Consumer<ReadOnlyPlayerDataQueryResult> resultHandler
	) {
		ReadOnlyPlayerDataQueryResult result;
		try {
			Optional<UUID> playerId = storage.findPlayerIdByNameReadOnly(playerName);
			if (playerId.isEmpty()) {
				result = ReadOnlyPlayerDataQueryResult.of(ReadOnlyPlayerDataQueryResult.Status.NOT_FOUND);
			} else {
				Optional<StoredPlayerData> storedPlayerData = storage.loadReadOnly(playerId.get());
				result = storedPlayerData
						.map(playerData -> ReadOnlyPlayerDataQueryResult.found(playerId.get(), playerData))
						.orElseGet(() -> ReadOnlyPlayerDataQueryResult.of(
								ReadOnlyPlayerDataQueryResult.Status.NOT_FOUND
						));
			}
		} catch (CorruptPlayerDataException exception) {
			plugin.getLogger().log(
					Level.WARNING,
					"Read-only query found corrupt player data for " + playerName + ". The file was not changed.",
					exception
			);
			result = ReadOnlyPlayerDataQueryResult.of(ReadOnlyPlayerDataQueryResult.Status.CORRUPT);
		} catch (PlayerDataStorageException exception) {
			plugin.getLogger().log(
					Level.WARNING,
					"Could not complete read-only player data query for " + playerName + ".",
					exception
			);
			result = ReadOnlyPlayerDataQueryResult.of(ReadOnlyPlayerDataQueryResult.Status.FAILED);
		}

		ReadOnlyPlayerDataQueryResult completedResult = result;
		runOnServerThread(() -> resultHandler.accept(completedResult));
	}

	private void scheduleLoadedDataApplication(
			UUID playerId,
			StoredPlayerData storedPlayerData,
			boolean replacingCorruptData
	) {
		runOnServerThread(() -> {
			loadingPlayerIds.remove(playerId);
			Player player = plugin.getServer().getPlayer(playerId);
			if (player == null || !player.isOnline()) {
				return;
			}

			lastKnownNames.put(playerId, player.getName());
			PlayerData playerData;
			if (storedPlayerData == null) {
				playerData = playerDataManager.getOrCreatePlayerData(player);
			} else {
				playerData = playerDataManager.restorePlayerData(player, storedPlayerData);
			}

			if (replacingCorruptData) {
				player.sendMessage(plugin.getLanguageManager().getMessage("data.corrupt-reset"));
			}

			if (storedPlayerData == null) {
				submitSave(playerId, createSnapshot(playerData, player.getName(), System.currentTimeMillis()));
			}
		});
	}

	private void scheduleLoadFailure(UUID playerId) {
		runOnServerThread(() -> {
			loadingPlayerIds.remove(playerId);
			Player player = plugin.getServer().getPlayer(playerId);
			if (player != null && player.isOnline()) {
				player.kick(plugin.getLanguageManager().getMessage("data.load-failure"));
			}
		});
	}

	private void submitSave(UUID playerId, StoredPlayerData storedPlayerData) {
		if (stopping) {
			return;
		}

		submitStorageTask(() -> saveSnapshot(playerId, storedPlayerData));
	}

	private void submitFinalSave(UUID playerId, StoredPlayerData storedPlayerData) {
		try {
			storageExecutor.execute(() -> saveSnapshot(playerId, storedPlayerData));
		} catch (RejectedExecutionException exception) {
			plugin.getLogger().log(Level.SEVERE, "Could not queue final player data save for " + playerId + ".", exception);
		}
	}

	private void saveSnapshot(UUID playerId, StoredPlayerData storedPlayerData) {
		try {
			storage.save(playerId, storedPlayerData);
		} catch (PlayerDataStorageException exception) {
			plugin.getLogger().log(Level.SEVERE, "Could not save player data for " + playerId + ".", exception);
		}
	}

	private StoredPlayerData createSnapshot(PlayerData playerData, String playerName, long lastSeen) {
		return new StoredPlayerData(
				playerName,
				playerData.getNutrition(),
				playerData.getFullness(),
				playerData.isNutritionFrozen(),
				playerData.isFullnessFrozen(),
				lastSeen
		);
	}

	private Map<UUID, StoredPlayerData> createAllSnapshots() {
		Map<UUID, StoredPlayerData> snapshots = new LinkedHashMap<>();
		long currentTime = System.currentTimeMillis();

		for (PlayerData playerData : playerDataManager.getLoadedPlayerData()) {
			UUID playerId = playerData.getPlayerId();
			Player onlinePlayer = plugin.getServer().getPlayer(playerId);
			String playerName;
			long lastSeen;

			if (onlinePlayer != null && onlinePlayer.isOnline()) {
				playerName = onlinePlayer.getName();
				lastKnownNames.put(playerId, playerName);
				lastSeen = currentTime;
			} else {
				playerName = lastKnownNames.getOrDefault(playerId, playerId.toString());
				lastSeen = offlineSince.getOrDefault(playerId, currentTime);
			}

			snapshots.put(playerId, createSnapshot(playerData, playerName, lastSeen));
		}

		return snapshots;
	}

	private void scheduleAutosave() {
		cancelAutosave();
		if (!settings.isAutosaveEnabled() || !started || stopping) {
			return;
		}

		long intervalTicks = settings.getAutosaveIntervalSeconds() * TICKS_PER_SECOND;
		autosaveTask = plugin.getServer().getScheduler().runTaskTimer(
				plugin,
				this::saveAllLoadedPlayerData,
				intervalTicks,
				intervalTicks
		);
	}

	private void cancelAutosave() {
		if (autosaveTask == null) {
			return;
		}

		autosaveTask.cancel();
		autosaveTask = null;
	}

	private void scheduleUnload(UUID playerId, long quitTime) {
		cancelUnload(playerId);
		long ttlMillis = settings.getCacheTtlSeconds() * 1000L;
		long elapsedMillis = Math.max(0L, System.currentTimeMillis() - quitTime);
		long remainingMillis = ttlMillis - elapsedMillis;

		if (remainingMillis <= 0L) {
			unloadOfflinePlayerData(playerId, quitTime);
			return;
		}

		long delayTicks = Math.max(1L, (remainingMillis + 49L) / 50L);
		BukkitTask unloadTask = plugin.getServer().getScheduler().runTaskLater(
				plugin,
				() -> unloadOfflinePlayerData(playerId, quitTime),
				delayTicks
		);
		unloadTasks.put(playerId, unloadTask);
	}

	private void unloadOfflinePlayerData(UUID playerId, long expectedQuitTime) {
		unloadTasks.remove(playerId);
		Long recordedQuitTime = offlineSince.get(playerId);
		Player player = plugin.getServer().getPlayer(playerId);
		if (recordedQuitTime == null
				|| recordedQuitTime != expectedQuitTime
				|| (player != null && player.isOnline())) {
			return;
		}

		offlineSince.remove(playerId);
		lastKnownNames.remove(playerId);
		playerDataManager.unloadPlayerData(playerId);
	}

	private void rescheduleOfflineUnloads() {
		for (BukkitTask unloadTask : unloadTasks.values()) {
			unloadTask.cancel();
		}
		unloadTasks.clear();

		for (Map.Entry<UUID, Long> entry : Map.copyOf(offlineSince).entrySet()) {
			scheduleUnload(entry.getKey(), entry.getValue());
		}
	}

	private void cancelUnload(UUID playerId) {
		BukkitTask unloadTask = unloadTasks.remove(playerId);
		if (unloadTask != null) {
			unloadTask.cancel();
		}
	}

	private void cancelAllUnloadTasks() {
		for (BukkitTask unloadTask : unloadTasks.values()) {
			unloadTask.cancel();
		}
		unloadTasks.clear();
	}

	private boolean submitStorageTask(Runnable storageTask) {
		try {
			storageExecutor.execute(storageTask);
			return true;
		} catch (RejectedExecutionException exception) {
			plugin.getLogger().log(Level.SEVERE, "Could not queue a player data storage task.", exception);
			return false;
		}
	}

	private void runOnServerThread(Runnable task) {
		if (stopping || !plugin.isEnabled()) {
			return;
		}

		try {
			plugin.getServer().getScheduler().runTask(plugin, task);
		} catch (RuntimeException exception) {
			plugin.getLogger().log(Level.SEVERE, "Could not finish a player data loading task.", exception);
		}
	}

	private void waitForStorageShutdown() {
		try {
			if (!storageExecutor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
				plugin.getLogger().severe("Player data saving did not finish within 10 seconds.");
				storageExecutor.shutdownNow();
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			storageExecutor.shutdownNow();
			plugin.getLogger().log(Level.SEVERE, "Interrupted while waiting for player data saving to finish.", exception);
		}
	}
}
