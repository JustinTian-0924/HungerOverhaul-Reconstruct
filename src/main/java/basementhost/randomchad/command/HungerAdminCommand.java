package basementhost.randomchad.command;

import basementhost.randomchad.HungerOverhaul;
import basementhost.randomchad.data.AdminAuditEntry;
import basementhost.randomchad.data.GroupSettings;
import basementhost.randomchad.data.PlayerData;
import basementhost.randomchad.data.StoredPlayerData;
import basementhost.randomchad.lang.LanguageManager;
import basementhost.randomchad.storage.OfflinePlayerDataUpdateResult;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

// Handles administrator changes separately from the basic hunger command.
public final class HungerAdminCommand {
	public static final String ADMIN_PERMISSION = "hungeroverhaul.admin";
	private static final UUID DETACHED_PLAYER_ID = new UUID(0L, 0L);

	private static final Set<String> ADMIN_SUBCOMMANDS = Set.of(
			"set",
			"add",
			"reduce",
			"freeze",
			"unfreeze"
	);
	private static final List<String> VALUE_TARGETS = List.of("nutrition", "fullness", "all");

	private final HungerOverhaul plugin;

	public HungerAdminCommand(HungerOverhaul plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
	}

	public boolean isAdminSubcommand(String subcommand) {
		return ADMIN_SUBCOMMANDS.contains(subcommand.toLowerCase(Locale.ROOT));
	}

	public void execute(CommandSender sender, String label, String[] args) {
		LanguageManager languageManager = plugin.getLanguageManager();
		if (!sender.hasPermission(ADMIN_PERMISSION)) {
			sender.sendMessage(languageManager.getMessage("command.no-permission"));
			return;
		}

		String operation = args[0].toLowerCase(Locale.ROOT);
		boolean freezeOperation = operation.equals("freeze") || operation.equals("unfreeze");
		int expectedArguments = freezeOperation ? 3 : 4;
		if (args.length != expectedArguments) {
			sendUsage(sender, label, freezeOperation);
			return;
		}

		String valueTarget = args[2].toLowerCase(Locale.ROOT);
		if (!VALUE_TARGETS.contains(valueTarget)) {
			sendUsage(sender, label, freezeOperation);
			return;
		}

		double amount = 0.0;
		if (!freezeOperation) {
			Double parsedAmount = readAmount(sender, args[3]);
			if (parsedAmount == null) {
				return;
			}
			amount = parsedAmount;
		}

		AdminAuditEntry.Action change = new AdminAuditEntry.Action(
				sender.getName(),
				getActorType(sender),
				sender instanceof Player player ? player.getUniqueId() : null,
				operation,
				valueTarget,
				freezeOperation ? null : amount
		);
		Player targetPlayer = findOnlinePlayer(args[1]);
		if (targetPlayer != null) {
			executeOnlineChange(sender, targetPlayer, change);
			return;
		}

		executeOfflineChange(sender, args[1], change);
	}

	private void executeOnlineChange(CommandSender sender, Player targetPlayer, AdminAuditEntry.Action change) {
		LanguageManager languageManager = plugin.getLanguageManager();
		if (plugin.getPersistenceManager().isPlayerDataLoading(targetPlayer.getUniqueId())) {
			sender.sendMessage(languageManager.getMessage(
					"command.admin.loading",
					Map.of("player", targetPlayer.getName())
			));
			return;
		}

		PlayerData playerData = plugin.getPlayerDataManager()
				.findPlayerData(targetPlayer)
				.orElse(null);
		if (playerData == null) {
			sender.sendMessage(languageManager.getMessage(
					"command.admin.unavailable",
					Map.of("player", targetPlayer.getName())
			));
			return;
		}

		plugin.getPlayerDataManager().refreshGroupSettings(targetPlayer);
		AdminAuditEntry.Values before = AdminAuditEntry.Values.from(playerData);
		applyChange(playerData, change);
		plugin.getPersistenceManager().savePlayerData(targetPlayer);
		plugin.getPersistenceManager().recordOnlineAdminChange(new AdminAuditEntry(
				change,
				targetPlayer.getUniqueId(),
				targetPlayer.getName(),
				AdminAuditEntry.TargetMode.ONLINE,
				before,
				AdminAuditEntry.Values.from(playerData)
		));
		sendResult(sender, AdminResultSnapshot.from(targetPlayer.getName(), playerData));
	}

	private void executeOfflineChange(CommandSender sender, String requestedName, AdminAuditEntry.Action change) {
		OfflinePlayer cachedProfile = findKnownOfflinePlayer(requestedName);
		String displayedName = cachedProfile == null
				? requestedName
				: getOfflinePlayerName(cachedProfile, requestedName);
		sender.sendMessage(plugin.getLanguageManager().getMessage(
				"command.admin.offline-loading",
				Map.of("player", displayedName)
		));

		UUID playerId = cachedProfile != null
				? cachedProfile.getUniqueId()
				: plugin.getPersistenceManager().findCachedOfflinePlayerIdByName(requestedName).orElse(null);
		if (playerId != null) {
			executeResolvedOfflineChange(sender, playerId, displayedName, change);
			return;
		}

		plugin.getPersistenceManager().queryPlayerDataReadOnly(requestedName, result -> {
			switch (result.getStatus()) {
				case FOUND -> executeResolvedOfflineChange(
						sender,
						result.getPlayerId().orElseThrow(),
						result.getPlayerData().orElseThrow().getLastKnownName(),
						change
				);
				case NOT_FOUND -> sendOfflineMessage(sender, "command.admin.player-not-found", requestedName);
				case CORRUPT -> sendOfflineMessage(sender, "command.admin.offline-data-corrupt", requestedName);
				case FAILED, UNAVAILABLE -> sendOfflineMessage(sender, "command.admin.offline-read-failure", requestedName);
			}
		});
	}

	private void executeResolvedOfflineChange(
			CommandSender sender,
			UUID playerId,
			String playerName,
			AdminAuditEntry.Action change
	) {
		// Name lookup can finish after the target joins or enters the TTL cache.
		Player onlinePlayer = plugin.getServer().getPlayer(playerId);
		if (onlinePlayer != null && onlinePlayer.isOnline()) {
			executeOnlineChange(sender, onlinePlayer, change);
			return;
		}
		if (plugin.getPersistenceManager().isPlayerDataLoading(playerId)) {
			sendOfflineMessage(sender, "command.admin.loading", playerName);
			return;
		}

		PlayerData cachedPlayerData = plugin.getPlayerDataManager().findPlayerData(playerId).orElse(null);
		if (cachedPlayerData != null) {
			executeCachedOfflineChange(sender, playerId, playerName, cachedPlayerData, change);
			return;
		}

		OfflineDefaults defaults = getOfflineDefaults();
		plugin.getPersistenceManager().updateOfflinePlayerData(
				playerId,
				storedData -> updateStoredPlayerData(storedData, change, defaults),
				change,
				result -> finishStoredOfflineChange(sender, playerName, result)
		);
	}

	private void executeCachedOfflineChange(
			CommandSender sender,
			UUID playerId,
			String playerName,
			PlayerData playerData,
			AdminAuditEntry.Action change
	) {
		AdminAuditEntry.Values before = AdminAuditEntry.Values.from(playerData);
		applyChange(playerData, change);
		AdminResultSnapshot resultSnapshot = AdminResultSnapshot.from(playerName, playerData);
		AdminAuditEntry auditEntry = new AdminAuditEntry(
				change,
				playerId,
				playerName,
				AdminAuditEntry.TargetMode.OFFLINE_CACHE,
				before,
				AdminAuditEntry.Values.from(playerData)
		);
		boolean queued = plugin.getPersistenceManager().saveCachedOfflinePlayerData(
				auditEntry,
				saved -> finishOfflineSave(sender, resultSnapshot, saved)
		);
		if (!queued) {
			sendOfflineSaveFailure(sender, playerName);
		}
	}

	private void finishStoredOfflineChange(
			CommandSender sender,
			String requestedName,
			OfflinePlayerDataUpdateResult result
	) {
		if (!isRecipientAvailable(sender)) {
			return;
		}

		String messageKey = switch (result.getStatus()) {
			case UPDATED -> null;
			case NOT_FOUND -> "command.admin.offline-data-not-found";
			case CORRUPT -> "command.admin.offline-data-corrupt";
			case READ_FAILED, UNAVAILABLE -> "command.admin.offline-read-failure";
			case WRITE_FAILED -> "command.admin.offline-save-failure";
		};
		if (messageKey != null) {
			sendOfflineMessage(sender, messageKey, requestedName);
			return;
		}

		StoredPlayerData updatedData = result.getPlayerData().orElseThrow();
		sendResult(sender, AdminResultSnapshot.from(updatedData));
	}

	private static StoredPlayerData updateStoredPlayerData(
			StoredPlayerData storedData,
			AdminAuditEntry.Action change,
			OfflineDefaults defaults
	) {
		String groupId = storedData.getLastKnownGroupId()
				.orElse(defaults.groupId());
		// Legacy v1 files have no maximum snapshot, so preserve any existing value above today's default.
		double maxNutrition = storedData.getMaxNutrition().orElse(
				Math.max(defaults.maxNutrition(), storedData.getNutrition())
		);
		double maxFullness = storedData.getMaxFullness().orElse(
				Math.max(defaults.maxFullness(), storedData.getFullness())
		);
		PlayerData detachedPlayerData = new PlayerData(
				DETACHED_PLAYER_ID,
				maxNutrition,
				maxFullness,
				storedData.getNutrition(),
				storedData.getFullness()
		);
		detachedPlayerData.setNutritionFrozen(storedData.isNutritionFrozen());
		detachedPlayerData.setFullnessFrozen(storedData.isFullnessFrozen());
		applyChange(detachedPlayerData, change);

		return new StoredPlayerData(
				storedData.getLastKnownName(),
				groupId,
				maxNutrition,
				maxFullness,
				detachedPlayerData.getNutrition(),
				detachedPlayerData.getFullness(),
				detachedPlayerData.isNutritionFrozen(),
				detachedPlayerData.isFullnessFrozen(),
				storedData.getLastSeen()
		);
	}

	private OfflineDefaults getOfflineDefaults() {
		GroupSettings settings = plugin.getGroupManager().getDefaultGroup().getSettings();
		return new OfflineDefaults(
				plugin.getGroupManager().getDefaultGroup().getId(),
				settings.getMaxNutrition(),
				settings.getMaxFullness()
		);
	}

	private void finishOfflineSave(
			CommandSender sender,
			AdminResultSnapshot resultSnapshot,
			boolean saved
	) {
		if (!isRecipientAvailable(sender)) {
			return;
		}

		if (!saved) {
			sendOfflineMessage(sender, "command.admin.offline-cache-save-failure", resultSnapshot.playerName());
			return;
		}

		sendResult(sender, resultSnapshot);
	}

	private void sendOfflineSaveFailure(CommandSender sender, String playerName) {
		sendOfflineMessage(sender, "command.admin.offline-save-failure", playerName);
	}

	private void sendOfflineMessage(CommandSender sender, String messageKey, String playerName) {
		if (!isRecipientAvailable(sender)) {
			return;
		}
		sender.sendMessage(plugin.getLanguageManager().getMessage(
				messageKey,
				Map.of("player", playerName)
		));
	}

	private static void applyChange(PlayerData playerData, AdminAuditEntry.Action change) {
		if (change.amount() == null) {
			setFrozen(playerData, change.valueTarget(), change.operation().equals("freeze"));
			return;
		}

		changeValue(playerData, change.operation(), change.valueTarget(), change.amount());
	}

	public List<String> tabComplete(CommandSender sender, String[] args) {
		if (!sender.hasPermission(ADMIN_PERMISSION)) {
			return List.of();
		}

		if (args.length == 2) {
			String typedText = args[1].toLowerCase(Locale.ROOT);
			List<String> playerNames = new ArrayList<>();
			for (Player player : plugin.getServer().getOnlinePlayers()) {
				if (player.getName().toLowerCase(Locale.ROOT).startsWith(typedText)) {
					playerNames.add(player.getName());
				}
			}

			playerNames.sort(String.CASE_INSENSITIVE_ORDER);
			return List.copyOf(playerNames);
		}

		if (args.length == 3) {
			return matchingSuggestions(VALUE_TARGETS, args[2]);
		}

		return List.of();
	}

	private Double readAmount(CommandSender sender, String input) {
		double amount;
		try {
			amount = Double.parseDouble(input);
		} catch (NumberFormatException exception) {
			sendInvalidNumber(sender, input);
			return null;
		}

		if (!Double.isFinite(amount)) {
			sendInvalidNumber(sender, input);
			return null;
		}

		if (amount < 0.0) {
			sender.sendMessage(plugin.getLanguageManager().getMessage("command.admin.negative-number"));
			return null;
		}

		return amount;
	}

	private void sendInvalidNumber(CommandSender sender, String input) {
		sender.sendMessage(plugin.getLanguageManager().getMessage(
				"command.admin.invalid-number",
				Map.of("value", input)
		));
	}

	private static void changeValue(PlayerData playerData, String operation, String valueTarget, double amount) {
		if (valueTarget.equals("nutrition") || valueTarget.equals("all")) {
			changeNutrition(playerData, operation, amount);
		}

		if (valueTarget.equals("fullness") || valueTarget.equals("all")) {
			changeFullness(playerData, operation, amount);
		}
	}

	private static void changeNutrition(PlayerData playerData, String operation, double amount) {
		switch (operation) {
			case "set" -> playerData.setNutrition(amount);
			case "add" -> playerData.addNutrition(amount);
			case "reduce" -> playerData.removeNutrition(amount);
			default -> throw new IllegalArgumentException("Unknown value operation: " + operation);
		}
	}

	private static void changeFullness(PlayerData playerData, String operation, double amount) {
		switch (operation) {
			case "set" -> playerData.setFullness(amount);
			case "add" -> playerData.addFullness(amount);
			case "reduce" -> playerData.removeFullness(amount);
			default -> throw new IllegalArgumentException("Unknown value operation: " + operation);
		}
	}

	private static void setFrozen(PlayerData playerData, String valueTarget, boolean frozen) {
		if (valueTarget.equals("nutrition") || valueTarget.equals("all")) {
			playerData.setNutritionFrozen(frozen);
		}

		if (valueTarget.equals("fullness") || valueTarget.equals("all")) {
			playerData.setFullnessFrozen(frozen);
		}
	}

	private void sendResult(CommandSender sender, AdminResultSnapshot result) {
		LanguageManager languageManager = plugin.getLanguageManager();
		sender.sendMessage(languageManager.getMessage(
				"command.admin.success",
				Map.of("player", result.playerName())
		));
		sender.sendMessage(languageManager.getMessage(
				"command.status.nutrition",
				Map.of(
						"current", formatValue(result.nutrition()),
						"maximum", formatValue(result.maxNutrition()),
						"percentage", formatPercentage(result.nutritionPercentage())
				)
		));
		sender.sendMessage(languageManager.getMessage(
				"command.status.fullness",
				Map.of(
						"current", formatValue(result.fullness()),
						"maximum", formatValue(result.maxFullness()),
						"percentage", formatPercentage(result.fullnessPercentage())
				)
		));
		sender.sendMessage(languageManager.getMessage(
				result.nutritionFrozen()
						? "command.status.nutrition-frozen"
						: "command.status.nutrition-active"
		));
		sender.sendMessage(languageManager.getMessage(
				result.fullnessFrozen()
						? "command.status.fullness-frozen"
						: "command.status.fullness-active"
		));
	}

	private Player findOnlinePlayer(String playerName) {
		return plugin.getServer().getOnlinePlayers().stream()
				.filter(player -> player.getName().equalsIgnoreCase(playerName))
				.findFirst()
				.orElse(null);
	}

	private OfflinePlayer findKnownOfflinePlayer(String playerName) {
		OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayerIfCached(playerName);
		if (offlinePlayer == null) {
			return null;
		}

		String knownName = offlinePlayer.getName();
		return knownName != null && knownName.equalsIgnoreCase(playerName)
				? offlinePlayer
				: null;
	}

	private String getOfflinePlayerName(OfflinePlayer offlinePlayer, String fallback) {
		String playerName = offlinePlayer.getName();
		return playerName == null || playerName.isBlank() ? fallback : playerName;
	}

	private boolean isRecipientAvailable(CommandSender recipient) {
		return !(recipient instanceof Player player) || player.isOnline();
	}

	private static String getActorType(CommandSender sender) {
		return switch (sender) {
			case Player player -> "player";
			case RemoteConsoleCommandSender remoteConsole -> "remote_console";
			case ConsoleCommandSender console -> "console";
			case BlockCommandSender commandBlock -> "command_block";
			default -> "other";
		};
	}

	private void sendUsage(CommandSender sender, String label, boolean freezeOperation) {
		String messageKey = freezeOperation
				? "command.admin.freeze-usage"
				: "command.admin.value-usage";
		sender.sendMessage(plugin.getLanguageManager().getMessage(
				messageKey,
				Map.of("label", label)
		));
	}

	private List<String> matchingSuggestions(List<String> options, String input) {
		String typedText = input.toLowerCase(Locale.ROOT);
		return options.stream()
				.filter(option -> option.startsWith(typedText))
				.toList();
	}

	private String formatValue(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private String formatPercentage(double percentage) {
		return String.format(Locale.ROOT, "%.1f", percentage);
	}

	private record OfflineDefaults(String groupId, double maxNutrition, double maxFullness) {
	}

	private record AdminResultSnapshot(
			String playerName,
			double nutrition,
			double maxNutrition,
			double fullness,
			double maxFullness,
			boolean nutritionFrozen,
			boolean fullnessFrozen
	) {
		private static AdminResultSnapshot from(String playerName, PlayerData playerData) {
			return new AdminResultSnapshot(
					playerName,
					playerData.getNutrition(),
					playerData.getMaxNutrition(),
					playerData.getFullness(),
					playerData.getMaxFullness(),
					playerData.isNutritionFrozen(),
					playerData.isFullnessFrozen()
			);
		}

		private static AdminResultSnapshot from(StoredPlayerData playerData) {
			return new AdminResultSnapshot(
					playerData.getLastKnownName(),
					playerData.getNutrition(),
					playerData.getMaxNutrition().orElseThrow(),
					playerData.getFullness(),
					playerData.getMaxFullness().orElseThrow(),
					playerData.isNutritionFrozen(),
					playerData.isFullnessFrozen()
			);
		}

		private double nutritionPercentage() {
			return nutrition / maxNutrition * 100.0;
		}

		private double fullnessPercentage() {
			return fullness / maxFullness * 100.0;
		}
	}
}
