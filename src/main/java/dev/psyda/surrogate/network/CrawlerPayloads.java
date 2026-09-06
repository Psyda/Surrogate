package dev.psyda.surrogate.network;

import dev.psyda.surrogate.Surrogate;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** The crawler cabin's traffic: the readout to whoever is aboard, and the keys from whoever is at a console. */
public final class CrawlerPayloads {
	private CrawlerPayloads() {
	}

	/**
	 * Server to client: the hull as the cabin sees it. {@code seat} is the console the player is at (0 none,
	 * 1 helm, 2 docking); {@code lost} means the hull could not be reached this tick.
	 */
	public record State(int seat, boolean lost, float heading, float speed, int charge, boolean docked, boolean collar, float dockOffset, float dockAngle)
			implements CustomPayload {
		public static final Id<State> ID = new Id<>(Surrogate.id("crawler_state"));
		public static final PacketCodec<RegistryByteBuf, State> CODEC = PacketCodec.of((value, buf) -> {
			buf.writeVarInt(value.seat);
			buf.writeBoolean(value.lost);
			buf.writeFloat(value.heading);
			buf.writeFloat(value.speed);
			buf.writeVarInt(value.charge);
			buf.writeBoolean(value.docked);
			buf.writeBoolean(value.collar);
			buf.writeFloat(value.dockOffset);
			buf.writeFloat(value.dockAngle);
		}, buf -> new State(buf.readVarInt(), buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(),
				buf.readFloat(), buf.readFloat()));

		public static State none() {
			return new State(0, false, 0f, 0f, 0, false, false, 0f, 0f);
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/**
	 * Server to client: the sensor picture around the hull, ahead up, one byte a cell (height relative to the
	 * hull in the low bits, flags above; see CrawlerSonar), and where the nearest collar is in the same frame.
	 */
	public record Scan(int size, int step, boolean hasTarget, float targetX, float targetZ, byte[] cells, float[] pois) implements CustomPayload {
		public static final Id<Scan> ID = new Id<>(Surrogate.id("crawler_scan"));
		/** Kinds in {@code pois}, which holds {@code x, z, kind} triples in the hull's frame (blocks): home, Site Two, a shelter, a shelter that is done. */
		public static final int POI_HOME = 1;
		public static final int POI_SITE_TWO = 2;
		public static final int POI_SHELTER = 3;
		public static final int POI_SHELTER_DONE = 4;
		public static final PacketCodec<RegistryByteBuf, Scan> CODEC = PacketCodec.of((value, buf) -> {
			buf.writeVarInt(value.size);
			buf.writeVarInt(value.step);
			buf.writeBoolean(value.hasTarget);
			buf.writeFloat(value.targetX);
			buf.writeFloat(value.targetZ);
			buf.writeByteArray(value.cells);
			buf.writeVarInt(value.pois.length);
			for (float f : value.pois) buf.writeFloat(f);
		}, buf -> {
			int size = buf.readVarInt();
			int step = buf.readVarInt();
			boolean hasTarget = buf.readBoolean();
			float targetX = buf.readFloat();
			float targetZ = buf.readFloat();
			byte[] cells = buf.readByteArray();
			float[] pois = new float[buf.readVarInt()];
			for (int i = 0; i < pois.length; i++) pois[i] = buf.readFloat();
			return new Scan(size, step, hasTarget, targetX, targetZ, cells, pois);
		});

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/**
	 * Server to client: what the hull's cameras can see, as a colour heightmap of the ground around it, the
	 * way the map item sees it: {@code size} by {@code size} cells one block apart from {@code originX/Z}
	 * (world aligned), each a height relative to the hull (signed byte) and a map colour id, plus the hull's
	 * own position and heading and a few nearby entities as {@code x, y, z, kind} quads relative to the hull.
	 */
	public record Camera(int originX, int originZ, int size, double hullX, double hullY, double hullZ, float yaw, byte[] heights, byte[] colors, float[] entities)
			implements CustomPayload {
		public static final Id<Camera> ID = new Id<>(Surrogate.id("crawler_camera"));
		public static final PacketCodec<RegistryByteBuf, Camera> CODEC = PacketCodec.of((value, buf) -> {
			buf.writeVarInt(value.originX);
			buf.writeVarInt(value.originZ);
			buf.writeVarInt(value.size);
			buf.writeDouble(value.hullX);
			buf.writeDouble(value.hullY);
			buf.writeDouble(value.hullZ);
			buf.writeFloat(value.yaw);
			buf.writeByteArray(value.heights);
			buf.writeByteArray(value.colors);
			buf.writeVarInt(value.entities.length);
			for (float f : value.entities) buf.writeFloat(f);
		}, buf -> {
			int originX = buf.readVarInt();
			int originZ = buf.readVarInt();
			int size = buf.readVarInt();
			double hx = buf.readDouble();
			double hy = buf.readDouble();
			double hz = buf.readDouble();
			float yaw = buf.readFloat();
			byte[] heights = buf.readByteArray();
			byte[] colors = buf.readByteArray();
			float[] entities = new float[buf.readVarInt()];
			for (int i = 0; i < entities.length; i++) entities[i] = buf.readFloat();
			return new Camera(originX, originZ, size, hx, hy, hz, yaw, heights, colors, entities);
		});

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/**
	 * Client to server: the keys held at a console this tick, that the player is getting up, or that they
	 * clicked to lock the ring onto the collar.
	 */
	public record Control(float throttle, float steer, boolean leave, boolean lock) implements CustomPayload {
		public static final Id<Control> ID = new Id<>(Surrogate.id("crawler_control"));
		public static final PacketCodec<RegistryByteBuf, Control> CODEC = PacketCodec.of((value, buf) -> {
			buf.writeFloat(value.throttle);
			buf.writeFloat(value.steer);
			buf.writeBoolean(value.leave);
			buf.writeBoolean(value.lock);
		}, buf -> new Control(buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readBoolean()));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
