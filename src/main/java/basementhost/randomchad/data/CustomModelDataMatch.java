package basementhost.randomchad.data;

import io.papermc.paper.datacomponent.item.CustomModelData;
import org.bukkit.Color;

import java.util.List;

// A scalar matches floats[0]; a section compares only the lists it names.
public record CustomModelDataMatch(
		Float firstFloat,
		List<Float> floats,
		List<Boolean> flags,
		List<String> strings,
		List<Color> colors
) {
	public boolean matches(CustomModelData data) {
		if (data == null) {
			return false;
		}
		if (firstFloat != null) {
			List<Float> actualFloats = data.floats();
			return !actualFloats.isEmpty() && firstFloat.equals(actualFloats.getFirst());
		}
		return (floats == null || floats.equals(data.floats()))
				&& (flags == null || flags.equals(data.flags()))
				&& (strings == null || strings.equals(data.strings()))
				&& (colors == null || colors.equals(data.colors()));
	}
}
