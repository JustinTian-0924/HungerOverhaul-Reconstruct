package basementhost.randomchad.data;

import io.papermc.paper.datacomponent.item.CustomModelData;

// Read-only description of a held item and the currently active rule it resolves to.
public record FoodInspection(
		String material,
		Float nativeConsumeSeconds,
		String craftEngineId,
		boolean craftEngineAvailable,
		String itemModel,
		boolean itemModelOverridden,
		CustomModelData customModelData,
		FoodSettings food,
		Source source
) {
	public enum Source {
		NONE,
		CRAFTENGINE,
		BOTH_MODELS,
		ITEM_MODEL,
		CUSTOM_MODEL_DATA,
		MATERIAL,
		FALLBACK,
		CAKE_SLICE
	}
}
