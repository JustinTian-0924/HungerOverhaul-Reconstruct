package basementhost.randomchad.data;

// Holds behavior applied when a player dies.
public final class DeathLifecycleSettings {
	private final boolean cancelEating;
	private final boolean preserveDigestionCooldown;
	private final boolean saveData;

	public DeathLifecycleSettings(
			boolean cancelEating,
			boolean preserveDigestionCooldown,
			boolean saveData
	) {
		this.cancelEating = cancelEating;
		this.preserveDigestionCooldown = preserveDigestionCooldown;
		this.saveData = saveData;
	}

	public boolean isCancelEating() {
		return cancelEating;
	}

	public boolean isPreserveDigestionCooldown() {
		return preserveDigestionCooldown;
	}

	public boolean isSaveData() {
		return saveData;
	}
}
