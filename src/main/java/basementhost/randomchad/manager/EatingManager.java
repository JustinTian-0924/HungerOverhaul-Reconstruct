package basementhost.randomchad.manager;

import basementhost.randomchad.HungerOverhaul;
import basementhost.randomchad.data.EatingFeedbackSettings;
import basementhost.randomchad.data.EatingInterruptReason;
import basementhost.randomchad.data.EatingInterruptSettings;
import basementhost.randomchad.data.EatingSettings;
import basementhost.randomchad.data.EatingStatusSnapshot;
import basementhost.randomchad.data.FoodConsumptionResult;
import basementhost.randomchad.data.FoodInterruptSettings;
import basementhost.randomchad.data.FoodOverflowPolicy;
import basementhost.randomchad.data.FoodSettings;
import basementhost.randomchad.data.PlayerData;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// Coordinates capacity checks, feedback, interruption rules, cooldowns, and native item use timing.
public final class EatingManager {
	private static final long MILLIS_PER_TICK = 50L;
	private static final double OVERFLOW_EPSILON = 0.0000001;

	private final HungerOverhaul plugin;
	private final GroupManager groupManager;
	private final PlayerDataManager playerDataManager;
	private final StatusBarManager statusBarManager;
	private final Map<UUID, CooldownWindow> cooldownsByPlayer = new HashMap<>();
	private final Map<UUID, Long> blockedMessageSilenceEndsByPlayer = new HashMap<>();
	private final Map<UUID, EnumMap<BlockedMessageReason, Long>> blockedMessageEndsByPlayer = new HashMap<>();
	private final Map<UUID, PendingEatingStart> pendingStartsByPlayer = new HashMap<>();
	private final Map<UUID, EatingSession> sessionsByPlayer = new HashMap<>();

	private EatingSettings settings;
	private BukkitTask timingTask;
	private boolean started;

	public EatingManager(
			HungerOverhaul plugin,
			GroupManager groupManager,
			PlayerDataManager playerDataManager,
			StatusBarManager statusBarManager,
			EatingSettings settings
	) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.groupManager = Objects.requireNonNull(groupManager, "groupManager");
		this.playerDataManager = Objects.requireNonNull(playerDataManager, "playerDataManager");
		this.statusBarManager = Objects.requireNonNull(statusBarManager, "statusBarManager");
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	public void start() {
		if (started) {
			throw new IllegalStateException("EatingManager has already started");
		}

		started = true;
		timingTask = plugin.getServer().getScheduler().runTaskTimer(
				plugin,
				this::updateEatingSessions,
				1L,
				1L
		);
	}

	public void updateSettings(EatingSettings settings) {
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	public EatingSettings getSettings() {
		return settings;
	}

	public boolean isProgressFeedbackActive(UUID playerId) {
		return settings.getFeedbackSettings().isProgressActionBarEnabled()
				&& (sessionsByPlayer.containsKey(playerId) || pendingStartsByPlayer.containsKey(playerId));
	}

	public EatingStatusSnapshot getStatus(Player player) {
		Objects.requireNonNull(player, "player");
		UUID playerId = player.getUniqueId();
		EatingSession session = sessionsByPlayer.get(playerId);
		PendingEatingStart pendingStart = pendingStartsByPlayer.get(playerId);
		EatingStatusSnapshot.Activity activity = EatingStatusSnapshot.Activity.IDLE;
		String foodId = null;
		double progressPercentage = 0.0;
		long eatingRemainingTicks = 0L;

		if (session != null) {
			activity = EatingStatusSnapshot.Activity.EATING;
			foodId = getStatusFoodId(session.getItemSnapshot(), session.getFoodSettings());
			progressPercentage = Math.min(
					100.0,
					session.getElapsedTicks() * 100.0 / session.getAdjustedDurationTicks()
			);
			eatingRemainingTicks = Math.max(
					0L,
					(long) session.getAdjustedDurationTicks() - session.getElapsedTicks()
			);
		} else if (pendingStart != null) {
			activity = EatingStatusSnapshot.Activity.STARTING;
			foodId = pendingStart.getFoodId();
		}

		long now = System.currentTimeMillis();
		CooldownWindow cooldown = getActiveCooldown(playerId, now);
		return new EatingStatusSnapshot(
				activity,
				foodId,
				progressPercentage,
				eatingRemainingTicks,
				cooldown == null ? 0L : millisToTicks(cooldown.endsAtMillis() - now),
				getRemainingTicks(blockedMessageSilenceEndsByPlayer, playerId, now)
		);
	}

	private String getStatusFoodId(ItemStack item, FoodSettings foodSettings) {
		String id = foodSettings.getId();
		return id.contains(":") || id.startsWith("custom-foods.") ? id : item.getType().getKey().toString();
	}

	// A successful reload ends sessions that were calculated from the previous configuration.
	public void handleSuccessfulReload() {
		for (Map.Entry<UUID, EatingSession> entry : List.copyOf(sessionsByPlayer.entrySet())) {
			Player player = plugin.getServer().getPlayer(entry.getKey());
			EatingSession session = entry.getValue();
			if (player == null
					|| !player.isOnline()
					|| !matchesActiveItem(player, session.getHand(), session.getItemSnapshot())) {
				sessionsByPlayer.remove(entry.getKey(), entry.getValue());
				continue;
			}

			interruptSession(player, session, EatingInterruptReason.RELOAD);
		}

		for (Map.Entry<UUID, PendingEatingStart> entry : List.copyOf(pendingStartsByPlayer.entrySet())) {
			PendingEatingStart pendingStart = entry.getValue();
			if (!pendingStartsByPlayer.remove(entry.getKey(), pendingStart)) {
				continue;
			}

			pendingStart.getTask().cancel();
			Player player = plugin.getServer().getPlayer(entry.getKey());
			if (player == null
					|| !player.isOnline()
					|| !matchesActiveItem(player, pendingStart.getHand(), pendingStart.getItemSnapshot())) {
				continue;
			}

			player.clearActiveItem();
			sendInterruptionFeedback(player, EatingInterruptReason.RELOAD);
		}
	}

	public void handleLifecycleTransition(
			Player player,
			EatingInterruptReason reason,
			boolean cancelEating,
			boolean preserveDigestionCooldown,
			boolean showInterruptionFeedback
	) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(reason, "reason");
		UUID playerId = player.getUniqueId();
		boolean cancelledEatingAttempt = false;

		if (cancelEating) {
			EatingSession session = sessionsByPlayer.remove(playerId);
			if (session != null) {
				cancelledEatingAttempt = true;
				if (matchesActiveItem(player, session.getHand(), session.getItemSnapshot())) {
					player.clearActiveItem();
				}
			}

			PendingEatingStart pendingStart = pendingStartsByPlayer.remove(playerId);
			if (pendingStart != null) {
				cancelledEatingAttempt = true;
				pendingStart.getTask().cancel();
				if (matchesActiveItem(player, pendingStart.getHand(), pendingStart.getItemSnapshot())) {
					player.clearActiveItem();
				}
			}
		}

		if (!preserveDigestionCooldown) {
			cooldownsByPlayer.remove(playerId);
		}
		blockedMessageSilenceEndsByPlayer.remove(playerId);
		blockedMessageEndsByPlayer.remove(playerId);

		if (cancelledEatingAttempt && showInterruptionFeedback) {
			sendInterruptionFeedback(player, reason);
		}
	}

	public void shutdown() {
		started = false;
		if (timingTask != null) {
			timingTask.cancel();
			timingTask = null;
		}

		for (PendingEatingStart pendingStart : pendingStartsByPlayer.values()) {
			pendingStart.getTask().cancel();
		}
		pendingStartsByPlayer.clear();
		sessionsByPlayer.clear();
		cooldownsByPlayer.clear();
		blockedMessageSilenceEndsByPlayer.clear();
		blockedMessageEndsByPlayer.clear();
	}

	public boolean canConsume(Player player, FoodSettings foodSettings) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(foodSettings, "foodSettings");
		long now = System.currentTimeMillis();
		CooldownWindow cooldown = getActiveCooldown(player.getUniqueId(), now);
		if (cooldown != null) {
			sendCooldownFeedback(player, cooldown, now);
			return false;
		}

		if (!playerDataManager.refreshGroupSettings(player)) {
			return false;
		}
		PlayerData playerData = playerDataManager.findPlayerData(player).orElse(null);
		if (playerData == null) {
			return false;
		}

		FoodOverflowPolicy overflowPolicy = foodSettings.getOverflowPolicy() == null
				? settings.getOverflowPolicy()
				: foodSettings.getOverflowPolicy();
		if (overflowPolicy == FoodOverflowPolicy.CLAMP) {
			return true;
		}

		double nutritionSpace = Math.max(0.0, playerData.getMaxNutrition() - playerData.getNutrition());
		double fullnessSpace = Math.max(0.0, playerData.getMaxFullness() - playerData.getFullness());
		double nutritionOverflow = playerData.isNutritionFrozen()
				? 0.0
				: calculateOverflow(foodSettings.getNutritionChange(), nutritionSpace);
		double fullnessOverflow = playerData.isFullnessFrozen()
				? 0.0
				: calculateOverflow(foodSettings.getFullnessChange(), fullnessSpace);
		if (nutritionOverflow <= OVERFLOW_EPSILON && fullnessOverflow <= OVERFLOW_EPSILON) {
			return true;
		}

		if (shouldSendBlockedMessage(player, BlockedMessageReason.OVERFLOW)) {
			sendOverflowMessages(
					player,
					foodSettings,
					nutritionSpace,
					fullnessSpace,
					nutritionOverflow,
					fullnessOverflow
			);
		}
		return false;
	}

	public void notifyDataBlocked(Player player, boolean loading) {
		Objects.requireNonNull(player, "player");
		BlockedMessageReason reason = loading
				? BlockedMessageReason.DATA_LOADING
				: BlockedMessageReason.DATA_UNAVAILABLE;
		if (shouldSendBlockedMessage(player, reason)) {
			String messageKey = loading ? "command.status.loading" : "command.status.unavailable";
			player.sendMessage(plugin.getLanguageManager().getMessage(messageKey));
		}
	}

	public void prepareEatingTime(
			Player player,
			EquipmentSlot hand,
			ItemStack item,
			FoodSettings foodSettings
	) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(hand, "hand");
		Objects.requireNonNull(item, "item");
		Objects.requireNonNull(foodSettings, "foodSettings");
		UUID playerId = player.getUniqueId();
		cancelPendingStart(playerId);
		sessionsByPlayer.remove(playerId);
		ItemStack itemSnapshot = item.clone();
		BukkitTask pendingStart = plugin.getServer().getScheduler().runTask(
				plugin,
				() -> beginEatingSession(playerId, hand, itemSnapshot, foodSettings)
		);
		pendingStartsByPlayer.put(
				playerId,
				new PendingEatingStart(pendingStart, hand, itemSnapshot, getStatusFoodId(itemSnapshot, foodSettings))
		);
	}

	public void recordSuccessfulConsumption(
			Player player,
			FoodSettings foodSettings,
			FoodConsumptionResult result
	) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(foodSettings, "foodSettings");
		Objects.requireNonNull(result, "result");
		UUID playerId = player.getUniqueId();
		cancelPendingStart(playerId);
		sessionsByPlayer.remove(playerId);

		long now = System.currentTimeMillis();
		long silentTicks = settings.getFeedbackSettings().getPostConsumptionSilentTicks();
		if (silentTicks <= 0L) {
			blockedMessageSilenceEndsByPlayer.remove(playerId);
		} else {
			blockedMessageSilenceEndsByPlayer.put(playerId, calculateEndTime(now, silentTicks));
		}

		long cooldownTicks = foodSettings.getCooldownTicks() == null
				? settings.getCooldownTicks()
				: foodSettings.getCooldownTicks();
		if (cooldownTicks <= 0L) {
			cooldownsByPlayer.remove(playerId);
		} else {
			cooldownsByPlayer.put(playerId, new CooldownWindow(
					calculateEndTime(now, cooldownTicks),
					plugin.getServer().getCurrentTick()
			));
		}

		EatingFeedbackSettings feedbackSettings = settings.getFeedbackSettings();
		if (feedbackSettings.isCompletionActionBarEnabled()) {
			String messageKey = result.wasAnyValueClamped()
					? "eating.feedback.completed-clamped"
					: "eating.feedback.completed";
			statusBarManager.showEatingFeedback(
					player,
					plugin.getLanguageManager().getMessage(messageKey, Map.of(
							"nutrition", formatSignedValue(result.getNutritionChange()),
							"fullness", formatSignedValue(result.getFullnessChange())
					)),
					feedbackSettings.getResultHoldTicks()
			);
		}
	}

	public boolean interruptIfConfigured(Player player, EatingInterruptReason reason) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(reason, "reason");
		EatingSession session = sessionsByPlayer.get(player.getUniqueId());
		if (session == null || !isInterruptionEnabled(session.getFoodSettings(), reason)) {
			return false;
		}

		interruptSession(player, session, reason);
		return true;
	}

	public boolean interruptNativeUse(Player player, EatingInterruptReason reason) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(reason, "reason");
		EatingSession session = sessionsByPlayer.get(player.getUniqueId());
		if (session == null) {
			return false;
		}

		interruptSession(player, session, reason);
		return true;
	}

	// The stop event also fires around successful use, so confirmation is delayed by one tick.
	public void handleStoppedUsingItem(Player player) {
		Objects.requireNonNull(player, "player");
		UUID playerId = player.getUniqueId();
		if (!sessionsByPlayer.containsKey(playerId)) {
			return;
		}

		plugin.getServer().getScheduler().runTask(plugin, () -> {
			EatingSession session = sessionsByPlayer.get(playerId);
			Player onlinePlayer = plugin.getServer().getPlayer(playerId);
			if (session != null && onlinePlayer != null && onlinePlayer.isOnline()) {
				interruptSession(onlinePlayer, session, EatingInterruptReason.RELEASED);
			}
		});
	}

	public void clearPlayer(Player player) {
		Objects.requireNonNull(player, "player");
		UUID playerId = player.getUniqueId();
		statusBarManager.removePlayer(playerId);
		cancelPendingStart(playerId);
		sessionsByPlayer.remove(playerId);
		cooldownsByPlayer.remove(playerId);
		blockedMessageSilenceEndsByPlayer.remove(playerId);
		blockedMessageEndsByPlayer.remove(playerId);
	}

	private void beginEatingSession(
			UUID playerId,
			EquipmentSlot hand,
			ItemStack itemSnapshot,
			FoodSettings foodSettings
	) {
		pendingStartsByPlayer.remove(playerId);
		Player player = plugin.getServer().getPlayer(playerId);
		if (player == null
				|| !player.isOnline()
				|| !player.hasActiveItem()
				|| player.getActiveItemHand() != hand
				|| !player.getActiveItem().isSimilar(itemSnapshot)) {
			return;
		}

		int nativeDurationTicks = getNativeDurationTicks(player);
		if (nativeDurationTicks <= 0) {
			return;
		}

		int baseDurationTicks = foodSettings.getEatingTimeTicks() == null
				? nativeDurationTicks
				: foodSettings.getEatingTimeTicks();
		double speedMultiplier = groupManager.getSettings(player)
				.getEatingSettings()
				.getSpeedMultiplier();
		int adjustedDurationTicks = divideDuration(baseDurationTicks, speedMultiplier);
		EatingSession session = new EatingSession(
				hand,
				itemSnapshot,
				foodSettings,
				nativeDurationTicks,
				adjustedDurationTicks,
				Math.max(0, player.getActiveItemUsedTime())
		);
		sessionsByPlayer.put(playerId, session);
		updateEatingSession(playerId, session);
	}

	private void updateEatingSessions() {
		for (Map.Entry<UUID, EatingSession> entry : List.copyOf(sessionsByPlayer.entrySet())) {
			if (sessionsByPlayer.get(entry.getKey()) == entry.getValue()) {
				updateEatingSession(entry.getKey(), entry.getValue());
			}
		}
	}

	private void updateEatingSession(UUID playerId, EatingSession session) {
		Player player = plugin.getServer().getPlayer(playerId);
		if (player == null || !player.isOnline()) {
			sessionsByPlayer.remove(playerId);
			return;
		}

		if (!player.hasActiveItem()
				|| player.getActiveItemHand() != session.getHand()
				|| !player.getActiveItem().isSimilar(session.getItemSnapshot())) {
			interruptSession(player, session, EatingInterruptReason.RELEASED);
			return;
		}

		int remainingTicks = session.getAdjustedDurationTicks() - session.getElapsedTicks();
		if (remainingTicks <= 0) {
			sessionsByPlayer.remove(playerId);
			player.completeUsingActiveItem();
			return;
		}

		player.setActiveItemRemainingTime(Math.min(session.getNativeDurationTicks(), remainingTicks));
		sendProgressFeedback(player, session, remainingTicks);
		session.incrementElapsedTicks();
	}

	private void sendProgressFeedback(Player player, EatingSession session, int remainingTicks) {
		EatingFeedbackSettings feedbackSettings = settings.getFeedbackSettings();
		if (!feedbackSettings.isProgressActionBarEnabled()
				|| !session.shouldSendProgress(feedbackSettings.getProgressUpdateIntervalTicks())) {
			return;
		}

		double percentage = Math.min(
				100.0,
				session.getElapsedTicks() * 100.0 / session.getAdjustedDurationTicks()
		);
		statusBarManager.showEatingFeedback(
				player,
				plugin.getLanguageManager().getMessage("eating.feedback.progress", Map.of(
						"percentage", formatPercentage(percentage),
						"seconds", formatSeconds(remainingTicks / 20.0)
				)),
				0
		);
		session.markProgressSent();
	}

	private void interruptSession(
			Player player,
			EatingSession expectedSession,
			EatingInterruptReason reason
	) {
		UUID playerId = player.getUniqueId();
		if (!sessionsByPlayer.remove(playerId, expectedSession)) {
			return;
		}

		if (player.hasActiveItem()) {
			player.clearActiveItem();
		}

		sendInterruptionFeedback(player, reason);
	}

	private void sendInterruptionFeedback(Player player, EatingInterruptReason reason) {
		EatingFeedbackSettings feedbackSettings = settings.getFeedbackSettings();
		if (feedbackSettings.isInterruptionActionBarEnabled()) {
			statusBarManager.showEatingFeedback(
					player,
					plugin.getLanguageManager().getMessage(getInterruptionMessageKey(reason)),
					feedbackSettings.getResultHoldTicks()
			);
		}
	}

	private boolean isInterruptionEnabled(FoodSettings foodSettings, EatingInterruptReason reason) {
		EatingInterruptSettings global = settings.getInterruptSettings();
		FoodInterruptSettings food = foodSettings.getInterruptSettings();
		return switch (reason) {
			case DAMAGE -> resolveBoolean(food.getOnDamage(), global.isOnDamage());
			case MOVE -> resolveBoolean(food.getOnMove(), global.isOnMove());
			case SNEAK_TOGGLE -> resolveBoolean(food.getOnSneakToggle(), global.isOnSneakToggle());
			case ITEM_SWITCH, RELEASED, RELOAD, DEATH, WORLD_CHANGE -> true;
		};
	}

	private boolean resolveBoolean(Boolean override, boolean fallback) {
		return override == null ? fallback : override;
	}

	private String getInterruptionMessageKey(EatingInterruptReason reason) {
		return switch (reason) {
			case DAMAGE -> "eating.feedback.interrupted-damage";
			case MOVE -> "eating.feedback.interrupted-move";
			case SNEAK_TOGGLE -> "eating.feedback.interrupted-sneak-toggle";
			case ITEM_SWITCH -> "eating.feedback.interrupted-item-switch";
			case RELEASED -> "eating.feedback.interrupted-released";
			case RELOAD -> "eating.feedback.interrupted-reload";
			case DEATH -> "eating.feedback.interrupted-death";
			case WORLD_CHANGE -> "eating.feedback.interrupted-world-change";
		};
	}

	private boolean matchesActiveItem(Player player, EquipmentSlot hand, ItemStack itemSnapshot) {
		return player.hasActiveItem()
				&& player.getActiveItemHand() == hand
				&& player.getActiveItem().isSimilar(itemSnapshot);
	}

	private int getNativeDurationTicks(Player player) {
		long totalDuration = (long) player.getActiveItemRemainingTime() + player.getActiveItemUsedTime();
		return (int) Math.max(1L, Math.min(totalDuration, Integer.MAX_VALUE));
	}

	private int divideDuration(int baseDurationTicks, double speedMultiplier) {
		double adjustedDuration = Math.ceil(baseDurationTicks / speedMultiplier);
		if (!Double.isFinite(adjustedDuration) || adjustedDuration >= Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}

		return Math.max(1, (int) adjustedDuration);
	}

	private CooldownWindow getActiveCooldown(UUID playerId, long now) {
		CooldownWindow cooldown = cooldownsByPlayer.get(playerId);
		if (cooldown != null && cooldown.endsAtMillis() <= now) {
			cooldownsByPlayer.remove(playerId);
			return null;
		}
		return cooldown;
	}

	private void sendCooldownFeedback(Player player, CooldownWindow cooldown, long now) {
		EatingFeedbackSettings feedback = settings.getFeedbackSettings();
		long elapsedTicks = Integer.toUnsignedLong(plugin.getServer().getCurrentTick() - cooldown.startedAtTick());
		boolean actionBar = feedback.isCooldownActionBarEnabled();
		if (elapsedTicks < feedback.getCooldownMessageDelayTicks()
				|| (actionBar && isProgressFeedbackActive(player.getUniqueId()))
				|| !shouldSendBlockedMessage(player, BlockedMessageReason.COOLDOWN)) {
			return;
		}

		long remainingMillis = cooldown.endsAtMillis() - now;
		Component message = plugin.getLanguageManager().getMessage(
				"eating.cooldown",
				Map.of("seconds", formatSeconds(remainingMillis / 1000.0))
		);
		if (actionBar) {
			int holdTicks = (int) Math.min(feedback.getResultHoldTicks(), millisToTicks(remainingMillis));
			statusBarManager.showEatingFeedback(player, message, holdTicks);
		} else {
			player.sendMessage(message);
		}
	}

	private long getRemainingTicks(Map<UUID, Long> endsByPlayer, UUID playerId, long now) {
		Long end = endsByPlayer.get(playerId);
		if (end == null) {
			return 0L;
		}

		long remainingMillis = end - now;
		if (remainingMillis <= 0L) {
			endsByPlayer.remove(playerId);
			return 0L;
		}

		return millisToTicks(remainingMillis);
	}

	private long millisToTicks(long remainingMillis) {
		long completeTicks = remainingMillis / MILLIS_PER_TICK;
		return remainingMillis % MILLIS_PER_TICK == 0L ? completeTicks : completeTicks + 1L;
	}

	private boolean shouldSendBlockedMessage(Player player, BlockedMessageReason reason) {
		UUID playerId = player.getUniqueId();
		long now = System.currentTimeMillis();
		Long silenceEnd = blockedMessageSilenceEndsByPlayer.get(playerId);
		if (silenceEnd != null) {
			if (silenceEnd > now) {
				return false;
			}
			blockedMessageSilenceEndsByPlayer.remove(playerId);
		}

		long intervalTicks = settings.getFeedbackSettings().getBlockedMessageIntervalTicks();
		if (intervalTicks <= 0L) {
			return true;
		}

		EnumMap<BlockedMessageReason, Long> messageEnds = blockedMessageEndsByPlayer.computeIfAbsent(
				playerId,
				ignored -> new EnumMap<>(BlockedMessageReason.class)
		);
		Long messageEnd = messageEnds.get(reason);
		if (messageEnd != null && messageEnd > now) {
			return false;
		}

		messageEnds.put(reason, calculateEndTime(now, intervalTicks));
		return true;
	}

	private double calculateOverflow(double configuredChange, double availableSpace) {
		return configuredChange <= 0.0 ? 0.0 : Math.max(0.0, configuredChange - availableSpace);
	}

	private void sendOverflowMessages(
			Player player,
			FoodSettings foodSettings,
			double nutritionSpace,
			double fullnessSpace,
			double nutritionOverflow,
			double fullnessOverflow
	) {
		player.sendMessage(plugin.getLanguageManager().getMessage("eating.overflow-header"));
		if (nutritionOverflow > OVERFLOW_EPSILON) {
			player.sendMessage(plugin.getLanguageManager().getMessage(
					"eating.nutrition-overflow",
					Map.of(
							"change", formatValue(foodSettings.getNutritionChange()),
							"space", formatValue(nutritionSpace),
							"overflow", formatValue(nutritionOverflow)
					)
			));
		}
		if (fullnessOverflow > OVERFLOW_EPSILON) {
			player.sendMessage(plugin.getLanguageManager().getMessage(
					"eating.fullness-overflow",
					Map.of(
							"change", formatValue(foodSettings.getFullnessChange()),
							"space", formatValue(fullnessSpace),
							"overflow", formatValue(fullnessOverflow)
					)
			));
		}
		player.sendMessage(plugin.getLanguageManager().getMessage("eating.overflow-hint"));
	}

	private void cancelPendingStart(UUID playerId) {
		PendingEatingStart pendingStart = pendingStartsByPlayer.remove(playerId);
		if (pendingStart != null) {
			pendingStart.getTask().cancel();
		}
	}

	private long calculateEndTime(long now, long ticks) {
		return addWithoutOverflow(now, multiplyWithoutOverflow(ticks, MILLIS_PER_TICK));
	}

	private long multiplyWithoutOverflow(long value, long multiplier) {
		return value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
	}

	private long addWithoutOverflow(long value, long addedValue) {
		return value > Long.MAX_VALUE - addedValue ? Long.MAX_VALUE : value + addedValue;
	}

	private String formatSeconds(double seconds) {
		return String.format(Locale.ROOT, "%.1f", seconds);
	}

	private String formatPercentage(double percentage) {
		return String.format(Locale.ROOT, "%.0f", percentage);
	}

	private String formatValue(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private String formatSignedValue(double value) {
		double normalizedValue = Math.abs(value) <= OVERFLOW_EPSILON ? 0.0 : value;
		return String.format(Locale.ROOT, "%+.2f", normalizedValue);
	}

	private enum BlockedMessageReason {
		COOLDOWN,
		OVERFLOW,
		DATA_LOADING,
		DATA_UNAVAILABLE
	}

	private record CooldownWindow(long endsAtMillis, int startedAtTick) {
	}

	private static final class PendingEatingStart {
		private final BukkitTask task;
		private final EquipmentSlot hand;
		private final ItemStack itemSnapshot;
		private final String foodId;

		private PendingEatingStart(BukkitTask task, EquipmentSlot hand, ItemStack itemSnapshot, String foodId) {
			this.task = task;
			this.hand = hand;
			this.itemSnapshot = itemSnapshot;
			this.foodId = foodId;
		}

		private BukkitTask getTask() {
			return task;
		}

		private EquipmentSlot getHand() {
			return hand;
		}

		private ItemStack getItemSnapshot() {
			return itemSnapshot;
		}

		private String getFoodId() {
			return foodId;
		}
	}

	private static final class EatingSession {
		private final EquipmentSlot hand;
		private final ItemStack itemSnapshot;
		private final FoodSettings foodSettings;
		private final int nativeDurationTicks;
		private final int adjustedDurationTicks;
		private int elapsedTicks;
		private int lastProgressTick = -1;

		private EatingSession(
				EquipmentSlot hand,
				ItemStack itemSnapshot,
				FoodSettings foodSettings,
				int nativeDurationTicks,
				int adjustedDurationTicks,
				int elapsedTicks
		) {
			this.hand = hand;
			this.itemSnapshot = itemSnapshot;
			this.foodSettings = foodSettings;
			this.nativeDurationTicks = nativeDurationTicks;
			this.adjustedDurationTicks = adjustedDurationTicks;
			this.elapsedTicks = elapsedTicks;
		}

		private EquipmentSlot getHand() {
			return hand;
		}

		private ItemStack getItemSnapshot() {
			return itemSnapshot;
		}

		private FoodSettings getFoodSettings() {
			return foodSettings;
		}

		private int getNativeDurationTicks() {
			return nativeDurationTicks;
		}

		private int getAdjustedDurationTicks() {
			return adjustedDurationTicks;
		}

		private int getElapsedTicks() {
			return elapsedTicks;
		}

		private boolean shouldSendProgress(long intervalTicks) {
			return lastProgressTick < 0 || (long) elapsedTicks - lastProgressTick >= intervalTicks;
		}

		private void markProgressSent() {
			lastProgressTick = elapsedTicks;
		}

		private void incrementElapsedTicks() {
			if (elapsedTicks < Integer.MAX_VALUE) {
				elapsedTicks++;
			}
		}
	}
}
