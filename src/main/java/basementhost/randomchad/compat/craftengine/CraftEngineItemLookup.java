package basementhost.randomchad.compat.craftengine;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

// Uses CraftEngine's public item ID API without requiring it on the build classpath.
public final class CraftEngineItemLookup {
	private static final String ITEM_API_CLASS = "net.momirealms.craftengine.bukkit.api.CraftEngineItems";

	private final PluginManager plugins;
	private final Logger logger;
	private Plugin craftEngine;
	private Method getCustomItemId;
	private boolean lookupFailureLogged;

	public CraftEngineItemLookup(PluginManager plugins, Logger logger) {
		this.plugins = plugins;
		this.logger = logger;
	}

	public void refresh(boolean hasCraftEngineFoods) {
		craftEngine = null;
		getCustomItemId = null;
		lookupFailureLogged = false;
		if (!hasCraftEngineFoods) {
			return;
		}

		Plugin provider = plugins.getPlugin("CraftEngine");
		if (provider == null || !provider.isEnabled()) {
			logger.warning("CraftEngine food IDs are configured, but CraftEngine is not enabled. Model and vanilla food rules still apply.");
			return;
		}

		try {
			Class<?> api = Class.forName(ITEM_API_CLASS, false, provider.getClass().getClassLoader());
			getCustomItemId = api.getMethod("getCustomItemId", ItemStack.class);
			craftEngine = provider;
		} catch (ReflectiveOperationException | LinkageError | SecurityException exception) {
			logger.log(Level.WARNING, "CraftEngine food IDs are unavailable with the installed CraftEngine API.", exception);
		}
	}

	public String findId(ItemStack item) {
		if (getCustomItemId == null || !craftEngine.isEnabled()) {
			return null;
		}

		try {
			Object id = getCustomItemId.invoke(null, item);
			return id == null ? null : id.toString();
		} catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
			if (!lookupFailureLogged) {
				lookupFailureLogged = true;
				logger.log(Level.WARNING, "Could not read a CraftEngine item ID. Continuing with model and vanilla food rules.", exception);
			}
			return null;
		}
	}
}
