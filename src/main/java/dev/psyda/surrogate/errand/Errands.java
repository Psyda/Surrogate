package dev.psyda.surrogate.errand;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.entity.BorerEntity;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.entity.RobotModule;
import dev.psyda.surrogate.fauna.FaunaEntity;
import dev.psyda.surrogate.fauna.Specimen;
import dev.psyda.surrogate.registry.ModBlocks;
import dev.psyda.surrogate.registry.ModItems;
import dev.psyda.surrogate.survivor.Survivor;
import dev.psyda.surrogate.survivor.SurvivorEntity;
import dev.psyda.surrogate.survivor.SurvivorManager;
import dev.psyda.surrogate.world.HabitatState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.EulerAngle;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The optional work: what unlocks it, what finishes it, and what it is worth.
 *
 * <p>Everything here is a watcher rather than a quest log. No errand asks the player to press accept; they
 * are offered on the radio when their gate opens, and each one finishes when the world says it has. That
 * matters for the ending, which reads {@link ErrandState#done} directly — a player who wandered into
 * finishing one without noticing gets the credit for it, because they did the thing.
 *
 * <p>The two that need a whole scene of their own — the housewarming's module drop and act six's vote — are
 * not here. This is the bookkeeping; {@link Housewarming} is the scene.
 */
public final class Errands {
	/** Ticks between sweeps. Half a second is far more often than any of this changes. */
	private static final int SWEEP = 10;

	/** How near home counts as home, for the tiredness and for bringing the cat back. */
	private static final int AT_HOME = 24;
	/** How far Ballast wanders. Far enough to need the chassis, near enough to be a walk and not a drive. */
	private static final int CAT_WANDER = 180;
	/** How far from Reyes' door a grave has to be. Not on her step. */
	private static final int GRAVE_CLEARANCE = 6;
	/** Corroded machines Brandt wants back, of the ones the rain took. */
	public static final int MACHINES = 3;

	private static int sweepIn = SWEEP;

	private Errands() {
	}

	public static void registerEvents() {
		ServerTickEvents.END_SERVER_TICK.register(Errands::tick);
		// The housewarming is a Director and wants a real tick, not the sweep's every-tenth: a script run at
		// a tenth speed reads every line ten times too slowly and never finishes anything.
		ServerTickEvents.END_SERVER_TICK.register(Housewarming::tick);
		UseEntityCallback.EVENT.register(Errands::onUseEntity);
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register(Errands::onUseBlock);
	}

	/**
	 * The two things done to the ground rather than to an animal: a reading of the seep or the crust, and a
	 * marker put up over a grave. Both are deliberately the same button as everything else.
	 */
	private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, net.minecraft.util.hit.BlockHitResult hit) {
		if (world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
		if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
		ItemStack stack = player.getStackInHand(hand);
		if (stack.isOf(ModItems.SURVEY_MARKER) && buryHere(serverPlayer, hit.getBlockPos().up())) {
			if (!player.getAbilities().creativeMode) stack.decrement(1);
			return ActionResult.SUCCESS;
		}
		if (stack.isEmpty() && sampleBlock(serverPlayer, hit.getBlockPos())) return ActionResult.SUCCESS;
		return ActionResult.PASS;
	}

	// ------------------------------------------------------------------ the clock

	private static void tick(MinecraftServer server) {
		if (--sweepIn > 0) return;
		sweepIn = SWEEP;
		HabitatState habitat = HabitatState.get(server);
		if (habitat.origin == null) return;
		ErrandState errands = ErrandState.get(server);
		ServerWorld world = server.getOverworld();
		offer(server, errands, habitat);
		watchHousewarming(server, errands, habitat);
		watchBallast(server, errands, habitat, world);
		watchVent(server, errands);
		watchCorroded(server, errands);
	}

	/**
	 * Whether each errand's gate has opened. Offering is one line on the radio and a note in the log; it is
	 * not a contract, and nothing anywhere checks that an errand was offered before letting it be finished.
	 */
	private static void offer(MinecraftServer server, ErrandState errands, HabitatState habitat) {
		SurvivorManager survivors = SurvivorManager.get(server);
		for (Errand errand : Errand.all()) {
			if (errands.offered(errand) || !gateOpen(server, errands, survivors, errand)) continue;
			open(server, errands, habitat, errand);
		}
	}

	/**
	 * Offer one, and set up whatever it needs in the world. Split out from the sweep so the test command can
	 * open an errand without waiting for its gate: everything the sweep would have done happens here, so a
	 * forced errand and an earned one are the same errand.
	 */
	public static void open(MinecraftServer server, ErrandState errands, HabitatState habitat, Errand errand) {
		errands.offer(errand);
		announce(server, errand);
		// The two errands that hand something over do it at the moment they are offered, because both are
		// the survivor giving you the tool for the job they are about to describe.
		if (errand == Errand.SURVEY) give(server, ModItems.BIO_SAMPLER, ModItems.ANALYSIS_DISK);
		if (errand == Errand.ARK) give(server, ModItems.SPECIMEN_BAG);
		if (errand == Errand.BURIAL) placeBody(server, errands);
		if (errand == Errand.VENT_CLEAR) placeVent(server);
		if (errand == Errand.BALLAST) sendCatOff(server, habitat);
	}

	/**
	 * Where an errand happens, in world coordinates, or null when the world does not know yet — a survivor
	 * whose shelter has not been placed, or a cat in a world that never had one. This is what the test
	 * command's teleport aims at, and it is deliberately the same answer the errand itself uses.
	 */
	@Nullable
	public static BlockPos locate(MinecraftServer server, Errand errand) {
		HabitatState habitat = HabitatState.get(server);
		ErrandState errands = ErrandState.get(server);
		// The two with a specific spot in the world answer with it, whatever their Where tag says: a grave
		// is at the body, not at the clinic, and Tanaka's vent is not at Tanaka's door.
		if (errand == Errand.BURIAL && errands.bodyPos != null) return errands.bodyPos;
		if (errand == Errand.VENT_CLEAR) {
			BlockPos vent = ventPos(server);
			if (vent != null) return vent;
		}
		return switch (errand.where()) {
			case HOME -> habitat.origin;
			case SURVIVOR -> {
				Survivor who = errand.who();
				if (who == null) yield habitat.origin;
				SurvivorManager.Site site = siteOf(server, who);
				yield site == null ? null : site.origin();
			}
			case CAT -> {
				CatEntity cat = cat(server, habitat);
				yield cat == null ? habitat.origin : cat.getBlockPos();
			}
		};
	}

	private static boolean gateOpen(MinecraftServer server, ErrandState errands, SurvivorManager survivors, Errand errand) {
		return switch (errand) {
			// Both of the two you start beside, helped. That is the whole gate, and it is the only errand
			// whose gate is another errand rather than an act.
			case HOUSEWARMING -> helped(server, Survivor.OKAFOR) && helped(server, Survivor.SORENSEN);
			case HOT_MEAL -> helped(server, Survivor.SORENSEN);
			case SURVEY -> helped(server, Survivor.OKAFOR) && errands.done(Errand.HOUSEWARMING);
			case BALLAST -> errands.done(Errand.HOUSEWARMING);
			case VENT_CLEAR -> helped(server, Survivor.TANAKA);
			// The three late ones all wait on their person being off the far side and home.
			case BURIAL -> rescued(survivors, Survivor.REYES);
			case CORRODED, ARK -> rescued(survivors, Survivor.BRANDT);
		};
	}

	/** Their standing need has been met: read off the person, the way the research runs read it. */
	public static boolean helped(MinecraftServer server, Survivor who) {
		SurvivorManager survivors = SurvivorManager.get(server);
		ServerWorld world = server.getOverworld();
		for (SurvivorManager.Site site : survivors.sites()) {
			if (site.character != who.ordinal() || !site.built) continue;
			if (site.rescued) return true;
			if (!world.isChunkLoaded(site.x >> 4, site.z >> 4)) continue;
			List<SurvivorEntity> found = world.getEntitiesByClass(SurvivorEntity.class, new Box(site.origin()).expand(10.0),
					person -> person.getCharacter() == who && person.isRescued());
			if (!found.isEmpty()) return true;
		}
		return false;
	}

	private static boolean rescued(SurvivorManager survivors, Survivor who) {
		for (SurvivorManager.Site site : survivors.sites()) {
			if (site.character == who.ordinal()) return site.rescued;
		}
		return false;
	}

	// ------------------------------------------------------------------ the housewarming

	/**
	 * The tiredness. Offered, the player is told they are worn out the first time they are back inside their
	 * own base; from then on the errand is waiting on a night's sleep, which {@link Housewarming} takes over.
	 */
	private static void watchHousewarming(MinecraftServer server, ErrandState errands, HabitatState habitat) {
		if (!errands.offered(Errand.HOUSEWARMING) || errands.done(Errand.HOUSEWARMING)) return;
		if (errands.tiredDay >= 0L) return;
		ServerPlayerEntity player = protagonist(server);
		if (player == null || !nearHome(player, habitat)) return;
		errands.tiredDay = server.getOverworld().getTimeOfDay() / 24000L;
		errands.markDirty();
		player.sendMessage(Errand.HOUSEWARMING.line("tired").formatted(Formatting.GRAY), false);
	}

	// ------------------------------------------------------------------ the small ones

	/** Ballast, once she is missing, is home when she is back inside the fence. */
	private static void watchBallast(MinecraftServer server, ErrandState errands, HabitatState habitat, ServerWorld world) {
		if (!errands.offered(Errand.BALLAST) || errands.done(Errand.BALLAST)) return;
		CatEntity cat = cat(server, habitat);
		if (cat == null) return;
		if (cat.getBlockPos().isWithinDistance(habitat.origin, AT_HOME)) {
			// She rides home on the chassis and gets off by herself, which is the only part of this she
			// does willingly.
			cat.stopRiding();
			finish(server, errands, Errand.BALLAST);
		}
	}

	/** Tanaka's vent is clear when something is capping it. */
	private static void watchVent(MinecraftServer server, ErrandState errands) {
		if (!errands.offered(Errand.VENT_CLEAR) || errands.done(Errand.VENT_CLEAR)) return;
		BlockPos at = ventPos(server);
		if (at == null) return;
		ServerWorld world = server.getOverworld();
		if (!world.isChunkLoaded(at.getX() >> 4, at.getZ() >> 4)) return;
		if (world.getBlockState(at).isOf(ModBlocks.GEOTHERMAL_TAP)) finish(server, errands, Errand.VENT_CLEAR);
	}

	/** Brandt's neighbours' machines. The count is kept by the repair itself; this only notices the third. */
	private static void watchCorroded(MinecraftServer server, ErrandState errands) {
		if (!errands.offered(Errand.CORRODED) || errands.done(Errand.CORRODED)) return;
		if (errands.machinesFixed >= MACHINES) finish(server, errands, Errand.CORRODED);
	}


	// ------------------------------------------------------------------ what the world answers back

	/**
	 * A chassis touched something. Everything the sampler and the crate do goes through here, and the order
	 * matters: a reading first, because a specimen you have not read is one you want to read before you shut
	 * it in a box, and because that ordering means one button does the obvious thing twice.
	 */
	private static ActionResult onUseEntity(PlayerEntity player, World world, Hand hand, Entity target, @Nullable net.minecraft.util.hit.EntityHitResult hit) {
		if (world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
		if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
		if (!player.getStackInHand(hand).isEmpty()) return ActionResult.PASS;
		RobotEntity chassis = chassisOf(player);
		Specimen what = specimenOf(target);
		if (what != null && chassis != null) {
			if (chassis.hasModule(RobotModule.SAMPLER) && takeReading(serverPlayer, target, what)) return ActionResult.SUCCESS;
			if (chassis.hasModule(RobotModule.COLLECTOR) && crate(serverPlayer, target, what)) return ActionResult.SUCCESS;
		}
		if (target instanceof CatEntity cat && carryCat(serverPlayer, cat)) return ActionResult.SUCCESS;
		if (target instanceof ArmorStandEntity stand && liftBody(serverPlayer, stand)) return ActionResult.SUCCESS;
		return ActionResult.PASS;
	}

	/** What row of Okafor's table this entity is, or null for anything that is not on it. */
	@Nullable
	public static Specimen specimenOf(Entity entity) {
		if (entity instanceof FaunaEntity fauna) return fauna.specimen();
		if (entity instanceof BorerEntity) return Specimen.BORER;
		if (entity instanceof CatEntity) return Specimen.CAT;
		return null;
	}

	/** A reading. Returns false when there was nothing new to say, so the caller can try the crate instead. */
	public static boolean takeReading(ServerPlayerEntity player, @Nullable Entity subject, Specimen what) {
		ErrandState errands = ErrandState.get(player.server);
		if (subject instanceof FaunaEntity fauna) {
			if (fauna.wasRead() && errands.hasRead(what)) return false;
			fauna.setRead(true);
		} else if (errands.hasRead(what)) {
			return false;
		}
		boolean fresh = errands.fileReading(what);
		player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(),
				SoundCategory.PLAYERS, 0.6f, 1.4f);
		if (!fresh) return false;
		player.sendMessage(Text.translatable("message.surrogate.survey.filed",
				Text.translatable(what.nameKey()), errands.readCount(), Specimen.all().length).formatted(Formatting.AQUA), false);
		if (errands.readCount() >= Specimen.all().length && !errands.done(Errand.SURVEY)) {
			finish(player.server, errands, Errand.SURVEY);
		}
		return true;
	}

	/** Into a crate, alive, one of each. Refused by anything that is not going to hold still for it. */
	private static boolean crate(ServerPlayerEntity player, Entity subject, Specimen what) {
		if (!what.live()) return false;
		ErrandState errands = ErrandState.get(player.server);
		if (errands.hasCaged(what)) {
			player.sendMessage(Text.translatable("message.surrogate.bag.already", Text.translatable(what.nameKey())), true);
			return false;
		}
		if (subject instanceof FaunaEntity fauna && !fauna.canBeBagged()) {
			String why = fauna.bagRefusal();
			if (why != null) player.sendMessage(Text.translatable(why), true);
			return false;
		}
		if (subject instanceof BorerEntity) {
			// A borer will not be handled and there is no version of this where it goes in a box.
			player.sendMessage(Text.translatable("message.surrogate.bag.borer"), true);
			return false;
		}
		errands.fileCage(what);
		subject.discard();
		player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BARREL_CLOSE,
				SoundCategory.PLAYERS, 0.7f, 1.1f);
		player.sendMessage(Text.translatable("message.surrogate.bag.crated",
				Text.translatable(what.nameKey()), errands.cagedCount(), Specimen.liveCount()).formatted(Formatting.GREEN), false);
		if (errands.cagedCount() >= Specimen.liveCount() && !errands.done(Errand.ARK)) {
			finish(player.server, errands, Errand.ARK);
		}
		return true;
	}

	/** Ballast, picked up. She rides whatever picked her up until it gets home or thinks better of it. */
	private static boolean carryCat(ServerPlayerEntity player, CatEntity cat) {
		ErrandState errands = ErrandState.get(player.server);
		if (!errands.offered(Errand.BALLAST) || errands.done(Errand.BALLAST)) return false;
		Entity ride = player.getVehicle() != null ? player.getVehicle() : player;
		if (cat.hasVehicle()) {
			cat.stopRiding();
			return true;
		}
		cat.startRiding(ride, true);
		player.sendMessage(Errand.BALLAST.line("picked_up"), true);
		return true;
	}

	/**
	 * The body outside Clinic Nine. Lifting it is the easy half; the errand finishes when it is laid in the
	 * ground somewhere that is not her doorstep, which is {@link #buryHere}.
	 */
	private static boolean liftBody(ServerPlayerEntity player, ArmorStandEntity stand) {
		ErrandState errands = ErrandState.get(player.server);
		if (errands.bodyId == null || !stand.getUuid().equals(errands.bodyId)) return false;
		return lift(player, errands, stand);
	}

	/**
	 * Picking him up, or putting him down again. Shared with {@code /surrogate errand lift}, which is the
	 * only way to reach this without a chassis and a right-click on an entity: the smoke test cannot land a
	 * click on one, and the half of this errand worth testing is what happens afterwards.
	 */
	public static boolean lift(ServerPlayerEntity player, ErrandState errands, ArmorStandEntity stand) {
		if (!errands.offered(Errand.BURIAL) || errands.done(Errand.BURIAL)) return false;
		Entity ride = player.getVehicle() != null ? player.getVehicle() : player;
		if (stand.hasVehicle()) {
			stand.stopRiding();
			return true;
		}
		stand.startRiding(ride, true);
		player.sendMessage(Errand.BURIAL.line("lifted"), true);
		return true;
	}

	/** The armour stand this save calls Petrov, wherever it is, or null when nothing has been placed. */
	@Nullable
	public static ArmorStandEntity body(MinecraftServer server, ErrandState errands) {
		if (errands.bodyId == null) return null;
		return server.getOverworld().getEntity(errands.bodyId) instanceof ArmorStandEntity stand ? stand : null;
	}

	/**
	 * Digging the grave. Called when a survey marker is used on the ground while the body is being carried:
	 * it opens the ground, lays him in it, closes it and leaves the marker standing. One action, because the
	 * alternative was asking the player to shovel, and that is not what this scene is.
	 *
	 * <p>The marker really is raised. It used to be taken out of the hand and never put anywhere, which left
	 * a patch of ash in the middle of a hundred kilometres of identical ash and an errand that had quietly
	 * finished; the one thing the errand is named for was the one thing it did not do.
	 *
	 * @param marker the cell the marker would stand in, which is the one above the block that was clicked
	 */
	public static boolean buryHere(ServerPlayerEntity player, BlockPos marker) {
		ErrandState errands = ErrandState.get(player.server);
		if (!errands.offered(Errand.BURIAL) || errands.done(Errand.BURIAL)) return false;
		ArmorStandEntity body = carriedBody(player);
		if (body == null) return false;
		SurvivorManager.Site clinic = siteOf(player.server, Survivor.REYES);
		if (clinic != null && marker.isWithinDistance(clinic.origin(), GRAVE_CLEARANCE)) {
			player.sendMessage(Errand.BURIAL.line("too_close").formatted(Formatting.RED), true);
			return false;
		}
		ServerWorld world = player.getServerWorld();
		BlockPos grave = marker.down();
		// Ground to dig, ground under that, and room to stand a marker in. A click on the side of a boulder
		// answers none of them, and burying him inside one would be a worse ending than leaving him on her
		// step. The floor below matters because the grave is ash and ash falls: laid over a hollow, the whole
		// thing drops out from under the marker within a second of being made.
		BlockPos under = grave.down();
		if (!world.getBlockState(grave).isSolidBlock(world, grave)
				|| !world.getBlockState(under).isSolidBlock(world, under)
				|| !world.getBlockState(marker).isReplaceable()) {
			player.sendMessage(Errand.BURIAL.line("no_ground").formatted(Formatting.RED), true);
			return false;
		}
		body.stopRiding();
		body.discard();
		world.setBlockState(grave, ModBlocks.ASH.getDefaultState());
		world.setBlockState(marker, ModBlocks.SURVEY_MARKER.getDefaultState());
		errands.gravePos = grave;
		errands.markDirty();
		world.playSound(null, marker, SoundEvents.BLOCK_ROOTED_DIRT_PLACE, SoundCategory.BLOCKS, 0.8f, 0.7f);
		world.playSound(null, marker, SoundEvents.BLOCK_NETHERITE_BLOCK_PLACE, SoundCategory.BLOCKS, 0.7f, 0.8f);
		world.spawnParticles(net.minecraft.particle.ParticleTypes.ASH, marker.getX() + 0.5, marker.getY() + 0.2,
				marker.getZ() + 0.5, 40, 0.5, 0.4, 0.5, 0.01);
		finish(player.server, errands, Errand.BURIAL);
		return true;
	}

	@Nullable
	private static ArmorStandEntity carriedBody(ServerPlayerEntity player) {
		ErrandState errands = ErrandState.get(player.server);
		if (errands.bodyId == null) return null;
		Entity ride = player.getVehicle() != null ? player.getVehicle() : player;
		for (Entity passenger : ride.getPassengersDeep()) {
			if (passenger instanceof ArmorStandEntity stand && stand.getUuid().equals(errands.bodyId)) return stand;
		}
		return null;
	}

	/** A meal, handed over warm. Called from the survivor's own interaction, which is where food changes hands. */
	public static boolean offerMeal(ServerPlayerEntity player, SurvivorEntity person, ItemStack stack) {
		ErrandState errands = ErrandState.get(player.server);
		if (!errands.offered(Errand.HOT_MEAL) || errands.done(Errand.HOT_MEAL)) return false;
		if (person.getCharacter() != Survivor.SORENSEN) return false;
		if (!stack.isOf(Items.BAKED_POTATO) && !stack.isOf(Items.COOKED_BEEF) && !stack.isOf(Items.MUSHROOM_STEW)
				&& !stack.isOf(Items.RABBIT_STEW) && !stack.isOf(Items.BEETROOT_SOUP)) {
			return false;
		}
		if (!player.getAbilities().creativeMode) stack.decrement(1);
		finish(player.server, errands, Errand.HOT_MEAL);
		return true;
	}

	/** A corroded machine went back together in the belt. Only Brandt's three are counted. */
	public static void machineRestored(MinecraftServer server) {
		ErrandState errands = ErrandState.get(server);
		if (!errands.offered(Errand.CORRODED) || errands.done(Errand.CORRODED)) return;
		errands.machinesFixed++;
		errands.markDirty();
		ServerPlayerEntity player = protagonist(server);
		if (player != null && errands.machinesFixed < MACHINES) {
			player.sendMessage(Errand.CORRODED.line("progress").append(Text.literal(
					" (" + errands.machinesFixed + "/" + MACHINES + ")")).formatted(Formatting.GRAY), true);
		}
	}

	/** A reading taken off something that is not an entity: the seep, and the crust. */
	public static boolean sampleBlock(ServerPlayerEntity player, BlockPos pos) {
		RobotEntity chassis = chassisOf(player);
		if (chassis == null || !chassis.hasModule(RobotModule.SAMPLER)) return false;
		ServerWorld world = player.getServerWorld();
		BlockState state = world.getBlockState(pos);
		Specimen what = null;
		if (!world.getFluidState(pos).isEmpty() && world.getFluidState(pos).isIn(FluidTags.WATER)) what = Specimen.SEEP_WATER;
		else if (state.isOf(ModBlocks.SULFUR_CRUST) || state.isOf(net.minecraft.block.Blocks.GLOW_LICHEN)) what = Specimen.BIOMATTER;
		if (what == null) return false;
		return takeReading(player, null, what);
	}

	// ------------------------------------------------------------------ setting the scene

	/** Reyes' companion, where he fell: fifteen metres out, slumped, in a suit that did not hold. */
	private static void placeBody(MinecraftServer server, ErrandState errands) {
		SurvivorManager.Site clinic = siteOf(server, Survivor.REYES);
		if (clinic == null) return;
		ServerWorld world = server.getOverworld();
		BlockPos at = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, clinic.origin().add(11, 0, 7));
		ArmorStandEntity stand = new ArmorStandEntity(world, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		stand.setCustomName(Errand.BURIAL.line("name"));
		stand.setCustomNameVisible(true);
		stand.setHideBasePlate(true);
		stand.setNoGravity(false);
		stand.setInvulnerable(true);
		// Slumped forward and turned whichever way he fell, rather than nine weeks at attention facing north.
		// Not laid flat: an armour stand rotates its torso about the hip, so a face-down pose renders the
		// whole body horizontally about a block off the ground, which looks worse than standing does. A body
		// that actually lies down wants the collapsed CrewEntity pose the prologue's dead use, and that is a
		// bigger change than this errand needed.
		stand.setYaw(world.random.nextFloat() * 360f);
		stand.setHeadRotation(new EulerAngle(-18f, 12f, 0f));
		stand.setBodyRotation(new EulerAngle(-22f, 0f, 0f));
		stand.setLeftArmRotation(new EulerAngle(-16f, 0f, -14f));
		stand.setRightArmRotation(new EulerAngle(-12f, 0f, 10f));
		world.spawnEntity(stand);
		errands.bodyPos = at;
		errands.bodyId = stand.getUuid();
		errands.markDirty();
		Surrogate.LOGGER.info("Errand body for reyes at {}", at.toShortString());
	}

	/** The vent under Tanaka's power run. Put where a cable would be, which is straight out of her door. */
	private static void placeVent(MinecraftServer server) {
		BlockPos at = ventPos(server);
		if (at == null) return;
		ServerWorld world = server.getOverworld();
		if (world.getBlockState(at).isOf(ModBlocks.GEYSER)) return;
		world.setBlockState(at, ModBlocks.GEYSER.getDefaultState());
		Surrogate.LOGGER.info("Errand vent for tanaka at {}", at.toShortString());
	}

	@Nullable
	private static BlockPos ventPos(MinecraftServer server) {
		SurvivorManager.Site works = siteOf(server, Survivor.TANAKA);
		if (works == null || !works.built) return null;
		ServerWorld world = server.getOverworld();
		BlockPos column = works.origin().add(9, 0, 0);
		if (!world.isChunkLoaded(column.getX() >> 4, column.getZ() >> 4)) return null;
		return world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column).down();
	}

	/** Ballast, gone. Somewhere out in the wastes and, being a cat, not remotely sorry. */
	private static void sendCatOff(MinecraftServer server, HabitatState habitat) {
		CatEntity cat = cat(server, habitat);
		if (cat == null) return;
		ServerWorld world = server.getOverworld();
		net.minecraft.util.math.random.Random random = net.minecraft.util.math.random.Random.create(
				world.getSeed() ^ 0x43415400L);
		double angle = random.nextDouble() * Math.PI * 2.0;
		int distance = CAT_WANDER / 2 + random.nextInt(CAT_WANDER / 2);
		BlockPos out = habitat.origin.add((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
		out = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, out);
		cat.requestTeleport(out.getX() + 0.5, out.getY(), out.getZ() + 0.5);
		Surrogate.LOGGER.info("Errand cat for ballast at {}", out.toShortString());
	}

	// ------------------------------------------------------------------ plumbing

	public static void finish(MinecraftServer server, ErrandState errands, Errand errand) {
		if (errands.done(errand)) return;
		errands.finish(errand);
		ServerPlayerEntity player = protagonist(server);
		if (player != null) {
			player.sendMessage(Text.translatable("message.surrogate.errand.done", errand.title()).formatted(Formatting.GREEN), false);
			player.sendMessage(errand.line("thanks"), false);
			player.playSoundToPlayer(net.minecraft.sound.SoundEvents.ENTITY_PLAYER_LEVELUP,
					SoundCategory.PLAYERS, 0.5f, 1.6f);
		}
		Surrogate.LOGGER.info("Errand done: {} ({} of {})", errand.key(), errands.doneCount(), Errand.all().length);
	}

	private static void announce(MinecraftServer server, Errand errand) {
		ServerPlayerEntity player = protagonist(server);
		if (player == null) return;
		player.sendMessage(Text.translatable("message.surrogate.errand.offered", errand.title()).formatted(Formatting.YELLOW), false);
		player.sendMessage(errand.brief().formatted(Formatting.GRAY), false);
		Surrogate.LOGGER.info("Errand offered: {}", errand.key());
	}

	private static void give(MinecraftServer server, net.minecraft.item.Item... items) {
		ServerPlayerEntity player = protagonist(server);
		if (player == null) return;
		for (net.minecraft.item.Item item : items) {
			ItemStack stack = new ItemStack(item);
			Text what = stack.getName();
			if (!player.giveItemStack(stack)) player.dropItem(stack, false);
			player.sendMessage(Text.translatable("message.surrogate.errand.received", what).formatted(Formatting.GOLD), false);
		}
	}

	/** The chassis the player is driving, or null if they are standing in their own body. */
	@Nullable
	public static RobotEntity chassisOf(PlayerEntity player) {
		return player.getVehicle() instanceof RobotEntity robot && robot.isPilot(player) ? robot : null;
	}

	@Nullable
	public static SurvivorManager.Site siteOf(MinecraftServer server, Survivor who) {
		for (SurvivorManager.Site site : SurvivorManager.get(server).sites()) {
			if (site.character == who.ordinal()) return site;
		}
		return null;
	}

	@Nullable
	private static CatEntity cat(MinecraftServer server, HabitatState habitat) {
		if (habitat.cat == null) return null;
		return server.getOverworld().getEntity(habitat.cat) instanceof CatEntity cat ? cat : null;
	}


	@Nullable
	public static ServerPlayerEntity protagonist(MinecraftServer server) {
		HabitatState habitat = HabitatState.get(server);
		if (habitat.protagonist != null) {
			ServerPlayerEntity found = server.getPlayerManager().getPlayer(habitat.protagonist);
			if (found != null) return found;
		}
		List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
		return players.isEmpty() ? null : players.get(0);
	}

	private static boolean nearHome(ServerPlayerEntity player, HabitatState habitat) {
		return player.getWorld().getRegistryKey() == World.OVERWORLD
				&& player.getBlockPos().isWithinDistance(habitat.origin, AT_HOME);
	}
}
