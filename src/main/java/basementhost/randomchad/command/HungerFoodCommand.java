package basementhost.randomchad.command;

import basementhost.randomchad.HungerOverhaul;
import basementhost.randomchad.data.EatingInterruptSettings;
import basementhost.randomchad.data.EatingSettings;
import basementhost.randomchad.data.FoodInspection;
import basementhost.randomchad.data.FoodInterruptSettings;
import basementhost.randomchad.data.FoodPostConsumeSettings;
import basementhost.randomchad.data.FoodSettings;
import basementhost.randomchad.manager.GroupManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

// Inspects the main-hand rule without using an item, checking admission, or running actions.
final class HungerFoodCommand {
	static final String PERMISSION = "hungeroverhaul.food";
	private final HungerOverhaul plugin;

	HungerFoodCommand(HungerOverhaul plugin) {
		this.plugin = plugin;
	}

	void execute(CommandSender sender, String label, String[] args) {
		if (!sender.hasPermission(PERMISSION)) {
			sender.sendMessage(plugin.getLanguageManager().getMessage("command.no-permission"));
			return;
		}
		if (args.length != 1) {
			send(sender, "usage", Map.of("label", label));
			return;
		}
		if (!(sender instanceof Player player)) {
			send(sender, "player-only", Map.of());
			return;
		}
		FoodInspection inspection = plugin.getFoodManager().inspectFood(player.getInventory().getItemInMainHand()).orElse(null);
		if (inspection == null) {
			send(sender, "empty-hand", Map.of());
			return;
		}
		send(sender, "header", Map.of());
		send(sender, "read-only", Map.of());
		send(sender, "material", Map.of("material", inspection.material()));
		send(sender, "consumable", Map.of("value", term(inspection.nativeConsumeSeconds() != null)));
		String craftEngine = inspection.craftEngineId() != null ? inspection.craftEngineId()
				: text(inspection.craftEngineAvailable() ? "craftengine-no-id" : "craftengine-unavailable");
		send(sender, "craftengine", Map.of("id", craftEngine));
		send(sender, "item-model", Map.of("model", inspection.itemModel() == null ? text("none") : inspection.itemModel(),
				"source", text(inspection.itemModelOverridden() ? "model-custom" : "model-default")));
		if (inspection.customModelData() == null) {
			send(sender, "model-data-none", Map.of());
		} else {
			sendModelData(sender, "floats", inspection.customModelData().floats().toString());
			sendModelData(sender, "flags", inspection.customModelData().flags().toString());
			sendModelData(sender, "strings", inspection.customModelData().strings().toString());
			sendModelData(sender, "colors", inspection.customModelData().colors().stream()
					.map(color -> String.format(Locale.ROOT, "#%06X", color.asRGB())).toList().toString());
		}
		FoodSettings food = inspection.food();
		if (food == null) {
			send(sender, inspection.nativeConsumeSeconds() == null ? "not-consumable" : "no-match", Map.of());
			return;
		}
		send(sender, "matched", Map.of("food", food.getId(), "source", text("source." + inspection.source().name().toLowerCase(Locale.ROOT))));
		if (inspection.source() == FoodInspection.Source.CAKE_SLICE) {
			send(sender, "cake-note", Map.of());
		}
		send(sender, "values", Map.of("nutrition", signed(food.getNutritionChange()), "fullness", signed(food.getFullnessChange())));
		sendEatingSettings(player, inspection, food);
		sendActions(sender, food.getPostConsumeSettings());
	}

	private void sendEatingSettings(Player player, FoodInspection inspection, FoodSettings food) {
		EatingSettings global = plugin.getEatingManager().getSettings();
		GroupManager.Group group = plugin.getGroupManager().getGroup(player);
		send(player, "group", Map.of("group", group.getId(), "speed", number(group.getSettings().getEatingSettings().getSpeedMultiplier())));
		if (inspection.source() != FoodInspection.Source.CAKE_SLICE) {
			if (food.getEatingTimeTicks() != null) {
				send(player, "duration-override", Map.of("ticks", food.getEatingTimeTicks()));
			} else {
				send(player, "duration-native", Map.of("seconds", number(inspection.nativeConsumeSeconds())));
			}
		}
		send(player, "cooldown", Map.of("ticks", food.getCooldownTicks() == null ? global.getCooldownTicks() : food.getCooldownTicks(),
				"source", text(food.getCooldownTicks() == null ? "global-default" : "food-override")));
		String overflow = (food.getOverflowPolicy() == null ? global.getOverflowPolicy() : food.getOverflowPolicy()).name().toLowerCase(Locale.ROOT);
		send(player, "overflow", Map.of("policy", text("overflow-policy." + overflow),
				"source", text(food.getOverflowPolicy() == null ? "global-default" : "food-override")));
		if (inspection.source() != FoodInspection.Source.CAKE_SLICE) {
			FoodInterruptSettings foodRules = food.getInterruptSettings();
			EatingInterruptSettings defaultRules = global.getInterruptSettings();
			send(player, "interruptions", Map.of("damage", resolvedTerm(foodRules.getOnDamage(), defaultRules.isOnDamage()),
					"move", resolvedTerm(foodRules.getOnMove(), defaultRules.isOnMove()),
					"sneak", resolvedTerm(foodRules.getOnSneakToggle(), defaultRules.isOnSneakToggle())));
			send(player, "native-interruptions", Map.of());
		}
	}

	private void sendActions(CommandSender sender, FoodPostConsumeSettings actions) {
		send(sender, "actions", Map.of("effects", actions.effects().size(), "commands", actions.commands().size()));
		for (int index = 0; index < actions.effects().size(); index++) {
			FoodPostConsumeSettings.Effect action = actions.effects().get(index);
			PotionEffect effect = action.effect();
			send(sender, "effect", Map.of("index", index + 1, "type", effect.getType().getKey().asString(),
					"ticks", effect.getDuration(), "level", effect.getAmplifier() + 1, "chance", percent(action.chance()),
					"ambient", term(effect.isAmbient()), "particles", term(effect.hasParticles()), "icon", term(effect.hasIcon())));
		}
		for (int index = 0; index < actions.commands().size(); index++) {
			FoodPostConsumeSettings.Command action = actions.commands().get(index);
			Component line = plugin.getLanguageManager().getMessage("command.food.command", Map.of("index", index + 1,
					"sender", text("sender." + action.sender().name().toLowerCase(Locale.ROOT)), "chance", percent(action.chance()),
					"command", abbreviated(action.command())));
			sender.sendMessage(line.hoverEvent(HoverEvent.showText(Component.text(action.command()))));
		}
		send(sender, "actions-note", Map.of());
	}

	private void sendModelData(CommandSender sender, String field, String value) {
		Component line = plugin.getLanguageManager().getMessage("command.food.model-data", Map.of("field", field, "value", abbreviated(value)));
		sender.sendMessage(line.hoverEvent(HoverEvent.showText(Component.text(value))));
	}

	private String resolvedTerm(Boolean override, boolean fallback) {
		return term(override == null ? fallback : override);
	}

	private String term(boolean value) {
		return text(value ? "yes" : "no");
	}

	private String text(String key) {
		return plugin.getLanguageManager().getTextOrDefault("command.food." + key, key);
	}

	private void send(CommandSender sender, String key, Map<String, ?> values) {
		sender.sendMessage(plugin.getLanguageManager().getMessage("command.food." + key, values));
	}

	private String number(Number value) {
		return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
	}

	private String signed(double value) {
		return (value > 0 ? "+" : "") + number(value);
	}

	private String percent(double value) {
		return BigDecimal.valueOf(value).movePointRight(2).stripTrailingZeros().toPlainString();
	}

	private String abbreviated(String text) {
		return text.length() > 120 ? text.substring(0, 120) + "…" : text;
	}
}
