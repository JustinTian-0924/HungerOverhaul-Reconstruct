package basementhost.randomchad.storage;

import basementhost.randomchad.data.AdminAuditEntry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

// Appends one line per admin change on the player-data storage thread.
public final class AdminAuditLog {
	public static final String FILE_NAME = "admin-audit.log";

	private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]");

	private final Path logFile;
	private final Logger logger;

	public AdminAuditLog(Path logFile, Logger logger) {
		this.logFile = logFile;
		this.logger = logger;
	}

	public void append(AdminAuditEntry entry, AdminAuditEntry.Outcome outcome) {
		AdminAuditEntry.Action action = entry.action();
		String line = Instant.now()
				+ " actor=" + quote(action.actorName())
				+ " actor_type=" + action.actorType()
				+ " actor_uuid=" + (action.actorId() == null ? "-" : action.actorId())
				+ " target=" + quote(entry.playerName())
				+ " target_uuid=" + entry.playerId()
				+ " target_mode=" + entry.targetMode()
				+ " operation=" + action.operation()
				+ " field=" + action.valueTarget()
				+ " amount=" + (action.amount() == null ? "-" : action.amount())
				+ " outcome=" + outcome
				+ " before=" + formatValues(entry.before())
				+ " after=" + formatValues(entry.after());
		try {
			Files.writeString(
					logFile,
					line + "\n",
					StandardCharsets.UTF_8,
					StandardOpenOption.CREATE,
					StandardOpenOption.APPEND
			);
		} catch (IOException | SecurityException exception) {
			logger.log(Level.WARNING, "Could not append admin-audit.log. Audit entry: " + line, exception);
		}
	}

	private static String quote(String text) {
		String singleLine = CONTROL_CHARACTERS.matcher(text).replaceAll("?");
		return "\"" + singleLine.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private static String formatValues(AdminAuditEntry.Values values) {
		return "{nutrition=" + values.nutrition()
				+ ",fullness=" + values.fullness()
				+ ",nutrition_frozen=" + values.nutritionFrozen()
				+ ",fullness_frozen=" + values.fullnessFrozen() + "}";
	}
}
