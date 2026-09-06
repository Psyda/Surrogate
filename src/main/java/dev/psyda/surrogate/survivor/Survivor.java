package dev.psyda.surrogate.survivor;

import dev.psyda.surrogate.registry.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.function.Supplier;

/**
 * The people still out there. Each has a voice on the radio, something they need, something they can give
 * back for it, and one thing they send across the chassis port the first time it is used: the plans that
 * open the next act. Lines live in the lang file under {@code survivor.surrogate.<key>}.
 *
 * <p>Six of them, and the last two are on the far side of the Rift. The ordinal is the save format, so new
 * people go on the end and never in the middle.
 */
public enum Survivor {
	OKAFOR("okafor", () -> ModItems.POWER_CELL, 2, () -> new ItemStack(ModItems.FABRICATOR), 3, () -> new ItemStack(ModItems.CRAWLER_BLUEPRINT)),
	SORENSEN("sorensen", () -> ModItems.REPAIR_KIT, 2, () -> new ItemStack(ModItems.CARGO_BAY), 3, () -> new ItemStack(ModItems.RELAY_MODULE)),
	TANAKA("tanaka", () -> ModItems.SULFUR, 8, () -> new ItemStack(ModItems.BATTERY_UPGRADE), 3, () -> new ItemStack(ModItems.RESONANCE_DAMPER)),
	BRANDT("brandt", () -> Items.BREAD, 8, () -> new ItemStack(ModItems.PLATING_MK1), 3, () -> new ItemStack(ModItems.CERAMIC_CLADDING)),
	/** The medic at Clinic Nine, behind her own blown airlock. Without her Novak does not come up. */
	REYES("reyes", () -> ModItems.HULL_PLATING, 4, () -> new ItemStack(ModItems.REBREATHER, 2), 3, () -> new ItemStack(ModItems.SHIELDED_UPLINK)),
	/** On the floor of the Rift, in a wrecked crawler. He asks for nothing and he gives nothing back. */
	NOVAK("novak", () -> Items.AIR, 0, () -> ItemStack.EMPTY, 3, () -> ItemStack.EMPTY);

	private static final Survivor[] VALUES = values();

	private final String key;
	private final Supplier<Item> need;
	private final int needCount;
	private final Supplier<ItemStack> reward;
	private final int radioLines;
	private final Supplier<ItemStack> handover;

	Survivor(String key, Supplier<Item> need, int needCount, Supplier<ItemStack> reward, int radioLines, Supplier<ItemStack> handover) {
		this.key = key;
		this.need = need;
		this.needCount = needCount;
		this.reward = reward;
		this.radioLines = radioLines;
		this.handover = handover;
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

	/**
	 * What they send across the chassis port the first time one calls: the crawler blueprint, a relay, a
	 * damper, a cladding pattern. Empty for anyone with nothing left to give.
	 */
	public ItemStack handover() {
		return handover.get().copy();
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
