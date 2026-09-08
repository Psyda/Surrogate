package dev.psyda.surrogate.block;

import dev.psyda.surrogate.flashback.Flashback;
import dev.psyda.surrogate.network.DocumentPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/**
 * A prop with something written on it: the bills on the kitchen table, the calendar on the wall, the page
 * that came out of the printer. A click holds it up to read. Lang keys under
 * {@code document.surrogate.<document>.title} and {@code .body}.
 */
public class ReadableBlock extends PropBlock {
	private final String document;
	private final int style;

	public ReadableBlock(Settings settings, Mount mount, VoxelShape north, String document, int style) {
		super(settings, mount, north);
		this.document = document;
		this.style = style;
	}

	public String document() {
		return document;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (player instanceof ServerPlayerEntity served) open(served, document, style);
		world.playSound(null, pos, SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.BLOCKS, 0.5f, 1.1f);
		return ActionResult.SUCCESS;
	}

	/** Hands {@code document} to {@code player} to read, and tells the flashback they read it. */
	public static void open(ServerPlayerEntity player, String document, int style) {
		ServerPlayNetworking.send(player, new DocumentPayload("document.surrogate." + document + ".title",
				"document.surrogate." + document + ".body", style));
		Flashback.noteRead(player, document);
	}
}
