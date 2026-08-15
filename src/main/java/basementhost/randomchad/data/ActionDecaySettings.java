package basementhost.randomchad.data;

import java.util.Objects;

// Holds the prices of player actions for one permission group.
public final class ActionDecaySettings {
	private final ActionCost jumping;
	private final ActionCost attacking;
	private final ActionCost takingDamage;
	private final ActionCost breakingBlock;
	private final ActionCost placingBlock;

	public ActionDecaySettings(
			ActionCost jumping,
			ActionCost attacking,
			ActionCost takingDamage,
			ActionCost breakingBlock,
			ActionCost placingBlock
	) {
		this.jumping = Objects.requireNonNull(jumping, "jumping");
		this.attacking = Objects.requireNonNull(attacking, "attacking");
		this.takingDamage = Objects.requireNonNull(takingDamage, "takingDamage");
		this.breakingBlock = Objects.requireNonNull(breakingBlock, "breakingBlock");
		this.placingBlock = Objects.requireNonNull(placingBlock, "placingBlock");
	}

	public ActionCost getJumping() {
		return jumping;
	}

	public ActionCost getAttacking() {
		return attacking;
	}

	public ActionCost getTakingDamage() {
		return takingDamage;
	}

	public ActionCost getBreakingBlock() {
		return breakingBlock;
	}

	public ActionCost getPlacingBlock() {
		return placingBlock;
	}
}
