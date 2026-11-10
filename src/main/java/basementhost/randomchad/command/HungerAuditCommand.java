package basementhost.randomchad.command;

import basementhost.randomchad.HungerOverhaul;
import basementhost.randomchad.lang.LanguageManager;
import basementhost.randomchad.storage.AdminAuditLog;
import basementhost.randomchad.storage.AdminAuditLogReader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

// Runs a read-only audit query away from both the server thread and the player save queue.
final class HungerAuditCommand {
	static final String PERMISSION = "hungeroverhaul.audit";
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter
			.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.ROOT)
			.withZone(ZoneId.systemDefault());

	private final HungerOverhaul plugin;
	private final AdminAuditLogReader reader;
	private boolean queryRunning;

	HungerAuditCommand(HungerOverhaul plugin) {
		this.plugin = plugin;
		this.reader = new AdminAuditLogReader(plugin.getDataFolder().toPath().resolve(AdminAuditLog.FILE_NAME));
	}

	void execute(CommandSender sender, String label, String[] args) {
		if (!sender.hasPermission(PERMISSION)) {
			send(sender, "command.no-permission", Map.of());
			return;
		}
		if (args.length < 2 || args.length > 3 || args[1].isBlank()) {
			send(sender, "command.audit.usage", Map.of("label", label));
			return;
		}

		int page = args.length == 3 ? parsePage(args[2]) : 1;
		if (page < 1) {
			send(sender, "command.audit.invalid-page", Map.of());
			return;
		}
		if (queryRunning) {
			send(sender, "command.audit.busy", Map.of());
			return;
		}

		String target = args[1];
		UUID targetId = resolveTargetId(target);
		BukkitScheduler scheduler = plugin.getServer().getScheduler();
		queryRunning = true;
		try {
			scheduler.runTaskAsynchronously(plugin, () -> {
				AdminAuditLogReader.Page result = readPage(target, targetId, page);
				try {
					scheduler.runTask(plugin, () -> finishQuery(sender, label, target, page, result));
				} catch (IllegalPluginAccessException ignored) {
					// The plugin stopped while the read was in progress.
				}
			});
		} catch (IllegalPluginAccessException exception) {
			queryRunning = false;
			send(sender, "command.audit.read-failed", Map.of());
		}
	}

	List<String> tabComplete(CommandSender sender, String[] args) {
		if (!sender.hasPermission(PERMISSION) || args.length != 2) {
			return List.of();
		}

		String prefix = args[1].toLowerCase(Locale.ROOT);
		return plugin.getServer().getOnlinePlayers().stream()
				.map(Player::getName)
				.filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();
	}

	private UUID resolveTargetId(String target) {
		if (target.length() == 36) {
			try {
				UUID candidate = UUID.fromString(target);
				if (candidate.toString().equalsIgnoreCase(target)) {
					return candidate;
				}
			} catch (IllegalArgumentException ignored) {
				// Continue with exact name matching.
			}
		}

		Player online = plugin.getServer().getPlayerExact(target);
		if (online != null) {
			return online.getUniqueId();
		}
		OfflinePlayer cached = plugin.getServer().getOfflinePlayerIfCached(target);
		return cached != null && target.equalsIgnoreCase(cached.getName()) ? cached.getUniqueId() : null;
	}

	private AdminAuditLogReader.Page readPage(String target, UUID targetId, int page) {
		try {
			return reader.readPage(target, targetId, page);
		} catch (IOException | RuntimeException exception) {
			plugin.getLogger().log(Level.WARNING, "Could not read admin-audit.log.", exception);
			return null;
		}
	}

	private void finishQuery(CommandSender sender, String label, String target, int page, AdminAuditLogReader.Page result) {
		queryRunning = false;
		if ((sender instanceof Player player && !player.isOnline()) || !sender.hasPermission(PERMISSION)) {
			return;
		}
		if (result == null) {
			send(sender, "command.audit.read-failed", Map.of());
			return;
		}
		if (result.entries().isEmpty()) {
			String key = result.hasMatches() ? "command.audit.page-not-found" : "command.audit.no-entries";
			send(sender, key, Map.of("player", target, "page", page));
			return;
		}

		send(sender, "command.audit.header", Map.of("player", target, "page", page));
		for (AdminAuditLogReader.Entry entry : result.entries()) {
			sendEntry(sender, entry);
		}
		if (result.hasNextPage()) {
			send(sender, "command.audit.next-page", Map.of("label", label, "player", target, "page", page + 1L));
		}
	}

	private void sendEntry(CommandSender sender, AdminAuditLogReader.Entry entry) {
		LanguageManager language = plugin.getLanguageManager();
		String mode = formatEnum(language, "mode", entry.targetMode());
		String outcome = formatEnum(language, "outcome", entry.outcome());
		String operation = entry.operation() + " " + entry.field();
		if (!entry.amount().equals("-")) {
			operation += " " + entry.amount();
		}

		Component description = language.getMessage("command.audit.entry", Map.of(
				"time", TIMESTAMP_FORMAT.format(entry.timestamp()),
				"actor", entry.actorName(),
				"player", entry.targetName(),
				"mode", mode,
				"operation", operation,
				"outcome", outcome
		));
		sender.sendMessage(description.hoverEvent(HoverEvent.showText(Component.text(entry.targetId().toString()))));
		send(sender, "command.audit.values", Map.of(
				"nutrition_before", formatValue(language, entry.before().nutrition(), entry.before().nutritionFrozen()),
				"nutrition_after", formatValue(language, entry.after().nutrition(), entry.after().nutritionFrozen()),
				"fullness_before", formatValue(language, entry.before().fullness(), entry.before().fullnessFrozen()),
				"fullness_after", formatValue(language, entry.after().fullness(), entry.after().fullnessFrozen())
		));
	}

	private String formatEnum(LanguageManager language, String category, Enum<?> value) {
		String key = value.name().toLowerCase(Locale.ROOT).replace('_', '-');
		return language.getTextOrDefault("command.audit." + category + "." + key, key);
	}

	private String formatValue(LanguageManager language, double value, boolean frozen) {
		String formatted = String.format(Locale.ROOT, "%.2f", value);
		return frozen ? formatted + " [" + language.getTextOrDefault("command.audit.frozen", "frozen") + "]" : formatted;
	}

	private int parsePage(String text) {
		try {
			return Integer.parseInt(text);
		} catch (NumberFormatException exception) {
			return 0;
		}
	}

	private void send(CommandSender sender, String key, Map<String, ?> values) {
		sender.sendMessage(plugin.getLanguageManager().getMessage(key, values));
	}
}
