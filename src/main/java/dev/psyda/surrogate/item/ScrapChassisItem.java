package dev.psyda.surrogate.item;

import dev.psyda.surrogate.registry.ModComponents;
import dev.psyda.surrogate.registry.ModItems;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** Salvage from a wreck. Carries the wreck's upgrades and identity so a rebuilt chassis keeps its links. */
public class ScrapChassisItem extends Item {
	public ScrapChassisItem(Settings settings) {
		super(settings);
	}

	/** Turns scrap into a barely functional chassis (1 hull point, no charge) with the old upgrades and identity. */
	public static ItemStack rebuild(ItemStack scrap, RegistryWrapper.WrapperLookup registries) {
		NbtCompound data = scrap.get(ModComponents.ROBOT_DATA);
		NbtCompound robot = new NbtCompound();
		ItemStack result = new ItemStack(ModItems.ROBOT_CHASSIS);
		if (data != null) {
			if (data.containsUuid("RobotUuid")) robot.putUuid("UUID", data.getUuid("RobotUuid"));
			robot.putInt("Plating", data.getInt("Plating"));
			robot.putInt("Battery", data.getInt("Battery"));
			robot.putInt("CargoTier", data.getInt("CargoTier"));
			robot.putBoolean("Fabricator", data.getBoolean("Fabricator"));
			if (data.contains("RobotName", NbtElement.STRING_TYPE)) {
				robot.putString("CustomName", data.getString("RobotName"));
				try {
					result.set(DataComponentTypes.CUSTOM_NAME, Text.Serialization.fromJson(data.getString("RobotName"), registries));
				} catch (RuntimeException ignored) {
				}
			}
		}
		robot.putFloat("Health", 1.0f);
		robot.putInt("Energy", 0);
		robot.putString("State", "OFFLINE");
		result.set(ModComponents.ROBOT_DATA, robot);
		return result;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		NbtCompound data = stack.get(ModComponents.ROBOT_DATA);
		if (data != null) {
			tooltip.add(Text.translatable("tooltip.surrogate.tiers", data.getInt("Plating"), data.getInt("Battery")).formatted(Formatting.DARK_GRAY));
		}
		tooltip.add(Text.translatable("tooltip.surrogate.scrap").formatted(Formatting.GRAY));
	}
}
