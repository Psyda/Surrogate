package dev.psyda.surrogate.network;

import dev.psyda.surrogate.Surrogate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.encoding.VarInts;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client messages that drive the cinematic layer: letterbox and input lock, a keyframed camera,
 * subtitles, fades, objectives and screen effects. Text travels as translation keys so the client renders
 * it in its own language. One client to server message asks to skip the whole sequence.
 */
public final class CinematicPayloads {
	// Subtitle styles
	public static final int NARRATION = 0;
	public static final int SPEECH = 1;
	public static final int RADIO = 2;
	public static final int ALERT = 3;
	public static final int TITLE = 4;
	/** A chapter heading: smaller than a title, with a subtitle that may carry one format argument. */
	public static final int CHAPTER = 5;
	/** Someone aboard the same vessel, speaking over its own speakers. */
	public static final int INTERCOM = 6;
	/** A machine talking. */
	public static final int SYSTEM = 7;

	// Objective states
	public static final int OBJECTIVE_CLEAR = 0;
	public static final int OBJECTIVE_SHOW = 1;
	public static final int OBJECTIVE_DONE = 2;

	// Effects
	public static final int EFFECT_SHAKE = 0;

	// Camera easing
	public static final int EASE_LINEAR = 0;
	public static final int EASE_SMOOTH = 1;
	public static final int EASE_OUT = 2;

	/** Whether the cinematic layer is on at all, whether the player may move, and whether the bars are down. */
	public record State(boolean active, boolean lockInput, boolean letterbox) implements CustomPayload {
		public static final Id<State> ID = new Id<>(Surrogate.id("cinematic_state"));
		public static final PacketCodec<ByteBuf, State> CODEC = PacketCodec.of((p, buf) -> {
			buf.writeBoolean(p.active);
			buf.writeBoolean(p.lockInput);
			buf.writeBoolean(p.letterbox);
		}, buf -> new State(buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** One camera keyframe; {@code ticks} is how long the move to the next frame takes. */
	public record Frame(double x, double y, double z, float yaw, float pitch, int ticks, int ease) {
		static void write(Frame f, ByteBuf buf) {
			buf.writeDouble(f.x);
			buf.writeDouble(f.y);
			buf.writeDouble(f.z);
			buf.writeFloat(f.yaw);
			buf.writeFloat(f.pitch);
			VarInts.write(buf, f.ticks);
			VarInts.write(buf, f.ease);
		}

		static Frame read(ByteBuf buf) {
			return new Frame(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), VarInts.read(buf), VarInts.read(buf));
		}
	}

	/** A camera path. An empty list hands the camera back to the player. */
	public record Camera(List<Frame> frames) implements CustomPayload {
		public static final Id<Camera> ID = new Id<>(Surrogate.id("cinematic_camera"));
		public static final PacketCodec<ByteBuf, Camera> CODEC = PacketCodec.of((p, buf) -> {
			VarInts.write(buf, p.frames.size());
			for (Frame frame : p.frames) Frame.write(frame, buf);
		}, buf -> {
			int count = VarInts.read(buf);
			List<Frame> frames = new ArrayList<>(count);
			for (int i = 0; i < count; i++) frames.add(Frame.read(buf));
			return new Camera(frames);
		});

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/**
	 * A subtitle. {@code speaker} and {@code text} are translation keys (either may be empty), {@code voice}
	 * is a sound id the client plays if it has such a sound, so recorded lines can be dropped in later, and
	 * {@code arg} is an optional format argument for the text (or, for chapters, the subtitle).
	 *
	 * <p>{@code advance} is whether the player may press on through this one. It rides on the line rather
	 * than on the scene because it is a fact about the subtitle, and because the client has to know it to
	 * decide whether to offer the prompt: a prompt for a key that does nothing is worse than no prompt.
	 */
	public record Line(String speaker, String text, int style, int ticks, String voice, String arg, boolean advance) implements CustomPayload {
		public static final Id<Line> ID = new Id<>(Surrogate.id("cinematic_line"));
		public static final PacketCodec<ByteBuf, Line> CODEC = PacketCodec.of((p, buf) -> {
			PacketCodecs.STRING.encode(buf, p.speaker);
			PacketCodecs.STRING.encode(buf, p.text);
			VarInts.write(buf, p.style);
			VarInts.write(buf, p.ticks);
			PacketCodecs.STRING.encode(buf, p.voice);
			PacketCodecs.STRING.encode(buf, p.arg);
			buf.writeBoolean(p.advance);
		}, buf -> new Line(PacketCodecs.STRING.decode(buf), PacketCodecs.STRING.decode(buf), VarInts.read(buf), VarInts.read(buf),
				PacketCodecs.STRING.decode(buf), PacketCodecs.STRING.decode(buf), buf.readBoolean()));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Fade the screen to {@code alpha} (0 clear, 255 black) over {@code ticks}. */
	public record Fade(int alpha, int ticks) implements CustomPayload {
		public static final Id<Fade> ID = new Id<>(Surrogate.id("cinematic_fade"));
		public static final PacketCodec<ByteBuf, Fade> CODEC = PacketCodec.of((p, buf) -> {
			VarInts.write(buf, p.alpha);
			VarInts.write(buf, p.ticks);
		}, buf -> new Fade(VarInts.read(buf), VarInts.read(buf)));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Show, complete or clear the objective banner. {@code text} is a translation key. */
	public record Objective(String text, int state) implements CustomPayload {
		public static final Id<Objective> ID = new Id<>(Surrogate.id("cinematic_objective"));
		public static final PacketCodec<ByteBuf, Objective> CODEC = PacketCodec.of((p, buf) -> {
			PacketCodecs.STRING.encode(buf, p.text);
			VarInts.write(buf, p.state);
		}, buf -> new Objective(PacketCodecs.STRING.decode(buf), VarInts.read(buf)));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** The current objective in plain words (a translation key) for the log screen, or empty to clear it. */
	public record Hint(String text) implements CustomPayload {
		public static final Id<Hint> ID = new Id<>(Surrogate.id("cinematic_hint"));
		public static final PacketCodec<ByteBuf, Hint> CODEC = PacketCodec.of((p, buf) -> PacketCodecs.STRING.encode(buf, p.text),
				buf -> new Hint(PacketCodecs.STRING.decode(buf)));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** A screen effect, currently only camera shake. */
	public record Effect(int kind, float strength, int ticks) implements CustomPayload {
		public static final Id<Effect> ID = new Id<>(Surrogate.id("cinematic_effect"));
		public static final PacketCodec<ByteBuf, Effect> CODEC = PacketCodec.of((p, buf) -> {
			VarInts.write(buf, p.kind);
			buf.writeFloat(p.strength);
			VarInts.write(buf, p.ticks);
		}, buf -> new Effect(VarInts.read(buf), buf.readFloat(), VarInts.read(buf)));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/**
	 * The conference call on the hub terminal: which of {@link dev.psyda.surrogate.rescue.CallPanel}'s tiles
	 * are up, which have gone to snow, and whose turn it is to talk.
	 *
	 * <p>Two bitmasks and an index rather than a list of panels, because the roster is fixed and the ordinal
	 * is already the wire format. A {@code live} of zero closes the call; so does the state payload going
	 * inactive, which is the one every director sends at the end of a scene without knowing this exists.
	 *
	 * @param speaking the ordinal of whoever is talking, or -1 for nobody
	 */
	public record Call(int live, int snow, int speaking) implements CustomPayload {
		public static final Id<Call> ID = new Id<>(Surrogate.id("cinematic_call"));
		public static final PacketCodec<ByteBuf, Call> CODEC = PacketCodec.of((p, buf) -> {
			VarInts.write(buf, p.live);
			VarInts.write(buf, p.snow);
			VarInts.write(buf, p.speaking);
		}, buf -> new Call(VarInts.read(buf), VarInts.read(buf), VarInts.read(buf)));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/**
	 * Server to client: a question with answers, for the player to pick one of.
	 *
	 * <p>The only place in the game where a script waits on a decision rather than on a position. The
	 * flashback asks these; everything else says its piece and carries on. {@code prompt} and {@code options}
	 * are translation keys, and the answer comes back as {@link Choice.Picked} with an index into them.
	 */
	public record Choice(String prompt, String speaker, List<String> options) implements CustomPayload {
		public static final Id<Choice> ID = new Id<>(Surrogate.id("cinematic_choice"));
		public static final PacketCodec<ByteBuf, Choice> CODEC = PacketCodec.of((p, buf) -> {
			PacketCodecs.STRING.encode(buf, p.prompt);
			PacketCodecs.STRING.encode(buf, p.speaker);
			VarInts.write(buf, p.options.size());
			for (String option : p.options) PacketCodecs.STRING.encode(buf, option);
		}, buf -> {
			String prompt = PacketCodecs.STRING.decode(buf);
			String speaker = PacketCodecs.STRING.decode(buf);
			int count = VarInts.read(buf);
			List<String> options = new java.util.ArrayList<>(count);
			for (int i = 0; i < count; i++) options.add(PacketCodecs.STRING.decode(buf));
			return new Choice(prompt, speaker, List.copyOf(options));
		});

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}

		/** Client to server: which of them. A negative index means the question went unanswered. */
		public record Picked(int index) implements CustomPayload {
			public static final Id<Picked> ID = new Id<>(Surrogate.id("cinematic_choice_picked"));
			public static final PacketCodec<ByteBuf, Picked> CODEC =
					PacketCodec.of((p, buf) -> VarInts.write(buf, p.index), buf -> new Picked(VarInts.read(buf)));

			@Override
			public Id<? extends CustomPayload> getId() {
				return ID;
			}
		}
	}

	/** Client to server: the player held the skip key. */
	public record Skip() implements CustomPayload {
		public static final Id<Skip> ID = new Id<>(Surrogate.id("cinematic_skip"));
		public static final PacketCodec<ByteBuf, Skip> CODEC = PacketCodec.unit(new Skip());

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/**
	 * Client to server: the player pressed on through the line they were reading.
	 *
	 * <p>Separate from {@link Skip}, which is held rather than pressed and takes the whole scene. This one
	 * only ever shortens the subtitle that is on screen, and only outside a shot — inside one the player has
	 * no hands and the hold-to-skip prompt is the offer instead.
	 */
	public record Advance() implements CustomPayload {
		public static final Id<Advance> ID = new Id<>(Surrogate.id("cinematic_advance"));
		public static final PacketCodec<ByteBuf, Advance> CODEC = PacketCodec.unit(new Advance());

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	private CinematicPayloads() {
	}
}
