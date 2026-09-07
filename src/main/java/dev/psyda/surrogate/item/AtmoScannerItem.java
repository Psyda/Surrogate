package dev.psyda.surrogate.item;

import dev.psyda.surrogate.atmosphere.Atmosphere;
import dev.psyda.surrogate.atmosphere.Exposure;
import dev.psyda.surrogate.atmosphere.SealedVolume;
import dev.psyda.surrogate.hazard.AcidRain;
import dev.psyda.surrogate.hazard.Hazards;
import dev.psyda.surrogate.registry.ModBlocks;
import dev.psyda.surrogate.world.Valleys;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.List;

/**
 * Reads the air where you (or your chassis) stand: sealed, leaking and where, or open sky. Sneak-use on a
 * block to ask whether that block is airtight. Works in the hand and through a chassis.
 */
public class AtmoScannerItem extends Item {
	private static final DustParticleEffect LEAK_MARKER = new DustParticleEffect(new Vector3f(1.0f, 0.25f, 0.1f), 1.6f);

	public AtmoScannerItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		if (world instanceof ServerWorld serverWorld) {
			scan(serverWorld, user);
		}
		return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		PlayerEntity player = context.getPlayer();
		if (player == null) return ActionResult.PASS;
		if (!(world instanceof ServerWorld serverWorld)) return ActionResult.SUCCESS;
		if (player.isSneaking()) {
			BlockPos pos = context.getBlockPos();
			BlockState state = world.getBlockState(pos);
			boolean airtight = Atmosphere.isAirtight(world, pos, state);
			player.sendMessage(Text.translatable(airtight ? "message.surrogate.scanner.airtight" : "message.surrogate.scanner.porous", state.getBlock().getName())
					.formatted(airtight ? Formatting.GREEN : Formatting.YELLOW), true);
			reportCorrosion(serverWorld, player, pos, state);
		} else {
			scan(serverWorld, player);
		}
		return ActionResult.CONSUME;
	}

	private void scan(ServerWorld world, PlayerEntity player) {
		player.getItemCooldownManager().set(this, 15);
		BlockPos pos = BlockPos.ofFloored(player.getEyePos());
		boolean toxic = Atmosphere.isToxic(world, pos);
		SealedVolume volume = Atmosphere.volumeAt(world, pos);
		world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.PLAYERS, 0.5f, 1.4f);
		reportBelt(world, player, pos);

		if (volume == null) {
			player.sendMessage(Text.translatable(toxic ? "message.surrogate.scanner.open_toxic" : "message.surrogate.scanner.open_clean")
					.formatted(toxic ? Formatting.RED : Formatting.GREEN), false);
			return;
		}
		Text unit = Text.literal(volume.origin.getX() + ", " + volume.origin.getY() + ", " + volume.origin.getZ());
		Text power = Text.translatable(volume.powered ? "message.surrogate.life_support.powered" : "message.surrogate.life_support.unpowered");
		if (volume.sealed) {
			player.sendMessage(Text.translatable("message.surrogate.scanner.sealed", volume.size, Exposure.percent(volume.quality), unit, power)
					.formatted(Formatting.GREEN), false);
			if (volume.isContaminated()) {
				player.sendMessage(Text.translatable("message.surrogate.contamination", volume.dirtyChassis, volume.causticItems, volume.causticBlocks)
						.formatted(Formatting.YELLOW), false);
			}
			return;
		}
		BlockPos leak = volume.leak;
		if (leak == null) {
			player.sendMessage(Text.translatable("message.surrogate.scanner.leak_unknown").formatted(Formatting.RED), false);
			return;
		}
		double dx = leak.getX() + 0.5 - player.getX();
		double dy = leak.getY() + 0.5 - player.getY();
		double dz = leak.getZ() + 0.5 - player.getZ();
		int distance = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
		Direction toward = Direction.getFacing(dx, dy, dz);
		player.sendMessage(Text.translatable("message.surrogate.scanner.leak",
				leak.getX() + ", " + leak.getY() + ", " + leak.getZ(), distance,
				Text.translatable("direction.surrogate." + toward.asString()), Exposure.percent(volume.quality)).formatted(Formatting.RED), false);
		world.spawnParticles(LEAK_MARKER, leak.getX() + 0.5, leak.getY() + 0.5, leak.getZ() + 0.5, 40, 0.4, 0.4, 0.4, 0.0);
		world.playSound(null, leak, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.BLOCKS, 1.0f, 0.6f);
	}

	/**
	 * Whether this column is in the belt and whether it is falling. The mask is read straight out of the
	 * noise, so the answer costs nothing and does not need a chunk; outside Sallow there is no belt to name.
	 */
	private static void reportBelt(ServerWorld world, PlayerEntity player, BlockPos pos) {
		if (!Valleys.isMesaWorld(world)) return;
		if (!Valleys.inBelt(world, pos.getX(), pos.getZ())) {
			player.sendMessage(Text.translatable("message.surrogate.scanner.belt_out").formatted(Formatting.GRAY), false);
			return;
		}
		boolean falling = Hazards.acidRain(world);
		player.sendMessage(Text.translatable(falling ? "message.surrogate.scanner.belt_rain" : "message.surrogate.scanner.belt_dry")
				.formatted(falling ? Formatting.RED : Formatting.YELLOW), false);
	}

	/** How eaten the machine under the crosshair is: the other half of what the scanner is for in the belt. */
	private static void reportCorrosion(ServerWorld world, PlayerEntity player, BlockPos pos, BlockState state) {
		if (!AcidRain.corrodible(state) && !state.isOf(ModBlocks.CORRODED_MACHINE)) return;
		int eaten = AcidRain.percent(world, pos);
		if (eaten <= 0) {
			player.sendMessage(Text.translatable("message.surrogate.scanner.machine_sound").formatted(Formatting.GREEN), false);
			return;
		}
		player.sendMessage(Text.translatable("message.surrogate.acid.corroded", eaten)
				.formatted(eaten >= 50 ? Formatting.RED : Formatting.YELLOW), false);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.surrogate.scanner.use").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.surrogate.scanner.block").formatted(Formatting.DARK_GRAY));
	}
}
