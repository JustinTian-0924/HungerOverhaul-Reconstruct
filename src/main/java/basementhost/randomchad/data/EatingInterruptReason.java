package basementhost.randomchad.data;

// Identifies why an active eating session ended.
public enum EatingInterruptReason {
	DAMAGE,
	MOVE,
	SNEAK_TOGGLE,
	ITEM_SWITCH,
	RELEASED,
	RELOAD,
	DEATH,
	WORLD_CHANGE
}
