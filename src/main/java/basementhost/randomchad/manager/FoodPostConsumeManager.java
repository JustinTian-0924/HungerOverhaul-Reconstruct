package basementhost.randomchad.manager;

import basementhost.randomchad.data.FoodPostConsumeSettings;
import basementhost.randomchad.data.FoodSettings;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

// Waits until native item consumption is complete before effects or external commands run.
public final class FoodPostConsumeManager {
	private final Plugin plugin;

	public FoodPostConsumeManager(Plugin plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
	}

	public void schedule(Player player, FoodSettings food) {
		FoodPostConsumeSettings actions = food.getPostConsumeSettings();
		if (!actions.isEmpty()) {
			plugin.getServer().getScheduler().runTask(plugin, () -> execute(player, food.getId(), actions));
		}
	}

	private void execute(Player player, String foodId, FoodPostConsumeSettings actions) {
		for (int index = 0; index < actions.effects().size(); index++) {
			if (!canApply(player)) {
				return;
			}
			try {
				// Do not force replacement of stronger native or other-plugin effects.
				player.addPotionEffect(actions.effects().get(index));
			} catch (RuntimeException exception) {
				warn("effect", index, foodId, player, exception.getClass().getSimpleName());
			}
		}

		for (int index = 0; index < actions.commands().size(); index++) {
			if (!canApply(player)) {
				return;
			}
			FoodPostConsumeSettings.Command action = actions.commands().get(index);
			CommandSender sender = action.sender() == FoodPostConsumeSettings.Sender.CONSOLE
					? plugin.getServer().getConsoleSender()
					: player;
			String command = action.format(player.getName(), player.getUniqueId(), foodId);
			try {
				if (!plugin.getServer().dispatchCommand(sender, command)) {
					warn("command", index, foodId, player, "command was not handled");
				}
			} catch (RuntimeException exception) {
				warn("command", index, foodId, player, exception.getClass().getSimpleName());
			}
		}
	}

	private boolean canApply(Player player) {
		return plugin.isEnabled() && player.isOnline() && !player.isDead();
	}

	private void warn(String kind, int index, String foodId, Player player, String reason) {
		// Do not log the raw command, which may contain private arguments from another plugin.
		plugin.getLogger().warning("Post-consume " + kind + " #" + (index + 1) + " for food '" + foodId
				+ "' and player " + player.getUniqueId() + " failed: " + reason + ". Continuing with remaining actions.");
	}
}
