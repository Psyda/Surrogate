package dev.psyda.surrogate.atmosphere;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.SurrogateConfig;
import dev.psyda.surrogate.pilot.PilotData;
import dev.psyda.surrogate.registry.ModEffects;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * What the outside air does to a pilot's body. The body is wherever the player physically is, except while
 * linked, when it is lying in the dive chair and that is where the air matters.
 */
public final class Exposure {
	public static final int SAFE = 0;
	public static final int EXPOSED = 1;
	public static final int LEAK = 2;
	public static final int SEALED = 3;

	/** How much slower the lungs fill while a rebreather is running. */
	private static final float REBREATHER_RATE = 0.3f;

	public record Report(int state, float quality) {
	}

	private Exposure() {
	}

	public static World bodyWorld(ServerPlayerEntity player, PilotData data) {
		if (data.session != null) {
			ServerWorld chairWorld = player.server.getWorld(data.session.chairDimension);
			if (chairWorld != null) return chairWorld;
		}
		return player.getWorld();
	}

	public static BlockPos bodyPos(ServerPlayerEntity player, PilotData data) {
		if (data.session != null && player.server.getWorld(data.session.chairDimension) != null) {
			return data.session.chairPos.up();
		}
		return BlockPos.ofFloored(player.getEyePos());
	}

	public static Report inspect(World world, BlockPos pos) {
		if (!Atmosphere.isToxic(world, pos)) return new Report(SAFE, 1f);
		SealedVolume volume = Atmosphere.volumeAt(world, pos);
		if (volume == null) return new Report(EXPOSED, 0f);
		return new Report(volume.sealed ? SEALED : LEAK, volume.quality);
	}

	/** Runs every tick for every player. Returns what the body is breathing so the HUD can show it. */
	public static Report tick(ServerPlayerEntity player, PilotData data) {
		SurrogateConfig cfg = Surrogate.CONFIG;
		World world = bodyWorld(player, data);
		BlockPos pos = bodyPos(player, data);
		Report report = inspect(world, pos);
		boolean exempt = player.isCreative() || player.isSpectator() || !player.isAlive();
		boolean masked = player.hasStatusEffect(ModEffects.REBREATHER);

		float rate = report.state == SAFE || exempt ? 0f : perTick(cfg.exposureSeconds) * (1f - report.quality);
		if (masked) rate *= REBREATHER_RATE;
		if (rate > 1e-6f) {
			data.toxin = Math.min(1f, data.toxin + rate);
			data.exposedTicks++;
			if (data.exposedTicks == 8) warn(player, data.session != null);
		} else {
			data.toxin = Math.max(0f, data.toxin - perTick(cfg.toxinRecoverySeconds));
			data.exposedTicks = 0;
		}

		if (!exempt && data.toxin > 0f && player.age % 10 == 0) {
			applyEffects(player, world, data.toxin, masked);
		}
		return report;
	}

	/**
	 * The air is not a slow poison, it is caustic: a few breaths and the world swims, then goes dark, then
	 * the lungs give out. From the first breath outside to death is about ten seconds at default settings.
	 * A rebreather keeps the lungs going but not the head clear.
	 */
	private static void applyEffects(ServerPlayerEntity player, World world, float toxin, boolean masked) {
		if (toxin >= 0.12f) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0, true, false, false));
		}
		if (toxin >= 0.3f) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 60, 0, true, false, false));
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1, true, false, false));
		}
		if (masked) return;
		if (toxin >= 0.5f) {
			player.damage(ModDamageTypes.toxin(world), toxin >= 0.75f ? 3f : 2f);
			if (player.age % 20 == 0) {
				world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_HURT_DROWN, SoundCategory.PLAYERS, 0.8f, 0.7f);
			}
		}
		if (toxin >= 0.75f) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 1, true, false, false));
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 30, 0, true, false, false));
		}
	}

	private static void warn(ServerPlayerEntity player, boolean linked) {
		player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 40, 15));
		player.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable(linked ? "title.surrogate.body_exposed" : "title.surrogate.exposed")));
		player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable(linked ? "title.surrogate.body_exposed.sub" : "title.surrogate.exposed.sub")));
		player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.8f, 0.5f);
	}

	private static float perTick(double seconds) {
		return seconds <= 0 ? 1f : (float) (1.0 / (seconds * 20.0));
	}

	public static int percent(float fraction) {
		return Math.round(MathHelper.clamp(fraction, 0f, 1f) * 100f);
	}
}
