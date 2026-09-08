package dev.psyda.surrogate.item;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.block.SpanAnchorBlock;
import dev.psyda.surrogate.registry.ModBlocks;
import dev.psyda.surrogate.registry.ModItems;
import dev.psyda.surrogate.rescue.RescueState;
import dev.psyda.surrogate.rescue.SpanBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.List;

/**
 * A bridge in a box. Used on a span anchor, it surveys the gap the anchor is looking at and then lays one
 * course of five-wide deck each time it is used, taking the plating out of whoever is holding it.
 *
 * <p>The kit is not spent. What is spent is deck plating, at {@link #PLATES} a course, which is the whole
 * cost of the thing: the kit is a way of laying a hull-wide deck straight and level in four-block bites
 * rather than a way of getting a bridge for nothing. A player with a stack of plating and an afternoon can
 * build the same crossing by hand and the crawler will never know the difference.
 */
public class SpanKitItem extends Item {
	/** Deck plating one course costs. Twenty blocks of deck for eight plates: the kit rolls the rest. */
	public static final int PLATES = 8;

	public SpanKitItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (context.getWorld().isClient) return ActionResult.SUCCESS;
		if (!(context.getPlayer() instanceof ServerPlayerEntity player)) return ActionResult.PASS;
		return use(player, context.getBlockPos()) ? ActionResult.CONSUME : ActionResult.PASS;
	}

	/**
	 * A kit against the anchor at {@code pos}: survey it, or lay the next course of the span it started.
	 *
	 * <p>Separate from {@link #useOnBlock} because a Carpet fake player drives its actions into the
	 * interaction manager rather than through the play network handler, so neither an item's use on a block
	 * nor a use on an entity exists for one — and a headless test that presses the button passes whether or
	 * not any of this is wired up. {@code /surrogate rescue kit} calls this, and so does a real right click.
	 *
	 * @return true when this was an anchor and the kit had something to say about it
	 */
	public static boolean use(ServerPlayerEntity player, BlockPos pos) {
		ServerWorld world = player.getServerWorld();
		BlockState state = world.getBlockState(pos);
		if (!state.isOf(ModBlocks.SPAN_ANCHOR)) return false;
		Direction facing = state.get(SpanAnchorBlock.FACING);
		RescueState rescue = RescueState.get(player.server);

		// A kit used on a different anchor, or on the same one pointing somewhere else, starts a different
		// bridge. The bearing has to be part of that test: the anchor mines and drops itself and takes its
		// facing from whoever puts it back, and the obvious place to stand while doing that is on the deck,
		// which turns it a hundred and eighty degrees. Only one span is ever in progress, which is a limit
		// nobody will meet: there is one Rift and it has one place worth crossing.
		boolean sameAnchor = rescue.anchor != null && rescue.anchor.equals(pos)
				&& rescue.spanStepX == facing.getOffsetX() && rescue.spanStepZ == facing.getOffsetZ();
		if (!sameAnchor) {
			int length = SpanBuilder.measure(world, pos, facing);
			if (length <= 0) {
				player.sendMessage(Text.translatable("message.surrogate.span.nothing").formatted(Formatting.RED), false);
				world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.BLOCKS, 0.7f, 0.6f);
				return true;
			}
			rescue.anchor = pos;
			rescue.spanStepX = facing.getOffsetX();
			rescue.spanStepZ = facing.getOffsetZ();
			rescue.spanLength = length;
			rescue.spanCourses = 0;
			// Not spanDone. It means a deck has reached the far side at least once, which surveying a second
			// gap somewhere else does not undo -- and the mission board reads it to decide whether the Rift is
			// still the answer, so clearing it would put "the Rift" back beside three names over a bridge the
			// player is standing on.
			rescue.markDirty();
			player.sendMessage(Text.translatable("message.surrogate.span.surveyed", length,
					SpanBuilder.courses(length), PLATES).formatted(Formatting.AQUA), false);
			world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.BLOCKS, 0.8f, 1.4f);
			return true;
		}

		int total = SpanBuilder.courses(rescue.spanLength);
		if (rescue.spanCourses >= total) {
			player.sendMessage(Text.translatable("message.surrogate.span.done_already").formatted(Formatting.GRAY), true);
			return true;
		}
		if (!spend(player)) {
			player.sendMessage(Text.translatable("message.surrogate.span.needs", PLATES,
					new ItemStack(ModItems.DECK_PLATING).getName()).formatted(Formatting.RED), false);
			world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.BLOCKS, 0.7f, 0.6f);
			return true;
		}
		rescue.spanCourses++;
		SpanBuilder.lay(world, pos, facing, rescue.spanLength, rescue.spanCourses);
		if (rescue.spanCourses >= total) {
			rescue.spanDone = true;
			player.sendMessage(Text.translatable("message.surrogate.span.finished").formatted(Formatting.GREEN), false);
			world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 0.9f, 1.2f);
			Surrogate.LOGGER.info("Span complete: {} blocks of deck from {}", rescue.spanLength, pos.toShortString());
		} else {
			player.sendMessage(Text.translatable("message.surrogate.span.course", rescue.spanCourses, total).formatted(Formatting.AQUA), true);
		}
		rescue.markDirty();
		return true;
	}

	/** Takes a course's worth of plating out of the player, or leaves them alone and says no. */
	private static boolean spend(ServerPlayerEntity player) {
		if (player.getAbilities().creativeMode) return true;
		PlayerInventory inventory = player.getInventory();
		int found = 0;
		for (int slot = 0; slot < inventory.size(); slot++) {
			if (inventory.getStack(slot).isOf(ModItems.DECK_PLATING)) found += inventory.getStack(slot).getCount();
		}
		if (found < PLATES) return false;
		int left = PLATES;
		for (int slot = 0; slot < inventory.size() && left > 0; slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (!stack.isOf(ModItems.DECK_PLATING)) continue;
			int take = Math.min(left, stack.getCount());
			stack.decrement(take);
			left -= take;
		}
		return true;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.surrogate.span_kit").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.surrogate.span_kit.cost", PLATES).formatted(Formatting.DARK_GRAY));
	}
}
