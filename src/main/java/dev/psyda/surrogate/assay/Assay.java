package dev.psyda.surrogate.assay;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.entity.CompanyShipEntity;
import dev.psyda.surrogate.registry.ModEntities;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.entity.RobotModule;
import dev.psyda.surrogate.entity.RobotPaint;
import dev.psyda.surrogate.entity.RocketEntity;
import dev.psyda.surrogate.hazard.Borers;
import dev.psyda.surrogate.item.RocketKitItem;
import net.minecraft.util.math.Box;
import dev.psyda.surrogate.network.CinematicPayloads;
import dev.psyda.surrogate.prologue.AnnexBuilder;
import dev.psyda.surrogate.prologue.Beat;
import dev.psyda.surrogate.prologue.Crew;
import dev.psyda.surrogate.prologue.CrewEntity;
import dev.psyda.surrogate.prologue.Director;
import dev.psyda.surrogate.prologue.Prologue;
import dev.psyda.surrogate.research.ResearchState;
import dev.psyda.surrogate.registry.ModBlocks;
import dev.psyda.surrogate.registry.ModItems;
import dev.psyda.surrogate.registry.ModSounds;
import dev.psyda.surrogate.world.HabitatState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Contract Seven: the corporation's research task, and the first thing everyone on Sallow works on together.
 * The company designates a site; the player drives out and stakes it; the pad goes down course by course as
 * the other researchers send their chassis with their own pieces; a deep sample comes up out of the ground;
 * the gantry builds a sample rocket; and the whole thing is lit with a tellurium crystal.
 *
 * <p>The ship the company promised does arrive. It takes the payload and it does not come down. That is the
 * end of the arc and the beginning of the next one.
 *
 * <p>Like {@link Prologue} this is a flat list of {@link Beat}s built in {@link #buildScript()}, read top to
 * bottom. It resumes across restarts, skips a stage at a time, and works around anything the player ignores.
 */
public final class Assay extends Director {
	private static final String KEY = "cinematic.surrogate.assay.";
	private static final String PAD = KEY + "pad";
	/** Tellurium crystals the core wants. */
	public static final int CORE_TARGET = 3;

	@Nullable
	private static Assay running;

	// Where things stand, relative to the pad origin (the middle of the apron, ground level).
	private static final Vec3d ARRIVAL = new Vec3d(0.5, 1, 9.5);
	private static final Vec3d PAD_MIDDLE = new Vec3d(0.5, 2, 0.5);
	private static final Vec3d SOUTH_APPROACH = new Vec3d(0.5, 1, 26.5);
	// Work spots sit beside what is being installed, never on top of it: the uprights land at +/-4 and the
	// gantry at (0, 2), and a chassis standing inside either one reads as a bug.
	private static final Vec3d SORENSEN_WORK = new Vec3d(-2.5, 2, -4.5);
	private static final Vec3d HALLORAN_WORK = new Vec3d(2.5, 2, 4.5);
	private static final Vec3d MARSH_EDGE = new Vec3d(5.5, 2, 5.5);
	/** Where Sorensen's chassis stands for the finale: at the rail, as the gathering line says. */
	private static final Vec3d SORENSEN_RAIL = new Vec3d(-5.5, 2, 0.5);

	private final AssayState assay;
	private final HabitatState habitat;
	private BlockPos origin;

	// Which way the story went.
	private boolean apronDone;
	private boolean skippedApron;
	private boolean skippedCore;
	private boolean ignited;
	// Stage three goes under the borer line, so it gets one line the first time something wakes for the drill.
	// The mark is taken when the stage opens; everything after the first one is the pilot's own problem.
	private int coreWakeMark;
	private boolean borerHeard;
	// One chassis per character: the finale has more than one of them on the pad at once, and the beats that
	// drive a walk need to know which one they are steering.
	@Nullable
	private RobotEntity sorensenChassis;
	@Nullable
	private RobotEntity halloranChassis;
	@Nullable
	private RobotEntity marshChassis;
	@Nullable
	private RobotEntity activeChassis;

	private Assay(MinecraftServer server, AssayState assay, HabitatState habitat) {
		super(server, KEY);
		this.assay = assay;
		this.habitat = habitat;
		this.origin = assay.padSite == null ? BlockPos.ORIGIN : assay.padSite;
		buildScript();
	}

	// ------------------------------------------------------------------ lifecycle

	public static void registerEvents() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (running != null) running.tick();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> running = null);
		// A tellurium crystal used on the rocket lights it. Nothing else does.
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (running == null || world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
			if (!(entity instanceof RocketEntity) || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
			if (!running.isProtagonist(serverPlayer)) return ActionResult.PASS;
			return running.onRocketUsed(player, player.getStackInHand(hand)) ? ActionResult.SUCCESS : ActionResult.PASS;
		});
	}

	@Nullable
	public static Assay running() {
		return running;
	}

	public static boolean isRunning() {
		return running != null;
	}

	/**
	 * The contract opens once the opening days are over and nothing else is on stage. The player is told by
	 * radio; nothing is built anywhere until they drive out to the site.
	 */
	public static boolean shouldBegin(MinecraftServer server, HabitatState habitat) {
		if (!Surrogate.CONFIG.assay) return false;
		if (habitat.prologueStage < Prologue.STAGE_DONE || habitat.protagonist == null) return false;
		// The neighbours' runs and the drive out to Tanaka come first; the company waits behind them.
		if (!dev.psyda.surrogate.research.Research.done(server)) return false;
		AssayState assay = AssayState.get(server);
		return assay.stage == AssayState.STAGE_NONE && assay.padSite != null;
	}

	public static void begin(MinecraftServer server) {
		HabitatState habitat = HabitatState.get(server);
		AssayState assay = AssayState.get(server);
		if (assay.padSite == null || habitat.protagonist == null) return;
		if (Director.current() != null) return;
		assay.stage = AssayState.STAGE_STAKE;
		assay.markDirty();
		running = new Assay(server, assay, habitat);
		running.startDelay = 100;
		running.takeStage();
		Surrogate.LOGGER.info("Assay: Contract Seven opens");
	}

	/** A player joined: if the contract is half done for them, pick it up where it stopped. */
	public static boolean onJoin(ServerPlayerEntity player, HabitatState habitat) {
		AssayState assay = AssayState.get(player.server);
		if (habitat.protagonist == null || !habitat.protagonist.equals(player.getUuid())) return false;
		if (assay.stage <= AssayState.STAGE_NONE || assay.stage >= AssayState.STAGE_DONE) return false;
		if (running == null) {
			running = new Assay(player.server, assay, habitat);
			running.restorePad();
			running.jump(switch (assay.stage) {
				case AssayState.STAGE_PAD -> "pad";
				case AssayState.STAGE_CORE -> "core";
				case AssayState.STAGE_PAYLOAD -> "payload";
				case AssayState.STAGE_IGNITION -> "ignition";
				default -> "stake";
			});
			running.startDelay = 60;
			running.takeStage();
			Surrogate.LOGGER.info("Assay: resuming at stage {}", assay.stage);
		}
		running.send(new CinematicPayloads.State(true, false, false));
		return true;
	}

	/** Dev: start the contract over at a stage. */
	public static void restartAt(ServerPlayerEntity player, String label) {
		MinecraftServer server = player.server;
		HabitatState habitat = HabitatState.get(server);
		AssayState assay = AssayState.get(server);
		if (assay.padSite == null) PadSite.choose(server.getOverworld(), habitat, assay);
		if (assay.padSite == null) return;
		reset(server);
		habitat.prologueStage = Prologue.STAGE_DONE;
		if (habitat.protagonist == null) habitat.protagonist = player.getUuid();
		habitat.markDirty();
		int stage = switch (label) {
			case "pad" -> AssayState.STAGE_PAD;
			case "core" -> AssayState.STAGE_CORE;
			case "payload" -> AssayState.STAGE_PAYLOAD;
			case "ignition" -> AssayState.STAGE_IGNITION;
			default -> AssayState.STAGE_STAKE;
		};
		assay.stage = AssayState.STAGE_NONE;
		assay.markDirty();
		begin(server);
		if (running == null || stage == AssayState.STAGE_STAKE) return;
		// Jumping in past the stake means the site has to exist already. The origin is resolved once and then
		// frozen, so replaying a stage rebuilds in exactly the same place.
		ServerWorld world = server.getOverworld();
		running.origin = PadSite.resolve(world, assay);
		assay.stage = stage;
		// The pad stage lays its own courses; every later stage needs them already standing.
		assay.courses = stage == AssayState.STAGE_PAD ? 0 : PadBuilder.COURSES;
		assay.markDirty();
		running.forceLoad(true);
		PadBuilder.clear(world, running.origin);
		PadBuilder.stake(world, running.origin);
		running.restorePad();
		running.jump(switch (stage) {
			case AssayState.STAGE_PAD -> "pad";
			case AssayState.STAGE_CORE -> "core";
			case AssayState.STAGE_PAYLOAD -> "payload";
			default -> "ignition";
		});
	}

	/** Forgets the contract. The pad it built is left standing; only the story resets. */
	public static void reset(MinecraftServer server) {
		AssayState assay = AssayState.get(server);
		if (running != null) {
			running.resetClient();
			running.leaveStage();
			running = null;
		}
		assay.stage = AssayState.STAGE_NONE;
		assay.courses = 0;
		assay.coreSamples = 0;
		assay.payloadLoaded = false;
		assay.shipSeen = false;
		assay.markDirty();
	}

	@Override
	protected void onSkip(ServerPlayerEntity player) {
		if (!isProtagonist(player)) return;
		String label = currentLabel();
		Surrogate.LOGGER.info("Assay: skipped at {}", label);
		resetClient();
		switch (assay.stage) {
			case AssayState.STAGE_STAKE -> {
				arriveAtSite();
				checkpoint(AssayState.STAGE_PAD);
				jump("pad");
			}
			case AssayState.STAGE_PAD -> {
				finishPad();
				checkpoint(AssayState.STAGE_CORE);
				jump("core");
			}
			case AssayState.STAGE_CORE -> {
				assay.coreSamples = CORE_TARGET;
				checkpoint(AssayState.STAGE_PAYLOAD);
				jump("payload");
			}
			case AssayState.STAGE_PAYLOAD -> {
				if (!rocketBuilt()) buildRocket();
				assay.payloadLoaded = true;
				checkpoint(AssayState.STAGE_IGNITION);
				jump("ignition");
			}
			// Skipping the last stage still has to deliver the ending: the ship came, the payload went, and
			// the contract closed. Only the ceremony is cut.
			default -> {
				if (!rocketBuilt()) buildRocket();
				assay.shipSeen = true;
				assay.markDirty();
				launch();
				payloadTaken();
				jump("done");
			}
		}
	}

	// ------------------------------------------------------------------ the rock, during stage three

	@Override
	protected void tick() {
		super.tick();
		watchTheRock();
	}

	/** Stage three opening: from here, the next borer anywhere is the first one of this stage. */
	private void markTheRock() {
		coreWakeMark = Borers.wakes();
		borerHeard = false;
	}

	/**
	 * The first borer of the stage gets a line the moment it wakes rather than at the next beat, because by
	 * the next beat it is either through the wall or gone and neither reads as a warning.
	 */
	private void watchTheRock() {
		if (borerHeard || assay.stage != AssayState.STAGE_CORE) return;
		// Nobody to say it to means nobody heard it, so the stage keeps its one line for when they are back.
		if (player() == null || Borers.wakes() <= coreWakeMark) return;
		borerHeard = true;
		sendLine(Crew.HALLORAN.nameKey(), KEY + "core_borer", CinematicPayloads.RADIO, -1, true, "");
	}

	// ------------------------------------------------------------------ the script

	@Override
	protected void buildScript() {
		Crew H = Crew.HALLORAN;
		Crew M = Crew.MARSH;

		// ================================================================ One: the stake.
		// The company designates a site. Nobody is told why that one.
		label("stake");
		run(() -> {
			state(true, false, false);
			checkpoint0(AssayState.STAGE_STAKE);
		});
		radio(M, "open1");
		radio(H, "open2");
		radio(M, "open3");
		radio(H, "open4");
		objective("drive");
		hint("drive_nudge");
		until(this::playerAtSite, timeout() * 8, H, "drive_nudge", 2400, null);
		objectiveDone();

		// ---- Arriving. The ground is levelled and staked while they watch.
		beginShot();
		run(this::arriveAtSite);
		shotFromEyes(at(PAD_MIDDLE), at(0.5, 8, 18.5), 100);
		wait(20);
		chapter(KEY + "chapter", KEY + "chapter.sub", "ONE", 80);
		wait(80);
		run(() -> sound(SoundEvents.BLOCK_NETHERITE_BLOCK_PLACE, PAD_MIDDLE, 1.0f, 0.6f));
		radio(H, "site1");
		radio(M, "site2");
		radio(H, "site3");
		endShot();
		assertReleased("stake");

		// ================================================================ Two: the pad, and the others.
		label("pad");
		run(() -> checkpoint0(AssayState.STAGE_PAD));
		radio(M, "pad1");
		radio(H, "pad2");

		// ---- Course one: the player lays the apron themselves.
		objective("apron");
		hint("apron_nudge");
		until(this::apronLaid, timeout() * 6, H, "apron_nudge", 1800, () -> skippedApron = true);
		branch(() -> skippedApron, line(H, "apron_timeout", CinematicPayloads.RADIO));
		run(() -> {
			apronDone = true;
			PadBuilder.course(world(), origin, 1);
			courses(1);
		});
		objectiveDone();
		radio(H, "apron_done");

		// ---- Course two: Sorensen's chassis walks in from the south with the legs.
		label("pad2");
		radio(Crew.SORENSEN, "sorensen1");
		run(this::spawnSorensen);
		beginShot();
		shotFromEyes(at(SOUTH_APPROACH), at(4.5, 4, 8.5), 90);
		robotWalk(SORENSEN_WORK);
		radio(Crew.SORENSEN, "sorensen2");
		robotArrive(SORENSEN_WORK, 700);
		run(() -> {
			PadBuilder.course(world(), origin, 2);
			courses(2);
			workSparks(SORENSEN_WORK);
		});
		wait(30);
		// The walk is the shot; the conversation after it is not.
		endShot();
		radio(H, "sorensen3");
		radio(Crew.SORENSEN, "sorensen4");

		// ---- Course three: the mast, the lights, the crate.
		run(() -> {
			PadBuilder.course(world(), origin, 3);
			courses(3);
			PadBuilder.stockCrate(world(), origin);
			sound(SoundEvents.BLOCK_BEACON_ACTIVATE, PAD_MIDDLE, 0.8f, 1.4f);
		});
		radio(M, "mast1");
		radio(H, "mast2");

		// ---- Course four: Halloran brings the gantry, which is the thing the whole arc runs on.
		label("pad4");
		radio(H, "gantry1");
		run(this::retireSorensen);
		run(this::spawnHalloran);
		robotWalk(HALLORAN_WORK);
		robotArrive(HALLORAN_WORK, 700);
		beginShot();
		run(() -> workSparks(HALLORAN_WORK));
		shotFromEyes(at(0.5, 2.5, 2.5), at(6.5, 5, 8.5), 80);
		run(() -> {
			PadBuilder.course(world(), origin, 4);
			courses(4);
			sound(SoundEvents.BLOCK_ANVIL_PLACE, PAD_MIDDLE, 1.0f, 0.7f);
		});
		wait(40);
		endShot();
		assertReleased("pad");
		radio(H, "gantry2");
		radio(M, "gantry3");
		radio(H, "gantry4");
		run(this::retireHalloran);

		// ================================================================ Three: the core.
		label("core");
		run(() -> {
			checkpoint0(AssayState.STAGE_CORE);
			markTheRock();
		});
		radio(M, "core1");
		radio(H, "core2");
		radio(M, "core3");
		radio(H, "core4");
		// Tellurium only generates below y -8 and the borer line runs at y 8, so this stage is a borer stage
		// whether the pilot is carrying Tanaka's damper or not. Neither line stops anyone going down: one of
		// them is a reminder and the other is a warning, and the difference is the noise.
		branch(() -> rockIsAwake() && hasDamper(), line(H, "core_damper", CinematicPayloads.RADIO));
		branch(() -> rockIsAwake() && !hasDamper(), line(H, "core_nodamper", CinematicPayloads.RADIO));
		objective("core");
		hint("core_nudge");
		until(this::coreDelivered, timeout() * 8, H, "core_nudge", 2400, () -> skippedCore = true);
		branch(() -> skippedCore, line(H, "core_timeout", CinematicPayloads.RADIO));
		branch(() -> !skippedCore, new Beat.Run(this::objectiveDone0),
				line(H, "core_up", CinematicPayloads.RADIO),
				line(M, "core_done", CinematicPayloads.RADIO));
		radio(H, "core5");

		// ================================================================ Four: the payload.
		label("payload");
		run(() -> checkpoint0(AssayState.STAGE_PAYLOAD));
		radio(M, "payload1");
		radio(H, "payload2");
		objective("rocket");
		hint("rocket_nudge");
		until(this::rocketBuilt, timeout() * 6, H, "rocket_nudge", 1800, this::forceRocket);
		objectiveDone();
		radio(H, "payload3");
		// Okafor patches in from Survey Two: the first time the rescue arc's voices reach a company job.
		radio(Crew.OKAFOR, "okafor1");
		radio(H, "okafor2");
		radio(Crew.OKAFOR, "okafor3");
		objective("load");
		hint("load_nudge");
		until(this::payloadLoaded, timeout() * 4, H, "load_nudge", 1500, this::forcePayload);
		objectiveDone();
		radio(M, "payload4");

		// ================================================================ Five: the ignition, and the ship.
		label("ignition");
		run(() -> checkpoint0(AssayState.STAGE_IGNITION));
		radio(H, "gather1");
		run(this::gatherEveryone);
		objective("ignite");
		hint("ignite_nudge");
		until(() -> ignited, timeout() * 4, H, "ignite_nudge", 1500, this::forceIgnite);
		objectiveDone();

		// ---- The countdown. Letterboxed from here to the end.
		beginShot();
		run(() -> padLights(true));
		shotFromEyes(at(0.5, 4, -2.5), at(8.5, 4, 6.5), 90);
		system(PAD, "count1");
		wait(30);
		system(PAD, "count2");
		wait(20);

		// ---- The launch. It goes up first; nobody knows yet that anything is coming for it.
		run(this::launch);
		system(PAD, "launch1");
		// The camera tips back and follows it up until it is a speck.
		shot(cam(0.5, 3, -6.5, 0.5, 30, -2.5, 100, CinematicPayloads.EASE_SMOOTH),
				cam(0.5, 5, -6.5, 0.5, 120, -2.5, 90, CinematicPayloads.EASE_SMOOTH));
		wait(70);
		radio(H, "climb1");
		wait(50);
		radio(M, "climb2");
		radio(H, "climb3");
		// The held pause: three people watching a dot, with nothing to do about any of it.
		wait(70);

		// ---- The interception. The light goes first, then the noise, then the hull.
		run(() -> {
			darkenSky();
			playToPlayer(ModSounds.QUAKE, 1.0f, 0.4f);
			flyover();
			assay.shipSeen = true;
			assay.markDirty();
		});
		// Swing west and up to catch it coming in, then track it across as it passes overhead. Without a shot
		// here the camera stays where the climb left it, pointing at empty sky while the ship crosses behind.
		// Aim points follow the flight path in flyover(): y + 95 along z + 6, west to east. The camera picks
		// it up while it is still out west, holds as it comes over, and follows it away east.
		shot(cam(0.5, 4, 10.5, -70.5, 95, 6.5, 45, CinematicPayloads.EASE_SMOOTH),
				cam(0.5, 4, 10.5, -20.5, 95, 6.5, 55, CinematicPayloads.EASE_SMOOTH),
				cam(0.5, 4, 10.5, 40.5, 95, 6.5, 70, CinematicPayloads.EASE_SMOOTH));
		wait(25);
		radio(M, "intercept1");
		wait(45);
		radio(H, "ship1");
		radio(M, "ship2");
		radio(H, "ship3");
		wait(60);
		run(this::payloadTaken);
		radio(M, "intercept2");
		wait(30);
		radio(H, "intercept3");
		wait(30);
		radio(H, "after2");
		wait(40);
		radio(M, "after3");
		// Marsh's radio goes quiet, and the last word of the contract is hers.
		run(this::sever);
		wait(60);
		radio(H, "after4");
		endShot();
		run(() -> {
			state(false, false, false);
			padLights(false);
		});
		assertReleased("ignition");

		label("done");
		run(() -> {
			checkpoint0(AssayState.STAGE_DONE);
			resetClient();
			leaveStage();
			running = null;
			Surrogate.LOGGER.info("Assay: Contract Seven complete; the company has its payload");
		});
	}

	// ------------------------------------------------------------------ what the script does to the world

	/** Levels and stakes the site, and keeps its chunks loaded until the arc is over. */
	private void arriveAtSite() {
		ServerWorld world = world();
		if (assay.padSite == null) return;
		origin = PadSite.resolve(world, assay);
		forceLoad(true);
		PadBuilder.stake(world, origin);
	}

	/** Puts every course down at once, for a skip or a resumed world. */
	private void finishPad() {
		if (assay.padSite == null) return;
		if (!assay.padResolved) arriveAtSite();
		PadBuilder.rebuild(world(), origin, PadBuilder.COURSES);
		courses(PadBuilder.COURSES);
	}

	/** Rebuilds whatever stood when the world was last saved, quietly. */
	private void restorePad() {
		if (assay.padSite == null || !assay.padResolved || assay.courses <= 0) return;
		origin = assay.padSite;
		forceLoad(true);
		PadBuilder.rebuild(world(), origin, assay.courses);
	}

	void forceLoad(boolean on) {
		ChunkPos centre = new ChunkPos(origin);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				world().setChunkForced(centre.x + dx, centre.z + dz, on);
			}
		}
	}

	private void courses(int n) {
		assay.courses = Math.max(assay.courses, n);
		assay.markDirty();
	}

	private void checkpoint(int stage) {
		assay.stage = stage;
		assay.markDirty();
	}

	private void checkpoint0(int stage) {
		checkpoint(stage);
	}

	/** Sparks and dust where a chassis is fitting its piece. */
	private void workSparks(Vec3d where) {
		Vec3d at = at(where);
		world().spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.6, at.z, 24, 0.4, 0.3, 0.4, 0.08);
		world().spawnParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 12, 0.5, 0.1, 0.5, 0.01);
		sound(SoundEvents.BLOCK_ANVIL_USE, where, 0.7f, 1.2f);
	}

	private void spawnSorensen() {
		sorensenChassis = AnnexBuilder.spawnCrewChassis(world(), Crew.SORENSEN, RobotPaint.SORENSEN, atSurface(SOUTH_APPROACH), 0f, true);
		activeChassis = sorensenChassis;
	}

	private void retireSorensen() {
		sorensenChassis = retire(sorensenChassis);
	}

	private void spawnHalloran() {
		halloranChassis = AnnexBuilder.spawnCrewChassis(world(), Crew.HALLORAN, RobotPaint.HALLORAN, atSurface(ARRIVAL), 180f, true);
		activeChassis = halloranChassis;
	}

	private void retireHalloran() {
		halloranChassis = retire(halloranChassis);
	}

	@Nullable
	private RobotEntity retire(@Nullable RobotEntity chassis) {
		if (chassis != null) AnnexBuilder.killRobot(world(), chassis);
		if (activeChassis == chassis) activeChassis = null;
		return null;
	}

	/**
	 * Everyone who can be here, on the pad, for the last stage. Nobody stands in the open: the air is lethal
	 * and the pad is most of a day's drive from the nearest airlock, so every one of them is a chassis.
	 */
	private void gatherEveryone() {
		if (halloranChassis == null) spawnHalloran();
		if (marshChassis == null) {
			marshChassis = AnnexBuilder.spawnCrewChassis(world(), Crew.MARSH, RobotPaint.MARSH, atSurface(MARSH_EDGE), 225f, true);
		}
		if (sorensenChassis == null) {
			sorensenChassis = AnnexBuilder.spawnCrewChassis(world(), Crew.SORENSEN, RobotPaint.SORENSEN, atSurface(SORENSEN_RAIL), 270f, true);
		}
	}

	private void padLights(boolean on) {
		// The corner lights and the mast are already emissive; the strobe is the sound and the particles.
		Vec3d at = at(PAD_MIDDLE);
		if (on) {
			world().spawnParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 60, 5.0, 0.5, 5.0, 0.01);
			sound(SoundEvents.BLOCK_BEACON_POWER_SELECT, PAD_MIDDLE, 1.0f, 0.7f);
		}
	}

	private void darkenSky() {
		ServerWorld world = world();
		world.setWeather(0, 6000, true, false);
		Vec3d at = at(PAD_MIDDLE);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 30, at.z, 120, 12.0, 3.0, 12.0, 0.02);
	}

	/** The rocket goes. It climbs under its own power from here; the script just watches it. */
	private void launch() {
		if (!rocketBuilt()) buildRocket();
		RocketEntity rocket = rocket();
		if (rocket != null) rocket.ignite();
	}

	/**
	 * The company ship crosses the valley. It comes in from the west on a fixed heading, high but not high
	 * enough to be comfortable, and it does not slow down: it takes the payload in passing and is gone.
	 */
	private void flyover() {
		ServerWorld world = world();
		CompanyShipEntity ship = new CompanyShipEntity(ModEntities.COMPANY_SHIP, world);
		// It has to spawn inside the client's tracking window or nobody ever sees it: entity tracking is
		// capped by the server's view distance (96 blocks at view-distance 6), not by maxTrackingRange, so a
		// ship spawned half a kilometre out simply does not exist for the client until it is nearly overhead.
		// Enter close and low enough to be inside that window from the first tick, and cross slowly enough to
		// be looked at.
		double y = origin.getY() + 95;
		ship.refreshPositionAndAngles(origin.getX() - 90, y, origin.getZ() + 6, 270f, 0f);
		ship.setSpeed(0.85f);
		ship.setLife(320);
		world.spawnEntity(ship);
		world.playSound(null, origin, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 4.0f, 0.35f);
		Surrogate.LOGGER.info("Assay: the ship is overhead");
	}

	private void payloadTaken() {
		assay.payloadLoaded = false;
		assay.markDirty();
		ServerWorld world = world();
		world.setWeather(6000, 0, false, false);
	}

	/** The company's channel closes. Marsh is still standing there; the company is not. */
	private void sever() {
		playToPlayer(ModSounds.SIGNAL_LOST, 0.9f);
	}

	private void forceRocket() {
		// Nobody built it, so Halloran's chassis did, off screen.
		buildRocket();
	}

	private void forcePayload() {
		assay.payloadLoaded = true;
		assay.markDirty();
		RocketEntity rocket = rocket();
		if (rocket != null) rocket.setLoaded(true);
	}

	private void forceIgnite() {
		ignited = true;
	}

	/** Puts the rocket on the pad, the same way the gantry would have. Used when the player never built one. */
	private void buildRocket() {
		if (rocket() != null) return;
		BlockPos base = origin.add(PadBuilder.ROCKET);
		Vec3d at = new Vec3d(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
		((RocketKitItem) ModItems.ROCKET_KIT).assemble(world(), at, 180f);
	}

	/** The rocket standing on the pad, if one has been built. */
	@Nullable
	private RocketEntity rocket() {
		BlockPos base = origin.add(PadBuilder.ROCKET);
		Box around = new Box(base).expand(6.0, 8.0, 6.0);
		return world().getEntitiesByClass(RocketEntity.class, around, r -> !r.isRemoved()).stream().findFirst().orElse(null);
	}

	// ------------------------------------------------------------------ conditions

	private boolean isProtagonist(ServerPlayerEntity player) {
		return habitat.protagonist != null && habitat.protagonist.equals(player.getUuid());
	}

	/** The player, or the chassis they are driving, is standing on the designated ground. */
	private boolean playerAtSite() {
		if (assay.padSite == null) return false;
		ServerPlayerEntity player = player();
		if (player == null) return false;
		BlockPos site = assay.padSite;
		double range = 24.0 * 24.0;
		if (horizontalDistanceSq(player.getPos(), site) < range) return true;
		RobotEntity robot = player.getVehicle() instanceof RobotEntity driven && driven.isPilot(player) ? driven : null;
		return robot != null && horizontalDistanceSq(robot.getPos(), site) < range;
	}

	private static double horizontalDistanceSq(Vec3d pos, BlockPos site) {
		double dx = pos.x - (site.getX() + 0.5);
		double dz = pos.z - (site.getZ() + 0.5);
		return dx * dx + dz * dz;
	}

	private boolean apronLaid() {
		if (apronDone) return true;
		// Most of it, not all of it: nobody should have to chase the last corner block.
		return PadBuilder.apronLaid(world(), origin) >= PadBuilder.apronTotal() * 3 / 4;
	}

	/** Whether anything under this world would come for a drill at all. With hazards off, none of it is said. */
	private boolean rockIsAwake() {
		return Borers.enabled(world());
	}

	/**
	 * Tanaka's damper, anywhere the pilot can reach it: handed over in act two, already fitted to the chassis
	 * they are driving, or loose in a pocket. A player who never made the drive out gets the other line and
	 * does the stage anyway.
	 */
	private boolean hasDamper() {
		if (ResearchState.get(server).damper) return true;
		ServerPlayerEntity player = player();
		if (player == null) return false;
		if (player.getVehicle() instanceof RobotEntity robot && robot.hasModule(RobotModule.DAMPER)) return true;
		return player.getInventory().containsAny(stack -> stack.isOf(ModItems.RESONANCE_DAMPER));
	}

	private boolean coreDelivered() {
		int held = crateCount(ModItems.TELLURIUM_CRYSTAL);
		if (held > assay.coreSamples) {
			assay.coreSamples = held;
			assay.markDirty();
		}
		return assay.coreSamples >= CORE_TARGET;
	}

	private boolean rocketBuilt() {
		return rocket() != null;
	}

	private boolean payloadLoaded() {
		if (assay.payloadLoaded) return true;
		RocketEntity rocket = rocket();
		if (rocket != null && rocket.isLoaded()) {
			assay.payloadLoaded = true;
			assay.markDirty();
			return true;
		}
		// The capsule takes whatever the assay has been collecting.
		if (crateCount(ModItems.SULFUR) > 0 || crateCount(ModItems.CINNABAR) > 0 || crateCount(ModItems.SALT) > 0) {
			assay.payloadLoaded = true;
			assay.markDirty();
		}
		return assay.payloadLoaded;
	}

	private int crateCount(net.minecraft.item.Item item) {
		return world().getBlockEntity(origin.add(PadBuilder.CRATE)) instanceof ChestBlockEntity chest ? chest.count(item) : 0;
	}

	/** A tellurium crystal used on the rocket lights it. */
	public boolean onRocketUsed(PlayerEntity player, ItemStack stack) {
		RocketEntity rocket = rocket();
		// Stage four: anything the assay collected goes in the capsule.
		if (rocket != null && !rocket.isLoaded() && assay.stage == AssayState.STAGE_PAYLOAD
				&& (stack.isOf(ModItems.SULFUR) || stack.isOf(ModItems.CINNABAR) || stack.isOf(ModItems.SALT))) {
			stack.decrement(1);
			rocket.setLoaded(true);
			world().playSound(null, rocket.getBlockPos(), SoundEvents.BLOCK_BARREL_CLOSE, SoundCategory.BLOCKS, 1.0f, 0.8f);
			return true;
		}
		if (ignited || !stack.isOf(ModItems.TELLURIUM_CRYSTAL)) return false;
		if (assay.stage != AssayState.STAGE_IGNITION) return false;
		stack.decrement(1);
		ignited = true;
		Vec3d at = at(PadBuilder.ROCKET.getX() + 0.5, PadBuilder.ROCKET.getY() + 1.0, PadBuilder.ROCKET.getZ() + 0.5);
		world().spawnParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 40, 0.4, 0.8, 0.4, 0.05);
		world().playSound(null, BlockPos.ofFloored(at), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 1.2f, 0.6f);
		return true;
	}

	// ------------------------------------------------------------------ what a script needs from its world

	@Override
	@Nullable
	public ServerPlayerEntity player() {
		return habitat.protagonist == null ? null : server.getPlayerManager().getPlayer(habitat.protagonist);
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
		var id = who == Crew.HALLORAN ? habitat.halloran : who == Crew.MARSH ? habitat.marsh : null;
		return id != null && world().getEntity(id) instanceof CrewEntity found ? found : null;
	}

	@Override
	@Nullable
	public RobotEntity scriptedRobot() {
		return activeChassis;
	}
}
