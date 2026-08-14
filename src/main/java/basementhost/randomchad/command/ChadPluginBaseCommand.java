package basementhost.randomchad.command;

import basementhost.randomchad.ChadPluginBase;
import basementhost.randomchad.lang.LanguageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;
import java.util.Map;

// Handling commands, for now only base command and reload command implemented
public final class ChadPluginBaseCommand implements CommandExecutor, TabCompleter {
	private static final String ADMIN_PERMISSION = "chadpluginbase.admin";

	private final ChadPluginBase plugin;

	public ChadPluginBaseCommand(ChadPluginBase plugin) {
		this.plugin = plugin;
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

		sender.sendMessage(plugin.getLanguageManager().getMessage(
				"command.usage",
				Map.of("label", label)
		));
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length != 1 || !sender.hasPermission(ADMIN_PERMISSION)) {
			return List.of();
		}

		String typedText = args[0].toLowerCase(Locale.ROOT);
		return "reload".startsWith(typedText) ? List.of("reload") : List.of();
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
				"command.info.reload-hint",
				Map.of("label", label)
		));
	}

	private void reloadPlugin(CommandSender sender) {
		if (!sender.hasPermission(ADMIN_PERMISSION)) {
			sender.sendMessage(plugin.getLanguageManager().getMessage("command.no-permission"));
			return;
		}

		boolean remainsEnabled = plugin.reloadPluginSettings();
		if (remainsEnabled) {
			sender.sendMessage(plugin.getLanguageManager().getMessage("command.reload-success"));
			return;
		}

		sender.sendMessage(plugin.getLanguageManager().getMessage("plugin.disabled-by-config"));
	}
}
