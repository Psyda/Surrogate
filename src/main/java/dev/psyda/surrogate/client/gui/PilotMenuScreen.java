package dev.psyda.surrogate.client.gui;

import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.entity.RobotModule;
import dev.psyda.surrogate.network.PilotActionPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** Opened with the pilot menu key while linked: the fabricator, or leave the chassis running, or shut it down. */
@Environment(EnvType.CLIENT)
public class PilotMenuScreen extends Screen {
	public PilotMenuScreen() {
		super(Text.translatable("screen.surrogate.pilot_menu"));
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int cy = this.height / 2;
		boolean fabricator = this.client != null && this.client.player != null
				&& this.client.player.getVehicle() instanceof RobotEntity robot && robot.hasFabricator();
		ButtonWidget bench = addDrawableChild(ButtonWidget.builder(
				Text.translatable(fabricator ? "screen.surrogate.pilot_menu.fabricator" : "screen.surrogate.pilot_menu.no_fabricator"),
				button -> act(PilotActionPayload.FABRICATOR)).dimensions(cx - 100, cy - 36, 200, 20).build());
		bench.active = fabricator;
		addDrawableChild(ButtonWidget.builder(Text.translatable("screen.surrogate.pilot_menu.disconnect"),
				button -> act(PilotActionPayload.DISCONNECT)).dimensions(cx - 100, cy - 6, 200, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("screen.surrogate.pilot_menu.shutdown"),
				button -> act(PilotActionPayload.SHUTDOWN)).dimensions(cx - 100, cy + 18, 200, 20).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> close()).dimensions(cx - 100, cy + 48, 200, 20).build());
	}

	private void act(int action) {
		ClientPlayNetworking.send(new PilotActionPayload(action));
		close();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 72, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("screen.surrogate.pilot_menu.hint").formatted(Formatting.GRAY),
				this.width / 2, this.height / 2 - 56, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(this.textRenderer, modules().formatted(Formatting.DARK_AQUA),
				this.width / 2, this.height / 2 - 44, 0xFFFFFFFF);
	}

	/** What the chassis is carrying, listed as one line: the pilot's own inventory of countermeasures. */
	private MutableText modules() {
		if (this.client == null || this.client.player == null
				|| !(this.client.player.getVehicle() instanceof RobotEntity robot)) {
			return Text.translatable("screen.surrogate.pilot_menu.no_modules");
		}
		List<RobotModule> fitted = robot.getModules();
		if (fitted.isEmpty()) return Text.translatable("screen.surrogate.pilot_menu.no_modules");
		MutableText list = Text.empty();
		for (int i = 0; i < fitted.size(); i++) {
			if (i > 0) list.append(Text.literal(", "));
			list.append(Text.translatable(fitted.get(i).translationKey()));
		}
		return Text.translatable("screen.surrogate.pilot_menu.modules", list);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
