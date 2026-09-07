package dev.psyda.surrogate.errand;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.entity.RobotPaint;
import dev.psyda.surrogate.prologue.AnnexBuilder;
import dev.psyda.surrogate.prologue.Crew;
import dev.psyda.surrogate.prologue.CrewEntity;
import dev.psyda.surrogate.prologue.Director;
import dev.psyda.surrogate.registry.ModSounds;
import dev.psyda.surrogate.world.HabitatBuilder;
import dev.psyda.surrogate.world.HabitatState;
import dev.psyda.surrogate.world.LightRefresh;
import dev.psyda.surrogate.world.ModuleTwo;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Two people you helped, letting themselves in.
 *
 * <p>The shape of it: help Okafor and Sorensen both, and the next time you are home you are told you are
 * tired. Sleep. You wake up with two chassis standing in your own pod, because neither of them has a body
 * that can walk here and both of them wanted to say thank you in person, near enough. They have been talking
 * to each other about you. What they have decided is that the orbital platform still has your second module
 * in a rack, that neither of you has the standing to ask for it alone, and that the three of you together do.
 *
 * <p>Then it comes down, and that is the scene: a light that is not a star, growing, a long way of falling,
 * and a room on the slab where there has only ever been a slab.
 *
 * <p>This is a {@link Director} like every other scene in the mod, so it takes the stage, it can be skipped,
 * and nothing else can run while it does.
 */
public final class Housewarming extends Director {
	private static final String KEY = "cinematic.surrogate.housewarming.";

	/** Where the two visiting chassis stand: either side of the pod's inner floor, facing the bunk. */
	private static final Vec3d OKAFOR_STANDS = new Vec3d(-1.5, 1, 1.5);
	private static final Vec3d SORENSEN_STANDS = new Vec3d(-1.5, 1, 3.5);

	/** How far up the module comes from, and how long it takes. */
	private static final int DROP_FROM = 96;
	private static final int DROP_TICKS = 110;

	@Nullable
	private static Housewarming running;

	private final HabitatState habitat;
	private final ErrandState errands;
	private final BlockPos origin;

	/** The two chassis on stage, so they can be cleared again whatever happens to the script. */
	private final List<RobotEntity> visitors = new ArrayList<>();

	/** Ticks left of the fall, counted down by {@link #dropTick()} while the script waits on it. */
	private int falling = -1;

	/** Slows the "should this start yet" check down to twice a second; the scene itself runs every tick. */
	private static int idle;

	private Housewarming(MinecraftServer server, HabitatState habitat, ErrandState errands) {
		super(server, KEY);
		this.habitat = habitat;
		this.errands = errands;
		this.origin = habitat.origin == null ? BlockPos.ORIGIN : habitat.origin;
		buildScript();
	}

	// ------------------------------------------------------------------ lifecycle

	/**
	 * Called off the errand sweep. Two conditions and they are both about sleep: the tiredness has landed,
	 * and a night has gone by since. Waking is what starts it, not going to bed.
	 */
	static void tick(MinecraftServer server) {
		if (running != null) {
			running.tick();
			return;
		}
		// Nothing below changes faster than a night, so it is looked at twice a second rather than twenty
		// times. The scene above is the part that needs every tick.
		if (++idle < 10) return;
		idle = 0;
		ErrandState errands = ErrandState.get(server);
		if (errands.done(Errand.HOUSEWARMING) || !errands.offered(Errand.HOUSEWARMING)) return;
		if (errands.tiredDay < 0L) return;
		HabitatState habitat = HabitatState.get(server);
		if (habitat.origin == null) return;
		ServerWorld world = server.getOverworld();
		if (world.getTimeOfDay() / 24000L <= errands.tiredDay) return;
		// A night has passed, but only in a bed: a player who stayed up through it has not slept, and being
		// woken by visitors you never went to sleep for is nonsense.
		ServerPlayerEntity player = Errands.protagonist(server);
		if (player == null || !world.isDay()) return;
		if (Director.current() != null) return;
		begin(server, habitat, errands);
	}

	private static void begin(MinecraftServer server, HabitatState habitat, ErrandState errands) {
		running = new Housewarming(server, habitat, errands);
		running.startDelay = 40;
		running.takeStage();
		Surrogate.LOGGER.info("Housewarming: two chassis in the pod");
	}

	/** For the test command: start it from wherever, ready or not. */
	public static void force(MinecraftServer server) {
		HabitatState habitat = HabitatState.get(server);
		ErrandState errands = ErrandState.get(server);
		if (habitat.origin == null) return;
		Director.clearStage();
		running = null;
		errands.offer(Errand.HOUSEWARMING);
		if (errands.tiredDay < 0L) {
			errands.tiredDay = server.getOverworld().getTimeOfDay() / 24000L;
			errands.markDirty();
		}
		begin(server, habitat, errands);
	}

	public static boolean isRunning() {
		return running != null;
	}

	// ------------------------------------------------------------------ the script

	@Override
	protected void buildScript() {
		run(this::showVisitors);
		wait(30);
		say(Crew.OKAFOR, "wake_1");
		say(Crew.SORENSEN, "wake_2");
		say(Crew.OKAFOR, "wake_3");
		wait(20);
		say(Crew.SORENSEN, "offer_1");
		say(Crew.OKAFOR, "offer_2");
		say(Crew.SORENSEN, "offer_3");
		wait(20);

		// Outside, and look up. The camera does the work here; the module is a long way off and small.
		cinematic();
		run(this::stepOutside);
		wait(20);
		say(Crew.OKAFOR, "wait_1");
		run(this::startDrop);
		wait(40);
		say(Crew.SORENSEN, "wait_2");
		// The fall is its own clock so the script does not have to know how many ticks a block takes to come
		// ninety-six blocks; it waits for the counter to run out and the counter does the particles.
		until(() -> falling <= 0, DROP_TICKS + 200, null, null, 0, () -> falling = 0);
		run(this::land);
		wait(30);
		say(Crew.OKAFOR, "landed_1");
		say(Crew.SORENSEN, "landed_2");
		endCinematic();
		wait(20);
		say(Crew.OKAFOR, "goodbye");
		run(this::clearVisitors);
		run(this::finish);
	}

	/** The two chassis, already inside, already switched on, already having a conversation about you. */
	private void showVisitors() {
		ServerWorld world = world();
		visitors.add(AnnexBuilder.spawnCrewChassis(world, Crew.OKAFOR, RobotPaint.OKAFOR,
				Vec3d.of(origin).add(OKAFOR_STANDS), 90f, true));
		visitors.add(AnnexBuilder.spawnCrewChassis(world, Crew.SORENSEN, RobotPaint.SORENSEN,
				Vec3d.of(origin).add(SORENSEN_STANDS), 90f, true));
		world.playSound(null, origin, ModSounds.RADIO_OPEN, SoundCategory.NEUTRAL, 0.6f, 1.0f);
	}

	/** Everyone out onto the pad, because the thing that is about to happen is not visible from indoors. */
	private void stepOutside() {
		ServerPlayerEntity player = player();
		if (player == null) return;
		Vec3d pad = Vec3d.of(origin).add(HabitatBuilder.ROBOT_PAD).add(0, 0, 2);
		Entity ride = player.getVehicle() != null ? player.getVehicle() : player;
		ride.requestTeleport(pad.x, pad.y, pad.z);
		for (int i = 0; i < visitors.size(); i++) {
			RobotEntity chassis = visitors.get(i);
			if (chassis == null || !chassis.isAlive()) continue;
			chassis.requestTeleport(pad.x - 2 + i * 4, pad.y, pad.z - 1);
		}
		// Look up. The shot is the sky over the slab, which is where the light is going to appear. Sent
		// rather than scripted, because it is aimed from wherever the player ended up standing.
		Vec3d eye = pad.add(0, 2, 0);
		Vec3d sky = Vec3d.of(origin.add(ModuleTwo.CENTRE)).add(0, DROP_FROM, 0);
		send(new dev.psyda.surrogate.network.CinematicPayloads.Camera(java.util.List.of(
				frame(eye, sky, 0, 0),
				frame(eye.add(0, 1, 0), sky, DROP_TICKS + 60, dev.psyda.surrogate.network.CinematicPayloads.EASE_SMOOTH))));
	}

	/**
	 * The fall itself. There is no drop-pod entity: the module is a handful of falling hull plates with a
	 * particle column behind them, which from the ground at ninety-six blocks reads as one object coming in
	 * and costs nothing. The slab is cleared first, so nothing it passes through stops it.
	 */
	private void startDrop() {
		ServerWorld world = world();
		ModuleTwo.clearApproach(world, origin);
		BlockPos aim = origin.add(ModuleTwo.CENTRE);
		world.playSound(null, aim, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 0.8f, 0.5f);
		falling = DROP_TICKS;
	}

	/** One tick of the fall: the light coming down, drawn as particles on a line. */
	private void dropTick() {
		if (falling <= 0) return;
		ServerWorld world = world();
		BlockPos aim = origin.add(ModuleTwo.CENTRE);
		double progress = 1.0 - (falling / (double) DROP_TICKS);
		double y = origin.getY() + DROP_FROM * (1.0 - progress * progress);
		world.spawnParticles(ParticleTypes.FLAME, aim.getX() + 0.5, y, aim.getZ() + 0.5, 12, 0.6, 1.5, 0.6, 0.02);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, aim.getX() + 0.5, y + 2, aim.getZ() + 0.5, 6, 0.8, 1.0, 0.8, 0.01);
		if (falling % 20 == 0) {
			world.playSound(null, aim, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.WEATHER,
					0.3f + (float) progress * 0.7f, 0.4f);
		}
		falling--;
	}

	/** Touchdown: dust, a bang, and the room is there. */
	private void land() {
		ServerWorld world = world();
		BlockPos aim = origin.add(ModuleTwo.CENTRE);
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, aim.getX() + 0.5, aim.getY(), aim.getZ() + 0.5, 4, 3.0, 0.5, 3.0, 0.0);
		world.playSound(null, aim, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 1.0f, 0.5f);
		// A scatter of plates thrown up by the impact, which settle back onto the slab as the room appears.
		for (int i = 0; i < 8; i++) {
			BlockPos from = aim.add(world.random.nextInt(7) - 3, 6 + world.random.nextInt(4), world.random.nextInt(9) - 4);
			FallingBlockEntity.spawnFromBlock(world, from, ModuleTwo.skin().getDefaultState());
		}
		ModuleTwo.build(world, origin);
		ServerPlayerEntity player = player();
		if (player != null) {
			LightRefresh.schedule(player, world.getRegistryKey(), origin.add(ModuleTwo.CENTRE), 16, 10);
		}
	}

	private void clearVisitors() {
		for (RobotEntity chassis : visitors) {
			if (chassis != null && chassis.isAlive()) chassis.discard();
		}
		visitors.clear();
	}

	private void finish() {
		Errands.finish(server, errands, Errand.HOUSEWARMING);
		running = null;
	}

	@Override
	protected void tick() {
		dropTick();
		super.tick();
	}

	@Override
	protected void onSkip(ServerPlayerEntity player) {
		// Skipping still has to leave the world in the state the scene would have left it in: the room is the
		// point of the errand, and an errand that says done with no room behind it is a bug in a save file.
		falling = 0;
		if (!ModuleTwo.exists(world(), origin)) {
			ModuleTwo.clearApproach(world(), origin);
			ModuleTwo.build(world(), origin);
		}
		clearVisitors();
		endCinematic();
		finish();
	}

	// ------------------------------------------------------------------ Director's plumbing

	/**
	 * Whoever this scene is for.
	 *
	 * <p>Not {@code habitat.protagonist} alone. That field is only ever set by the prologue, so on a world
	 * with {@code prologue} turned off it is null forever — and {@link Director#tick()} returns immediately
	 * when the player is null, which meant the whole scene took the stage and then sat there, silently, for
	 * as long as anybody cared to wait. The fallback is the same one the rest of the errand layer uses.
	 */
	@Override
	@Nullable
	public ServerPlayerEntity player() {
		if (habitat.protagonist != null) {
			ServerPlayerEntity found = server.getPlayerManager().getPlayer(habitat.protagonist);
			if (found != null) return found;
		}
		return Errands.protagonist(server);
	}

	@Override
	public ServerWorld world() {
		return server.getOverworld();
	}

	@Override
	public BlockPos origin() {
		return origin;
	}

	@Override
	@Nullable
	public CrewEntity crew(Crew who) {
		// Nobody is here in person. Both visitors are chassis, so every line goes out over the radio style
		// rather than to a body standing in the room.
		return null;
	}
}
