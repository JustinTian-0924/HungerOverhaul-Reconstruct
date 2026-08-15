package basementhost.randomchad.data;

// Holds optional per-food overrides; null means use the global eating rule.
public final class FoodInterruptSettings {
	private final Boolean onDamage;
	private final Boolean onMove;
	private final Boolean onSneakToggle;

	public FoodInterruptSettings(Boolean onDamage, Boolean onMove, Boolean onSneakToggle) {
		this.onDamage = onDamage;
		this.onMove = onMove;
		this.onSneakToggle = onSneakToggle;
	}

	public Boolean getOnDamage() {
		return onDamage;
	}

	public Boolean getOnMove() {
		return onMove;
	}

	public Boolean getOnSneakToggle() {
		return onSneakToggle;
	}
}
