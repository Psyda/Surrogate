package dev.psyda.surrogate.network;

import dev.psyda.surrogate.crawler.CrawlerInterior;
import dev.psyda.surrogate.pilot.PilotManager;
import dev.psyda.surrogate.prologue.Director;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.text.Text;

public final class ModNetworking {
	public static void register() {
		PayloadTypeRegistry.playS2C().register(PilotStatusPayload.ID, PilotStatusPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PilotActionPayload.ID, PilotActionPayload.CODEC);

		PayloadTypeRegistry.playS2C().register(CinematicPayloads.State.ID, CinematicPayloads.State.CODEC);
		PayloadTypeRegistry.playS2C().register(CinematicPayloads.Camera.ID, CinematicPayloads.Camera.CODEC);
		PayloadTypeRegistry.playS2C().register(CinematicPayloads.Line.ID, CinematicPayloads.Line.CODEC);
		PayloadTypeRegistry.playS2C().register(CinematicPayloads.Fade.ID, CinematicPayloads.Fade.CODEC);
		PayloadTypeRegistry.playS2C().register(CinematicPayloads.Objective.ID, CinematicPayloads.Objective.CODEC);
		PayloadTypeRegistry.playS2C().register(CinematicPayloads.Effect.ID, CinematicPayloads.Effect.CODEC);
		PayloadTypeRegistry.playS2C().register(CinematicPayloads.Hint.ID, CinematicPayloads.Hint.CODEC);
		PayloadTypeRegistry.playS2C().register(CinematicPayloads.Call.ID, CinematicPayloads.Call.CODEC);
		PayloadTypeRegistry.playS2C().register(TransitPayload.ID, TransitPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TerminalPayload.ID, TerminalPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(MissionPayload.ID, MissionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SurveyPayloads.Survey.ID, SurveyPayloads.Survey.CODEC);
		PayloadTypeRegistry.playS2C().register(HazardPayload.ID, HazardPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CinematicPayloads.Skip.ID, CinematicPayloads.Skip.CODEC);
		PayloadTypeRegistry.playC2S().register(CinematicPayloads.Advance.ID, CinematicPayloads.Advance.CODEC);
		PayloadTypeRegistry.playS2C().register(CinematicPayloads.Choice.ID, CinematicPayloads.Choice.CODEC);
		PayloadTypeRegistry.playS2C().register(DocumentPayload.ID, DocumentPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CinematicPayloads.Choice.Picked.ID, CinematicPayloads.Choice.Picked.CODEC);
		PayloadTypeRegistry.playS2C().register(CrawlerPayloads.State.ID, CrawlerPayloads.State.CODEC);
		PayloadTypeRegistry.playS2C().register(CrawlerPayloads.Scan.ID, CrawlerPayloads.Scan.CODEC);
		PayloadTypeRegistry.playS2C().register(CrawlerPayloads.Camera.ID, CrawlerPayloads.Camera.CODEC);
		PayloadTypeRegistry.playC2S().register(CrawlerPayloads.Control.ID, CrawlerPayloads.Control.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CrawlerPayloads.Control.ID, (payload, context) ->
				CrawlerInterior.control(context.player(), payload.throttle(), payload.steer(), payload.leave(), payload.lock()));

		ServerPlayNetworking.registerGlobalReceiver(PilotActionPayload.ID, (payload, context) -> {
			switch (payload.action()) {
				case PilotActionPayload.DISCONNECT -> PilotManager.disconnect(context.player(), false,
						Text.translatable("message.surrogate.disconnected"), false);
				case PilotActionPayload.SHUTDOWN -> PilotManager.disconnect(context.player(), true,
						Text.translatable("message.surrogate.disconnected_shutdown"), false);
				case PilotActionPayload.FABRICATOR -> PilotManager.openFabricator(context.player());
				default -> {
				}
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(CinematicPayloads.Skip.ID, (payload, context) -> Director.skipRequested(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(CinematicPayloads.Advance.ID, (payload, context) -> Director.advanceRequested(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(CinematicPayloads.Choice.Picked.ID,
				(payload, context) -> Director.choicePicked(context.player(), payload.index()));
	}

	private ModNetworking() {
	}
}
