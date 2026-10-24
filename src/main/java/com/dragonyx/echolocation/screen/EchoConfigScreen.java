package com.dragonyx.echolocation.screen;

import com.dragonyx.echolocation.config.EchoConfig;
import com.dragonyx.echolocation.echo.EchoEngine;
import com.mojang.serialization.Codec;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/** Экран настроек: прокручиваемый список ползунков и переключателей в стиле ванильных настроек. */
public final class EchoConfigScreen extends GameOptionsScreen {
	private static final String P = "option.echovision.";

	public EchoConfigScreen(Screen parent) {
		super(parent, MinecraftClient.getInstance().options, Text.translatable("screen.echovision.title"));
	}

	@Override
	protected void addOptions() {
		if (this.body == null) return;
		EchoConfig c = EchoConfig.get();

		this.body.addHeader(Text.translatable("screen.echovision.tab.main"));
		this.body.addAll(
			bool("echoEnabled", c.echoEnabled, v -> {
				c.echoEnabled = v;
				if (!v) EchoEngine.INSTANCE.reset();
			}),
			bool("blindness", c.blindness, v -> c.blindness = v),
			slider("visionRadius", 0, 64, () -> Math.round(c.visionRadius * 2), v -> c.visionRadius = v / 2f, v -> fmt(v / 2f)),
			slider("pulseRange", 4, 96, () -> Math.round(c.pulseRange), v -> c.pulseRange = v, Integer::toString),
			slider("pulseCooldown", 0, 100, () -> Math.round(c.pulseCooldown * 10), v -> c.pulseCooldown = v / 10f, v -> fmt(v / 10f)),
			bool("pulseSound", c.pulseSound, v -> c.pulseSound = v)
		);

		this.body.addHeader(Text.translatable("screen.echovision.tab.sound"));
		this.body.addAll(
			slider("soundSpeed", 2, 343, () -> Math.round(c.soundSpeed), v -> c.soundSpeed = v, Integer::toString),
			slider("baseRange", 4, 96, () -> Math.round(c.baseRange), v -> c.baseRange = v, Integer::toString),
			slider("rangeMultiplier", 1, 40, () -> Math.round(c.rangeMultiplier * 10), v -> c.rangeMultiplier = v / 10f, v -> fmt(v / 10f)),
			slider("maxRange", 8, 96, () -> Math.round(c.maxRange), v -> c.maxRange = v, Integer::toString),
			bool("wallPenetration", c.wallPenetration, v -> c.wallPenetration = v),
			slider("wallAbsorption", 2, 100, () -> Math.round(c.wallAbsorption * 2), v -> c.wallAbsorption = v / 2f, v -> fmt(v / 2f))
		);

		this.body.addHeader(Text.translatable("screen.echovision.tab.look"));
		this.body.addAll(
			cycle("style", EchoConfig.Style.values(), c.style, v -> c.style = v),
			bool("visionContours", c.visionContours, v -> c.visionContours = v),
			cycle("colorMode", EchoConfig.ColorMode.values(), c.colorMode, v -> c.colorMode = v),
			slider("hue", 0, 359, () -> c.hue, v -> c.hue = v, Integer::toString),
			slider("saturation", 0, 100, () -> Math.round(c.saturation * 100), v -> c.saturation = v / 100f, v -> v + "%"),
			slider("echoDuration", 3, 200, () -> Math.round(c.echoDuration * 10), v -> c.echoDuration = v / 10f, v -> fmt(v / 10f)),
			slider("brightness", 1, 60, () -> Math.round(c.brightness * 20), v -> c.brightness = v / 20f, v -> fmt(v / 20f)),
			slider("lineWidth", 1, 60, () -> Math.round(c.lineWidth * 200), v -> c.lineWidth = v / 200f, v -> fmt(v / 200f)),
			bool("wavefrontRing", c.wavefrontRing, v -> c.wavefrontRing = v),
			slider("ringWidth", 1, 40, () -> Math.round(c.ringWidth * 10), v -> c.ringWidth = v / 10f, v -> fmt(v / 10f)),
			bool("wavefrontFlash", c.wavefrontFlash, v -> c.wavefrontFlash = v),
			slider("fillOpacity", 0, 100, () -> Math.round(c.fillOpacity * 100), v -> c.fillOpacity = v / 100f, v -> v + "%"),
			bool("showEntities", c.showEntities, v -> c.showEntities = v),
			bool("showSources", c.showSources, v -> c.showSources = v),
			slider("maxBlocks", 1, 200, () -> c.maxBlocks / 1000, v -> c.maxBlocks = v * 1000, v -> (v * 1000) + "")
		);

		this.body.addHeader(Text.translatable("screen.echovision.tab.sources"));
		this.body.addAll(
			bool("movementEcho", c.movementEcho, v -> c.movementEcho = v),
			bool("catPlayers", c.catPlayers, v -> c.catPlayers = v),
			bool("catBlocks", c.catBlocks, v -> c.catBlocks = v),
			bool("catHostile", c.catHostile, v -> c.catHostile = v),
			bool("catNeutral", c.catNeutral, v -> c.catNeutral = v),
			bool("catAmbient", c.catAmbient, v -> c.catAmbient = v),
			bool("catWeather", c.catWeather, v -> c.catWeather = v),
			bool("catRecords", c.catRecords, v -> c.catRecords = v),
			bool("catVoice", c.catVoice, v -> c.catVoice = v)
		);

		this.body.addHeader(Text.translatable("screen.echovision.tab.voice"));
		this.body.addAll(
			bool("voiceEcho", c.voiceEcho, v -> c.voiceEcho = v),
			slider("voiceThreshold", -70, -5, () -> Math.round(c.voiceThreshold), v -> c.voiceThreshold = v, Integer::toString),
			slider("voiceInterval", 1, 30, () -> Math.round(c.voiceInterval * 10), v -> c.voiceInterval = v / 10f, v -> fmt(v / 10f))
		);
	}

	@Override
	protected void initFooter() {
		DirectionalLayoutWidget row = this.layout.addFooter(DirectionalLayoutWidget.horizontal().spacing(8));
		row.add(ButtonWidget.builder(Text.translatable("screen.echovision.reset"), b -> {
			EchoConfig.reset();
			EchoEngine.INSTANCE.reset();
			if (this.client != null) this.client.setScreen(new EchoConfigScreen(this.parent));
		}).width(150).build());
		row.add(ButtonWidget.builder(ScreenTexts.DONE, b -> this.close()).width(150).build());
	}

	@Override
	public void removed() {
		EchoConfig.save();
	}

	private static SimpleOption<Boolean> bool(String key, boolean value, Consumer<Boolean> set) {
		return SimpleOption.ofBoolean(P + key, SimpleOption.emptyTooltip(), value, v -> {
			set.accept(v);
			EchoConfig.save();
		});
	}

	private static SimpleOption<Integer> slider(String key, int min, int max, IntSupplier get, IntConsumer set, IntFunction<String> text) {
		int value = Math.max(min, Math.min(max, get.getAsInt()));
		return new SimpleOption<>(P + key, SimpleOption.emptyTooltip(),
			(optionText, v) -> Text.translatable(P + key, text.apply(v)),
			new SimpleOption.ValidatingIntSliderCallbacks(min, max, true),
			value, v -> set.accept(v));
	}

	private static <E extends Enum<E>> SimpleOption<E> cycle(String key, E[] values, E value, Consumer<E> set) {
		Codec<E> codec = Codec.INT.xmap(i -> values[Math.max(0, Math.min(values.length - 1, i))], Enum::ordinal);
		return new SimpleOption<>(P + key, SimpleOption.emptyTooltip(),
			(optionText, v) -> Text.empty().append(optionText).append(": ")
				.append(Text.translatable(P + key + "." + v.name().toLowerCase(Locale.ROOT))),
			new SimpleOption.PotentialValuesBasedCallbacks<>(Arrays.asList(values), codec),
			value, v -> {
				set.accept(v);
				EchoConfig.save();
			});
	}

	private static String fmt(float v) {
		return v == Math.floor(v) ? Integer.toString((int) v) : String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "");
	}
}
