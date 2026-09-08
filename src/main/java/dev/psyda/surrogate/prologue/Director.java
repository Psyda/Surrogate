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

	/**
	 * The player pressed on through a line.
	 *
	 * <p>Only the line is cut, never the beat after it: a script's shape is its own, and reading faster than
	 * the estimate is not the same as asking to be somewhere else. Scripts that want a shortcut override
	 * {@link #onSkip}.
	 */
	public static void advanceRequested(ServerPlayerEntity player) {
		if (onStage != null) onStage.onAdvance(player);
	}

	protected void onAdvance(ServerPlayerEntity player) {
		if (allowAdvance() && current != null) current.cut();
	}

	// ------------------------------------------------------------------ questions

	/** The question on the table, and what came back. Null and -1 when nothing is being asked. */
	@Nullable
	private String askingId;
	@Nullable
	private Question asking;
	private int answer = -1;

	/** The player picked one, or the screen closed. @return whether anything was actually waiting for it */
	public static boolean choicePicked(ServerPlayerEntity player, int index) {
		if (onStage == null) return false;
		Surrogate.LOGGER.info("Director: {} picked {} for {}", player.getGameProfile().getName(), index, onStage.askingId);
		return onStage.answerNow(index);
	}

	/** What is being asked right now, or null. For the dev command, which has to know before it answers. */
	@Nullable
	public static String questionOnTheTable() {
		return onStage == null ? null : onStage.askingId;
	}

	void askNow(Question question) {
		askingId = question.id();
		answer = -1;
		asking = question;
		send(new CinematicPayloads.Choice(question.prompt(), question.speaker(), question.options()));
	}

	boolean answerNow(int index) {
		if (askingId == null || answer >= 0) return false;
		answer = Math.max(0, index);
		Question question = asking;
		askingId = null;
		asking = null;
		// Take the screen down before the script moves on, or a scene that ends here ends behind a dialogue.
		send(new CinematicPayloads.Choice("", "", List.of()));
		if (question != null) onAnswer(question, answer);
		return true;
	}

	boolean answered() {
		return answer >= 0;
	}

	/** What the player said. Overridden by scripts that ask anything. */
	protected void onAnswer(Question question, int index) {
	}

	/** Any dialogue still on somebody's screen, taken down. Called when a scene ends however it ended. */
	protected void closeQuestion() {
		if (askingId == null) return;
		askingId = null;
		asking = null;
		send(new CinematicPayloads.Choice("", "", List.of()));
	}

	/**
	 * Whether this script's lines may be pressed through.
	 *
	 * <p>True for everything that is a conversation. A script overrides it to false when its lines are the
	 * scene rather than the delivery of it — where reading on is not a courtesy but a way of missing the
	 * whole thing, usually by leaning on sneak without meaning anything by it.
	 */
	protected boolean allowAdvance() {
		return true;
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

	/**
	 * Take whatever is on stage off it, camera and all. Only the dev commands want this: a jump into the
	 * middle of one arc while another is running would otherwise reset the state and then refuse to start,
	 * leaving the world reading as an arc that is waiting for a beat nobody is playing.
	 */
	public static void clearStage() {
		if (onStage != null) onStage.leaveStage();
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
		ServerPlayerEntity watching = player();
		if (watching == null) return;
		// A shot held over a death screen is a shot nobody is ever going to be handed back: the respawn puts
		// a new body in the world and the camera is still bolted to the scenery. Give it back and let the
		// script carry on; whatever it was showing is over either way.
		if (holding && !watching.isAlive()) {
			Surrogate.LOGGER.warn("Director: player died mid-shot at {}, releasing the camera", currentLabel());
			endCinematic();
		}
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

	/**
	 * A question, and the script held until it is answered.
	 *
	 * <p>{@code id} is what the answer is filed under, {@code prompt} and {@code options} are lang keys.
	 * Scripts read the result back with {@link #onAnswer}, which is called once, with the index picked.
	 */
	protected void ask(java.util.function.Supplier<Question> question, int timeout) {
		beats.add(new Beat.Ask(question, fast ? Math.max(100, timeout / 4) : timeout));
	}

	/** One question: what the answer is filed under, who is asking, and the lang keys for all of it. */
	public record Question(String id, String prompt, String speaker, List<String> options) {
	}

	/** Narration whose key depends on something the player has not decided yet. Empty means say nothing. */
	protected void narrationOf(java.util.function.Supplier<String> fullKey) {
		beats.add(new Beat.Dynamic(fullKey, CinematicPayloads.NARRATION));
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

	/** Waits for the player, with something other than a line as the nudge: a horn outside, a light going off. */
	protected void until(BooleanSupplier condition, int timeout, int nudgeAfter, @Nullable Runnable onNudge, @Nullable Runnable onTimeout) {
		int nudge = fast ? Math.min(nudgeAfter, 60) : nudgeAfter;
		int limit = fast ? Math.max(100, timeout / 4) : timeout;
		beats.add(new Beat.Until(condition, limit, null, null, nudge, onTimeout, onNudge));
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
		closeQuestion();
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
		send(new CinematicPayloads.Line(speakerKey, textKey, style, ticks, voice, arg, allowAdvance()));
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
