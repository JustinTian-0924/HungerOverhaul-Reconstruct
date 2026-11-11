package basementhost.randomchad.command;

import basementhost.randomchad.HungerOverhaul;
import basementhost.randomchad.data.DeathLifecycleSettings;
import basementhost.randomchad.data.EatingStatusSnapshot;
import basementhost.randomchad.data.GroupSettings;
import basementhost.randomchad.data.HealthRegenerationGroupSettings;
import basementhost.randomchad.data.LifecycleValueRule;
import basementhost.randomchad.data.PlayerData;
import basementhost.randomchad.data.PlayerLifecycleSettings;
import basementhost.randomchad.data.RespawnLifecycleSettings;
import basementhost.randomchad.data.StatusEffectDefinition;
import basementhost.randomchad.data.StatusEffectsConfiguration;
import basementhost.randomchad.data.StoredPlayerData;
import basementhost.randomchad.data.WorldChangeLifecycleSettings;
import basementhost.randomchad.lang.LanguageManager;
import basementhost.randomchad.manager.GroupManager;
import basementhost.randomchad.manager.HealthRegenerationManager;
import basementhost.randomchad.storage.ReadOnlyPlayerDataQueryResult;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class HungerOverhaulCommand implements CommandExecutor, TabCompleter {
	private static final String RELOAD_PERMISSION = "hungeroverhaul.reload";
	private static final String STATUS_OTHERS_PERMISSION = "hungeroverhaul.status.others";
	private static final DateTimeFormatter LAST_SEEN_FORMAT = DateTimeFormatter
			.ofPattern("yyyy-MM-dd HH:mm:ss z")
			.withZone(ZoneId.systemDefault());

	private final HungerOverhaul plugin;
	private final HungerAdminCommand adminCommand;
	private final HungerAuditCommand auditCommand;
	private final HungerFoodCommand foodCommand;

	public HungerOverhaulCommand(HungerOverhaul plugin) {
		this.plugin = plugin;
		this.adminCommand = new HungerAdminCommand(plugin);
		this.auditCommand = new HungerAuditCommand(plugin);
		this.foodCommand = new HungerFoodCommand(plugin);
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
			StatusRequest statusRequest = readStatusRequest(args);
			if (statusRequest == null) {
				sender.sendMessage(plugin.getLanguageManager().getMessage(
						"command.status.usage",
						Map.of("label", label)
				));
				return true;
			}

			sendPlayerStatus(sender, statusRequest);
			return true;
		}

		if (args[0].equalsIgnoreCase("food")) {
			foodCommand.execute(sender, label, args);
			return true;
		}

		if (args[0].equalsIgnoreCase("audit")) {
			auditCommand.execute(sender, label, args);
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
			List<String> suggestions = new ArrayList<>(List.of("compact", "detail"));
			if (sender.hasPermission(STATUS_OTHERS_PERMISSION)) {
				plugin.getServer().getOnlinePlayers().stream()
						.map(Player::getName)
						.sorted(String.CASE_INSENSITIVE_ORDER)
						.forEach(suggestions::add);
			}
			return matchingSuggestions(suggestions, args[1]);
		}

		if (args.length == 3
				&& args[0].equalsIgnoreCase("status")
				&& sender.hasPermission(STATUS_OTHERS_PERMISSION)
				&& parseStatusViewMode(args[1]) == null) {
			return matchingSuggestions(List.of("compact", "detail"), args[2]);
		}

		if (args.length > 1 && args[0].equalsIgnoreCase("audit")) {
			return auditCommand.tabComplete(sender, args);
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
		if (sender.hasPermission(HungerAuditCommand.PERMISSION) && "audit".startsWith(typedText)) {
			suggestions.add("audit");
		}
		if (sender.hasPermission(HungerFoodCommand.PERMISSION) && "food".startsWith(typedText)) {
			suggestions.add("food");
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
		if (sender.hasPermission(HungerFoodCommand.PERMISSION)) {
			sender.sendMessage(languageManager.getMessage("command.info.food-hint", Map.of("label", label)));
		}
	}

	private void sendPlayerStatus(CommandSender sender, StatusRequest statusRequest) {
		LanguageManager languageManager = plugin.getLanguageManager();
		if (statusRequest.targetName() == null) {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(languageManager.getMessage("command.status.player-only"));
				return;
			}
			sendOnlinePlayerStatus(sender, player, statusRequest.viewMode(), true);
			return;
		}

		if (!sender.hasPermission(STATUS_OTHERS_PERMISSION)) {
			sender.sendMessage(languageManager.getMessage("command.status.no-permission-others"));
			return;
		}

		Player onlinePlayer = findOnlinePlayer(statusRequest.targetName());
		if (onlinePlayer != null) {
			sendOnlinePlayerStatus(sender, onlinePlayer, statusRequest.viewMode(), false);
			return;
		}

		OfflinePlayer offlinePlayer = findKnownOfflinePlayer(statusRequest.targetName());
		String displayedName = offlinePlayer == null
				? statusRequest.targetName()
				: getOfflinePlayerName(offlinePlayer, statusRequest.targetName());
		sender.sendMessage(languageManager.getMessage(
				"command.status.offline-loading",
				Map.of("player", displayedName)
		));
		if (offlinePlayer != null) {
			UUID playerId = offlinePlayer.getUniqueId();
			plugin.getPersistenceManager().queryPlayerDataReadOnly(
					playerId,
					result -> finishOfflinePlayerStatusQuery(
							sender,
							playerId,
							displayedName,
							statusRequest.viewMode(),
							result
					)
			);
			return;
		}

		plugin.getPersistenceManager().queryPlayerDataReadOnly(
				statusRequest.targetName(),
				result -> finishOfflinePlayerStatusQuery(
						sender,
						null,
						displayedName,
						statusRequest.viewMode(),
						result
				)
		);
	}

	private void sendOnlinePlayerStatus(
			CommandSender sender,
			Player targetPlayer,
			StatusViewMode viewMode,
			boolean selfQuery
	) {
		LanguageManager languageManager = plugin.getLanguageManager();

		if (plugin.getPersistenceManager().isPlayerDataLoading(targetPlayer.getUniqueId())) {
			String messageKey = selfQuery
					? "command.status.loading"
					: "command.status.other-loading";
			sender.sendMessage(languageManager.getMessage(
					messageKey,
					Map.of("player", targetPlayer.getName())
			));
			return;
		}

		PlayerData playerData = plugin.getPlayerDataManager()
				.findPlayerData(targetPlayer)
				.orElse(null);
		if (playerData == null) {
			String messageKey = selfQuery
					? "command.status.unavailable"
					: "command.status.other-unavailable";
			sender.sendMessage(languageManager.getMessage(
					messageKey,
					Map.of("player", targetPlayer.getName())
			));
			return;
		}

		plugin.getPlayerDataManager().refreshGroupSettings(targetPlayer);
		String groupId = plugin.getGroupManager().getGroup(targetPlayer).getId();
		StatusValues values = StatusValues.from(playerData);
		sendBaseStatus(sender, targetPlayer.getName(), groupId, values, languageManager);

		if (viewMode == StatusViewMode.DETAIL) {
			sendDetailedEffects(sender, values, languageManager);
			sendDetailedHealthRegeneration(sender, targetPlayer, playerData, languageManager);
			sendDetailedEating(sender, targetPlayer, languageManager);
			sendDetailedLifecycle(sender, languageManager);
		}
	}

	private void finishOfflinePlayerStatusQuery(
			CommandSender sender,
			UUID knownPlayerId,
			String requestedName,
			StatusViewMode viewMode,
			ReadOnlyPlayerDataQueryResult result
	) {
		if (!isRecipientAvailable(sender)) {
			return;
		}

		UUID resolvedPlayerId = result.getPlayerId().orElse(knownPlayerId);
		if (resolvedPlayerId != null) {
			Player nowOnline = plugin.getServer().getPlayer(resolvedPlayerId);
			if (nowOnline != null && nowOnline.isOnline()) {
				sendOnlinePlayerStatus(sender, nowOnline, viewMode, false);
				return;
			}
		}

		LanguageManager languageManager = plugin.getLanguageManager();
		if (result.getStatus() != ReadOnlyPlayerDataQueryResult.Status.FOUND) {
			String messageKey = switch (result.getStatus()) {
				case NOT_FOUND -> knownPlayerId == null
						? "command.status.player-not-found"
						: "command.status.offline-data-not-found";
				case CORRUPT -> "command.status.offline-data-corrupt";
				case FAILED, UNAVAILABLE -> "command.status.offline-query-failed";
				case FOUND -> throw new IllegalStateException("FOUND result has no player data");
			};
			sender.sendMessage(languageManager.getMessage(messageKey, Map.of("player", requestedName)));
			return;
		}

		StoredPlayerData storedData = result.getPlayerData().orElseThrow();
		GroupManager.Group defaultGroup = plugin.getGroupManager().getDefaultGroup();
		GroupSettings defaultSettings = defaultGroup.getSettings();
		boolean hasGroupSnapshot = storedData.hasGroupSnapshot();
		String groupId = storedData.getLastKnownGroupId().orElse(defaultGroup.getId());
		double maxNutrition = storedData.getMaxNutrition().orElse(defaultSettings.getMaxNutrition());
		double maxFullness = storedData.getMaxFullness().orElse(defaultSettings.getMaxFullness());
		GroupManager.Group configuredGroup = plugin.getGroupManager().findGroup(groupId).orElse(null);
		GroupSettings detailGroupSettings = configuredGroup == null
				? defaultSettings
				: configuredGroup.getSettings();
		StatusValues values = new StatusValues(
				storedData.getNutrition(),
				maxNutrition,
				storedData.getFullness(),
				maxFullness,
				storedData.isNutritionFrozen(),
				storedData.isFullnessFrozen()
		);

		sendBaseStatus(
				sender,
				storedData.getLastKnownName(),
				groupId,
				values,
				languageManager
		);
		if (hasGroupSnapshot) {
			sender.sendMessage(languageManager.getMessage("command.status.offline-group-snapshot"));
			if (configuredGroup == null) {
				sender.sendMessage(languageManager.getMessage(
						"command.status.offline-group-missing-fallback",
						Map.of("group", groupId)
				));
			}
		} else {
			sender.sendMessage(languageManager.getMessage("command.status.offline-default-fallback"));
		}
		sender.sendMessage(languageManager.getMessage(
				"command.status.offline-last-seen",
				Map.of("timestamp", formatLastSeen(storedData.getLastSeen()))
		));

		if (viewMode == StatusViewMode.DETAIL) {
			sender.sendMessage(languageManager.getMessage("command.status.offline-detail-note"));
			sendDetailedEffects(sender, values, languageManager);
			sendOfflineHealthRegeneration(sender, values, detailGroupSettings, languageManager);
			sender.sendMessage(languageManager.getMessage("command.status.eating-header"));
			sender.sendMessage(languageManager.getMessage("command.status.offline-eating-unavailable"));
			sendDetailedLifecycle(sender, languageManager);
		}
	}

	private void sendBaseStatus(
			CommandSender recipient,
			String playerName,
			String groupId,
			StatusValues values,
			LanguageManager languageManager
	) {
		recipient.sendMessage(languageManager.getMessage(
				"command.status.header",
				Map.of("player", playerName)
		));
		recipient.sendMessage(languageManager.getMessage(
				"command.status.group",
				Map.of("group", groupId)
		));
		recipient.sendMessage(languageManager.getMessage(
				"command.status.nutrition",
				Map.of(
						"current", formatValue(values.nutrition()),
						"maximum", formatValue(values.maxNutrition()),
						"percentage", formatPercentage(values.nutritionPercentage())
				)
		));
		recipient.sendMessage(languageManager.getMessage(
				"command.status.fullness",
				Map.of(
						"current", formatValue(values.fullness()),
						"maximum", formatValue(values.maxFullness()),
						"percentage", formatPercentage(values.fullnessPercentage())
				)
		));
		recipient.sendMessage(languageManager.getMessage(
				values.nutritionFrozen()
						? "command.status.nutrition-frozen"
						: "command.status.nutrition-active"
		));
		recipient.sendMessage(languageManager.getMessage(
				values.fullnessFrozen()
						? "command.status.fullness-frozen"
						: "command.status.fullness-active"
		));
	}

	private void sendDetailedEffects(
			CommandSender recipient,
			StatusValues values,
			LanguageManager languageManager
	) {
		StatusEffectsConfiguration configuration = plugin.getStatusEffectManager().getConfiguration();
		if (!configuration.isEnabled()) {
			recipient.sendMessage(languageManager.getMessage("command.status.effects-disabled"));
			return;
		}

		StatusEffectDefinition nutritionEffect = configuration.findNutritionEffect(
				values.effectiveNutritionPercentage()
		);
		StatusEffectDefinition fullnessEffect = configuration.findFullnessEffect(
				values.effectiveFullnessPercentage()
		);
		recipient.sendMessage(languageManager.getMessage(
				"command.status.nutrition-effect",
				Map.of(
						"status", getEffectName(languageManager, "nutrition", nutritionEffect),
						"minimum", formatPercentage(nutritionEffect.getMinimumPercentage()),
						"maximum", formatPercentage(nutritionEffect.getMaximumPercentage())
				)
		));
		recipient.sendMessage(languageManager.getMessage(
				"command.status.fullness-effect",
				Map.of(
						"status", getEffectName(languageManager, "fullness", fullnessEffect),
						"minimum", formatPercentage(fullnessEffect.getMinimumPercentage()),
						"maximum", formatPercentage(fullnessEffect.getMaximumPercentage())
				)
		));
		recipient.sendMessage(languageManager.getMessage(
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
		recipient.sendMessage(languageManager.getMessage(
				"command.status.attribute-modifiers",
				Map.of(
						"movement", formatSignedPercentage(movementSpeedModifier),
						"mining", formatSignedPercentage(blockBreakSpeedModifier),
						"attack", formatSignedPercentage(attackDamageModifier)
				)
		));
	}

	private void sendDetailedHealthRegeneration(
			CommandSender recipient,
			Player player,
			PlayerData playerData,
			LanguageManager languageManager
	) {
		HealthRegenerationManager regenerationManager = plugin.getHealthRegenerationManager();
		if (!regenerationManager.isEnabled()) {
			recipient.sendMessage(languageManager.getMessage("command.status.health-regeneration-disabled"));
			return;
		}

		HealthRegenerationGroupSettings groupSettings = plugin.getGroupManager()
				.getSettings(player)
				.getHealthRegenerationSettings();
		double regenerationMultiplier = regenerationManager.getRegenerationMultiplier(playerData);
		double adjustedHeal = regenerationManager.getAdjustedHealHpPerCycle(player, playerData);
		recipient.sendMessage(languageManager.getMessage(
				"command.status.health-regeneration",
				Map.of(
						"base", formatValue(groupSettings.getHealHpPerCycle()),
						"adjusted", formatValue(adjustedHeal),
						"multiplier", formatMultiplier(regenerationMultiplier),
						"interval", regenerationManager.getSettings().getIntervalTicks()
				)
		));
		recipient.sendMessage(languageManager.getMessage(
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

	private void sendOfflineHealthRegeneration(
			CommandSender recipient,
			StatusValues values,
			GroupSettings defaultSettings,
			LanguageManager languageManager
	) {
		HealthRegenerationManager regenerationManager = plugin.getHealthRegenerationManager();
		if (!regenerationManager.isEnabled()) {
			recipient.sendMessage(languageManager.getMessage("command.status.health-regeneration-disabled"));
			return;
		}

		HealthRegenerationGroupSettings regenerationSettings = defaultSettings.getHealthRegenerationSettings();
		double multiplier = getOfflineRegenerationMultiplier(values);
		double adjustedHeal = regenerationSettings.getHealHpPerCycle() * multiplier;
		if (!Double.isFinite(adjustedHeal)) {
			adjustedHeal = Double.MAX_VALUE;
		}

		recipient.sendMessage(languageManager.getMessage(
				"command.status.health-regeneration",
				Map.of(
						"base", formatValue(regenerationSettings.getHealHpPerCycle()),
						"adjusted", formatValue(adjustedHeal),
						"multiplier", formatMultiplier(multiplier),
						"interval", regenerationManager.getSettings().getIntervalTicks()
				)
		));
		recipient.sendMessage(languageManager.getMessage(
				"command.status.health-regeneration-cost",
				Map.of(
						"nutrition", formatValue(regenerationSettings.getNutritionCost()),
						"fullness", formatValue(regenerationSettings.getFullnessCost()),
						"threshold", formatValue(
								regenerationManager.getSettings().getMinimumChargedHealHp()
						)
				)
		));
	}

	private double getOfflineRegenerationMultiplier(StatusValues values) {
		StatusEffectsConfiguration configuration = plugin.getStatusEffectManager().getConfiguration();
		if (!configuration.isEnabled()) {
			return 1.0;
		}

		StatusEffectDefinition nutritionEffect = configuration.findNutritionEffect(
				values.effectiveNutritionPercentage()
		);
		StatusEffectDefinition fullnessEffect = configuration.findFullnessEffect(
				values.effectiveFullnessPercentage()
		);
		return Math.max(
				0.0,
				1.0
						+ nutritionEffect.getHealthRegenerationModifier()
						+ fullnessEffect.getHealthRegenerationModifier()
		);
	}

	private void sendDetailedEating(
			CommandSender recipient,
			Player player,
			LanguageManager languageManager
	) {
		EatingStatusSnapshot status = plugin.getEatingManager().getStatus(player);
		recipient.sendMessage(languageManager.getMessage("command.status.eating-header"));
		switch (status.getActivity()) {
			case IDLE -> recipient.sendMessage(languageManager.getMessage("command.status.eating-idle"));
			case STARTING -> recipient.sendMessage(languageManager.getMessage(
					"command.status.eating-starting",
					Map.of("food", status.getFoodId())
			));
			case EATING -> recipient.sendMessage(languageManager.getMessage(
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
			recipient.sendMessage(languageManager.getMessage(
					"command.status.digestion-active",
					Map.of(
							"ticks", status.getDigestionRemainingTicks(),
							"seconds", formatTickDuration(status.getDigestionRemainingTicks())
					)
			));
		} else {
			recipient.sendMessage(languageManager.getMessage("command.status.digestion-ready"));
		}

		if (status.getBlockedMessageSilenceRemainingTicks() > 0L) {
			recipient.sendMessage(languageManager.getMessage(
					"command.status.blocked-message-silence-active",
					Map.of(
							"ticks", status.getBlockedMessageSilenceRemainingTicks(),
							"seconds", formatTickDuration(status.getBlockedMessageSilenceRemainingTicks())
					)
			));
		} else {
			recipient.sendMessage(languageManager.getMessage("command.status.blocked-message-silence-inactive"));
		}
	}

	private void sendDetailedLifecycle(CommandSender recipient, LanguageManager languageManager) {
		PlayerLifecycleSettings lifecycleSettings = plugin.getPlayerLifecycleManager().getSettings();
		DeathLifecycleSettings deathSettings = lifecycleSettings.getDeathSettings();
		RespawnLifecycleSettings respawnSettings = lifecycleSettings.getRespawnSettings();
		WorldChangeLifecycleSettings worldSettings = lifecycleSettings.getWorldChangeSettings();

		recipient.sendMessage(languageManager.getMessage("command.status.lifecycle-header"));
		recipient.sendMessage(languageManager.getMessage(
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
		recipient.sendMessage(languageManager.getMessage(
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
		recipient.sendMessage(languageManager.getMessage(
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
		recipient.sendMessage(languageManager.getMessage(
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

	private StatusRequest readStatusRequest(String[] args) {
		if (args.length == 1) {
			return new StatusRequest(null, StatusViewMode.COMPACT);
		}

		if (args.length == 2) {
			StatusViewMode selfViewMode = parseStatusViewMode(args[1]);
			return selfViewMode == null
					? new StatusRequest(args[1], StatusViewMode.COMPACT)
					: new StatusRequest(null, selfViewMode);
		}

		if (args.length == 3 && parseStatusViewMode(args[1]) == null) {
			StatusViewMode targetViewMode = parseStatusViewMode(args[2]);
			return targetViewMode == null ? null : new StatusRequest(args[1], targetViewMode);
		}

		return null;
	}

	private StatusViewMode parseStatusViewMode(String configuredMode) {
		return switch (configuredMode.toLowerCase(Locale.ROOT)) {
			case "compact" -> StatusViewMode.COMPACT;
			case "detail" -> StatusViewMode.DETAIL;
			default -> null;
		};
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

	private String formatLastSeen(long lastSeen) {
		return LAST_SEEN_FORMAT.format(Instant.ofEpochMilli(lastSeen));
	}

	private List<String> matchingSuggestions(List<String> options, String input) {
		String typedText = input.toLowerCase(Locale.ROOT);
		return options.stream()
				.filter(option -> option.toLowerCase(Locale.ROOT).startsWith(typedText))
				.toList();
	}

	private void reloadPlugin(CommandSender sender) {
		if (!sender.hasPermission(RELOAD_PERMISSION)) {
			sender.sendMessage(plugin.getLanguageManager().getMessage("command.no-permission"));
			return;
		}

		HungerOverhaul.ReloadResult result = plugin.reloadPluginSettings();
		String messageKey = switch (result.status()) {
			case SUCCESS -> "command.reload-success";
			case REJECTED -> "command.reload-validation-failure";
			case ACTIVATION_FAILED -> "command.reload-activation-failure";
		};
		sender.sendMessage(plugin.getLanguageManager().getMessage(messageKey, Map.of("reason", result.reason())));
	}

	private enum StatusViewMode {
		COMPACT,
		DETAIL
	}

	private record StatusRequest(String targetName, StatusViewMode viewMode) {
	}

	private record StatusValues(
			double nutrition,
			double maxNutrition,
			double fullness,
			double maxFullness,
			boolean nutritionFrozen,
			boolean fullnessFrozen
	) {
		private static StatusValues from(PlayerData playerData) {
			return new StatusValues(
					playerData.getNutrition(),
					playerData.getMaxNutrition(),
					playerData.getFullness(),
					playerData.getMaxFullness(),
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

		private double effectiveNutritionPercentage() {
			return Math.max(0.0, Math.min(nutritionPercentage(), 100.0));
		}

		private double effectiveFullnessPercentage() {
			return Math.max(0.0, Math.min(fullnessPercentage(), 100.0));
		}
	}
}
