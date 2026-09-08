package dev.psyda.surrogate.errand;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.prologue.AnnexBuilder;
import dev.psyda.surrogate.prologue.Crew;
import dev.psyda.surrogate.prologue.CrewEntity;
import dev.psyda.surrogate.prologue.Director;
import dev.psyda.surrogate.registry.ModSounds;
import dev.psyda.surrogate.world.HabitatBuilder;
import dev.psyda.surrogate.world.HabitatState;
import dev.psyda.surrogate.world.LightRefresh;
import dev.psyda.surrogate.world.ModuleTwo;
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
 * tired. Sleep. You wake up with two people standing in your own pod, because both of them wanted to say
 * thank you in person and both of them own a suit that lets them. They have been talking to each other about
 * you. What they have decided is that the orbital platform still has your second module in a rack, that
 * neither of you has the standing to ask for it alone, and that the three of you together do.
 *
 * <p>Then they walk out and wait on the pad, because the thing that is about to happen is not visible from
 * indoors and because you cannot follow them the way they went. They have suits. You have a chassis. Going
 * outside is the one errand in the game that makes you do the thing the whole game is about, and it is worth
 * the ninety seconds it costs.
 *
 * <p>Then it comes down, and that is the scene: a light that is not a star, growing, a long way of falling,
 * and a room on the slab where there has only ever been a slab.
 *
 * <p>This is a {@link Director} like every other scene in the mod, so it takes the stage, it can be skipped,
 * and nothing else can run while it does.
 */
public final class Housewarming extends Director {
	private static final String KEY = "cinematic.surrogate.housewarming.";

	/** Where the two of them are standing when you wake: either side of the pod's floor, facing the bunk. */
	private static final Vec3d OKAFOR_STANDS = new Vec3d(-1.5, 1, 1.5);
	private static final Vec3d SORENSEN_STANDS = new Vec3d(-1.5, 1, 3.5);
	/** Facing east, which is the wall your bed is against. */
	private static final float FACING_BUNK = -90f;

	/** The pod's own airlock: two doors and the two cells between them. They cycle it like anyone else. */
	private static final BlockPos INNER_DOOR = new BlockPos(0, 1, 4);
	private static final BlockPos OUTER_DOOR = new BlockPos(0, 1, 7);
	private static final Vec3d CHAMBER_NEAR = new Vec3d(0.5, 1, 5.5);
	private static final Vec3d CHAMBER_FAR = new Vec3d(0.5, 1, 6.5);

	/**
	 * Where they wait. The west half of the pad, which leaves {@link HabitatBuilder#ROBOT_PAD} clear for
	 * whatever the player walks out in.
	 */
	private static final Vec3d OKAFOR_WAITS = new Vec3d(-1.5, 1, 9.5);
	private static final Vec3d SORENSEN_WAITS = new Vec3d(-1.5, 1, 11.5);
	/** Where they head when it is over: off the pad, across open ground, out of the shot. */
	private static final Vec3d LEAVING = new Vec3d(-14.5, 0, 16.5);

	/** How far up the module comes from, and how long it takes. */
	private static final int DROP_FROM = 96;
	private static final int DROP_TICKS = 110;

	/** How long they will stand out there waiting, and how long before one of them says so. */
	private static final int WAIT_OUTSIDE = 9600;
	private static final int NUDGE_AFTER = 1200;
	/** How far south of the pod counts as out, and how near the pad counts as with them. */
	private static final double OUTSIDE_Z = 8.0;
	private static final double NEAR_PAD = 14.0;

	@Nullable
	private static Housewarming running;

	private final HabitatState habitat;
	private final ErrandState errands;
	private final BlockPos origin;

	/** The two on stage, so they can be cleared again whatever happens to the script. */
	private final List<CrewEntity> visitors = new ArrayList<>();

	/** Ticks left of the fall, counted down by {@link #dropTick()} while the script waits on it. */
	private int falling = -1;

	/** Set when the player never came outside: the module still lands, but nobody films it. */
	private boolean unwatched;

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
		Surrogate.LOGGER.info("Housewarming: two visitors in the pod");
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
		label("wake");
		run(this::showVisitors);
		wait(30);
		say(Crew.OKAFOR, "wake_1");
		say(Crew.SORENSEN, "wake_2");
		say(Crew.OKAFOR, "wake_3");
		wait(20);
		say(Crew.SORENSEN, "offer_1");
		say(Crew.OKAFOR, "offer_2");
		say(Crew.SORENSEN, "offer_3");
		say(Crew.OKAFOR, "suits");

		// Out through the pod's own airlock, a leg at a time, because it is one cell wide and each door has
		// to be opened by whoever is standing at it. Nobody is teleported: the previous version moved the
		// player out with them, which put a body with no suit on it in the open air, and the scene's next
		// beat was a corpse.
		label("outside");
		run(this::intoAirlock);
		arrive(Crew.OKAFOR, CHAMBER_NEAR, 300);
		arrive(Crew.SORENSEN, CHAMBER_FAR, 300);
		run(this::ontoPad);
		arrive(Crew.OKAFOR, OKAFOR_WAITS, 400);
		arrive(Crew.SORENSEN, SORENSEN_WAITS, 400);
		run(this::waitForPlayer);
		objective("outside");
		hint("outside");
		// The player's own time, their own legs, and the camera stays theirs the whole way. This is the beat
		// the scene used to skip, and skipping it is what put the module in the sky over an empty pad.
		until(this::playerOutside, scaled(WAIT_OUTSIDE), Crew.SORENSEN, "outside_nudge", NUDGE_AFTER, () -> unwatched = true);
		objectiveDone();
		jumpIf(() -> unwatched, "unwatched");

		label("drop");
		run(this::lookUp);
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
		endShot();
		wait(20);
		say(Crew.OKAFOR, "goodbye");
		// And they walk off rather than blinking out where they stand. They were chassis once and a chassis
		// switching off is a thing that happens; two people in suits vanishing off the pad is not.
		run(this::seeThemOff);
		wait(120);
		jumpIf(() -> true, "end");

		// Nobody came out. It still lands, because two other people spent their standing on it and the world
		// does not rewind for an absent audience; they just tell you about it afterwards.
		label("unwatched");
		run(this::landUnwatched);
		radio(Crew.OKAFOR, "unwatched");

		label("end");
		run(this::clearVisitors);
		run(this::finish);
	}

	/** The two of them, already inside, already having a conversation about you. */
	private void showVisitors() {
		ServerWorld world = world();
		visitors.add(AnnexBuilder.spawnPerson(world, Crew.OKAFOR, Vec3d.of(origin).add(OKAFOR_STANDS), FACING_BUNK));
		visitors.add(AnnexBuilder.spawnPerson(world, Crew.SORENSEN, Vec3d.of(origin).add(SORENSEN_STANDS), FACING_BUNK));
		ServerPlayerEntity player = player();
		for (CrewEntity person : visitors) {
			if (person != null) person.setLookTarget(player);
		}
		world.playSound(null, origin, ModSounds.RADIO_OPEN, SoundCategory.NEUTRAL, 0.6f, 1.0f);
	}

	/** Both of them into the airlock chamber, opening the inner door on the way. */
	private void intoAirlock() {
		setOut(Crew.OKAFOR, CHAMBER_NEAR, INNER_DOOR);
		setOut(Crew.SORENSEN, CHAMBER_FAR, INNER_DOOR);
	}

	/** And out of it, opening the outer door, to the west half of the pad. */
	private void ontoPad() {
		setOut(Crew.OKAFOR, OKAFOR_WAITS, OUTER_DOOR);
		setOut(Crew.SORENSEN, SORENSEN_WAITS, OUTER_DOOR);
	}

	/**
	 * One of them walking, with the one door on that leg to open on the way past.
	 *
	 * <p>Refused for anyone without a suit. Both of these two have one — it is why they could come at all —
	 * but the check belongs on the move rather than in the casting, so that the next script to walk somebody
	 * through an airlock cannot quietly kill them by picking the wrong name.
	 */
	private void setOut(Crew who, Vec3d target, BlockPos door) {
		if (!who.suited()) {
			Surrogate.LOGGER.warn("Housewarming: {} has no suit and cannot be walked outside", who.key());
			return;
		}
		CrewEntity person = crew(who);
		if (person != null) person.walkTo(at(target), origin.add(door));
	}

	/** Standing on the pad, facing the door you have to come out of. */
	private void waitForPlayer() {
		ServerPlayerEntity player = player();
		for (CrewEntity person : visitors) {
			if (person == null || !person.isAlive()) continue;
			person.stopWalking();
			person.face(180f);
			person.setLookTarget(player);
		}
	}

	/**
	 * Whether the player has come out to them: past the pod's outer wall and near enough the pad to be part
	 * of the conversation. Their vehicle, if they are in one, because a pilot in a chassis is riding it and
	 * the chassis is the thing standing on the pad.
	 */
	private boolean playerOutside() {
		ServerPlayerEntity player = player();
		if (player == null) return false;
		Entity body = player.hasVehicle() ? player.getRootVehicle() : player;
		if (body.getWorld() != world()) return false;
		return body.getZ() > origin.getZ() + OUTSIDE_Z
				&& body.squaredDistanceTo(at(HabitatBuilder.ROBOT_PAD)) < NEAR_PAD * NEAR_PAD;
	}

	/**
	 * The shot. Taken here rather than at the top of the scene: everything before this is the player's own
	 * to walk through, and a camera held across it is a camera held across an objective nobody can finish.
	 *
	 * <p>Aimed from where the player actually ended up, at the patch of sky the module is coming out of.
	 */
	private void lookUp() {
		cinematic();
		ServerPlayerEntity player = player();
		if (player == null) return;
		Entity body = player.hasVehicle() ? player.getRootVehicle() : player;
		Vec3d eye = body.getPos().add(0, 2, 0);
		Vec3d sky = Vec3d.of(origin.add(ModuleTwo.CENTRE)).add(0, DROP_FROM, 0);
		send(new dev.psyda.surrogate.network.CinematicPayloads.Camera(List.of(
				frame(eye, sky, 0, 0),
				frame(eye.add(0, 1, 0), sky, DROP_TICKS + 60, dev.psyda.surrogate.network.CinematicPayloads.EASE_SMOOTH))));
		for (CrewEntity person : visitors) {
			if (person != null && person.isAlive()) person.setLookTarget(null);
		}
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
		raise();
	}

	/** The same room, without the show. */
	private void landUnwatched() {
		falling = 0;
		raise();
	}

	/** The room on the slab, and the light in it made to look right on a client that was not watching. */
	private void raise() {
		ServerWorld world = world();
		if (!ModuleTwo.exists(world, origin)) ModuleTwo.clearApproach(world, origin);
		ModuleTwo.build(world, origin);
		ServerPlayerEntity player = player();
		if (player != null) {
			LightRefresh.schedule(player, world.getRegistryKey(), origin.add(ModuleTwo.CENTRE), 16, 10);
		}
	}

	/** Away south-west, across the open ground, until they are far enough off to be taken off the board. */
	private void seeThemOff() {
		for (CrewEntity person : visitors) {
			if (person == null || !person.isAlive()) continue;
			person.setLookTarget(null);
			person.walkTo(atSurface(LEAVING), null);
		}
	}

	private void clearVisitors() {
		for (CrewEntity person : visitors) {
			if (person != null && person.isAlive()) person.discard();
		}
		visitors.clear();
	}

	/**
	 * The scene is over, however it ended.
	 *
	 * <p>{@link #leaveStage()} is the load-bearing line. Every other director calls it and this one never
	 * did, so a script that ran to the end left {@link Director#current()} pointing at a finished
	 * housewarming for the rest of the save — and the conference, the rescue, the research runs and the
	 * assay all refuse to start while anything is on stage. An early optional errand quietly closed the
	 * back half of the game.
	 */
	private void finish() {
		objectiveClear();
		resetClient();
		leaveStage();
		running = null;
		Errands.finish(server, errands, Errand.HOUSEWARMING);
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
		raise();
		clearVisitors();
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

	/** Both of them are here in person, so every line comes from a body standing in the room. */
	@Override
	@Nullable
	public CrewEntity crew(Crew who) {
		for (CrewEntity person : visitors) {
			if (person != null && person.isAlive() && person.getCharacter() == who) return person;
		}
		return null;
	}
}
