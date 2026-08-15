package basementhost.randomchad.lang;

import basementhost.randomchad.HungerOverhaul;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Pattern;

// Loads language files and converts their MiniMessage text into game chat components.
public final class LanguageManager {
	private static final String DEFAULT_LANGUAGE = "en_us";
	private static final String LANGUAGE_FOLDER = "lang";
	private static final Set<String> BUNDLED_LANGUAGES = Set.of(DEFAULT_LANGUAGE, "zh_cn");
	private static final Pattern LANGUAGE_NAME = Pattern.compile("[a-z0-9_-]+");
	private static final Pattern PLACEHOLDER_NAME = Pattern.compile("[a-z0-9_-]+");

	private final HungerOverhaul plugin;
	private final MiniMessage miniMessage = MiniMessage.miniMessage();
	private final Set<String> warnedMissingKeys = new HashSet<>();

	private YamlConfiguration currentLanguage = new YamlConfiguration();
	private YamlConfiguration fallbackLanguage = new YamlConfiguration();
	private YamlConfiguration bundledCurrentLanguage = new YamlConfiguration();
	private YamlConfiguration bundledFallbackLanguage = new YamlConfiguration();
	private String currentLanguageName = DEFAULT_LANGUAGE;

	public LanguageManager(HungerOverhaul plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
	}

	public void reload() {
		warnedMissingKeys.clear();
		copyBundledLanguageFiles();

		bundledFallbackLanguage = loadBundledLanguageFile(DEFAULT_LANGUAGE);
		fallbackLanguage = loadLanguageFile(DEFAULT_LANGUAGE);
		String configuredLanguage = plugin.getConfig().getString("language", DEFAULT_LANGUAGE);
		String normalizedLanguage = normalizeLanguageName(configuredLanguage);

		if (normalizedLanguage == null) {
			plugin.getLogger().warning(
					"Invalid language name '" + configuredLanguage + "'. Using " + DEFAULT_LANGUAGE + "."
			);
			useFallbackLanguage();
			return;
		}

		if (normalizedLanguage.equals(DEFAULT_LANGUAGE)) {
			useFallbackLanguage();
			return;
		}

		YamlConfiguration selectedLanguage = tryLoadLanguageFile(normalizedLanguage);
		if (selectedLanguage == null) {
			plugin.getLogger().warning(
					"Could not use language '" + normalizedLanguage + "'. Using " + DEFAULT_LANGUAGE + "."
			);
			useFallbackLanguage();
			return;
		}

		currentLanguage = selectedLanguage;
		bundledCurrentLanguage = BUNDLED_LANGUAGES.contains(normalizedLanguage)
				? loadBundledLanguageFile(normalizedLanguage)
				: new YamlConfiguration();
		currentLanguageName = normalizedLanguage;
	}

	public Component getMessage(String key) {
		return getMessage(key, Map.of());
	}

	// Placeholder values are treated as plain text, so they cannot become MiniMessage formatting tags.
	public Component getMessage(String key, Map<String, ?> placeholders) {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(placeholders, "placeholders");

		String template = findMessageTemplate(key);
		ArrayList<TagResolver> resolvers = new ArrayList<>();

		for (Map.Entry<String, ?> placeholder : placeholders.entrySet()) {
			String normalizedName = placeholder.getKey().toLowerCase(Locale.ROOT);
			if (!PLACEHOLDER_NAME.matcher(normalizedName).matches()) {
				plugin.getLogger().warning("Ignored invalid message placeholder: " + placeholder.getKey());
				continue;
			}

			template = template.replace("{" + placeholder.getKey() + "}", "<" + normalizedName + ">");
			String value = Objects.toString(placeholder.getValue(), "");
			resolvers.add(Placeholder.unparsed(normalizedName, value));
		}

		try {
			return miniMessage.deserialize(template, resolvers.toArray(TagResolver[]::new));
		} catch (RuntimeException exception) {
			plugin.getLogger().log(Level.WARNING, "Could not parse message key '" + key + "'.", exception);
			return Component.text(template);
		}
	}

	public String getCurrentLanguageName() {
		return currentLanguageName;
	}

	// Returns plain optional text without reporting a missing-key warning.
	public String getTextOrDefault(String key, String defaultValue) {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(defaultValue, "defaultValue");

		String text = getString(currentLanguage, key);
		if (text != null) {
			return text;
		}

		text = getString(bundledCurrentLanguage, key);
		if (text != null) {
			return text;
		}

		if (!currentLanguageName.equals(DEFAULT_LANGUAGE)) {
			text = getString(fallbackLanguage, key);
			if (text != null) {
				return text;
			}
		}

		text = getString(bundledFallbackLanguage, key);
		if (text != null) {
			return text;
		}

		return defaultValue;
	}

	private String normalizeLanguageName(String languageName) {
		if (languageName == null) {
			return DEFAULT_LANGUAGE;
		}

		String normalizedName = languageName.trim().toLowerCase(Locale.ROOT);
		return LANGUAGE_NAME.matcher(normalizedName).matches() ? normalizedName : null;
	}

	private String findMessageTemplate(String key) {
		String message = getString(currentLanguage, key);
		if (message != null) {
			return message;
		}

		message = getString(bundledCurrentLanguage, key);
		if (message != null) {
			return message;
		}

		if (!currentLanguageName.equals(DEFAULT_LANGUAGE)) {
			warnMissingKey(key, currentLanguageName);
			message = getString(fallbackLanguage, key);
			if (message != null) {
				return message;
			}
		}

		message = getString(bundledFallbackLanguage, key);
		if (message != null) {
			return message;
		}

		warnMissingKey(key, DEFAULT_LANGUAGE);
		return key;
	}

	private String getString(YamlConfiguration language, String key) {
		return language.isString(key) ? language.getString(key) : null;
	}

	private void warnMissingKey(String key, String languageName) {
		if (!warnedMissingKeys.add(key + "@" + languageName)) {
			return;
		}

		plugin.getLogger().warning(
				"Message key '" + key + "' is missing from " + languageName + ".yml."
		);
	}

	private void copyBundledLanguageFiles() {
		for (String languageName : BUNDLED_LANGUAGES) {
			File destination = getLanguageFile(languageName);
			if (destination.isFile()) {
				continue;
			}

			try {
				plugin.saveResource(LANGUAGE_FOLDER + "/" + languageName + ".yml", false);
			} catch (IllegalArgumentException exception) {
				plugin.getLogger().log(
						Level.SEVERE,
						"Could not copy bundled language file " + languageName + ".yml.",
						exception
				);
			}
		}
	}

	private YamlConfiguration loadLanguageFile(String languageName) {
		YamlConfiguration loadedLanguage = tryLoadLanguageFile(languageName);
		return loadedLanguage == null ? new YamlConfiguration() : loadedLanguage;
	}

	private YamlConfiguration loadBundledLanguageFile(String languageName) {
		String resourcePath = LANGUAGE_FOLDER + "/" + languageName + ".yml";
		try (InputStream inputStream = plugin.getResource(resourcePath)) {
			if (inputStream == null) {
				plugin.getLogger().warning("Could not find bundled language resource " + resourcePath + ".");
				return new YamlConfiguration();
			}

			InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
			return YamlConfiguration.loadConfiguration(reader);
		} catch (IOException exception) {
			plugin.getLogger().log(
					Level.WARNING,
					"Could not read bundled language resource " + resourcePath + ".",
					exception
			);
			return new YamlConfiguration();
		}
	}

	private YamlConfiguration tryLoadLanguageFile(String languageName) {
		File languageFile = getLanguageFile(languageName);
		if (!languageFile.isFile()) {
			plugin.getLogger().warning("Could not find language file " + languageFile.getPath() + ".");
			return null;
		}

		YamlConfiguration loadedLanguage = new YamlConfiguration();
		try {
			loadedLanguage.load(languageFile);
			return loadedLanguage;
		} catch (IOException | InvalidConfigurationException exception) {
			plugin.getLogger().log(
					Level.WARNING,
					"Could not load language file " + languageFile.getPath() + ".",
					exception
			);
			return null;
		}
	}

	private File getLanguageFile(String languageName) {
		File languageFolder = new File(plugin.getDataFolder(), LANGUAGE_FOLDER);
		return new File(languageFolder, languageName + ".yml");
	}

	private void useFallbackLanguage() {
		currentLanguage = fallbackLanguage;
		bundledCurrentLanguage = bundledFallbackLanguage;
		currentLanguageName = DEFAULT_LANGUAGE;
	}
}
