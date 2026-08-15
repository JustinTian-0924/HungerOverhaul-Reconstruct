package basementhost.randomchad.command;

import basementhost.randomchad.HungerOverhaul;
import basementhost.randomchad.data.PlayerData;
import basementhost.randomchad.lang.LanguageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// Handles administrator changes separately from the basic hunger command.
public final class HungerAdminCommand {
	public static final String ADMIN_PERMISSION = "hungeroverhaul.admin";

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

		Player targetPlayer = plugin.getServer().getPlayerExact(args[1]);
		if (targetPlayer == null) {
			sender.sendMessage(languageManager.getMessage(
					"command.admin.player-not-found",
					Map.of("player", args[1])
			));
			return;
		}

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

		String valueTarget = args[2].toLowerCase(Locale.ROOT);
		if (!VALUE_TARGETS.contains(valueTarget)) {
			sendUsage(sender, label, freezeOperation);
			return;
		}

		plugin.getPlayerDataManager().refreshGroupSettings(targetPlayer);
		if (freezeOperation) {
			setFrozen(playerData, valueTarget, operation.equals("freeze"));
		} else {
			Double amount = readAmount(sender, args[3]);
			if (amount == null) {
				return;
			}

			changeValue(playerData, operation, valueTarget, amount);
		}

		plugin.getPersistenceManager().savePlayerData(targetPlayer);
		sendResult(sender, targetPlayer, playerData);
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

	private void changeValue(PlayerData playerData, String operation, String valueTarget, double amount) {
		if (valueTarget.equals("nutrition") || valueTarget.equals("all")) {
			changeNutrition(playerData, operation, amount);
		}

		if (valueTarget.equals("fullness") || valueTarget.equals("all")) {
			changeFullness(playerData, operation, amount);
		}
	}

	private void changeNutrition(PlayerData playerData, String operation, double amount) {
		switch (operation) {
			case "set" -> playerData.setNutrition(amount);
			case "add" -> playerData.addNutrition(amount);
			case "reduce" -> playerData.removeNutrition(amount);
			default -> throw new IllegalArgumentException("Unknown value operation: " + operation);
		}
	}

	private void changeFullness(PlayerData playerData, String operation, double amount) {
		switch (operation) {
			case "set" -> playerData.setFullness(amount);
			case "add" -> playerData.addFullness(amount);
			case "reduce" -> playerData.removeFullness(amount);
			default -> throw new IllegalArgumentException("Unknown value operation: " + operation);
		}
	}

	private void setFrozen(PlayerData playerData, String valueTarget, boolean frozen) {
		if (valueTarget.equals("nutrition") || valueTarget.equals("all")) {
			playerData.setNutritionFrozen(frozen);
		}

		if (valueTarget.equals("fullness") || valueTarget.equals("all")) {
			playerData.setFullnessFrozen(frozen);
		}
	}

	private void sendResult(CommandSender sender, Player targetPlayer, PlayerData playerData) {
		LanguageManager languageManager = plugin.getLanguageManager();
		sender.sendMessage(languageManager.getMessage(
				"command.admin.success",
				Map.of("player", targetPlayer.getName())
		));
		sender.sendMessage(languageManager.getMessage(
				"command.status.nutrition",
				Map.of(
						"current", formatValue(playerData.getNutrition()),
						"maximum", formatValue(playerData.getMaxNutrition()),
						"percentage", formatPercentage(playerData.getNutritionPercentage())
				)
		));
		sender.sendMessage(languageManager.getMessage(
				"command.status.fullness",
				Map.of(
						"current", formatValue(playerData.getFullness()),
						"maximum", formatValue(playerData.getMaxFullness()),
						"percentage", formatPercentage(playerData.getFullnessPercentage())
				)
		));
		sender.sendMessage(languageManager.getMessage(
				playerData.isNutritionFrozen()
						? "command.status.nutrition-frozen"
						: "command.status.nutrition-active"
		));
		sender.sendMessage(languageManager.getMessage(
				playerData.isFullnessFrozen()
						? "command.status.fullness-frozen"
						: "command.status.fullness-active"
		));
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
}
