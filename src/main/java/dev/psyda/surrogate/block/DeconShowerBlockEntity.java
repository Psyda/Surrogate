package dev.psyda.surrogate.block;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.atmosphere.Atmosphere;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.registry.ModBlockEntities;
import dev.psyda.surrogate.registry.ModTags;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DeconShowerBlockEntity extends BlockEntity {
	private static final int CHAMBER_CAP = 24;

	private LongOpenHashSet chamber = new LongOpenHashSet();
	private Box bounds = new Box(0, 0, 0, 0, 0, 0);
	private boolean enclosed;
	private int scanCooldown;
	private int progress;
	private int alarmCooldown;
	private boolean locking;
	private boolean alarm;
	private String alarmSummary = "";

	public DeconShowerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DECON_SHOWER, pos, state);
	}

	/** True while something in the chamber still needs washing, or is carrying what cannot be washed. */
	public boolean isLocking() {
		return enclosed && locking;
	}

	public boolean isAlarm() {
		return enclosed && alarm;
	}

	public boolean chamberContains(BlockPos pos) {
		return enclosed && chamber.contains(pos.asLong());
	}

	/** The shower whose chamber includes {@code cell}, searching a few blocks around it. */
	@Nullable
	public static DeconShowerBlockEntity forCell(World world, BlockPos cell) {
		for (BlockPos candidate : BlockPos.iterate(cell.add(-4, -1, -4), cell.add(4, 5, 4))) {
			if (world.getBlockEntity(candidate) instanceof DeconShowerBlockEntity shower && shower.chamberContains(cell)) {
				return shower;
			}
		}
		return null;
	}

	private void scanChamber(World world) {
		LongOpenHashSet cells = Atmosphere.enclosure(world, pos.down(), CHAMBER_CAP);
		enclosed = cells != null && !cells.isEmpty();
		chamber = enclosed ? cells : new LongOpenHashSet();
		if (enclosed) {
			int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
			for (long packed : chamber) {
				BlockPos cell = BlockPos.fromLong(packed);
				minX = Math.min(minX, cell.getX());
				minY = Math.min(minY, cell.getY());
				minZ = Math.min(minZ, cell.getZ());
				maxX = Math.max(maxX, cell.getX());
				maxY = Math.max(maxY, cell.getY());
				maxZ = Math.max(maxZ, cell.getZ());
			}
			bounds = new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
		}
	}

	public static void tick(World world, BlockPos pos, BlockState state, DeconShowerBlockEntity shower) {
		if (!(world instanceof ServerWorld serverWorld)) return;
		if (--shower.scanCooldown <= 0) {
			shower.scanCooldown = 10;
			shower.scanChamber(world);
		}
		if (shower.alarmCooldown > 0) shower.alarmCooldown--;

		boolean lit = false;
		if (!shower.enclosed) {
			shower.progress = 0;
			shower.locking = false;
			shower.alarm = false;
		} else {
			List<Entity> occupants = world.getOtherEntities(null, shower.bounds, e -> shower.chamber.contains(e.getBlockPos().asLong()));
			List<RobotEntity> dirty = new ArrayList<>();
			Map<Text, Integer> caustic = new LinkedHashMap<>();
			List<ServerPlayerEntity> carriers = new ArrayList<>();
			for (Entity entity : occupants) {
				if (entity instanceof RobotEntity robot && robot.getContamination() > 0.005f) dirty.add(robot);
				if (entity instanceof ServerPlayerEntity player) {
					boolean any = false;
					for (int i = 0; i < player.getInventory().size(); i++) {
						ItemStack stack = player.getInventory().getStack(i);
						if (stack.isIn(ModTags.CAUSTIC_ITEMS)) {
							caustic.merge(stack.getName(), stack.getCount(), Integer::sum);
							any = true;
						}
					}
					if (any) carriers.add(player);
				}
				if (entity instanceof ItemEntity item && item.getStack().isIn(ModTags.CAUSTIC_ITEMS)) {
					caustic.merge(item.getStack().getName(), item.getStack().getCount(), Integer::sum);
				}
			}

			if (!caustic.isEmpty()) {
				shower.alarm = true;
				shower.locking = true;
				shower.progress = 0;
				lit = (world.getTime() / 10) % 2 == 0;
				StringBuilder summary = new StringBuilder();
				caustic.forEach((name, count) -> summary.append(summary.isEmpty() ? "" : ", ").append(name.getString()).append(" x").append(count));
				shower.alarmSummary = summary.toString();
				if (shower.alarmCooldown <= 0) {
					shower.alarmCooldown = 30;
					world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.BLOCKS, 1.0f, 0.5f);
					world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.BLOCKS, 0.6f, 0.6f);
					Text message = Text.translatable("message.surrogate.decon.alarm", shower.alarmSummary).formatted(Formatting.RED);
					for (ServerPlayerEntity player : carriers) player.sendMessage(message, true);
					for (RobotEntity robot : dirty) {
						ServerPlayerEntity pilot = robot.getPilotPlayer();
						if (pilot != null && !carriers.contains(pilot)) pilot.sendMessage(message, true);
					}
				}
			} else if (!dirty.isEmpty()) {
				shower.alarm = false;
				shower.locking = true;
				lit = true;
				if (shower.progress == 0) {
					world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, SoundCategory.BLOCKS, 0.8f, 0.9f);
					for (RobotEntity robot : dirty) {
						ServerPlayerEntity pilot = robot.getPilotPlayer();
						if (pilot != null) pilot.sendMessage(Text.translatable("message.surrogate.decon.start").formatted(Formatting.AQUA), true);
					}
				}
				shower.progress++;
				for (RobotEntity robot : dirty) {
					serverWorld.spawnParticles(ParticleTypes.SPLASH, robot.getX(), robot.getY() + 1.0, robot.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
					serverWorld.spawnParticles(ParticleTypes.CLOUD, robot.getX(), robot.getY() + 0.6, robot.getZ(), 1, 0.2, 0.2, 0.2, 0.01);
				}
				serverWorld.spawnParticles(ParticleTypes.FALLING_WATER, pos.getX() + 0.5, pos.getY() - 0.05, pos.getZ() + 0.5, 4, 0.3, 0.0, 0.3, 0.0);
				if (shower.progress % 10 == 0) {
					world.playSound(null, pos, SoundEvents.WEATHER_RAIN, SoundCategory.BLOCKS, 0.4f, 1.3f);
				}
				if (shower.progress >= Math.max(10, Surrogate.CONFIG.deconTicks)) {
					shower.progress = 0;
					shower.locking = false;
					world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 0.8f, 1.6f);
					for (RobotEntity robot : dirty) {
						robot.setContamination(0f);
						ServerPlayerEntity pilot = robot.getPilotPlayer();
						if (pilot != null) pilot.sendMessage(Text.translatable("message.surrogate.decon.done").formatted(Formatting.GREEN), true);
					}
				}
			} else {
				shower.alarm = false;
				shower.locking = false;
				shower.progress = 0;
			}
		}
		if (state.get(DeconShowerBlock.LIT) != lit) {
			world.setBlockState(pos, state.with(DeconShowerBlock.LIT, lit), Block.NOTIFY_ALL);
		}
	}

	public String getAlarmSummary() {
		return alarmSummary;
	}

	public void sendInfo(ServerPlayerEntity player) {
		if (!enclosed) {
			player.sendMessage(Text.translatable("message.surrogate.decon.open").formatted(Formatting.YELLOW), false);
		} else {
			player.sendMessage(Text.translatable("message.surrogate.decon.info", chamber.size(),
					Text.translatable(alarm ? "message.surrogate.decon.state.alarm" : locking ? "message.surrogate.decon.state.washing" : "message.surrogate.decon.state.idle")), false);
		}
	}
}
