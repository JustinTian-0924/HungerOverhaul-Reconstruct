package basementhost.randomchad.data;

import java.util.Locale;

// SQLite is local to one server; a future network backend must implement its own coordination.
public enum StorageType {
	YAML,
	SQLITE;

	public static StorageType fromConfigValue(String value) {
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "yaml" -> YAML;
			case "sqlite" -> SQLITE;
			default -> throw new IllegalArgumentException("storage.type must be yaml or sqlite");
		};
	}
}
