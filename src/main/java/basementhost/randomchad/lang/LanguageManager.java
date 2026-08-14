package basementhost.randomchad.lang;

import basementhost.randomchad.ChadPluginBase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Pattern;

// Loads language files and turns their MiniMessage text into Components that displays in-game

public final class LanguageManager {
	private static final String DEFAULT_LANGUAGE = "en_us.yml";
	private static final String LANGUAGE_FOLDER = "lang";
	private static final Pattern PLACEHOLDER_NAME = Pattern.compile("[a-z0-9_-]+");

	private final ChadPluginBase plugin;
	private final MiniMessage miniMessage = MiniMessage.miniMessage();
	private final Set<String> warnedMissingKeys = new HashSet<>();

	private YamlConfiguration currentLanguage = new YamlConfiguration();
	private YamlConfiguration fallbackLanguage = new YamlConfiguration();
	private String currentLanguageName = DEFAULT_LANGUAGE;

	public LanguageManager(ChadPluginBase plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
	}

	// Reloads both config.yml and the selected language file
	// the debug messages are hardcoded bcs I don't want to fk stuff up.
	public void reload() {
		plugin.reloadConfig();
		warnedMissingKeys.clear();
		copyBundledLanguageFiles();

		File languageDirectory = new File(plugin.getDataFolder(), LANGUAGE_FOLDER);
		fallbackLanguage = loadLanguageFile(
				new File(languageDirectory, DEFAULT_LANGUAGE),
				"fallback language " + DEFAULT_LANGUAGE
		);

		String configuredName = plugin.getConfig().getString("lang", DEFAULT_LANGUAGE);
		configuredName = configuredName == null ? DEFAULT_LANGUAGE : configuredName.trim();

		if (!isSafeLanguageFileName(configuredName)) {
			plugin.getLogger().warning(
					"Invalid language file name '" + configuredName
							+ "'. Only one .yml file name is allowed. Falling back to " + DEFAULT_LANGUAGE + "."
			);
			useFallbackLanguage();
			return;
		}

		if (configuredName.equalsIgnoreCase(DEFAULT_LANGUAGE)) {
			useFallbackLanguage();
			return;
		}

		File selectedFile = new File(languageDirectory, configuredName);
		if (!selectedFile.isFile()) {
			plugin.getLogger().warning(
					"Language file '" + configuredName + "' was not found. Falling back to " + DEFAULT_LANGUAGE + "."
			);
			useFallbackLanguage();
			return;
		}

		YamlConfiguration selectedLanguage = tryLoadLanguageFile(selectedFile, "selected language " + configuredName);
		if (selectedLanguage == null) {
			plugin.getLogger().warning("Falling back to " + DEFAULT_LANGUAGE + ".");
			useFallbackLanguage();
			return;
		}

		currentLanguage = selectedLanguage;
		currentLanguageName = configuredName;
	}

	public Component getMessage(String key) {
		return getMessage(key, Map.of());
	}


	 // Fetch a message and safely fills placeholders such as {player}.
	 // Placeholder values are inserted as plain text, so a player name like "<red>Name" cannot accidentally become a MiniMessage color tag.
	public Component getMessage(String key, Map<String, String> placeholders) {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(placeholders, "placeholders");

		String template = findMessageTemplate(key);
		ArrayList<TagResolver> resolvers = new ArrayList<>();

		for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
			String name = placeholder.getKey().toLowerCase(Locale.ROOT);
			if (!PLACEHOLDER_NAME.matcher(name).matches()) {
				plugin.getLogger().warning("Ignored invalid message placeholder name: " + placeholder.getKey());
				continue;
			}

			template = template.replace("{" + placeholder.getKey() + "}", "<" + name + ">");
			resolvers.add(Placeholder.unparsed(name, Objects.toString(placeholder.getValue(), "")));
		}

		try {
			return miniMessage.deserialize(template, resolvers.toArray(TagResolver[]::new));
		} catch (RuntimeException exception) {
			plugin.getLogger().log(Level.WARNING, "Could not parse MiniMessage for key '" + key + "'.", exception);
			return Component.text(template);
		}
	}

	public String getCurrentLanguageName() {
		return currentLanguageName;
	}

	private String findMessageTemplate(String key) {
		String message = getString(currentLanguage, key);
		if (message != null) {
			return message;
		}

		if (currentLanguageName.equalsIgnoreCase(DEFAULT_LANGUAGE)) {
			warnMissingFromFallback(key);
			return key;
		}

		if (warnedMissingKeys.add(key + "@" + currentLanguageName)) {
			plugin.getLogger().warning(
					"Message key '" + key + "' is missing from " + currentLanguageName
							+ ". Trying " + DEFAULT_LANGUAGE + "."
			);
		}

		message = getString(fallbackLanguage, key);
		if (message != null) {
			return message;
		}

		warnMissingFromFallback(key);
		return key;
	}

	private void warnMissingFromFallback(String key) {
		if (!warnedMissingKeys.add(key + "@" + DEFAULT_LANGUAGE)) {
			return;
		}

		plugin.getLogger().warning(
				"Message key '" + key + "' is missing from " + DEFAULT_LANGUAGE
						+ ". Displaying the key itself."
		);
	}

	private String getString(YamlConfiguration language, String key) {
		return language.isString(key) ? language.getString(key) : null;
	}

	private void copyBundledLanguageFiles() {
		copyBundledLanguageFile(DEFAULT_LANGUAGE);
		copyBundledLanguageFile("zh_cn.yml");
	}

	private void copyBundledLanguageFile(String fileName) {
		File destination = new File(new File(plugin.getDataFolder(), LANGUAGE_FOLDER), fileName);
		if (destination.exists()) {
			return;
		}

		try {
			plugin.saveResource(LANGUAGE_FOLDER + "/" + fileName, false);
		} catch (IllegalArgumentException exception) {
			plugin.getLogger().log(Level.SEVERE, "Could not copy bundled language file " + fileName + ".", exception);
		}
	}

	private YamlConfiguration loadLanguageFile(File file, String description) {
		YamlConfiguration loaded = tryLoadLanguageFile(file, description);
		return loaded == null ? new YamlConfiguration() : loaded;
	}

	private YamlConfiguration tryLoadLanguageFile(File file, String description) {
		if (!file.isFile()) {
			plugin.getLogger().warning("Could not find " + description + " at " + file.getPath() + ".");
			return null;
		}

		YamlConfiguration language = new YamlConfiguration();
		try {
			language.load(file);
			return language;
		} catch (IOException | InvalidConfigurationException exception) {
			plugin.getLogger().log(Level.WARNING, "Could not load " + description + ".", exception);
			return null;
		}
	}

	private boolean isSafeLanguageFileName(String fileName) {
		if (fileName.isBlank() || !fileName.toLowerCase().endsWith(".yml")) {
			return false;
		}

		try {
			Path path = Path.of(fileName);
			return !path.isAbsolute() && path.getNameCount() == 1 && !fileName.contains("/") && !fileName.contains("\\");
		} catch (InvalidPathException exception) {
			return false;
		}
	}

	private void useFallbackLanguage() {
		currentLanguage = fallbackLanguage;
		currentLanguageName = DEFAULT_LANGUAGE;
	}
}
