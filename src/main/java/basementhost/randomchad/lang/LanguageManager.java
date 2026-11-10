package basementhost.randomchad.lang;

import basementhost.randomchad.HungerOverhaul;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
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

	public void loadInitialConfiguration() {
		copyBundledLanguageFiles();
		applyConfiguration(readConfiguration(plugin.getConfig().getString("language", DEFAULT_LANGUAGE), false));
	}

	public LanguageConfiguration readConfiguration(String configuredLanguage) {
		return readConfiguration(configuredLanguage, true);
	}

	private LanguageConfiguration readConfiguration(String configuredLanguage, boolean strict) {
		String normalizedLanguage = normalizeLanguageName(configuredLanguage);
		if (normalizedLanguage == null) {
			String message = "Invalid config.yml setting 'language': '" + configuredLanguage + "'.";
			if (strict) {
				throw new IllegalStateException(message);
			}
			plugin.getLogger().warning(message + " Using " + DEFAULT_LANGUAGE + ".");
			normalizedLanguage = DEFAULT_LANGUAGE;
		}

		YamlConfiguration bundledFallback = loadBundledLanguageFile(DEFAULT_LANGUAGE, strict);
		YamlConfiguration diskFallback = loadLanguageFile(DEFAULT_LANGUAGE, strict);
		YamlConfiguration fallback = diskFallback == null ? new YamlConfiguration() : diskFallback;
		if (normalizedLanguage.equals(DEFAULT_LANGUAGE)) {
			return new LanguageConfiguration(DEFAULT_LANGUAGE, fallback, fallback, bundledFallback, bundledFallback);
		}

		YamlConfiguration selectedLanguage = loadLanguageFile(normalizedLanguage, strict);
		if (selectedLanguage == null) {
			return new LanguageConfiguration(DEFAULT_LANGUAGE, fallback, fallback, bundledFallback, bundledFallback);
		}

		YamlConfiguration bundledSelected = BUNDLED_LANGUAGES.contains(normalizedLanguage)
				? loadBundledLanguageFile(normalizedLanguage, strict)
				: new YamlConfiguration();
		return new LanguageConfiguration(normalizedLanguage, selectedLanguage, fallback, bundledSelected, bundledFallback);
	}

	public void applyConfiguration(LanguageConfiguration configuration) {
		currentLanguageName = configuration.languageName;
		currentLanguage = configuration.current;
		fallbackLanguage = configuration.fallback;
		bundledCurrentLanguage = configuration.bundledCurrent;
		bundledFallbackLanguage = configuration.bundledFallback;
		warnedMissingKeys.clear();
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

	private YamlConfiguration loadBundledLanguageFile(String languageName, boolean strict) {
		String resourcePath = LANGUAGE_FOLDER + "/" + languageName + ".yml";
		try (InputStream inputStream = plugin.getResource(resourcePath)) {
			if (inputStream == null) {
				throw new IOException("Bundled language resource is missing.");
			}

			InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
			YamlConfiguration language = new YamlConfiguration();
			language.load(reader);
			return language;
		} catch (IOException | InvalidConfigurationException exception) {
			String message = "Could not load bundled language resource " + resourcePath + ".";
			if (strict) {
				throw new IllegalStateException(message, exception);
			}
			plugin.getLogger().log(Level.WARNING, message, exception);
			return new YamlConfiguration();
		}
	}

	private YamlConfiguration loadLanguageFile(String languageName, boolean strict) {
		File languageFile = getLanguageFile(languageName);
		if (!languageFile.exists() && BUNDLED_LANGUAGES.contains(languageName)) {
			return new YamlConfiguration();
		}

		YamlConfiguration loadedLanguage = new YamlConfiguration();
		try {
			loadedLanguage.load(languageFile);
		} catch (IOException | InvalidConfigurationException exception) {
			String message = "Could not load language file " + LANGUAGE_FOLDER + "/" + languageFile.getName() + ".";
			if (strict) {
				throw new IllegalStateException(message, exception);
			}
			plugin.getLogger().log(Level.WARNING, message, exception);
			return null;
		}

		if (strict) {
			for (Map.Entry<String, Object> entry : loadedLanguage.getValues(true).entrySet()) {
				if (!(entry.getValue() instanceof String || entry.getValue() instanceof ConfigurationSection)) {
					throw new IllegalStateException(
							"Invalid " + LANGUAGE_FOLDER + "/" + languageFile.getName()
									+ ": '" + entry.getKey() + "' must be text."
					);
				}
			}
		}
		return loadedLanguage;
	}

	private File getLanguageFile(String languageName) {
		File languageFolder = new File(plugin.getDataFolder(), LANGUAGE_FOLDER);
		return new File(languageFolder, languageName + ".yml");
	}

	public static final class LanguageConfiguration {
		private final String languageName;
		private final YamlConfiguration current;
		private final YamlConfiguration fallback;
		private final YamlConfiguration bundledCurrent;
		private final YamlConfiguration bundledFallback;

		private LanguageConfiguration(
				String languageName,
				YamlConfiguration current,
				YamlConfiguration fallback,
				YamlConfiguration bundledCurrent,
				YamlConfiguration bundledFallback
		) {
			this.languageName = languageName;
			this.current = current;
			this.fallback = fallback;
			this.bundledCurrent = bundledCurrent;
			this.bundledFallback = bundledFallback;
		}
	}
}
