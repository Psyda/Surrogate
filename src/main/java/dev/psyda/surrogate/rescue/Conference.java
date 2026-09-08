package dev.psyda.surrogate.rescue;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.hazard.Hazards;
import dev.psyda.surrogate.network.CinematicPayloads;
import dev.psyda.surrogate.prologue.Beat;
import dev.psyda.surrogate.prologue.Crew;
import dev.psyda.surrogate.prologue.CrewEntity;
import dev.psyda.surrogate.prologue.Director;
import dev.psyda.surrogate.registry.ModSounds;
import dev.psyda.surrogate.world.HabitatState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Act four: the call. The company's ship took the payload and did not come down, every shelter on the planet
 * is losing a percent of its scrubber a day, and Halloran puts the whole band on one screen to decide what
 * eight people are going to do about it.
 *
 * <p>The scene is the pod's own hub terminal. The camera goes into the screen, the screen becomes eight
 * tiles, and it stays there for the whole call: nobody in this game has ever been in the same room, and the
 * one time they are all in one place it is a grid of faces on a monitor in a shed.
 *
 * <p>Mid-call a storm arrives and takes the far side off the air. That is not decoration. It is how the act
 * ends with the far side as the problem rather than as a conversation, and it is why act five is a drive and
 * not a phone call.
 */
public final class Conference extends Director {
	private static final String KEY = "cinematic.surrogate.conference.";
	/** Where the hub terminal is, relative to the pod origin: the 'M' in the north wall at head height. */
	private static final BlockPos TERMINAL = new BlockPos(2, 2, -4);

	@Nullable
	private static Conference running;

	private final RescueState rescue;
	private final HabitatState habitat;
	private final BlockPos origin;
	/**
	 * Whoever sat down at the terminal. Not the protagonist: on a server the person who opens the hub is
	 * whoever is standing in the pod, and pinning a four minute cinematic with a locked camera onto somebody
	 * eight hundred blocks away in a crawler — while the person who pressed the button sees nothing at all —
	 * is the wrong player in every respect, including who gets handed the span kit at the end.
	 */
	@Nullable
	private final java.util.UUID watcher;

	/** What the client is showing. Kept so a line can change the speaker without restating the whole grid. */
	private int live = CallPanel.EVERYONE;
	private int snow;

	private Conference(MinecraftServer server, RescueState rescue, HabitatState habitat, @Nullable ServerPlayerEntity watcher) {
		super(server, KEY);
		this.rescue = rescue;
		this.habitat = habitat;
		this.watcher = watcher == null ? null : watcher.getUuid();
		this.origin = habitat.origin == null ? BlockPos.ORIGIN : habitat.origin;
		buildScript();
	}

	// ------------------------------------------------------------------ lifecycle

	public static void registerEvents() {
		// Its own clock. A director ticked off somebody else's every-tenth sweep runs every wait ten times
		// too long and never finishes a scene (docs/DESIGN-campaign.md, and the housewarming before it).
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (running != null) running.tick();
		});
		// A call whose audience has gone is abandoned rather than paused. The client that comes back has
		// forgotten the bars and the camera, and resuming into the middle of a four minute scene with the
		// subtitles running and nothing else would read as a bug; it is short, it is behind one terminal,
		// and starting it again is a keypress.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (running == null) return;
			ServerPlayerEntity watching = running.player();
			if (watching != null && watching != handler.player) return;
			Surrogate.LOGGER.info("Conference: the audience left at {}; the call will start over", running.currentLabel());
			running.leaveStage();
			running = null;
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// And off the stage, not just out of the field. A director left holding it refuses to let the
			// next world's arcs open at all, because begin() will not start one while anything is on.
			if (running != null) running.leaveStage();
			running = null;
		});
	}

	@Nullable
	public static Conference running() {
		return running;
	}

	public static boolean isRunning() {
		return running != null;
	}

	/** The call opens the moment somebody sits down at the hub terminal with it waiting for them. */
	public static boolean begin(MinecraftServer server, @Nullable ServerPlayerEntity watcher) {
		RescueState rescue = RescueState.get(server);
		HabitatState habitat = HabitatState.get(server);
		if (rescue.called || habitat.origin == null) return false;
		if (Director.current() != null) return false;
		running = new Conference(server, rescue, habitat, watcher);
		running.takeStage();
		Surrogate.LOGGER.info("Conference: everybody on one screen");
		return true;
	}

	/** Take the call off the air without finishing it: nothing is agreed and nothing is handed over. */
	public static void stop() {
		if (running == null) return;
		running.closeCall();
		running.resetClient();
		running.leaveStage();
		running = null;
	}

	/** Dev, and the resume path: play it from the top whatever else is on stage. */
	public static void restart(MinecraftServer server) {
		RescueState rescue = RescueState.get(server);
		// A dev jump that resets the act while something else holds the stage would leave the world reading
		// as an act nobody is playing, so the stage is cleared first and then taken.
		Director.clearStage();
		running = null;
		rescue.called = false;
		rescue.stage = RescueState.STAGE_CALL;
		rescue.markDirty();
		begin(server, Rescue.protagonist(server));
	}

	@Override
	protected void onSkip(ServerPlayerEntity player) {
		Surrogate.LOGGER.info("Conference: skipped at {}", currentLabel());
		finish();
	}

	/** The call is over, however it ended. The plan is the same either way. */
	private void finish() {
		// Read before the stage is given up: player() falls back once the scene is off the air.
		ServerPlayerEntity watching = player();
		closeCall();
		resetClient();
		leaveStage();
		running = null;
		rescue.called = true;
		rescue.stage = RescueState.STAGE_PLAN;
		rescue.markDirty();
		Rescue.planAgreed(server, watching);
		Surrogate.LOGGER.info("Conference: the plan is one crawler, one chassis, everybody home");
	}

	// ------------------------------------------------------------------ what the script needs

	@Override
	@Nullable
	public ServerPlayerEntity player() {
		if (watcher != null) {
			ServerPlayerEntity found = server.getPlayerManager().getPlayer(watcher);
			if (found != null) return found;
		}
		if (habitat.protagonist != null) {
			ServerPlayerEntity found = server.getPlayerManager().getPlayer(habitat.protagonist);
			if (found != null) return found;
		}
		return Rescue.protagonist(server);
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
		// Nobody is in the room. That is the whole point of the scene.
		return null;
	}

	// ------------------------------------------------------------------ the grid

	private void openCall() {
		live = CallPanel.EVERYONE;
		snow = 0;
		send(new CinematicPayloads.Call(live, snow, -1));
	}

	private void closeCall() {
		live = 0;
		snow = 0;
		send(new CinematicPayloads.Call(0, 0, -1));
	}

	/** Whoever is talking gets the bright frame and the level meter. */
	private void speaker(CallPanel who) {
		send(new CinematicPayloads.Call(live, snow, who.ordinal()));
	}

	/** One line on the call: the panel lights up and the subtitle carries their name. */
	private void talk(CallPanel who, String lineKey) {
		beats.add(new Beat.Run(() -> speaker(who)));
		beats.add(new Beat.Line(null, who.nameKey(), KEY + lineKey, CinematicPayloads.RADIO, -1, true, ""));
	}

	/**
	 * The storm arrives. It is a real storm on the world's own clock, not a light show: whoever walks out of
	 * this room walks out into it, and the far side stays off the band until it passes.
	 */
	private void stormHits() {
		Hazards.forceStorm(server);
		snow = CallPanel.FAR_SIDE;
		live &= ~CallPanel.FAR_SIDE;
		send(new CinematicPayloads.Call(live, snow, -1));
		effect(CinematicPayloads.EFFECT_SHAKE, 0.5f, 40);
		playToPlayer(ModSounds.STORM_WASH, 0.9f, 0.8f);
	}

	// ------------------------------------------------------------------ the script

	@Override
	protected void buildScript() {
		CallPanel H = CallPanel.HALLORAN;
		CallPanel M = CallPanel.MARSH;
		CallPanel O = CallPanel.OKAFOR;
		CallPanel S = CallPanel.SORENSEN;
		CallPanel T = CallPanel.TANAKA;
		CallPanel B = CallPanel.BRANDT;
		CallPanel R = CallPanel.REYES;

		label("open");
		run(() -> {
			cinematic();
			objectiveDone0();
			openCall();
		});
		// Into the screen and no further. The shot never cuts away, because there is nowhere to cut to.
		double screenX = TERMINAL.getX() + 0.5;
		double screenY = TERMINAL.getY() + 0.5;
		double screenZ = TERMINAL.getZ() + 0.1;
		shot(cam(screenX, screenY - 0.2, screenZ + 3.7, screenX, screenY, screenZ, 90, CinematicPayloads.EASE_SMOOTH),
				cam(screenX, screenY - 0.3, screenZ + 1.5, screenX, screenY, screenZ, 0, 0));
		chapter(KEY + "chapter", KEY + "chapter.sub", "", 90);
		wait(40);

		// ---------------------------------------------------------------- one: the room
		talk(H, "roll1");
		talk(O, "roll2");
		talk(S, "roll3");
		talk(T, "roll4");
		talk(B, "roll5");
		talk(H, "roll6");
		talk(R, "roll7");
		talk(H, "roll8");

		// ---------------------------------------------------------------- the schedule
		label("schedule");
		talk(M, "sched1");
		talk(M, "sched2");
		talk(H, "sched3");
		talk(M, "sched4");

		// ---------------------------------------------------------------- two: the margins
		label("margins");
		talk(O, "green1");
		talk(O, "green2");
		talk(S, "shop1");
		talk(S, "shop2");

		// ---------------------------------------------------------------- three: the ground
		label("ground");
		talk(T, "quake1");
		talk(T, "quake2");
		talk(H, "quake3");

		// ---------------------------------------------------------------- four: the far side
		label("far");
		talk(B, "far1");
		talk(B, "far1b");
		talk(R, "far2");
		talk(R, "far3");
		talk(H, "far4");

		// ---------------------------------------------------------------- five: the storm
		label("storm");
		run(this::stormHits);
		alert(KEY + "storm_alert", KEY + "storm_alert.sub", 70);
		wait(30);
		talk(H, "storm1");
		talk(M, "storm2");
		talk(H, "storm3");

		// ---------------------------------------------------------------- six: the plan
		label("plan");
		talk(H, "plan1");
		talk(H, "plan2");
		talk(H, "plan2b");
		talk(S, "plan3");
		talk(O, "plan4");
		talk(M, "plan5");
		talk(M, "plan6");
		talk(M, "plan6b");
		talk(H, "plan7");
		wait(30);

		label("done");
		fade(255, 30);
		wait(35);
		run(this::finish);
		fade(0, 25);
		assertReleased("done");
	}
}
