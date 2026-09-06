package dev.psyda.surrogate.survivor;

import dev.psyda.surrogate.registry.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.function.Supplier;

/**
 * The people still out there. Each has a voice on the radio, something they need, and something they can
 * give back. Lines live in the lang file under {@code survivor.surrogate.<key>}.
 */
public enum Survivor {
	OKAFOR("okafor", () -> ModItems.POWER_CELL, 2, () -> new ItemStack(ModItems.FABRICATOR), 3),
	SORENSEN("sorensen", () -> ModItems.REPAIR_KIT, 2, () -> new ItemStack(ModItems.CARGO_BAY), 3),
	TANAKA("tanaka", () -> ModItems.SULFUR, 8, () -> new ItemStack(ModItems.BATTERY_UPGRADE), 3),
	BRANDT("brandt", () -> Items.BREAD, 8, () -> new ItemStack(ModItems.PLATING_MK1), 3);

	private static final Survivor[] VALUES = values();

	private final String key;
	private final Supplier<Item> need;
	private final int needCount;
	private final Supplier<ItemStack> reward;
	private final int radioLines;

	Survivor(String key, Supplier<Item> need, int needCount, Supplier<ItemStack> reward, int radioLines) {
		this.key = key;
		this.need = need;
		this.needCount = needCount;
		this.reward = reward;
		this.radioLines = radioLines;
	}

	public static Survivor byId(int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : OKAFOR;
	}

	public String key() {
		return key;
	}

	public Item need() {
		return need.get();
	}

	public int needCount() {
		return needCount;
	}

	public ItemStack reward() {
		return reward.get().copy();
	}

	public int radioLines() {
		return radioLines;
	}

	public String nameKey() {
		return "survivor.surrogate." + key + ".name";
	}

	public Text displayName() {
		return Text.translatable(nameKey());
	}

	public Text line(String which) {
		return Text.translatable("survivor.surrogate." + key + "." + which);
	}
}
