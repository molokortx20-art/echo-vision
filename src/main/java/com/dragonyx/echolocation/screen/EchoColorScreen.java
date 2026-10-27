package com.dragonyx.echolocation.screen;

import com.dragonyx.echolocation.config.EchoConfig;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.awt.Color;
import java.util.Locale;

/**
 * Палитра цвета эха: квадрат насыщенность × яркость, полоса оттенка и поле для HEX-кода.
 * Выбор цвета сразу включает режим «один цвет» — иначе его не было бы видно.
 */
public final class EchoColorScreen extends Screen {
	private static final int PALETTE_W = 220, PALETTE_H = 120, HUE_W = 18;
	/** Самая тёмная яркость внизу палитры: чёрное эхо в темноте не разглядеть. */
	private static final float MIN_VALUE = 0.15f;

	private final Screen parent;
	private TextFieldWidget hexField;
	private float hue, saturation, value;
	private boolean draggingPalette, draggingHue, syncingHex;
	private int paletteX, paletteY, hueX;

	public EchoColorScreen(Screen parent) {
		super(Text.translatable("screen.echovision.color.title"));
		this.parent = parent;
		syncFromConfig();
	}

	@Override
	protected void init() {
		paletteX = this.width / 2 - (PALETTE_W + 10 + HUE_W) / 2;
		paletteY = Math.max(36, this.height / 2 - 85);
		hueX = paletteX + PALETTE_W + 10;
		int below = paletteY + PALETTE_H;

		hexField = new TextFieldWidget(this.textRenderer, this.width / 2 - 80, below + 24, 160, 20, Text.translatable("screen.echovision.color.hex"));
		hexField.setMaxLength(7);
		hexField.setText(hex());
		hexField.setChangedListener(text -> {
			if (syncingHex) return;
			Integer rgb = parseHex(text);
			if (rgb == null) return;
			float[] hsb = Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null);
			hue = hsb[0];
			saturation = hsb[1];
			value = Math.max(MIN_VALUE, hsb[2]);
			apply(false);
		});
		addDrawableChild(hexField);
		addDrawableChild(ButtonWidget.builder(Text.translatable("screen.echovision.color.reset"), b -> {
			EchoConfig defaults = new EchoConfig();
			EchoConfig c = EchoConfig.get();
			c.hue = defaults.hue;
			c.saturation = defaults.saturation;
			c.colorValue = defaults.colorValue;
			EchoConfig.save();
			syncFromConfig();
			setHexText();
		}).dimensions(this.width / 2 - 105, below + 54, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
			.dimensions(this.width / 2 + 5, below + 54, 100, 20).build());
	}

	private void syncFromConfig() {
		EchoConfig c = EchoConfig.get();
		hue = c.hue / 360f;
		saturation = c.saturation;
		value = c.colorValue;
	}

	/** Записывает выбранный цвет в настройки (и, если цвет менялся не из поля, обновляет HEX). */
	private void apply(boolean updateHex) {
		EchoConfig c = EchoConfig.get();
		c.hue = Math.round(hue * 360) % 360;
		c.saturation = saturation;
		c.colorValue = value;
		c.colorMode = EchoConfig.ColorMode.SINGLE;
		if (updateHex) setHexText();
	}

	private void setHexText() {
		if (hexField == null || hexField.getText().equalsIgnoreCase(hex())) return;
		syncingHex = true;
		hexField.setText(hex());
		syncingHex = false;
	}

	private static String hex() {
		return String.format(Locale.ROOT, "#%06X", EchoConfig.get().baseColor());
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
		return x >= paletteX && x <= paletteX + PALETTE_W && y >= paletteY && y <= paletteY + PALETTE_H;
	}

	private boolean inHue(double x, double y) {
		return x >= hueX && x <= hueX + HUE_W && y >= paletteY && y <= paletteY + PALETTE_H;
	}

	private void pickPalette(double x, double y) {
		saturation = clamp01((float) ((x - paletteX) / PALETTE_W));
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
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, paletteY - 28, 0xFFFFFFFF);
		ctx.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("screen.echovision.color.description"),
			this.width / 2, paletteY - 14, 0xFFA0A0A0);

		// Квадрат: по горизонтали насыщенность, по вертикали яркость (сверху полная).
		for (int x = 0; x < PALETTE_W; x += 2) {
			float s = x / (PALETTE_W - 1f);
			int top = 0xFF000000 | Color.HSBtoRGB(hue, s, 1f);
			int bottom = 0xFF000000 | Color.HSBtoRGB(hue, s, MIN_VALUE);
			ctx.fillGradient(paletteX + x, paletteY, paletteX + Math.min(x + 2, PALETTE_W), paletteY + PALETTE_H, top, bottom);
		}
		for (int y = 0; y < PALETTE_H; y += 2) {
			int color = 0xFF000000 | Color.HSBtoRGB(y / (PALETTE_H - 1f), 1f, 1f);
			ctx.fill(hueX, paletteY + y, hueX + HUE_W, paletteY + Math.min(y + 2, PALETTE_H), color);
		}

		int sx = paletteX + Math.round(saturation * PALETTE_W);
		int sy = paletteY + Math.round((1 - value) / (1 - MIN_VALUE) * PALETTE_H);
		ctx.drawStrokedRectangle(sx - 3, sy - 3, 7, 7, 0xFFFFFFFF);
		int hy = paletteY + Math.round(hue * PALETTE_H);
		ctx.drawStrokedRectangle(hueX - 2, hy - 2, HUE_W + 4, 5, 0xFFFFFFFF);

		int below = paletteY + PALETTE_H;
		ctx.fill(this.width / 2 - 110, below + 23, this.width / 2 - 88, below + 45, 0xFF000000 | EchoConfig.get().baseColor());
		ctx.drawStrokedRectangle(this.width / 2 - 110, below + 23, 22, 22, 0xFFFFFFFF);
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
