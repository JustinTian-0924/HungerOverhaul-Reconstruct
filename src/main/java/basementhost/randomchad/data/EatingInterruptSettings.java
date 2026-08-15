package basementhost.randomchad.data;

// Holds the global defaults for plugin-controlled eating interruptions.
public final class EatingInterruptSettings {
	private final boolean onDamage;
	private final boolean onMove;
	private final boolean onSneakToggle;

	public EatingInterruptSettings(boolean onDamage, boolean onMove, boolean onSneakToggle) {
		this.onDamage = onDamage;
		this.onMove = onMove;
		this.onSneakToggle = onSneakToggle;
	}

	public boolean isOnDamage() {
		return onDamage;
	}

	public boolean isOnMove() {
		return onMove;
	}

	public boolean isOnSneakToggle() {
		return onSneakToggle;
	}
}
