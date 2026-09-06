package dev.psyda.surrogate.entity;

import java.util.Locale;

public enum RobotState {
	OFFLINE,
	BOOTING,
	ONLINE,
	SHUTTING_DOWN;

	private static final RobotState[] VALUES = values();

	public static RobotState byId(int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : OFFLINE;
	}

	public static RobotState byName(String name) {
		for (RobotState state : VALUES) {
			if (state.name().equalsIgnoreCase(name)) return state;
		}
		return OFFLINE;
	}

	public boolean isPowered() {
		return this != OFFLINE;
	}

	public String translationKey() {
		return "state.surrogate." + name().toLowerCase(Locale.ROOT);
	}
}
