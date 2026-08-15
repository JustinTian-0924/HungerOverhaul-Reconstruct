package basementhost.randomchad.command;

import basementhost.randomchad.HungerOverhaul;
import basementhost.randomchad.data.DeathLifecycleSettings;
import basementhost.randomchad.data.EatingStatusSnapshot;
import basementhost.randomchad.data.HealthRegenerationGroupSettings;
import basementhost.randomchad.data.LifecycleValueRule;
import basementhost.randomchad.data.PlayerData;
import basementhost.randomchad.data.PlayerLifecycleSettings;
import basementhost.randomchad.data.RespawnLifecycleSettings;
import basementhost.randomchad.data.StatusEffectDefinition;
import basementhost.randomchad.data.StatusEffectsConfiguration;
import basementhost.randomchad.data.WorldChangeLifecycleSettings;
import basementhost.randomchad.lang.LanguageManager;
import basementhost.randomchad.manager.HealthRegenerationManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HungerOverhaulCommand implements CommandExecutor, TabCompleter {
	private static final String RELOAD_PERMISSION = "hungeroverhaul.reload";

	private final HungerOverhaul plugin;
	private final HungerAdminCommand adminCommand;

	public HungerOverhaulCommand(HungerOverhaul plugin) {
		this.plugin = plugin;
		this.adminCommand = new HungerAdminCommand(plugin);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 0) {
			sendPluginInformation(sender, label);
			return true;
		}

		if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
			reloadPlugin(sender);
			return true;
		}

		if (args.length >= 1 && args[0].equalsIgnoreCase("status")) {
			StatusViewMode viewMode = readStatusViewMode(args);
			if (viewMode == null) {
				sender.sendMessage(plugin.getLanguageManager().getMessage(
						"command.status.usage",
						Map.of("label", label)
				));
				return true;
			}

			sendPlayerStatus(sender, viewMode);
			return true;
		}

		if (adminCommand.isAdminSubcommand(args[0])) {
			adminCommand.execute(sender, label, args);
			return true;
		}

		sender.sendMessage(plugin.getLanguageManager().getMessage(
				"command.usage",
				Map.of("label", label)
		));
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 2 && args[0].equalsIgnoreCase("status")) {
			return matchingSuggestions(List.of("compact", "detail"), args[1]);
		}

		if (args.length > 1 && adminCommand.isAdminSubcommand(args[0])) {
			return adminCommand.tabComplete(sender, args);
		}

		if (args.length != 1) {
			return List.of();
		}

		String typedText = args[0].toLowerCase(Locale.ROOT);
		List<String> suggestions = new ArrayList<>();
		if ("status".startsWith(typedText)) {
			suggestions.add("status");
		}
		if (sender.hasPermission(RELOAD_PERMISSION) && "reload".startsWith(typedText)) {
			suggestions.add("reload");
		}
		if (sender.hasPermission(HungerAdminCommand.ADMIN_PERMISSION)) {
			for (String adminSubcommand : List.of("set", "add", "reduce", "freeze", "unfreeze")) {
				if (adminSubcommand.startsWith(typedText)) {
					suggestions.add(adminSubcommand);
				}
			}
		}
		return List.copyOf(suggestions);
	}

	private void sendPluginInformation(CommandSender sender, String label) {
		LanguageManager languageManager = plugin.getLanguageManager();
		sender.sendMessage(languageManager.getMessage(
				"command.info.header",
				Map.of("name", plugin.getPluginMeta().getName())
		));
		sender.sendMessage(languageManager.getMessage(
				"command.info.version",
				Map.of("version", plugin.getPluginMeta().getVersion())
		));
		sender.sendMessage(languageManager.getMessage(
				"command.info.author",
				Map.of("author", String.join(", ", plugin.getPluginMeta().getAuthors()))
		));
		sender.sendMessage(languageManager.getMessage(
				"command.info.language",
				Map.of("language", languageManager.getCurrentLanguageName())
		));
		sender.sendMessage(languageManager.getMessage("command.info.status"));
		sender.sendMessage(languageManager.getMessage(
				"command.info.status-hint",
				Map.of("label", label)
		));
		if (sender.hasPermission(RELOAD_PERMISSION)) {
			sender.sendMessage(languageManager.getMessage(
					"command.info.reload-hint",
					Map.of("label", label)
			));
		}
	}

	private void sendPlayerStatus(CommandSender sender, StatusViewMode viewMode) {
		LanguageManager languageManager = plugin.getLanguageManager();
		if (!(sender instanceof Player player)) {
			sender.sendMessage(languageManager.getMessage("command.status.player-only"));
			return;
		}

		if (plugin.getPersistenceManager().isPlayerDataLoading(player.getUniqueId())) {
			player.sendMessage(languageManager.getMessage("command.status.loading"));
			return;
		}

		PlayerData playerData = plugin.getPlayerDataManager()
				.findPlayerData(player)
				.orElse(null);
		if (playerData == null) {
			player.sendMessage(languageManager.getMessage("command.status.unavailable"));
			return;
		}

		plugin.getPlayerDataManager().refreshGroupSettings(player);
		String groupId = plugin.getGroupManager().getGroup(player).getId();

		player.sendMessage(languageManager.getMessage(
				"command.status.header",
				Map.of("player", player.getName())
		));
		player.sendMessage(languageManager.getMessage(
				"command.status.group",
				Map.of("group", groupId)
		));
		player.sendMessage(languageManager.getMessage(
				"command.status.nutrition",
				Map.of(
						"current", formatValue(playerData.getNutrition()),
						"maximum", formatValue(playerData.getMaxNutrition()),
						"percentage", formatPercentage(playerData.getNutritionPercentage())
				)
		));
		player.sendMessage(languageManager.getMessage(
				"command.status.fullness",
				Map.of(
						"current", formatValue(playerData.getFullness()),
						"maximum", formatValue(playerData.getMaxFullness()),
						"percentage", formatPercentage(playerData.getFullnessPercentage())
				)
		));
		player.sendMessage(languageManager.getMessage(
				playerData.isNutritionFrozen()
						? "command.status.nutrition-frozen"
						: "command.status.nutrition-active"
		));
		player.sendMessage(languageManager.getMessage(
				playerData.isFullnessFrozen()
						? "command.status.fullness-frozen"
						: "command.status.fullness-active"
		));

		if (viewMode == StatusViewMode.DETAIL) {
			sendDetailedEffects(player, playerData, languageManager);
			sendDetailedHealthRegeneration(player, playerData, languageManager);
			sendDetailedEating(player, languageManager);
			sendDetailedLifecycle(player, languageManager);
		}
	}

	private void sendDetailedEffects(Player player, PlayerData playerData, LanguageManager languageManager) {
		StatusEffectsConfiguration configuration = plugin.getStatusEffectManager().getConfiguration();
		if (!configuration.isEnabled()) {
			player.sendMessage(languageManager.getMessage("command.status.effects-disabled"));
			return;
		}

		StatusEffectDefinition nutritionEffect = configuration.findNutritionEffect(
				playerData.getNutritionPercentage()
		);
		StatusEffectDefinition fullnessEffect = configuration.findFullnessEffect(
				playerData.getFullnessPercentage()
		);
		player.sendMessage(languageManager.getMessage(
				"command.status.nutrition-effect",
				Map.of(
						"status", getEffectName(languageManager, "nutrition", nutritionEffect),
						"minimum", formatPercentage(nutritionEffect.getMinimumPercentage()),
						"maximum", formatPercentage(nutritionEffect.getMaximumPercentage())
				)
		));
		player.sendMessage(languageManager.getMessage(
				"command.status.fullness-effect",
				Map.of(
						"status", getEffectName(languageManager, "fullness", fullnessEffect),
						"minimum", formatPercentage(fullnessEffect.getMinimumPercentage()),
						"maximum", formatPercentage(fullnessEffect.getMaximumPercentage())
				)
		));
		player.sendMessage(languageManager.getMessage(
				"command.status.decay-multipliers",
				Map.of(
						"nutrition", formatMultiplier(nutritionEffect.getDecayMultiplier()),
						"fullness", formatMultiplier(fullnessEffect.getDecayMultiplier())
				)
		));

		double movementSpeedModifier = configuration.clampAttributeModifier(
				nutritionEffect.getMovementSpeedModifier() + fullnessEffect.getMovementSpeedModifier()
		);
		double blockBreakSpeedModifier = configuration.clampAttributeModifier(
				nutritionEffect.getBlockBreakSpeedModifier() + fullnessEffect.getBlockBreakSpeedModifier()
		);
		double attackDamageModifier = configuration.clampAttributeModifier(
				nutritionEffect.getAttackDamageModifier() + fullnessEffect.getAttackDamageModifier()
		);
		player.sendMessage(languageManager.getMessage(
				"command.status.attribute-modifiers",
				Map.of(
						"movement", formatSignedPercentage(movementSpeedModifier),
						"mining", formatSignedPercentage(blockBreakSpeedModifier),
						"attack", formatSignedPercentage(attackDamageModifier)
				)
		));
	}

	private void sendDetailedHealthRegeneration(
			Player player,
			PlayerData playerData,
			LanguageManager languageManager
	) {
		HealthRegenerationManager regenerationManager = plugin.getHealthRegenerationManager();
		if (!regenerationManager.isEnabled()) {
			player.sendMessage(languageManager.getMessage("command.status.health-regeneration-disabled"));
			return;
		}

		HealthRegenerationGroupSettings groupSettings = plugin.getGroupManager()
				.getSettings(player)
				.getHealthRegenerationSettings();
		double regenerationMultiplier = regenerationManager.getRegenerationMultiplier(playerData);
		double adjustedHeal = regenerationManager.getAdjustedHealHpPerCycle(player, playerData);
		player.sendMessage(languageManager.getMessage(
				"command.status.health-regeneration",
				Map.of(
						"base", formatValue(groupSettings.getHealHpPerCycle()),
						"adjusted", formatValue(adjustedHeal),
						"multiplier", formatMultiplier(regenerationMultiplier),
						"interval", regenerationManager.getSettings().getIntervalTicks()
				)
		));
		player.sendMessage(languageManager.getMessage(
				"command.status.health-regeneration-cost",
				Map.of(
						"nutrition", formatValue(groupSettings.getNutritionCost()),
						"fullness", formatValue(groupSettings.getFullnessCost()),
						"threshold", formatValue(
								regenerationManager.getSettings().getMinimumChargedHealHp()
						)
				)
		));
	}

	private void sendDetailedEating(Player player, LanguageManager languageManager) {
		EatingStatusSnapshot status = plugin.getEatingManager().getStatus(player);
		player.sendMessage(languageManager.getMessage("command.status.eating-header"));
		switch (status.getActivity()) {
			case IDLE -> player.sendMessage(languageManager.getMessage("command.status.eating-idle"));
			case STARTING -> player.sendMessage(languageManager.getMessage(
					"command.status.eating-starting",
					Map.of("food", status.getFoodId())
			));
			case EATING -> player.sendMessage(languageManager.getMessage(
					"command.status.eating-active",
					Map.of(
							"food", status.getFoodId(),
							"percentage", formatPercentage(status.getProgressPercentage()),
							"ticks", status.getEatingRemainingTicks(),
							"seconds", formatTickDuration(status.getEatingRemainingTicks())
					)
			));
		}

		if (status.getDigestionRemainingTicks() > 0L) {
			player.sendMessage(languageManager.getMessage(
					"command.status.digestion-active",
					Map.of(
							"ticks", status.getDigestionRemainingTicks(),
							"seconds", formatTickDuration(status.getDigestionRemainingTicks())
					)
			));
		} else {
			player.sendMessage(languageManager.getMessage("command.status.digestion-ready"));
		}

		if (status.getBlockedMessageSilenceRemainingTicks() > 0L) {
			player.sendMessage(languageManager.getMessage(
					"command.status.blocked-message-silence-active",
					Map.of(
							"ticks", status.getBlockedMessageSilenceRemainingTicks(),
							"seconds", formatTickDuration(status.getBlockedMessageSilenceRemainingTicks())
					)
			));
		} else {
			player.sendMessage(languageManager.getMessage("command.status.blocked-message-silence-inactive"));
		}
	}

	private void sendDetailedLifecycle(Player player, LanguageManager languageManager) {
		PlayerLifecycleSettings lifecycleSettings = plugin.getPlayerLifecycleManager().getSettings();
		DeathLifecycleSettings deathSettings = lifecycleSettings.getDeathSettings();
		RespawnLifecycleSettings respawnSettings = lifecycleSettings.getRespawnSettings();
		WorldChangeLifecycleSettings worldSettings = lifecycleSettings.getWorldChangeSettings();

		player.sendMessage(languageManager.getMessage("command.status.lifecycle-header"));
		player.sendMessage(languageManager.getMessage(
				"command.status.lifecycle-death",
				Map.of(
						"cancel", formatYesNo(languageManager, deathSettings.isCancelEating()),
						"cooldown", formatPreserveClear(
								languageManager,
								deathSettings.isPreserveDigestionCooldown()
						),
						"save", formatYesNo(languageManager, deathSettings.isSaveData())
				)
		));
		player.sendMessage(languageManager.getMessage(
				"command.status.lifecycle-respawn",
				Map.of(
						"nutrition", formatLifecycleRule(languageManager, respawnSettings.getNutritionRule()),
						"fullness", formatLifecycleRule(languageManager, respawnSettings.getFullnessRule()),
						"frozen", formatFrozenHandling(
								languageManager,
								respawnSettings.isAffectFrozenValues()
						),
						"save", formatYesNo(languageManager, respawnSettings.isSaveData())
				)
		));
		player.sendMessage(languageManager.getMessage(
				"command.status.lifecycle-world-change",
				Map.of(
						"cancel", formatYesNo(languageManager, worldSettings.isCancelEating()),
						"cooldown", formatPreserveClear(
								languageManager,
								worldSettings.isPreserveDigestionCooldown()
						),
						"refresh", formatYesNo(languageManager, worldSettings.isRefreshGroup()),
						"save", formatYesNo(languageManager, worldSettings.isSaveData())
				)
		));
		player.sendMessage(languageManager.getMessage(
				"command.status.lifecycle-world-values",
				Map.of(
						"nutrition", formatLifecycleRule(languageManager, worldSettings.getNutritionRule()),
						"fullness", formatLifecycleRule(languageManager, worldSettings.getFullnessRule()),
						"frozen", formatFrozenHandling(
								languageManager,
								worldSettings.isAffectFrozenValues()
						)
				)
		));
	}

	private String formatLifecycleRule(LanguageManager languageManager, LifecycleValueRule rule) {
		String key = switch (rule.getMode()) {
			case KEEP -> "command.status.lifecycle-mode.keep";
			case MINIMUM_PERCENTAGE -> "command.status.lifecycle-mode.minimum-percentage";
			case SET_PERCENTAGE -> "command.status.lifecycle-mode.set-percentage";
			case GROUP_INITIAL -> "command.status.lifecycle-mode.group-initial";
			case PRESERVE_PERCENTAGE -> "command.status.lifecycle-mode.preserve-percentage";
		};
		String fallback = switch (rule.getMode()) {
			case KEEP -> "keep";
			case MINIMUM_PERCENTAGE -> "minimum {percentage}%";
			case SET_PERCENTAGE -> "set to {percentage}%";
			case GROUP_INITIAL -> "group initial";
			case PRESERVE_PERCENTAGE -> "preserve percentage";
		};
		String formattedRule = languageManager.getTextOrDefault(key, fallback);
		if (rule.getMode().requiresPercentage()) {
			formattedRule = formattedRule.replace("{percentage}", formatPercentage(rule.getPercentage()));
		}
		return formattedRule;
	}

	private String formatYesNo(LanguageManager languageManager, boolean value) {
		return languageManager.getTextOrDefault(
				value ? "command.status.term.yes" : "command.status.term.no",
				value ? "yes" : "no"
		);
	}

	private String formatPreserveClear(LanguageManager languageManager, boolean preserve) {
		return languageManager.getTextOrDefault(
				preserve ? "command.status.term.preserve" : "command.status.term.clear",
				preserve ? "preserve" : "clear"
		);
	}

	private String formatFrozenHandling(LanguageManager languageManager, boolean affected) {
		return languageManager.getTextOrDefault(
				affected ? "command.status.term.affected" : "command.status.term.protected",
				affected ? "affected" : "protected"
		);
	}

	private StatusViewMode readStatusViewMode(String[] args) {
		if (args.length == 1) {
			return StatusViewMode.COMPACT;
		}

		if (args.length != 2) {
			return null;
		}

		return switch (args[1].toLowerCase(Locale.ROOT)) {
			case "compact" -> StatusViewMode.COMPACT;
			case "detail" -> StatusViewMode.DETAIL;
			default -> null;
		};
	}

	private String getEffectName(
			LanguageManager languageManager,
			String valueType,
			StatusEffectDefinition effect
	) {
		return languageManager.getTextOrDefault(
				"status-effect." + valueType + "." + effect.getId(),
				effect.getId()
		);
	}

	private String formatValue(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private String formatPercentage(double percentage) {
		return String.format(Locale.ROOT, "%.1f", percentage);
	}

	private String formatMultiplier(double multiplier) {
		return String.format(Locale.ROOT, "%.2f", multiplier);
	}

	private String formatSignedPercentage(double modifier) {
		return String.format(Locale.ROOT, "%+.1f%%", modifier * 100.0);
	}

	private String formatTickDuration(long ticks) {
		return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
	}

	private List<String> matchingSuggestions(List<String> options, String input) {
		String typedText = input.toLowerCase(Locale.ROOT);
		return options.stream()
				.filter(option -> option.startsWith(typedText))
				.toList();
	}

	private void reloadPlugin(CommandSender sender) {
		if (!sender.hasPermission(RELOAD_PERMISSION)) {
			sender.sendMessage(plugin.getLanguageManager().getMessage("command.no-permission"));
			return;
		}

		if (plugin.reloadPluginSettings()) {
			sender.sendMessage(plugin.getLanguageManager().getMessage("command.reload-success"));
			return;
		}

		sender.sendMessage(plugin.getLanguageManager().getMessage("command.reload-failure"));
	}

	private enum StatusViewMode {
		COMPACT,
		DETAIL
	}
}
