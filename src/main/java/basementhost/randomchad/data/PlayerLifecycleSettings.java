package basementhost.randomchad.data;

import java.util.Objects;

// Groups death, respawn, and world-change settings into one reloadable object.
public final class PlayerLifecycleSettings {
	private final DeathLifecycleSettings deathSettings;
	private final RespawnLifecycleSettings respawnSettings;
	private final WorldChangeLifecycleSettings worldChangeSettings;

	public PlayerLifecycleSettings(
			DeathLifecycleSettings deathSettings,
			RespawnLifecycleSettings respawnSettings,
			WorldChangeLifecycleSettings worldChangeSettings
	) {
		this.deathSettings = Objects.requireNonNull(deathSettings, "deathSettings");
		this.respawnSettings = Objects.requireNonNull(respawnSettings, "respawnSettings");
		this.worldChangeSettings = Objects.requireNonNull(worldChangeSettings, "worldChangeSettings");
	}

	public DeathLifecycleSettings getDeathSettings() {
		return deathSettings;
	}

	public RespawnLifecycleSettings getRespawnSettings() {
		return respawnSettings;
	}

	public WorldChangeLifecycleSettings getWorldChangeSettings() {
		return worldChangeSettings;
	}
}
