package dev.psyda.surrogate.client;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.client.cinematic.CinematicOverlay;
import dev.psyda.surrogate.client.cinematic.CinematicState;
import dev.psyda.surrogate.client.crawler.CrawlerClientState;
import dev.psyda.surrogate.client.crawler.PortholeRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import dev.psyda.surrogate.client.gui.CrawlerHud;
import dev.psyda.surrogate.network.CrawlerPayloads;
import dev.psyda.surrogate.network.HazardPayload;
import dev.psyda.surrogate.client.gui.HazardOverlay;
import dev.psyda.surrogate.client.gui.PilotHud;
import dev.psyda.surrogate.client.render.CrawlerEntityModel;
import dev.psyda.surrogate.client.render.CrawlerEntityRenderer;
import dev.psyda.surrogate.client.gui.LogScreen;
import dev.psyda.surrogate.client.gui.PilotMenuScreen;
import dev.psyda.surrogate.client.gui.TerminalScreen;
import dev.psyda.surrogate.client.render.ChargingDockRenderer;
import dev.psyda.surrogate.client.render.CrewEntityRenderer;
import dev.psyda.surrogate.client.render.DiveChairRenderer;
import dev.psyda.surrogate.client.render.RobotEntityModel;
import dev.psyda.surrogate.client.render.RobotEntityRenderer;
import dev.psyda.surrogate.client.render.RobotWreckRenderer;
import dev.psyda.surrogate.client.render.SurvivorEntityRenderer;
import dev.psyda.surrogate.client.transit.TransitClientState;
import dev.psyda.surrogate.client.transit.TransitDimensionEffects;
import dev.psyda.surrogate.client.transit.TransitHud;
import dev.psyda.surrogate.client.transit.TransitSkyRenderer;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.network.CinematicPayloads;
import dev.psyda.surrogate.network.PilotStatusPayload;
import dev.psyda.surrogate.network.TerminalPayload;
import dev.psyda.surrogate.network.TransitPayload;
import dev.psyda.surrogate.registry.ModBlockEntities;
import dev.psyda.surrogate.registry.ModBlocks;
import dev.psyda.surrogate.registry.ModEntities;
import dev.psyda.surrogate.transit.TransitDimension;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class SurrogateClient implements ClientModInitializer {
	public static final KeyBinding PILOT_MENU = new KeyBinding("key.surrogate.pilot_menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, "category.surrogate");
	/** The mission log: the current objective, the hint for it, and what everyone has said so far. */
	public static final KeyBinding LOG = new KeyBinding("key.surrogate.log", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.surrogate");

	// Dev only: -Dsurrogate.devScreenshots=<ticks> saves a screenshot that often once in a world, and
	// -Dsurrogate.devQuitAfter=<ticks> closes the game after that long, so a scene can be looked at from files.
	private static final int DEV_SHOT_INTERVAL = Integer.getInteger("surrogate.devScreenshots", 0);
	private static final int DEV_QUIT_AFTER = Integer.getInteger("surrogate.devQuitAfter", 0);
	/** -Dsurrogate.devLook=yaw,pitch keeps the player looking that way whenever a scene is not holding the camera. */
	private static final String DEV_LOOK = System.getProperty("surrogate.devLook", "");
	private static int devTicks;

	@Override
	public void onInitializeClient() {
		EntityModelLayerRegistry.registerModelLayer(RobotEntityModel.LAYER, RobotEntityModel::getTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(CrawlerEntityModel.LAYER, CrawlerEntityModel::getTexturedModelData);
		EntityRendererRegistry.register(ModEntities.ROBOT, RobotEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.CRAWLER, CrawlerEntityRenderer::new);
		EntityModelLayerRegistry.registerModelLayer(dev.psyda.surrogate.client.render.RocketEntityModel.LAYER,
				dev.psyda.surrogate.client.render.RocketEntityModel::getTexturedModelData);
		EntityRendererRegistry.register(ModEntities.ROCKET, dev.psyda.surrogate.client.render.RocketEntityRenderer::new);
		EntityModelLayerRegistry.registerModelLayer(dev.psyda.surrogate.client.render.CompanyShipModel.LAYER,
				dev.psyda.surrogate.client.render.CompanyShipModel::getTexturedModelData);
		EntityRendererRegistry.register(ModEntities.COMPANY_SHIP, dev.psyda.surrogate.client.render.CompanyShipRenderer::new);
		EntityModelLayerRegistry.registerModelLayer(dev.psyda.surrogate.client.render.BorerEntityModel.LAYER,
				dev.psyda.surrogate.client.render.BorerEntityModel::getTexturedModelData);
		EntityRendererRegistry.register(ModEntities.BORER, dev.psyda.surrogate.client.render.BorerEntityRenderer::new);
		// The four animals. Registered together because they are the same three lines four times over.
		EntityModelLayerRegistry.registerModelLayer(dev.psyda.surrogate.client.render.fauna.TrundleModel.LAYER,
				dev.psyda.surrogate.client.render.fauna.TrundleModel::getTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(dev.psyda.surrogate.client.render.fauna.SlagbackModel.LAYER,
				dev.psyda.surrogate.client.render.fauna.SlagbackModel::getTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(dev.psyda.surrogate.client.render.fauna.TockerModel.LAYER,
				dev.psyda.surrogate.client.render.fauna.TockerModel::getTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(dev.psyda.surrogate.client.render.fauna.LanternSlugModel.LAYER,
				dev.psyda.surrogate.client.render.fauna.LanternSlugModel::getTexturedModelData);
		EntityRendererRegistry.register(ModEntities.TRUNDLE, dev.psyda.surrogate.client.render.fauna.FaunaRenderers.Trundle::new);
		EntityRendererRegistry.register(ModEntities.SLAGBACK, dev.psyda.surrogate.client.render.fauna.FaunaRenderers.Slagback::new);
		EntityRendererRegistry.register(ModEntities.TOCKER, dev.psyda.surrogate.client.render.fauna.FaunaRenderers.Tocker::new);
		EntityRendererRegistry.register(ModEntities.LANTERN_SLUG, dev.psyda.surrogate.client.render.fauna.FaunaRenderers.LanternSlug::new);
		EntityRendererRegistry.register(ModEntities.ROBOT_WRECK, RobotWreckRenderer::new);
		EntityRendererRegistry.register(ModEntities.SURVIVOR, SurvivorEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.CREW, CrewEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.CAMERA, EmptyEntityRenderer::new);
		BlockEntityRendererFactories.register(ModBlockEntities.DIVE_CHAIR, DiveChairRenderer::new);
		BlockEntityRendererFactories.register(ModBlockEntities.CHARGING_DOCK, ChargingDockRenderer::new);
		BlockEntityRendererFactories.register(ModBlockEntities.VEHICLE_FABRICATOR, dev.psyda.surrogate.client.render.VehicleFabricatorRenderer::new);
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.DECK_GRATING, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.HYDROPONIC_TRAY, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.SURVEY_MARKER, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ANTENNA_MAST, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.DIVE_CHAIR, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CHARGING_DOCK, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.REINFORCED_GLASS, RenderLayer.getTranslucent());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.AIRLOCK_DOOR, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.SOLAR_COLLECTOR, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.MICROWAVE, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.BREACHED_PLATING, RenderLayer.getCutout());

		// The void the ship crosses: its own sky, and nothing vanilla would draw there.
		DimensionRenderingRegistry.registerDimensionEffects(Surrogate.id("transit"), new TransitDimensionEffects());
		DimensionRenderingRegistry.registerSkyRenderer(TransitDimension.WORLD, new TransitSkyRenderer());

		ClientPlayNetworking.registerGlobalReceiver(PilotStatusPayload.ID, (payload, context) -> ClientPilotState.update(payload));
		ClientPlayNetworking.registerGlobalReceiver(TransitPayload.ID, (payload, context) -> TransitClientState.onPayload(payload));
		// The survey picture arrives whole and opens the screen with it: there is no client-side state to keep,
		// because the picture is only ever as fresh as the moment somebody leaned on the table.
		ClientPlayNetworking.registerGlobalReceiver(dev.psyda.surrogate.network.SurveyPayloads.Survey.ID,
				(payload, context) -> context.client().setScreen(new dev.psyda.surrogate.client.gui.SurveyScreen(payload)));
		ClientPlayNetworking.registerGlobalReceiver(CinematicPayloads.State.ID, (payload, context) -> CinematicState.onState(payload));
		ClientPlayNetworking.registerGlobalReceiver(CinematicPayloads.Camera.ID, (payload, context) -> CinematicState.onCamera(payload));
		ClientPlayNetworking.registerGlobalReceiver(CinematicPayloads.Line.ID, (payload, context) -> CinematicState.onLine(payload));
		ClientPlayNetworking.registerGlobalReceiver(CinematicPayloads.Fade.ID, (payload, context) -> CinematicState.onFade(payload));
		ClientPlayNetworking.registerGlobalReceiver(CinematicPayloads.Objective.ID, (payload, context) -> CinematicState.onObjective(payload));
		ClientPlayNetworking.registerGlobalReceiver(CinematicPayloads.Effect.ID, (payload, context) -> CinematicState.onEffect(payload));
		ClientPlayNetworking.registerGlobalReceiver(CinematicPayloads.Hint.ID, (payload, context) -> CinematicState.onHint(payload));
		ClientPlayNetworking.registerGlobalReceiver(TerminalPayload.ID, (payload, context) -> context.client().setScreen(new TerminalScreen(payload.unit(), payload.survey())));
		ClientPlayNetworking.registerGlobalReceiver(CrawlerPayloads.State.ID, (payload, context) -> CrawlerClientState.onState(payload));
		ClientPlayNetworking.registerGlobalReceiver(CrawlerPayloads.Scan.ID, (payload, context) -> CrawlerClientState.onScan(payload));
		ClientPlayNetworking.registerGlobalReceiver(CrawlerPayloads.Camera.ID, (payload, context) -> CrawlerClientState.onCamera(payload));
		ClientPlayNetworking.registerGlobalReceiver(HazardPayload.ID, (payload, context) -> HazardClientState.onPayload(payload));
		// The crawler's cameras: rendered at the start of the frame, painted on the porthole after the glass.
		WorldRenderEvents.START.register(PortholeRenderer::update);
		WorldRenderEvents.AFTER_TRANSLUCENT.register(PortholeRenderer::drawPorthole);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			CrawlerClientState.reset();
			ClientPilotState.reset();
			CinematicState.reset();
			TransitClientState.reset();
			HazardClientState.reset();
		});
		// The ship readout sits over the pilot HUD; the cinematic layer draws over both.
		HudRenderCallback.EVENT.register(PilotHud::render);
		HudRenderCallback.EVENT.register(CrawlerHud::render);
		HudRenderCallback.EVENT.register(TransitHud::render);
		// The weather paints over the readouts and under the cutscene bars.
		HudRenderCallback.EVENT.register(HazardOverlay::render);
		HudRenderCallback.EVENT.register(CinematicOverlay::render);

		KeyBindingHelper.registerKeyBinding(PILOT_MENU);
		KeyBindingHelper.registerKeyBinding(LOG);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (PILOT_MENU.wasPressed()) {
				if (client.player != null && client.currentScreen == null && !CinematicState.isInputLocked() && RobotEntity.isPiloting(client.player)) {
					client.setScreen(new PilotMenuScreen());
				}
			}
			while (LOG.wasPressed()) {
				// Readable mid-cutscene too: that is when a line goes by too fast.
				if (client.player != null && client.currentScreen == null) client.setScreen(new LogScreen());
			}
			ClientPilotState.tick(client);
			CrawlerClientState.tick(client);
			CinematicState.tick(client);
			TransitClientState.tick(client);
			HazardClientState.tick(client);
			devTick(client);
		});
	}

	private static void devTick(net.minecraft.client.MinecraftClient client) {
		if (DEV_SHOT_INTERVAL <= 0 && DEV_QUIT_AFTER <= 0) return;
		boolean inWorld = client.world != null && client.player != null;
		// Without a world the title screen counts too, so the menus can be looked at from files as well.
		if (!inWorld && !(client.currentScreen instanceof net.minecraft.client.gui.screen.TitleScreen)
				&& !(client.currentScreen instanceof net.minecraft.client.gui.screen.world.CreateWorldScreen)) return;
		devTicks++;
		// -Dsurrogate.devCreateWorld=true opens the new world screen from the title after a moment.
		if (!inWorld && Boolean.getBoolean("surrogate.devCreateWorld") && devTicks == 60 && client.currentScreen instanceof net.minecraft.client.gui.screen.TitleScreen) {
			net.minecraft.client.gui.screen.world.CreateWorldScreen.create(client, client.currentScreen);
		}
		// -Dsurrogate.devScreen=log or terminal:<unit> opens that screen for five seconds, ten seconds in.
		String devScreen = System.getProperty("surrogate.devScreen", "");
		if (inWorld && !devScreen.isEmpty()) {
			if (devTicks == 200 && client.currentScreen == null) {
				if (devScreen.equals("log")) client.setScreen(new LogScreen());
				else if (devScreen.startsWith("terminal:")) client.setScreen(new TerminalScreen(devScreen.substring("terminal:".length())));
			}
			if (devTicks == 300 && (client.currentScreen instanceof LogScreen || client.currentScreen instanceof TerminalScreen)) client.setScreen(null);
		}
		// -Dsurrogate.devRun=<command>@<ticks> sends that chat command once, that far into the world, so a
		// cinematic can be triggered and watched from screenshots without a server-side harness.
		String devRun = System.getProperty("surrogate.devRun", "");
		if (inWorld && !devRun.isEmpty() && client.player != null) {
			int at = devRun.contains("@") ? Integer.parseInt(devRun.substring(devRun.lastIndexOf('@') + 1).trim()) : 200;
			String command = devRun.contains("@") ? devRun.substring(0, devRun.lastIndexOf('@')) : devRun;
			if (devTicks == at) {
				// Run it on the integrated server at operator level: the mod's commands need level 2 and a
				// singleplayer player does not have it, so sending it as chat is silently refused.
				net.minecraft.server.MinecraftServer server = client.getServer();
				if (server != null) {
					String toRun = command;
					server.execute(() -> server.getCommandManager().executeWithPrefix(server.getCommandSource().withLevel(4), toRun));
				} else {
					client.player.networkHandler.sendCommand(command);
				}
			}
		}
		// -Dsurrogate.devTp=x,y,z,afterTicks puts the player there every five seconds from that tick on, scenes or not.
		String devTp = System.getProperty("surrogate.devTp", "");
		if (inWorld && !devTp.isEmpty() && devTicks % 100 == 0 && client.getServer() != null) {
			String[] parts = devTp.split(",");
			if (parts.length == 4 && devTicks >= Integer.parseInt(parts[3].trim())) {
				double tx = Double.parseDouble(parts[0].trim());
				double ty = Double.parseDouble(parts[1].trim());
				double tz = Double.parseDouble(parts[2].trim());
				java.util.UUID id = client.player.getUuid();
				net.minecraft.server.MinecraftServer server = client.getServer();
				server.execute(() -> {
					net.minecraft.server.network.ServerPlayerEntity sp = server.getPlayerManager().getPlayer(id);
					if (sp != null) sp.teleport(sp.getServerWorld(), tx, ty, tz, sp.getYaw(), sp.getPitch());
				});
			}
		}
		if (inWorld && !DEV_LOOK.isEmpty() && !CinematicState.isCameraDetached()) {
			String[] parts = DEV_LOOK.split(",");
			if (parts.length == 2) {
				try {
					client.player.setYaw(Float.parseFloat(parts[0].trim()));
					client.player.setPitch(Float.parseFloat(parts[1].trim()));
				} catch (NumberFormatException ignored) {
				}
			}
		}
		if (DEV_SHOT_INTERVAL > 0 && devTicks % DEV_SHOT_INTERVAL == 0) {
			ScreenshotRecorder.saveScreenshot(client.runDirectory, client.getFramebuffer(), text -> {
			});
		}
		if (DEV_QUIT_AFTER > 0 && devTicks >= DEV_QUIT_AFTER) client.scheduleStop();
	}
}
