package dev.psyda.surrogate.block;

import dev.psyda.surrogate.registry.ModBlockEntities;
import dev.psyda.surrogate.registry.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Optional;

/** One item, one timer, and a count of how many times someone has pressed the button while it was running. */
public class MicrowaveBlockEntity extends BlockEntity {
	/** A cycle, in ticks. */
	public static final int CYCLE = 100;
	/** Presses during a cycle before the unit gives up. */
	public static final int ABUSE_LIMIT = 4;

	private ItemStack stack = ItemStack.EMPTY;
	private int timer;
	private int abuse;
	private int explosions;

	public MicrowaveBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MICROWAVE, pos, state);
	}

	public ItemStack getStack() {
		return stack;
	}

	public boolean isRunning() {
		return timer > 0;
	}

	/** How many times the button has been pressed during the current cycle. */
	public int getAbuse() {
		return abuse;
	}

	/** How many times this unit has been blown up. Never resets; the crew keep count too. */
	public int getExplosions() {
		return explosions;
	}

	/** A press on the unit: load it, empty it, start it, or hurry it along. */
	public void use(PlayerEntity player, ItemStack held) {
		if (world == null || world.isClient) return;
		if (isRunning()) {
			abuse++;
			timer = Math.max(8, timer - 12);
			world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.BLOCKS, 0.7f, abuse >= 3 ? 0.6f : 1.6f);
			if (world instanceof ServerWorld server) {
				server.spawnParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 3 * abuse, 0.2, 0.1, 0.2, 0.01);
			}
			if (abuse >= ABUSE_LIMIT) explode();
			markDirty();
			return;
		}
		if (stack.isEmpty() && !held.isEmpty() && (held.contains(DataComponentTypes.FOOD) || cooked(held).isPresent())) {
			stack = held.copyWithCount(1);
			if (!player.getAbilities().creativeMode) held.decrement(1);
			start();
		} else if (!stack.isEmpty() && held.isEmpty()) {
			if (!player.giveItemStack(stack)) player.dropItem(stack, false);
			stack = ItemStack.EMPTY;
			world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.5f, 1.2f);
			markDirty();
		} else {
			// Nothing in it, nothing to put in it, and the button works anyway.
			start();
		}
	}

	private void start() {
		timer = CYCLE;
		abuse = 0;
		setLit(true);
		if (world != null) world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.BLOCKS, 0.5f, 1.8f);
		markDirty();
	}

	private void finish() {
		timer = 0;
		abuse = 0;
		setLit(false);
		if (world == null) return;
		world.playSound(null, pos, ModSounds.MICROWAVE_DING, SoundCategory.BLOCKS, 0.8f, 1.0f);
		if (!stack.isEmpty()) {
			Optional<ItemStack> result = cooked(stack);
			if (result.isPresent()) stack = result.get();
		}
		markDirty();
	}

	/** The end of the casserole. */
	public void explode() {
		if (world == null) return;
		timer = 0;
		abuse = 0;
		explosions++;
		setLit(false);
		if (!stack.isEmpty()) stack = new ItemStack(Items.CHARCOAL);
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.5;
		double z = pos.getZ() + 0.5;
		if (world instanceof ServerWorld server) {
			server.spawnParticles(ParticleTypes.EXPLOSION, x, y + 0.2, z, 3, 0.4, 0.3, 0.4, 0.0);
			server.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.4, z, 40, 0.6, 0.5, 0.6, 0.03);
			server.spawnParticles(ParticleTypes.LAVA, x, y + 0.2, z, 12, 0.5, 0.3, 0.5, 0.0);
		}
		world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 1.2f, 1.4f);
		world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1.0f, 0.8f);
		markDirty();
	}

	private Optional<ItemStack> cooked(ItemStack input) {
		if (world == null) return Optional.empty();
		Optional<RecipeEntry<SmeltingRecipe>> match = world.getRecipeManager().getFirstMatch(RecipeType.SMELTING, new SingleStackRecipeInput(input), world);
		return match.map(entry -> entry.value().getResult(world.getRegistryManager()).copy());
	}

	private void setLit(boolean lit) {
		if (world == null) return;
		BlockState state = world.getBlockState(pos);
		if (state.contains(MicrowaveBlock.LIT) && state.get(MicrowaveBlock.LIT) != lit) world.setBlockState(pos, state.with(MicrowaveBlock.LIT, lit));
	}

	public static void tick(World world, BlockPos pos, BlockState state, MicrowaveBlockEntity unit) {
		if (unit.timer <= 0) return;
		unit.timer--;
		if (unit.timer % 20 == 0) world.playSound(null, pos, ModSounds.MICROWAVE_HUM, SoundCategory.BLOCKS, 0.35f, unit.abuse > 0 ? 1.15f : 1.0f);
		if (unit.abuse > 0 && unit.timer % 10 == 0 && world instanceof ServerWorld server) {
			server.spawnParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, unit.abuse, 0.2, 0.1, 0.2, 0.01);
		}
		if (unit.timer == 0) unit.finish();
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.put("Item", stack.encodeAllowEmpty(registries));
		nbt.putInt("Timer", timer);
		nbt.putInt("Abuse", abuse);
		nbt.putInt("Explosions", explosions);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		stack = nbt.contains("Item") ? ItemStack.fromNbtOrEmpty(registries, nbt.getCompound("Item")) : ItemStack.EMPTY;
		timer = nbt.getInt("Timer");
		abuse = nbt.getInt("Abuse");
		explosions = nbt.getInt("Explosions");
	}
}
