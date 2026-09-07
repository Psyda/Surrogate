package dev.psyda.surrogate.hazard;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.SurrogateConfig;
import dev.psyda.surrogate.atmosphere.Atmosphere;
import dev.psyda.surrogate.atmosphere.ModDamageTypes;
import dev.psyda.surrogate.block.CorrodedMachineBlockEntity;
import dev.psyda.surrogate.crawler.CrawlerInterior;
import dev.psyda.surrogate.crawler.CrawlerInteriors;
import dev.psyda.surrogate.entity.CrawlerEntity;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.entity.RobotModule;
import dev.psyda.surrogate.prologue.Crew;
import dev.psyda.surrogate.registry.ModBlocks;
import dev.psyda.surrogate.registry.ModItems;
import dev.psyda.surrogate.registry.ModTags;
import dev.psyda.surrogate.world.Valleys;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What falls on the belt and what it takes. A machine with sky over it corrodes until it breaks and leaves a
 * stub; a chassis in the open loses hull and is filthy the moment it is out in it; a crawler drinks its charge
 * four times as fast and then the rain starts on the hull. A roof — any solid block above — stops all of it,
 * which is the only building lesson the belt has to teach; a coating or ceramic cladding only makes the same
 * rain cost a fraction, which is what keeps the belt somewhere you go rather than somewhere you live.
 */
public final class AcidRain {
	/** How often the belt is looked at, in ticks. Two seconds: the rain is slow and the sweep is not free. */
	private static final int SWEEP_TICKS = 40;
	private static final double SWEEP_SECONDS = SWEEP_TICKS / 20.0;
	/** Chunks either side of a player that get looked at. Past that nobody is there to watch a panel go. */
	private static final int SWEEP_CHUNKS = 4;
	/** How far from a player a chassis is looked for, matching the storm's reach. */
	private static final double BITE_RANGE = 64.0;
	/** Charge fractions at which the driver is told, each one louder than the last. */
	private static final float[] WARN_AT = {0.6f, 0.4f, 0.2f, 0.05f};
	/** Warning stages past the four: the hull itself going, and the hull stopped for good. */
	private static final int STAGE_FLAT = WARN_AT.length + 1;
	private static final int STAGE_SEIZED = WARN_AT.length + 2;
	/** One plate or kit takes this much of the limit back off a hull or a machine: a seized crawler costs two. */
	private static final double MEND_FRACTION = 0.5;

	private AcidRain() {
	}

	public static void registerEvents() {
		ServerTickEvents.END_SERVER_TICK.register(AcidRain::tick);
		// A plate or a kit against a machine that is still standing. The stub has its own handler on the
		// block; this is for everything the rain has only started on, which could not be mended at all.
		UseBlockCallback.EVENT.register(AcidRain::onUsedOnMachine);
	}

	// ------------------------------------------------------------------ asking

	/** How far the rain has got with the machine at {@code pos}, 0 up to the configured limit. */
	public static double corrosion(ServerWorld world, BlockPos pos) {
		if (!Surrogate.CONFIG.hazards || world.getRegistryKey() != World.OVERWORLD) return 0.0;
		AcidState state = AcidState.get(world.getServer());
		return state.isEmpty() ? 0.0 : state.machine(pos);
	}

	/** Adds wear, and takes the machine apart if that is the last of it. */
	public static void corrode(ServerWorld world, BlockPos pos, double amount) {
		if (!Surrogate.CONFIG.hazards || amount <= 0.0 || world.getRegistryKey() != World.OVERWORLD) return;
		double limit = Math.max(1.0, Surrogate.CONFIG.corrosionLimit);
		double wear = AcidState.get(world.getServer()).machine(pos) + amount;
		if (wear >= limit) {
			wreck(world, pos);
			return;
		}
		AcidState.get(world.getServer()).setMachine(pos, wear);
	}

	/** A repair kit or a hull plate: the machine is sound again and the belt starts from nothing. */
	public static void clear(ServerWorld world, BlockPos pos) {
		AcidState.get(world.getServer()).setMachine(pos, 0.0);
	}

	/**
	 * What a corroded machine still gives, 1 down to 0 at the limit. Read at a generation or drain line every
	 * tick, so it stops at the first cheap test on a planet where nothing has corroded yet.
	 */
	public static float output(World world, BlockPos pos) {
		if (!Surrogate.CONFIG.hazards || !(world instanceof ServerWorld server)) return 1f;
		double wear = corrosion(server, pos);
		if (wear <= 0.0) return 1f;
		return MathHelper.clamp(1f - (float) (wear / Math.max(1.0, Surrogate.CONFIG.corrosionLimit)), 0f, 1f);
	}

	/** How eaten the machine is, 0 to 100, for a readout. */
	public static int percent(ServerWorld world, BlockPos pos) {
		double wear = corrosion(world, pos);
		if (wear <= 0.0) return 0;
		return MathHelper.clamp((int) Math.round(wear * 100.0 / Math.max(1.0, Surrogate.CONFIG.corrosionLimit)), 0, 100);
	}

	/** Machines the belt can eat: everything of the company's that is made of metal and left outdoors. */
	public static boolean corrodible(BlockState state) {
		return state.isIn(ModTags.CORRODIBLE);
	}

	// ------------------------------------------------------------------ the sweep

	private static void tick(MinecraftServer server) {
		if (!Surrogate.CONFIG.hazards) return;
		ServerWorld world = server.getOverworld();
		if (world.getTime() % SWEEP_TICKS != 0) return;
		if (!Valleys.isMesaWorld(world)) return;
		// A hull that has already been eaten stays eaten after the sky clears, so this runs rain or not.
		sweepHulls(server, world);
		if (!Hazards.acidRain(world)) return;
		sweepMachines(server, world);
		sweepChassis(server);
	}

	/**
	 * Every machine in a loaded chunk near a player, which on this planet is every machine anyone could be
	 * watching: at most nine by nine chunks each, and a chunk's block entities are a map that is already in
	 * memory. Nothing loads a chunk and nothing walks a block.
	 */
	private static void sweepMachines(MinecraftServer server, ServerWorld world) {
		LongOpenHashSet seen = new LongOpenHashSet();
		List<BlockPos> exposed = new ArrayList<>();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player.getServerWorld() != world) continue;
			ChunkPos centre = player.getChunkPos();
			for (int dx = -SWEEP_CHUNKS; dx <= SWEEP_CHUNKS; dx++) {
				for (int dz = -SWEEP_CHUNKS; dz <= SWEEP_CHUNKS; dz++) {
					int x = centre.x + dx;
					int z = centre.z + dz;
					if (!seen.add(ChunkPos.toLong(x, z))) continue;
					WorldChunk chunk = world.getChunkManager().getWorldChunk(x, z);
					if (chunk == null) continue;
					for (BlockEntity machine : chunk.getBlockEntities().values()) {
						if (!corrodible(machine.getCachedState())) continue;
						BlockPos pos = machine.getPos();
						if (!Hazards.rainedOn(world, pos)) continue;
						exposed.add(pos.toImmutable());
					}
				}
			}
		}
		// Collected first: taking a machine apart edits the very map that was being read.
		double amount = Surrogate.CONFIG.corrosionPerSecond * SWEEP_SECONDS;
		for (BlockPos pos : exposed) corrode(world, pos, amount);
	}

	/** A chassis standing out in it. The coating is Brandt's pattern cut down, and it is the whole answer. */
	private static void sweepChassis(MinecraftServer server) {
		Set<RobotEntity> caught = new HashSet<>();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			ServerWorld world = player.getServerWorld();
			if (!Hazards.acidRain(world)) continue;
			caught.addAll(world.getEntitiesByClass(RobotEntity.class, player.getBoundingBox().expand(BITE_RANGE), RobotEntity::isAlive));
		}
		SurrogateConfig cfg = Surrogate.CONFIG;
		for (RobotEntity robot : caught) {
			if (!(robot.getWorld() instanceof ServerWorld world)) continue;
			if (!Hazards.rainedOn(world, robot.getBlockPos())) continue;
			// The coating does not keep the rain out, it slows it down: the number on the tooltip, made real.
			// Total immunity would have made the belt somewhere to live rather than somewhere to visit.
			double factor = robot.hasModule(RobotModule.COATING) ? Math.max(0.0, cfg.acidCoatingFactor) : 1.0;
			// The wastes take time to cake a hull; the belt does not, coated or not.
			robot.setContamination(1f);
			robot.damage(ModDamageTypes.acid(world), (float) (cfg.acidChassisDamagePerSecond * SWEEP_SECONDS * factor));
		}
	}

	/**
	 * Every hull with a cabin allocated to it, which is every hull that has ever been boarded. A hull whose
	 * chunk is not loaded simply is not found, and nothing here asks for one.
	 */
	private static void sweepHulls(MinecraftServer server, ServerWorld world) {
		for (CrawlerInteriors.Slot slot : CrawlerInteriors.get(server).slots()) {
			if (slot.hull == null) continue;
			if (world.getEntity(slot.hull) instanceof CrawlerEntity hull) sweepHull(server, world, hull);
		}
	}

	private static void sweepHull(MinecraftServer server, ServerWorld world, CrawlerEntity hull) {
		SurrogateConfig cfg = Surrogate.CONFIG;
		AcidState state = AcidState.get(server);
		UUID id = hull.getUuid();
		double limit = Math.max(1.0, cfg.corrosionLimit);
		double wear = state.hullWear(id);
		int warned = state.hullWarned(id);
		boolean wet = Hazards.rainedOn(world, hull.getBlockPos());
		// The ceramic is not a roof. It makes the belt cost a fifth as much, which is the difference between
		// a dash across and a day out there; it does not make the crawler a place to park in the rain.
		double factor = hull.hasCladding() ? Math.max(0.0, cfg.acidCladdingFactor) : 1.0;

		if (wear >= limit) {
			// Stopped for good until somebody spends plate on it: anything put in leaks straight back out.
			if (hull.getEnergy() > 0) hull.setEnergy(0);
			if (warned < STAGE_SEIZED) {
				state.setHull(id, wear, STAGE_SEIZED);
				alarm(server, hull, "message.surrogate.acid.seized", true);
			}
			return;
		}
		if (!wet) {
			// Out of the rain and charged again: the next trip out gets its own set of warnings.
			if (warned > 0 && wear <= 0.0 && hull.getEnergyFraction() > 0.7f) state.setHull(id, 0.0, 0);
			return;
		}

		if (hull.getEnergy() > 0) {
			// Four times as thirsty, so the extra three parts are taken here rather than in the drive line.
			if (hull.getSpeed() != 0f) {
				int extra = (int) Math.round(Math.max(0, cfg.acidCrawlerDrainMultiplier - 1)
						* (double) cfg.crawlerDrivePerTick * SWEEP_TICKS * factor);
				if (extra > 0) hull.drainEnergy(extra);
			}
			int stage = 0;
			float charge = hull.getEnergyFraction();
			for (int i = 0; i < WARN_AT.length; i++) {
				if (charge < WARN_AT[i]) stage = i + 1;
			}
			if (stage > warned && warned < STAGE_FLAT) {
				state.setHull(id, wear, stage);
				warn(server, hull, stage);
			}
			return;
		}

		// Flat, in the rain, with nothing over it. Now it is the hull's turn, and it is never quiet about it.
		double next = wear + cfg.corrosionPerSecond * SWEEP_SECONDS * factor;
		if (warned < STAGE_FLAT) {
			state.setHull(id, next, STAGE_FLAT);
			alarm(server, hull, "message.surrogate.acid.flat", false);
		} else {
			state.setHull(id, next, warned);
			if (world.getTime() % 200 == 0) alarm(server, hull, "message.surrogate.acid.eating", false);
		}
	}

	// ------------------------------------------------------------------ telling the driver

	/** One of the four, on the band and on the HUD, to whoever is riding in the cabin. */
	private static void warn(MinecraftServer server, CrawlerEntity hull, int stage) {
		Text line = radio(Text.translatable("message.surrogate.acid.crawler_" + stage));
		Formatting colour = stage >= 3 ? Formatting.RED : Formatting.GOLD;
		int charge = Math.round(hull.getEnergyFraction() * 100f);
		for (ServerPlayerEntity player : CrawlerInterior.playersAboard(server, hull)) {
			player.sendMessage(line, false);
			player.sendMessage(Text.translatable("message.surrogate.acid.hud", charge).formatted(colour), true);
			player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.7f, stage >= 3 ? 0.6f : 0.9f);
		}
	}

	/** The hull itself, which is worth a title over the glass rather than a line in the chat. */
	private static void alarm(MinecraftServer server, CrawlerEntity hull, String key, boolean stopped) {
		Text line = radio(Text.translatable(key));
		int hullPercent = 100 - hullPercent(server, hull);
		for (ServerPlayerEntity player : CrawlerInterior.playersAboard(server, hull)) {
			player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 50, 15));
			player.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable(stopped ? "title.surrogate.acid.seized" : "title.surrogate.acid.hull")));
			player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("title.surrogate.acid.hull.sub", hullPercent)));
			player.sendMessage(line, false);
			player.playSoundToPlayer(SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, 0.5f);
		}
	}

	private static Text radio(Text line) {
		return Text.literal("[RADIO] ").formatted(Formatting.DARK_AQUA)
				.append(Crew.HALLORAN.displayName().copy().formatted(Formatting.GOLD))
				.append(Text.literal(": ").formatted(Formatting.GRAY))
				.append(line.copy().formatted(Formatting.WHITE));
	}

	// ------------------------------------------------------------------ the hull

	/** How far gone a hull is, 0 to 100. At 100 it is where it stays. */
	public static int hullPercent(MinecraftServer server, CrawlerEntity hull) {
		if (!Surrogate.CONFIG.hazards) return 0;
		double wear = AcidState.get(server).hullWear(hull.getUuid());
		if (wear <= 0.0) return 0;
		return MathHelper.clamp((int) Math.round(wear * 100.0 / Math.max(1.0, Surrogate.CONFIG.corrosionLimit)), 0, 100);
	}

	/**
	 * Eat into a hull from somewhere that is not the belt's rain, and shout about it the way the rain does.
	 *
	 * <p>The floor of the Rift is the other place on Sallow that takes a crawler apart, and it does it in a
	 * pocket of standing mist rather than in weather, so it has its own sweep. What it wants from here is the
	 * mechanic: a crawler cannot be damaged at all ({@code CrawlerEntity.damage} refuses everything), and
	 * corrosion is the only thing a hull actually feels, seizes for, and is mended of with a plate.
	 */
	public static void corrode(MinecraftServer server, CrawlerEntity hull, double amount) {
		if (!Surrogate.CONFIG.hazards || amount <= 0.0) return;
		AcidState state = AcidState.get(server);
		UUID id = hull.getUuid();
		double limit = Math.max(1.0, Surrogate.CONFIG.corrosionLimit);
		double wear = state.hullWear(id);
		if (wear >= limit) {
			if (hull.getEnergy() > 0) hull.setEnergy(0);
			if (state.hullWarned(id) < STAGE_SEIZED) {
				state.setHull(id, wear, STAGE_SEIZED);
				alarm(server, hull, "message.surrogate.acid.seized", true);
			}
			return;
		}
		double next = wear + amount;
		int warned = state.hullWarned(id);
		state.setHull(id, next, Math.max(warned, STAGE_FLAT));
		if (warned < STAGE_FLAT) alarm(server, hull, "message.surrogate.acid.flat", false);
	}

	/**
	 * A plate or a repair kit against the hull. Half the limit each, so a crawler that stopped in the belt
	 * costs two of them and a walk out with them in your hands.
	 */
	public static boolean mend(PlayerEntity player, ItemStack stack, CrawlerEntity hull) {
		if (!(hull.getWorld() instanceof ServerWorld world)) return false;
		AcidState state = AcidState.get(world.getServer());
		UUID id = hull.getUuid();
		double wear = state.hullWear(id);
		if (wear <= 0.0) return false;
		double left = Math.max(0.0, wear - Math.max(1.0, Surrogate.CONFIG.corrosionLimit) * MEND_FRACTION);
		state.setHull(id, left, left <= 0.0 ? 0 : Math.min(state.hullWarned(id), STAGE_FLAT));
		if (!player.isCreative()) stack.decrement(1);
		hull.playSound(SoundEvents.BLOCK_ANVIL_USE, 0.8f, 1.2f);
		int percent = 100 - hullPercent(world.getServer(), hull);
		player.sendMessage(Text.translatable(left <= 0.0 ? "message.surrogate.acid.mended" : "message.surrogate.acid.mending", percent)
				.formatted(Formatting.AQUA), true);
		return true;
	}

	// ------------------------------------------------------------------ the machine

	/** A plate or a kit in hand, used on something the belt has been at: take some of the wear back off it. */
	private static ActionResult onUsedOnMachine(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
		if (hand != Hand.MAIN_HAND || !(world instanceof ServerWorld server)) return ActionResult.PASS;
		// Sneaking is the building gesture: somebody roofing a panel over is still allowed to place the plate.
		if (player.isSneaking()) return ActionResult.PASS;
		ItemStack stack = player.getStackInHand(hand);
		if (!stack.isOf(ModItems.HULL_PLATING) && !stack.isOf(ModItems.REPAIR_KIT)) return ActionResult.PASS;
		BlockPos pos = hit.getBlockPos();
		if (!corrodible(server.getBlockState(pos))) return ActionResult.PASS;
		return patch(player, stack, server, pos) ? ActionResult.SUCCESS : ActionResult.PASS;
	}

	/**
	 * Half the limit off a machine, the same as a hull takes, so a panel caught early costs one plate and a
	 * panel left out in it costs two. A machine with nothing on it is left alone: the plate is worth more.
	 */
	public static boolean patch(PlayerEntity player, ItemStack stack, ServerWorld world, BlockPos pos) {
		double wear = corrosion(world, pos);
		if (wear <= 0.0) return false;
		double left = Math.max(0.0, wear - Math.max(1.0, Surrogate.CONFIG.corrosionLimit) * MEND_FRACTION);
		AcidState.get(world.getServer()).setMachine(pos, left);
		if (!player.isCreative()) stack.decrement(1);
		world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.8f, 1.2f);
		world.spawnParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
		player.sendMessage(Text.translatable(left <= 0.0 ? "message.surrogate.acid.patched" : "message.surrogate.acid.patching",
				100 - percent(world, pos)).formatted(Formatting.AQUA), true);
		return true;
	}

	/** Eaten through: the machine goes, drops nothing, and leaves a stub with its shape still in it. */
	private static void wreck(ServerWorld world, BlockPos pos) {
		BlockState was = world.getBlockState(pos);
		if (!corrodible(was)) {
			// Somebody has already pulled it out; the belt has nothing left to remember here.
			clear(world, pos);
			return;
		}
		world.setBlockState(pos, ModBlocks.CORRODED_MACHINE.getDefaultState(), Block.NOTIFY_ALL);
		BlockEntity stub = world.getBlockEntity(pos);
		if (stub instanceof CorrodedMachineBlockEntity remnant) remnant.setOriginal(was);
		AcidState.get(world.getServer()).setMachine(pos, Math.max(1.0, Surrogate.CONFIG.corrosionLimit));
		Atmosphere.invalidateAround(world, pos);
		world.playSound(null, pos, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 0.9f, 0.6f);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.01);
		Text name = was.getBlock().getName();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0 * 64.0) continue;
			player.sendMessage(Text.translatable("message.surrogate.acid.machine_lost", name).formatted(Formatting.RED), false);
		}
	}

	/** The machine a stub used to be, for anything that wants to name it. */
	@Nullable
	public static BlockState original(World world, BlockPos pos) {
		return world.getBlockEntity(pos) instanceof CorrodedMachineBlockEntity remnant ? remnant.getOriginal() : null;
	}
}
