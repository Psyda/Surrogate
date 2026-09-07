package dev.psyda.surrogate.client;

import dev.psyda.surrogate.network.MissionPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.List;

/**
 * The last mission board the server sent. Held for exactly as long as it takes the terminal screen that
 * follows it to open, which is the next packet.
 */
@Environment(EnvType.CLIENT)
public final class ClientMissionState {
	public static List<MissionPayload.Row> rows = List.of();
	public static List<String> notes = List.of();

	private ClientMissionState() {
	}

	public static void onPayload(MissionPayload payload) {
		rows = payload.rows();
		notes = payload.notes();
	}

	public static boolean hasBoard() {
		return !rows.isEmpty();
	}

	public static void reset() {
		rows = List.of();
		notes = List.of();
	}
}
