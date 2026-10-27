package com.dragonyx.echolocation.screen;

import com.dragonyx.echolocation.config.EchoConfig;
import com.dragonyx.echolocation.config.EchoLook;
import com.dragonyx.echolocation.config.EchoPreset;
import com.dragonyx.echolocation.echo.EchoEngine;
import com.dragonyx.echolocation.screen.EchoWidgets.Choice;
import com.dragonyx.echolocation.screen.EchoWidgets.ColorRow;
import com.dragonyx.echolocation.screen.EchoWidgets.FlatButton;
import com.dragonyx.echolocation.screen.EchoWidgets.Header;
import com.dragonyx.echolocation.screen.EchoWidgets.Icon;
import com.dragonyx.echolocation.screen.EchoWidgets.IconButton;
import com.dragonyx.echolocation.screen.EchoWidgets.PresetCard;
import com.dragonyx.echolocation.screen.EchoWidgets.Slider;
import com.dragonyx.echolocation.screen.EchoWidgets.Tab;
import com.dragonyx.echolocation.screen.EchoWidgets.Toggle;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.ScrollableLayoutWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Настройки: панель слева, а справа виден мир — изменения вида сразу на экране, и раз в пару секунд
 * беззвучный щелчок (см. {@link EchoPreview}). Игра на паузу не встаёт.
 */
public final class EchoConfigScreen extends Screen {
	private static final String P = "option.echovision.";
	private static final String S = "screen.echovision.";

	private enum Page { LOOK, SOUND, SOURCES, MAIN }

	/** Последняя открытая вкладка — при следующем открытии настроек та же. */
	private static Page page = Page.LOOK;

	private final Screen parent;
	private int panelW, rowW;
	private TextFieldWidget presetName;
	private boolean confirmReset;
	/** Режим удаления пресетов (корзина над пресетами). */
	private boolean deleting;
	/** Вид прямо сейчас — по нему подсвечивается совпадающий пресет. */
	private EchoLook currentLook = EchoLook.of(EchoConfig.get());

	public EchoConfigScreen(Screen parent) {
		super(Text.translatable(S + "title"));
		this.parent = parent;
	}

	// ---------- раскладка ----------

	@Override
	protected void init() {
		panelW = Math.min(this.width, Math.max(230, Math.min(300, this.width * 2 / 5)));
		rowW = panelW - 28;
		confirmReset = false;

		// Шапка: щелчок, автощелчок, закрыть.
		IconButton close = addDrawableChild(new IconButton(18, Icon.CLOSE, Text.translatable(S + "close"), this::close));
		close.setPosition(panelW - 24, 5);
		IconButton auto = addDrawableChild(new IconButton(18, Icon.EYE, Text.translatable(S + "preview"), () -> {
			EchoConfig.get().previewPulses = !EchoConfig.get().previewPulses;
			EchoConfig.save();
		}, () -> EchoConfig.get().previewPulses));
		auto.setPosition(panelW - 46, 5);
		IconButton pulse = addDrawableChild(new IconButton(18, Icon.PULSE, Text.translatable(S + "pulse"), EchoPreview::pulseNow));
		pulse.setPosition(panelW - 68, 5);

		// Вкладки — ширина по длине названия.
		Page[] pages = Page.values();
		Text[] names = new Text[pages.length];
		int textW = 0;
		for (int i = 0; i < pages.length; i++) {
			names[i] = Text.translatable(S + "tab." + pages[i].name().toLowerCase(Locale.ROOT));
			textW += this.textRenderer.getWidth(names[i]);
		}
		int free = panelW - 20 - textW;
		int x = 10;
		for (int i = 0; i < pages.length; i++) {
			int w = this.textRenderer.getWidth(names[i]) + free / pages.length;
			if (i == pages.length - 1) w = panelW - 10 - x;
			Page target = pages[i];
			Tab tab = addDrawableChild(new Tab(w, names[i], page == target, () -> {
				page = target;
				clearAndInit();
			}));
			tab.setPosition(x, 27);
			x += w;
		}

		DirectionalLayoutWidget col = DirectionalLayoutWidget.vertical().spacing(3);
		switch (page) {
			case LOOK -> look(col);
			case SOUND -> sound(col);
			case SOURCES -> sources(col);
			case MAIN -> main(col);
		}
		int top = 50;
		ScrollableLayoutWidget scroll = new ScrollableLayoutWidget(this.client, col, this.height - top - 6);
		scroll.setWidth(panelW);
		scroll.setHeight(this.height - top - 6);
		scroll.refreshPositions();
		scroll.setX(0);
		scroll.setY(top);
		scroll.forEachChild(this::addDrawableChild);
	}

	private void look(DirectionalLayoutWidget col) {
		EchoConfig c = EchoConfig.get();
		// Заголовок с корзиной: она включает режим удаления — нажатый пресет удаляется.
		DirectionalLayoutWidget head = col.add(DirectionalLayoutWidget.horizontal().spacing(3));
		head.add(new Header(rowW - 21, Text.translatable(deleting ? S + "preset.delete_hint" : S + "presets")));
		head.add(new IconButton(18, Icon.TRASH, Text.translatable(S + "preset.delete"), () -> {
			deleting = !deleting;
			clearAndInit();
		}, () -> deleting));

		int half = (rowW - 3) / 2;
		DirectionalLayoutWidget row = null;
		int n = 0;
		for (EchoPreset p : EchoPreset.BUILT_IN) {
			if (c.hiddenPresets.contains(p.name)) continue;
			if (n++ % 2 == 0) row = col.add(DirectionalLayoutWidget.horizontal().spacing(3));
			row.add(presetCard(half, Text.translatable("preset.echovision." + p.name), p, () -> c.hiddenPresets.add(p.name)));
		}
		for (EchoPreset p : c.userPresets) {
			if (n++ % 2 == 0) row = col.add(DirectionalLayoutWidget.horizontal().spacing(3));
			row.add(presetCard(half, Text.literal(p.name), p, () -> c.userPresets.remove(p)));
		}
		if (!c.hiddenPresets.isEmpty()) {
			col.add(new FlatButton(rowW, () -> Text.translatable(S + "preset.restore"), EchoWidgets.MUTED, () -> {
				c.hiddenPresets.clear();
				EchoConfig.save();
				clearAndInit();
			}));
		}
		DirectionalLayoutWidget save = col.add(DirectionalLayoutWidget.horizontal().spacing(3));
		presetName = save.add(new TextFieldWidget(this.textRenderer, rowW - 23, 20, Text.translatable(S + "preset.name")));
		presetName.setMaxLength(32);
		presetName.setPlaceholder(Text.translatable(S + "preset.name"));
		save.add(new IconButton(20, Icon.SAVE, Text.translatable(S + "preset.save"), this::savePreset));

		col.add(new Header(rowW, Text.translatable(S + "colors")));
		col.add(new Choice<>(rowW, Text.translatable(P + "colorMode"), EchoConfig.ColorMode.values(), () -> c.colorMode, v -> c.colorMode = v,
			v -> Text.translatable(P + "colorMode." + v.name().toLowerCase(Locale.ROOT)), this::lookChanged));
		col.add(new ColorRow(rowW, Text.translatable(S + "color.echo"), () -> c.echoColor, () -> openColor(true)));
		col.add(new ColorRow(rowW, Text.translatable(S + "color.line"), () -> c.lineColor, () -> openColor(false)));

		col.add(new Header(rowW, Text.translatable(S + "surface")));
		col.add(percent("textureAmount", 100, () -> c.textureAmount, v -> c.textureAmount = v));
		col.add(percent("fillAmount", 100, () -> c.fillAmount, v -> c.fillAmount = v));
		col.add(percent("mottle", 100, () -> c.mottle, v -> c.mottle = v));
		col.add(percent("detailLines", 200, () -> c.detailLines, v -> c.detailLines = v));
		col.add(slider("brightness", 1, 60, () -> Math.round(c.brightness * 20), v -> c.brightness = v / 20f, v -> fmt(v / 20f)));
		col.add(percent("distanceFade", 100, () -> c.distanceFade, v -> c.distanceFade = v));

		col.add(new Header(rowW, Text.translatable(S + "wave")));
		col.add(slider("echoDuration", 3, 200, () -> Math.round(c.echoDuration * 10), v -> c.echoDuration = v / 10f, v -> unit("seconds", fmt(v / 10f))));
		col.add(toggle("wavefrontRing", () -> c.wavefrontRing, v -> c.wavefrontRing = v));
		col.add(slider("ringWidth", 1, 40, () -> Math.round(c.ringWidth * 10), v -> c.ringWidth = v / 10f, v -> unit("blocks", fmt(v / 10f))));
		col.add(toggle("wavefrontFlash", () -> c.wavefrontFlash, v -> c.wavefrontFlash = v));
		col.add(percent("waveNoise", 100, () -> c.waveNoise, v -> c.waveNoise = v));

		col.add(new Header(rowW, Text.translatable(S + "show")));
		col.add(toggle("showEntities", () -> c.showEntities, v -> c.showEntities = v));
		col.add(toggle("showSources", () -> c.showSources, v -> c.showSources = v));
	}

	private void sound(DirectionalLayoutWidget col) {
		EchoConfig c = EchoConfig.get();
		col.add(new Header(rowW, Text.translatable(S + "propagation")));
		col.add(slider("soundSpeed", 2, 343, () -> Math.round(c.soundSpeed), v -> c.soundSpeed = v, v -> unit("speed", v + "")));
		col.add(slider("baseRange", 4, 96, () -> Math.round(c.baseRange), v -> c.baseRange = v, v -> unit("blocks", v + "")));
		col.add(slider("rangeMultiplier", 1, 40, () -> Math.round(c.rangeMultiplier * 10), v -> c.rangeMultiplier = v / 10f, v -> "×" + fmt(v / 10f)));
		col.add(slider("maxRange", 8, 96, () -> Math.round(c.maxRange), v -> c.maxRange = v, v -> unit("blocks", v + "")));
		col.add(toggle("wallPenetration", () -> c.wallPenetration, v -> c.wallPenetration = v));
		col.add(slider("wallAbsorption", 2, 100, () -> Math.round(c.wallAbsorption * 2), v -> c.wallAbsorption = v / 2f, v -> fmt(v / 2f)));
		col.add(toggle("materialEcho", () -> c.materialEcho, v -> c.materialEcho = v));

		col.add(new Header(rowW, Text.translatable(S + "tab.kinds")));
		col.add(percent("scaleSteps", 200, () -> c.scaleSteps, v -> c.scaleSteps = v));
		col.add(percent("scaleMining", 200, () -> c.scaleMining, v -> c.scaleMining = v));
		col.add(percent("scalePlacing", 200, () -> c.scalePlacing, v -> c.scalePlacing = v));
		col.add(percent("scaleVoice", 200, () -> c.scaleVoice, v -> c.scaleVoice = v));
		col.add(percent("scaleOther", 200, () -> c.scaleOther, v -> c.scaleOther = v));

		col.add(new Header(rowW, Text.translatable(S + "limits")));
		col.add(slider("maxBlocks", 1, 200, () -> c.maxBlocks / 1000, v -> c.maxBlocks = v * 1000, v -> (v * 1000) + ""));
	}

	private void sources(DirectionalLayoutWidget col) {
		EchoConfig c = EchoConfig.get();
		col.add(new Header(rowW, Text.translatable(S + "tab.sources")));
		col.add(toggle("movementEcho", () -> c.movementEcho, v -> c.movementEcho = v));
		col.add(toggle("catPlayers", () -> c.catPlayers, v -> c.catPlayers = v));
		col.add(toggle("catBlocks", () -> c.catBlocks, v -> c.catBlocks = v));
		col.add(toggle("catHostile", () -> c.catHostile, v -> c.catHostile = v));
		col.add(toggle("catNeutral", () -> c.catNeutral, v -> c.catNeutral = v));
		col.add(toggle("catAmbient", () -> c.catAmbient, v -> c.catAmbient = v));
		col.add(toggle("catWeather", () -> c.catWeather, v -> c.catWeather = v));
		col.add(toggle("catRecords", () -> c.catRecords, v -> c.catRecords = v));
		col.add(toggle("catVoice", () -> c.catVoice, v -> c.catVoice = v));

		col.add(new Header(rowW, Text.translatable(S + "tab.voice")));
		col.add(toggle("voiceEcho", () -> c.voiceEcho, v -> c.voiceEcho = v));
		col.add(slider("voiceThreshold", -70, -5, () -> Math.round(c.voiceThreshold), v -> c.voiceThreshold = v, v -> unit("db", v + "")));
		col.add(slider("voiceInterval", 1, 30, () -> Math.round(c.voiceInterval * 10), v -> c.voiceInterval = v / 10f, v -> unit("seconds", fmt(v / 10f))));
	}

	private void main(DirectionalLayoutWidget col) {
		EchoConfig c = EchoConfig.get();
		col.add(new Header(rowW, Text.translatable(S + "tab.main")));
		col.add(toggle("echoEnabled", () -> c.echoEnabled, v -> {
			c.echoEnabled = v;
			if (!v) EchoEngine.INSTANCE.reset();
		}));
		col.add(toggle("blindness", () -> c.blindness, v -> c.blindness = v));
		col.add(slider("visionRadius", 0, 64, () -> Math.round(c.visionRadius * 2), v -> c.visionRadius = v / 2f, v -> unit("blocks", fmt(v / 2f))));

		col.add(new Header(rowW, Text.translatable(S + "click")));
		col.add(slider("pulseRange", 4, 96, () -> Math.round(c.pulseRange), v -> c.pulseRange = v, v -> unit("blocks", v + "")));
		col.add(slider("pulseCooldown", 0, 100, () -> Math.round(c.pulseCooldown * 10), v -> c.pulseCooldown = v / 10f, v -> unit("seconds", fmt(v / 10f))));
		col.add(toggle("pulseSound", () -> c.pulseSound, v -> c.pulseSound = v));

		col.add(new Header(rowW, Text.translatable(S + "other")));
		// Сброс — в два нажатия: первое только спрашивает.
		col.add(new FlatButton(rowW, () -> Text.translatable(confirmReset ? S + "reset.confirm" : S + "reset"), 0xFFFF8A80, () -> {
			if (!confirmReset) {
				confirmReset = true;
				return;
			}
			EchoConfig.reset();
			EchoEngine.INSTANCE.reset();
			clearAndInit();
		}));
	}

	// ---------- элементы ----------

	private Slider slider(String key, int min, int max, IntSupplier get, Consumer<Integer> set, IntFunction<String> text) {
		return new Slider(rowW, Text.translatable(P + key), min, max, get, set, text, this::lookChanged);
	}

	/** Доля 0–max %, шаг 5 %. */
	private Slider percent(String key, int maxPercent, Supplier<Float> get, Consumer<Float> set) {
		return slider(key, 0, maxPercent / 5, () -> Math.round(get.get() * 20), v -> set.accept(v / 20f), v -> (v * 5) + "%");
	}

	private Toggle toggle(String key, Supplier<Boolean> get, Consumer<Boolean> set) {
		return new Toggle(rowW, Text.translatable(P + key), get, set, this::lookChanged);
	}

	/** remove — как убрать пресет корзиной: свой удаляется, встроенный скрывается. */
	private PresetCard presetCard(int w, Text name, EchoPreset p, Runnable remove) {
		return new PresetCard(w, name, p.look.echoColor, p.look.lineColor, () -> p.look.equals(currentLook), () -> {
			p.look.applyTo(EchoConfig.get());
			EchoConfig.get().preset = p.name;
			EchoConfig.save();
			EchoPreview.changed();
			clearAndInit();
		}, () -> deleting, () -> {
			remove.run();
			EchoConfig.save();
			clearAndInit();
		});
	}

	private void savePreset() {
		EchoConfig c = EchoConfig.get();
		String name = presetName == null ? "" : presetName.getText().trim();
		if (name.isEmpty()) name = Text.translatable(S + "preset.default_name", c.userPresets.size() + 1).getString();
		EchoLook look = EchoLook.of(c);
		EchoPreset existing = null;
		for (EchoPreset p : c.userPresets) if (p.name.equalsIgnoreCase(name)) existing = p;
		if (existing != null) {
			existing.look = look;
		} else {
			c.userPresets.add(new EchoPreset(name, look));
		}
		c.preset = name;
		EchoConfig.save();
		clearAndInit();
	}

	private void openColor(boolean surface) {
		EchoConfig c = EchoConfig.get();
		if (this.client == null) return;
		this.client.setScreen(surface
			? new EchoColorScreen(this, Text.translatable(S + "color.echo"), () -> c.echoColor, v -> c.echoColor = v, 0xFFFFFF, true)
			: new EchoColorScreen(this, Text.translatable(S + "color.line"), () -> c.lineColor, v -> c.lineColor = v, 0xFFFFFF, false));
	}

	private void lookChanged() {
		EchoPreview.changed();
	}

	// ---------- кадр ----------

	@Override
	public void tick() {
		EchoPreview.tick();
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		// В мире фон не размываем и не затемняем — справа от панели видно эхо.
		if (this.client == null || this.client.world == null) super.renderBackground(ctx, mouseX, mouseY, delta);
		ctx.fill(0, 0, panelW, this.height, 0xD80E1216);
		ctx.fill(panelW, 0, panelW + 1, this.height, 0x665AD2FF);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		currentLook = EchoLook.of(EchoConfig.get());
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawText(this.textRenderer, EchoWidgets.fit(this.title, panelW - 82), 10, 10, 0xFFFFFFFF, true);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void removed() {
		EchoConfig.save();
	}

	@Override
	public void close() {
		if (this.client != null) this.client.setScreen(parent);
	}

	private static String unit(String key, String value) {
		return Text.translatable(S + "unit." + key, value).getString();
	}

	static String fmt(float v) {
		return v == Math.floor(v) ? Integer.toString((int) v) : String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "");
	}

	/** Для тестов: какую вкладку открыть (look, sound, sources, main). */
	public static void openPage(String name) {
		page = Page.valueOf(name.toUpperCase(Locale.ROOT));
	}
}
