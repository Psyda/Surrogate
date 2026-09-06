package dev.psyda.surrogate.registry;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameRules;

/**
 * Per-world switches. {@code surrogateIntro} is the week on the ship and the days in the pod: off, and a new
 * world starts in the pod with everything already told, for anyone replaying. Set it under Game Rules when
 * creating the world, or with {@code /gamerule surrogateIntro false} before the first join.
 */
public final class ModGameRules {
	public static final GameRules.Key<GameRules.BooleanRule> INTRO = GameRuleRegistry.register("surrogateIntro", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true));

	private ModGameRules() {
	}

	public static void register() {
	}

	public static boolean intro(MinecraftServer server) {
		return server.getGameRules().getBoolean(INTRO);
	}
}
