package basementhost.randomchad;

import basementhost.randomchad.lang.LanguageManager;
import org.bukkit.plugin.java.JavaPlugin;

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
