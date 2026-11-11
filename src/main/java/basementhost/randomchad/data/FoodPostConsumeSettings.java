package basementhost.randomchad.data;

import org.bukkit.potion.PotionEffect;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

// Immutable actions captured from the food rule that matched this consumption.
public record FoodPostConsumeSettings(List<PotionEffect> effects, List<Command> commands) {
	public static final FoodPostConsumeSettings EMPTY = new FoodPostConsumeSettings(List.of(), List.of());

	public FoodPostConsumeSettings {
		effects = List.copyOf(effects);
		commands = List.copyOf(commands);
	}

	public boolean isEmpty() {
		return effects.isEmpty() && commands.isEmpty();
	}

	public enum Sender {
		CONSOLE,
		PLAYER
	}

	public record Command(Sender sender, String command) {
		public Command {
			Objects.requireNonNull(sender, "sender");
			Objects.requireNonNull(command, "command");
			if (command.chars().anyMatch(Character::isISOControl)) {
				throw new IllegalArgumentException("command must be one line without control characters");
			}
			command = command.strip();
			if (command.startsWith("/")) {
				command = command.substring(1).stripLeading();
			}
			if (command.isBlank() || command.startsWith("/")) {
				throw new IllegalArgumentException("command must contain a command name with at most one leading slash");
			}
		}

		public String format(String playerName, UUID playerId, String foodId) {
			return command.replace("{player}", playerName)
					.replace("{player_uuid}", playerId.toString())
					.replace("{food}", foodId);
		}
	}
}
