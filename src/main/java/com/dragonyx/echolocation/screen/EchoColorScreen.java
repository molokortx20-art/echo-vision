package com.dragonyx.echolocation.screen;

import com.dragonyx.echolocation.config.EchoConfig;
import com.dragonyx.echolocation.screen.EchoWidgets.FlatButton;
import com.dragonyx.echolocation.screen.EchoWidgets.Icon;
import com.dragonyx.echolocation.screen.EchoWidgets.IconButton;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.awt.Color;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Палитра цвета: квадрат насыщенность × яркость, полоса оттенка, образцы и поле HEX.
 * Как и настройки — панелью слева, мир справа виден, цвет меняется на эхе сразу.
 */
public final class EchoColorScreen extends Screen {
	private static final String S = "screen.echovision.";
	private static final int HUE_W = 14, PALETTE_H = 110;
	/** Самая тёмная яркость внизу палитры: чёрное эхо в темноте не разглядеть. */
	private static final float MIN_VALUE = 0.15f;
	/** Готовые цвета под палитрой. */
	private static final int[] SWATCHES = {
		0xFFFFFF, 0x33D1FF, 0x5FE0FF, 0x2A5BD7, 0x6CFF7A, 0xFF8A2B, 0xFFD36B, 0xFF4D6A, 0xB48CFF, 0xFF9AE0
	};

	private final Screen parent;
	private final IntSupplier get;
	private final IntConsumer set;
	private final int defaultColor;
	/** Цвет поверхностей виден только в режиме «один цвет» — выбор цвета его включает. */
	private final boolean forceSingle;

	private TextFieldWidget hexField;
	private float hue, saturation, value;
	private boolean draggingPalette, draggingHue, syncingHex;
	private int panelW, paletteX, paletteY, paletteW, hueX, swatchY;

	public EchoColorScreen(Screen parent, Text title, IntSupplier get, IntConsumer set, int defaultColor, boolean forceSingle) {
		super(title);
		this.parent = parent;
		this.get = get;
		this.set = set;
		this.defaultColor = defaultColor;
		this.forceSingle = forceSingle;
		syncFromColor();
	}

	@Override
	protected void init() {
		panelW = Math.min(this.width, Math.max(230, Math.min(300, this.width * 2 / 5)));
		paletteX = 10;
		paletteY = 40;
		paletteW = panelW - 20 - HUE_W - 6;
		hueX = paletteX + paletteW + 6;
		swatchY = paletteY + PALETTE_H + 6;
		int rowW = panelW - 20;

		IconButton close = addDrawableChild(new IconButton(18, Icon.CLOSE, Text.translatable(S + "back"), this::close));
		close.setPosition(panelW - 24, 5);
		IconButton pulse = addDrawableChild(new IconButton(18, Icon.PULSE, Text.translatable(S + "pulse"), EchoPreview::pulseNow));
		pulse.setPosition(panelW - 46, 5);

		int y = swatchY + 20;
		hexField = addDrawableChild(new TextFieldWidget(this.textRenderer, 10 + 28, y, rowW - 28, 20, Text.translatable(S + "color.hex")));
		hexField.setMaxLength(7);
		hexField.setText(hex());
		hexField.setChangedListener(text -> {
			if (syncingHex) return;
			Integer rgb = parseHex(text);
			if (rgb == null) return;
			fromRgb(rgb);
			apply(false);
		});
		// Обе кнопки в один ряд — на маленьком окне по высоте места мало.
		y += 24;
		int half = (rowW - 3) / 2;
		FlatButton reset = addDrawableChild(new FlatButton(half, () -> Text.translatable(S + "color.reset"), EchoWidgets.TEXT, () -> {
			fromRgb(defaultColor);
			apply(true);
		}));
		reset.setPosition(10, y);
		FlatButton done = addDrawableChild(new FlatButton(rowW - half - 3, () -> Text.translatable(S + "done"), 0xFFFFFFFF, this::close));
		done.setPosition(10 + half + 3, y);
	}

	private void syncFromColor() {
		fromRgb(get.getAsInt());
	}

	private void fromRgb(int rgb) {
		float[] hsb = Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null);
		hue = hsb[0];
		saturation = hsb[1];
		value = Math.max(MIN_VALUE, hsb[2]);
	}

	/** Записывает выбранный цвет (и, если цвет менялся не из поля, обновляет HEX). */
	private void apply(boolean updateHex) {
		set.accept(Color.HSBtoRGB(hue, saturation, value) & 0xFFFFFF);
		if (forceSingle) EchoConfig.get().colorMode = EchoConfig.ColorMode.SINGLE;
		EchoPreview.changed();
		if (updateHex) setHexText();
	}

	private void setHexText() {
		if (hexField == null || hexField.getText().equalsIgnoreCase(hex())) return;
		syncingHex = true;
		hexField.setText(hex());
		syncingHex = false;
	}

	private String hex() {
		return String.format(Locale.ROOT, "#%06X", get.getAsInt());
	}

	private static Integer parseHex(String text) {
		String s = text.trim();
		if (s.startsWith("#")) s = s.substring(1);
		if (s.length() != 6) return null;
		try {
			return Integer.parseInt(s, 16);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private boolean inPalette(double x, double y) {
		return x >= paletteX && x <= paletteX + paletteW && y >= paletteY && y <= paletteY + PALETTE_H;
	}

	private boolean inHue(double x, double y) {
		return x >= hueX && x <= hueX + HUE_W && y >= paletteY && y <= paletteY + PALETTE_H;
	}

	private int swatchAt(double x, double y) {
		if (y < swatchY || y > swatchY + 16) return -1;
		int step = swatchStep();
		int i = (int) ((x - 10) / step);
		return x >= 10 && i >= 0 && i < SWATCHES.length && x - 10 - i * step <= 16 ? i : -1;
	}

	private int swatchStep() {
		return Math.max(17, (panelW - 20 - 16) / (SWATCHES.length - 1));
	}

	private void pickPalette(double x, double y) {
		saturation = clamp01((float) ((x - paletteX) / paletteW));
		value = 1 - clamp01((float) ((y - paletteY) / PALETTE_H)) * (1 - MIN_VALUE);
		apply(true);
	}

	private void pickHue(double y) {
		// Не ровно 1: оттенок 360° — это снова красный 0°, и маркер прыгнул бы наверх.
		hue = Math.min(0.999f, clamp01((float) ((y - paletteY) / PALETTE_H)));
		apply(true);
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (click.button() == 0 && inPalette(click.x(), click.y())) {
			draggingPalette = true;
			pickPalette(click.x(), click.y());
			return true;
		}
		if (click.button() == 0 && inHue(click.x(), click.y())) {
			draggingHue = true;
			pickHue(click.y());
			return true;
		}
		int s = click.button() == 0 ? swatchAt(click.x(), click.y()) : -1;
		if (s >= 0) {
			fromRgb(SWATCHES[s]);
			apply(true);
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (draggingPalette) {
			pickPalette(click.x(), click.y());
			return true;
		}
		if (draggingHue) {
			pickHue(click.y());
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(Click click) {
		draggingPalette = false;
		draggingHue = false;
		return super.mouseReleased(click);
	}

	@Override
	public void tick() {
		EchoPreview.tick();
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		if (this.client == null || this.client.world == null) super.renderBackground(ctx, mouseX, mouseY, delta);
		ctx.fill(0, 0, panelW, this.height, 0xD80E1216);
		ctx.fill(panelW, 0, panelW + 1, this.height, 0x665AD2FF);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawText(this.textRenderer, EchoWidgets.fit(this.title, panelW - 60), 10, 10, 0xFFFFFFFF, true);
		ctx.drawText(this.textRenderer, EchoWidgets.fit(Text.translatable(S + "color.description"), panelW - 20), 10, 27, EchoWidgets.MUTED, false);

		// Квадрат: по горизонтали насыщенность, по вертикали яркость (сверху полная).
		for (int x = 0; x < paletteW; x += 2) {
			float s = x / (paletteW - 1f);
			int top = 0xFF000000 | Color.HSBtoRGB(hue, s, 1f);
			int bottom = 0xFF000000 | Color.HSBtoRGB(hue, s, MIN_VALUE);
			ctx.fillGradient(paletteX + x, paletteY, paletteX + Math.min(x + 2, paletteW), paletteY + PALETTE_H, top, bottom);
		}
		for (int y = 0; y < PALETTE_H; y += 2) {
			int color = 0xFF000000 | Color.HSBtoRGB(y / (PALETTE_H - 1f), 1f, 1f);
			ctx.fill(hueX, paletteY + y, hueX + HUE_W, paletteY + Math.min(y + 2, PALETTE_H), color);
		}
		int sx = paletteX + Math.round(saturation * paletteW);
		int sy = paletteY + Math.round((1 - value) / (1 - MIN_VALUE) * PALETTE_H);
		ctx.drawStrokedRectangle(sx - 3, sy - 3, 7, 7, 0xFFFFFFFF);
		int hy = paletteY + Math.round(hue * PALETTE_H);
		ctx.drawStrokedRectangle(hueX - 2, hy - 2, HUE_W + 4, 5, 0xFFFFFFFF);

		int step = swatchStep();
		int current = get.getAsInt();
		for (int i = 0; i < SWATCHES.length; i++) {
			int x = 10 + i * step;
			ctx.fill(x, swatchY, x + 16, swatchY + 16, 0xFF000000 | SWATCHES[i]);
			if (SWATCHES[i] == current) ctx.drawStrokedRectangle(x - 2, swatchY - 2, 20, 20, 0xFFFFFFFF);
		}

		int py = swatchY + 20;
		ctx.fill(10, py, 32, py + 20, 0xFF000000 | current);
		ctx.drawStrokedRectangle(10, py, 22, 20, 0xFFFFFFFF);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		EchoConfig.save();
		if (this.client != null) this.client.setScreen(parent);
	}

	private static float clamp01(float v) {
		return Math.max(0f, Math.min(1f, v));
	}
}
