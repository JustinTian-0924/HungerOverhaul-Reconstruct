package basementhost.randomchad;

import basementhost.randomchad.command.ChadPluginBaseCommand;
import basementhost.randomchad.lang.LanguageManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class ChadPluginBase extends JavaPlugin {
	private LanguageManager languageManager;

	@Override
	public void onEnable() {
		// saveDefaultConfig only creates config.yml when it does not exist, to preventing overwrite after modify
		saveDefaultConfig();
		languageManager = new LanguageManager(this);
		if (!reloadPluginSettings()) {
			return;
		}

		registerCommands();
		getComponentLogger().info(languageManager.getMessage("plugin.enabled"));
	}

	@Override
	public void onDisable() {
		if (languageManager != null) {
			getComponentLogger().info(languageManager.getMessage("plugin.disabled"));
		}
	}

	public LanguageManager getLanguageManager() {
		return languageManager;
	}

	private void registerCommands() {
		PluginCommand pluginCommand = Objects.requireNonNull(
				getCommand("chadpluginbase"),
				"The chadpluginbase command is missing from plugin.yml"
		);
		ChadPluginBaseCommand commandHandler = new ChadPluginBaseCommand(this);
		pluginCommand.setExecutor(commandHandler);
		pluginCommand.setTabCompleter(commandHandler);
	}

	// Pending for furthur implementation
	// @return true when the plugin remains enabled after the reload
	public boolean reloadPluginSettings() {
		languageManager.reload();
		if (getConfig().getBoolean("enable", true)) {
			return true;
		}

		getComponentLogger().warn(languageManager.getMessage("plugin.disabled-by-config"));
		getServer().getPluginManager().disablePlugin(this);
		return false;
	}
}
