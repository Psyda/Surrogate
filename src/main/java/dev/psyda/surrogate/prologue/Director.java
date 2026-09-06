package dev.psyda.surrogate.prologue;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.network.CinematicPayloads;
import dev.psyda.surrogate.network.CinematicPayloads.Frame;
import dev.psyda.surrogate.registry.ModSounds;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Runs a scripted sequence: a flat list of {@link Beat}s ticked in order, plus the vocabulary the scripts are
 * written in (lines, shots, walks, objectives, fades) and the plumbing that talks to the client's cinematic
 * layer. The opening in the pod ({@link Prologue}) and the week on the ship before it
 * ({@link dev.psyda.surrogate.transit.Transit}) are both directors; at most one is on stage at a time.
 */
public abstract class Director {
	/** Dev switch: short lines and quarter-length waits and timeouts. Applies to the next start. */
	public static boolean fast;

	@Nullable
	private static Director onStage;

	protected final MinecraftServer server;
	/** Prefix of the lang keys this script's lines live under, ending in a dot. */
	protected final String key;
	protected final List<Beat> beats = new ArrayList<>();
	protected int index = -1;
	@Nullable
	protected Beat current;
	protected int startDelay;
	private int lastLineTicks;
	/** Whether this director is holding the camera and the player's input. */
	private boolean holding;
	@Nullable
	protected String hintKey;

	protected Director(MinecraftServer server, String key) {
		this.server = server;
		this.key = key;
	}

	// ------------------------------------------------------------------ who is on stage

	/** The director currently telling a story, if any. */
	@Nullable
	public static Director current() {
		return onStage;
	}

	/** The player held the skip key. */
	public static void skipRequested(ServerPlayerEntity player) {
		if (onStage != null) onStage.onSkip(player);
	}

	/** {@code player} used a field radio; a script may be waiting for exactly that. */
	public static void radioUsed(ServerPlayerEntity player) {
		if (onStage != null) onStage.onRadioUsed(player);
	}

	protected void takeStage() {
		onStage = this;
	}

	protected void leaveStage() {
		endCinematic();
		if (onStage == this) onStage = null;
	}

	// ------------------------------------------------------------------ what a script needs from its world

	@Nullable
	public abstract ServerPlayerEntity player();

	public abstract ServerWorld world();

	public abstract BlockPos origin();

	@Nullable
	public abstract CrewEntity crew(Crew who);

	/** The chassis a script may drive around, if it has one. */
	@Nullable
	public RobotEntity scriptedRobot() {
		return null;
	}

	/** Called every tick while a radio line from {@code who} is on screen. */
	protected void onRadioLine(Crew who) {
	}

	/** {@code player} keyed a field radio. */
	protected void onRadioUsed(ServerPlayerEntity player) {
	}

	/** {@code player} used {@code stack} on a crew member. @return true if the script took the item or the moment */
	public boolean onCrewUsed(Crew who, ServerPlayerEntity player, ItemStack stack) {
		return false;
	}

	protected abstract void onSkip(ServerPlayerEntity player);

	protected abstract void buildScript();

	// ------------------------------------------------------------------ ticking

	protected void tick() {
		if (player() == null) return;
		if (startDelay > 0) {
			startDelay--;
			return;
		}
		if (current == null && !advance()) return;
		int guard = 0;
		try {
			while (current != null && current.tick(this)) {
				if (!advance() || ++guard > 500) break;
			}
		} catch (Exception e) {
			// A throwing beat used to kill the script with the camera still detached, which strands the
			// player looking at the scenery forever. Give the camera back, then carry on to the next beat.
			Surrogate.LOGGER.error("Director: beat threw at {}", currentLabel(), e);
			endCinematic();
			advance();
		}
	}

	private boolean advance() {
		index++;
		if (index >= beats.size()) {
			current = null;
			// The script has run out; nothing left to release the camera but this.
			endCinematic();
			return false;
		}
		current = beats.get(index);
		current.start(this);
		return true;
	}

	protected void jump(String label) {
		int target = indexOf(label);
		if (target < 0) return;
		// A jump is a discontinuity: no shot survives one, including a resume into the middle of a script.
		endCinematic();
		index = target - 1;
		current = null;
	}

	protected int indexOf(String label) {
		for (int i = 0; i < beats.size(); i++) {
			if (beats.get(i) instanceof Beat.Label l && l.name.equals(label)) return i;
		}
		return -1;
	}

	/** The next label after the current beat whose name starts with {@code prefix}, or null. */
	@Nullable
	protected String nextLabel(String prefix) {
		for (int i = Math.max(0, index + 1); i < beats.size(); i++) {
			if (beats.get(i) instanceof Beat.Label l && l.name.startsWith(prefix)) return l.name;
		}
		return null;
	}

	void insertAfterCurrent(List<Beat> more) {
		beats.addAll(index + 1, more);
	}

	public String currentLabel() {
		for (int i = Math.min(index, beats.size() - 1); i >= 0; i--) {
			if (beats.get(i) instanceof Beat.Label label) return label.name;
		}
		return "start";
	}

	// ------------------------------------------------------------------ script vocabulary

	protected void label(String name) {
		beats.add(new Beat.Label(name));
	}

	protected void run(Runnable action) {
		beats.add(new Beat.Run(action));
	}

	protected void wait(int ticks) {
		beats.add(new Beat.Wait(fast ? Math.max(5, ticks / 4) : ticks));
	}

	protected Beat line(Crew who, String lineKey) {
		return line(who, lineKey, CinematicPayloads.SPEECH);
	}

	/** A line as an inline beat, for {@link #branch}: spoken, over the radio, or over an intercom. */
	protected Beat line(Crew who, String lineKey, int style) {
		return new Beat.Line(who, who.nameKey(), key + lineKey, style, -1, true, "");
	}

	protected void say(Crew who, String lineKey) {
		beats.add(line(who, lineKey));
	}

	protected void radio(Crew who, String lineKey) {
		beats.add(new Beat.Line(who, who.nameKey(), key + lineKey, CinematicPayloads.RADIO, -1, true, ""));
	}

	/** A line over the ship's own speakers: the speaker is aboard but not in the room. */
	protected void intercom(Crew who, String lineKey) {
		beats.add(new Beat.Line(who, who.nameKey(), key + lineKey, CinematicPayloads.INTERCOM, -1, true, ""));
	}

	/** A line from a machine. {@code speakerKey} names the system. */
	protected void system(String speakerKey, String lineKey) {
		beats.add(new Beat.Line(null, speakerKey, key + lineKey, CinematicPayloads.SYSTEM, -1, true, ""));
	}

	protected void narration(String fullKey) {
		beats.add(new Beat.Line(null, "", fullKey, CinematicPayloads.NARRATION, -1, true, ""));
	}

	protected void alert(String fullKey, String subKey, int ticks) {
		beats.add(new Beat.Line(null, subKey, fullKey, CinematicPayloads.ALERT, ticks, true, ""));
	}

	protected void title(String fullKey, String subKey, int ticks) {
		beats.add(new Beat.Line(null, subKey, fullKey, CinematicPayloads.TITLE, ticks, false, ""));
	}

	/** A smaller title card: a chapter heading with a subtitle that may take one format argument. */
	protected void chapter(String fullKey, String subKey, String arg, int ticks) {
		beats.add(new Beat.Line(null, subKey, fullKey, CinematicPayloads.CHAPTER, ticks, false, arg));
	}

	/**
	 * Takes the camera, the bars and the player's input for a shot. Always paired with {@link #endCinematic},
	 * and released anyway on a jump, on leaving the stage, at the end of the script and if a beat throws, so
	 * a lock can never outlive the shot that took it. Hand-pairing a lock with a release several beats later
	 * is what left players stuck watching.
	 */
	protected void cinematic() {
		holding = true;
		state(true, true, true);
	}

	/** Hands the camera and the player's input back. Safe to call when nothing is held. */
	protected void endCinematic() {
		if (!holding) return;
		holding = false;
		release();
		state(true, false, false);
	}

	/** Queues {@link #cinematic()} as a beat. */
	protected void beginShot() {
		beats.add(new Beat.Run(this::cinematic));
	}

	/** Queues {@link #endCinematic()} as a beat. */
	protected void endShot() {
		beats.add(new Beat.Run(this::endCinematic));
	}

	protected void shot(Frame... frames) {
		beats.add(new Beat.Run(() -> send(new CinematicPayloads.Camera(List.of(frames)))));
	}

	protected void walk(Crew who, Vec3d target, @Nullable BlockPos door) {
		beats.add(new Beat.Walk(who, target, door));
	}

	protected void arrive(Crew who, Vec3d target, int timeout) {
		beats.add(new Beat.Arrive(who, target, fast ? Math.max(40, timeout / 2) : timeout));
	}

	protected void robotWalk(Vec3d target) {
		beats.add(new Beat.RobotWalk(target));
	}

	protected void robotArrive(Vec3d target, int timeout) {
		beats.add(new Beat.RobotArrive(target, fast ? Math.max(40, timeout / 2) : timeout));
	}

	protected void until(BooleanSupplier condition, int timeout, @Nullable Crew nudgeWho, @Nullable String nudgeKey, int nudgeAfter, @Nullable Runnable onTimeout) {
		int nudge = fast ? Math.min(nudgeAfter, 60) : nudgeAfter;
		beats.add(new Beat.Until(condition, timeout, nudgeWho, nudgeKey, nudge, onTimeout));
	}

	protected void branch(BooleanSupplier condition, Beat... then) {
		beats.add(new Beat.Branch(condition, List.of(then)));
	}

	protected void jumpIf(BooleanSupplier condition, String label) {
		beats.add(new Beat.JumpIf(condition, label));
	}

	protected void objective(String objectiveKey) {
		beats.add(new Beat.Run(() -> objectiveNow(objectiveKey)));
	}

	protected void objectiveNow(String objectiveKey) {
		send(new CinematicPayloads.Objective(key + "objective." + objectiveKey, CinematicPayloads.OBJECTIVE_SHOW));
	}

	protected void objectiveDone() {
		beats.add(new Beat.Run(this::objectiveDone0));
	}

	protected void objectiveDone0() {
		send(new CinematicPayloads.Objective("", CinematicPayloads.OBJECTIVE_DONE));
		clearHint();
	}

	protected void objectiveClear() {
		send(new CinematicPayloads.Objective("", CinematicPayloads.OBJECTIVE_CLEAR));
		clearHint();
	}

	/** The objective in someone's own words: what the crew say when asked, and what the log screen shows. */
	protected void hint(String hintKey) {
		beats.add(new Beat.Run(() -> {
			this.hintKey = hintKey;
			send(new CinematicPayloads.Hint(key + hintKey));
		}));
	}

	private void clearHint() {
		hintKey = null;
		send(new CinematicPayloads.Hint(""));
	}

	/** Dev: complains in the log if the camera is still held here. The smoke tests grep for this. */
	protected void assertReleased(String where) {
		beats.add(new Beat.Run(() -> {
			if (holding) Surrogate.LOGGER.warn("Director: camera still held at {}", where);
		}));
	}

	protected int timeout() {
		return fast ? 200 : Math.max(200, Surrogate.CONFIG.prologueObjectiveTimeoutTicks);
	}

	/** Scales a wait to the current speed: quarter length in fast mode. */
	protected int scaled(int ticks) {
		return fast ? Math.max(1, ticks / 4) : ticks;
	}

	// ------------------------------------------------------------------ talking to the client

	protected void send(CustomPayload payload) {
		ServerPlayerEntity player = player();
		if (player != null) ServerPlayNetworking.send(player, payload);
	}

	protected void state(boolean active, boolean lockInput, boolean letterbox) {
		send(new CinematicPayloads.State(active, lockInput, letterbox));
	}

	/** Queues a fade as a beat. Inside a {@code run} lambda use {@link #sendFade} instead. */
	protected void fade(int alpha, int ticks) {
		beats.add(new Beat.Run(() -> sendFade(alpha, ticks)));
	}

	protected void sendFade(int alpha, int ticks) {
		send(new CinematicPayloads.Fade(alpha, fast ? Math.max(1, ticks / 4) : ticks));
	}

	protected void effect(int kind, float strength, int ticks) {
		send(new CinematicPayloads.Effect(kind, strength, ticks));
	}

	protected void release() {
		send(new CinematicPayloads.Camera(List.of()));
	}

	/** Everything back to normal on the client: camera, bars, fade, objective. */
	protected void resetClient() {
		holding = false;
		release();
		send(new CinematicPayloads.Objective("", CinematicPayloads.OBJECTIVE_CLEAR));
		send(new CinematicPayloads.Fade(0, 10));
		send(new CinematicPayloads.State(false, false, false));
	}

	/** Shows a subtitle and mirrors it to chat. @return how long the client will show it, in ticks */
	protected int sendLine(String speakerKey, String textKey, int style, int ticks, boolean chat, String arg) {
		ServerPlayerEntity player = player();
		if (player == null) return 1;
		if (ticks < 0) ticks = lineTicks(textKey);
		String voice = ModSounds.VOICE_PREFIX + textKey.replaceFirst("^[a-z]+\\.surrogate\\.", "");
		send(new CinematicPayloads.Line(speakerKey, textKey, style, ticks, voice, arg));
		if (chat) {
			MutableText message = switch (style) {
				case CinematicPayloads.RADIO -> Text.literal("[RADIO] ").formatted(Formatting.DARK_AQUA);
				case CinematicPayloads.INTERCOM -> Text.literal("[INTERCOM] ").formatted(Formatting.GOLD);
				case CinematicPayloads.SYSTEM -> Text.literal("[SYSTEM] ").formatted(Formatting.DARK_GRAY);
				default -> Text.empty();
			};
			message.append(Text.translatable(speakerKey).copy().formatted(Formatting.GOLD))
					.append(Text.literal(": ").formatted(Formatting.GRAY))
					.append(Text.translatable(textKey).copy().formatted(Formatting.WHITE));
			player.sendMessage(message, false);
		}
		lastLineTicks = ticks;
		return ticks;
	}

	protected int lineTicks(String textKey) {
		String text = Text.translatable(textKey).getString();
		double speed = Math.max(4.0, Surrogate.CONFIG.prologueReadSpeed);
		double seconds = Math.max(2.4, text.length() / speed + 1.4);
		int ticks = (int) (seconds * 20);
		return fast ? Math.max(10, ticks / 4) : ticks;
	}

	int lastLineTicks() {
		return lastLineTicks;
	}

	void nudge(Crew who, String nudgeKey) {
		sendLine(who.nameKey(), key + nudgeKey, CinematicPayloads.SPEECH, -1, true, "");
		CrewEntity crew = crew(who);
		if (crew != null && !crew.isFixed()) crew.setLookTarget(player());
	}

	/** What a crew member says when clicked mid-script: the current objective, in their words. */
	@Nullable
	public Text hintFor(Crew who) {
		return hintKey == null ? null : Text.translatable(key + hintKey);
	}

	protected void playToPlayer(SoundEvent sound, float volume) {
		ServerPlayerEntity player = player();
		if (player != null) player.playSoundToPlayer(sound, SoundCategory.MASTER, volume, 1.0f);
	}

	protected void playToPlayer(SoundEvent sound, float volume, float pitch) {
		ServerPlayerEntity player = player();
		if (player != null) player.playSoundToPlayer(sound, SoundCategory.MASTER, volume, pitch);
	}

	protected void sound(SoundEvent sound, Vec3d rel, float volume, float pitch) {
		Vec3d at = at(rel);
		world().playSound(null, at.x, at.y, at.z, sound, SoundCategory.BLOCKS, volume, pitch);
	}

	// ------------------------------------------------------------------ camera

	protected Frame cam(double x, double y, double z, double lookX, double lookY, double lookZ, int ticks, int ease) {
		return frame(at(x, y, z), at(lookX, lookY, lookZ), fast ? Math.max(1, ticks / 4) : ticks, ease);
	}

	protected static Frame frame(Vec3d pos, Vec3d look, int ticks, int ease) {
		double dx = look.x - pos.x;
		double dy = look.y - pos.y;
		double dz = look.z - pos.z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		return new Frame(pos.x, pos.y, pos.z, yaw, pitch, ticks, ease);
	}

	/** A shot that starts a little ahead of the player's own eyes, so their head is behind the lens. */
	protected void shotFromEyes(Vec3d look, Vec3d end, int ticks) {
		ServerPlayerEntity player = player();
		if (player == null) return;
		Vec3d eye = player.getEyePos();
		Vec3d start = eye.add(look.subtract(eye).normalize().multiply(0.45));
		send(new CinematicPayloads.Camera(List.of(frame(start, look, fast ? Math.max(1, ticks / 4) : ticks, CinematicPayloads.EASE_SMOOTH), frame(end, look, 0, 0))));
	}

	// ------------------------------------------------------------------ places

	public Vec3d at(Vec3d rel) {
		return Vec3d.of(origin()).add(rel);
	}

	protected Vec3d at(double x, double y, double z) {
		BlockPos origin = origin();
		return new Vec3d(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
	}

	/** Like {@link #at(Vec3d)} but on the ground, for places outside a building. */
	public Vec3d atSurface(Vec3d rel) {
		BlockPos origin = origin();
		int x = (int) Math.floor(origin.getX() + rel.x);
		int z = (int) Math.floor(origin.getZ() + rel.z);
		int y = world().getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		return new Vec3d(origin.getX() + rel.x, y, origin.getZ() + rel.z);
	}
}
