package com.dragonyx.echolocation.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.input.AbstractInput;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Плоские виджеты экрана настроек: тёмная полупрозрачная панель поверх мира, чтобы изменения
 * было видно сразу. Иконки нарисованы попиксельно (см. {@link Icon}).
 */
final class EchoWidgets {
	static final int ROW = 20;
	static final int ACCENT = 0xFF5AD2FF;
	static final int TEXT = 0xFFE6EDF2;
	static final int MUTED = 0xFF8C99A3;
	static final int ROW_BG = 0x22FFFFFF;
	static final int ROW_HOVER = 0x3CFFFFFF;

	private EchoWidgets() {}

	static TextRenderer font() {
		return MinecraftClient.getInstance().textRenderer;
	}

	static int bg(ClickableWidget w) {
		return w.isHovered() || w.isFocused() ? ROW_HOVER : ROW_BG;
	}

	static void text(DrawContext ctx, Text text, int x, int y, int color) {
		ctx.drawText(font(), text, x, y, color, true);
	}

	static void textRight(DrawContext ctx, Text text, int right, int y, int color) {
		ctx.drawText(font(), text, right - font().getWidth(text), y, color, true);
	}

	/** Обрезает подпись, если не помещается, многоточием. */
	static Text fit(Text text, int width) {
		TextRenderer f = font();
		if (f.getWidth(text) <= width) return text;
		String s = text.getString();
		while (!s.isEmpty() && f.getWidth(s + "…") > width) s = s.substring(0, s.length() - 1);
		return Text.literal(s + "…");
	}

	// ---------- иконки ----------

	/** Иконка 9×9: «X» — закрашенный пиксель. */
	enum Icon {
		CLOSE(
			"X.......X",
			".X.....X.",
			"..X...X..",
			"...X.X...",
			"....X....",
			"...X.X...",
			"..X...X..",
			".X.....X.",
			"X.......X"),
		PULSE(
			"..XXXXX..",
			".X.....X.",
			"X.......X",
			"X...X...X",
			"X..XXX..X",
			"X...X...X",
			"X.......X",
			".X.....X.",
			"..XXXXX.."),
		EYE(
			".........",
			"..XXXXX..",
			".X.....X.",
			"X..XXX..X",
			"X..XXX..X",
			"X..XXX..X",
			".X.....X.",
			"..XXXXX..",
			"........."),
		SAVE(
			"XXXXXXXX.",
			"X.XXXX.XX",
			"X.XXXX..X",
			"X.......X",
			"X.......X",
			"X.XXXXX.X",
			"X.X...X.X",
			"X.X...X.X",
			"XXXXXXXXX"),
		TRASH(
			"...XXX...",
			"XXXXXXXXX",
			".X.....X.",
			".X.X.X.X.",
			".X.X.X.X.",
			".X.X.X.X.",
			".X.X.X.X.",
			".X.....X.",
			"..XXXXX.."),
		RESET(
			"..XXXX.X.",
			".X....XX.",
			"X....XXX.",
			"X........",
			"X.......X",
			"X.......X",
			"X.......X",
			".X.....X.",
			"..XXXXX..");

		final String[] rows;

		Icon(String... rows) {
			this.rows = rows;
		}

		void draw(DrawContext ctx, int x, int y, int color) {
			for (int j = 0; j < rows.length; j++) {
				String r = rows[j];
				for (int i = 0; i < r.length(); i++) {
					if (r.charAt(i) == 'X') ctx.fill(x + i, y + j, x + i + 1, y + j + 1, color);
				}
			}
		}
	}

	/** Квадратная кнопка с иконкой и подсказкой при наведении. */
	static final class IconButton extends PressableWidget {
		private final Icon icon;
		private final Runnable action;
		private final Supplier<Boolean> lit;

		IconButton(int size, Icon icon, Text tooltip, Runnable action) {
			this(size, icon, tooltip, action, () -> false);
		}

		/** lit — кнопка-переключатель подсвечена, пока включено. */
		IconButton(int size, Icon icon, Text tooltip, Runnable action, Supplier<Boolean> lit) {
			super(0, 0, size, size, tooltip);
			this.icon = icon;
			this.action = action;
			this.lit = lit;
			setTooltip(Tooltip.of(tooltip));
		}

		@Override
		public void onPress(AbstractInput input) {
			action.run();
		}

		@Override
		protected void drawIcon(DrawContext ctx, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY();
			boolean on = lit.get();
			ctx.fill(x, y, x + width, y + height, on ? 0x5533B5E6 : bg(this));
			icon.draw(ctx, x + (width - 9) / 2, y + (height - 9) / 2, on || isHovered() ? 0xFFFFFFFF : 0xFFC8D2DA);
		}

		@Override
		protected void appendClickableNarrations(NarrationMessageBuilder builder) {
			appendDefaultNarrations(builder);
		}
	}

	/** Вкладка: текст, выбранная подчёркнута. */
	static final class Tab extends PressableWidget {
		private final Runnable action;
		private final boolean selected;

		Tab(int width, Text label, boolean selected, Runnable action) {
			super(0, 0, width, 18, label);
			this.action = action;
			this.selected = selected;
		}

		@Override
		public void onPress(AbstractInput input) {
			action.run();
		}

		@Override
		protected void drawIcon(DrawContext ctx, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY();
			if (selected || isHovered()) ctx.fill(x, y, x + width, y + height, selected ? 0x33FFFFFF : ROW_BG);
			if (selected) ctx.fill(x, y + height - 2, x + width, y + height, ACCENT);
			Text t = fit(getMessage(), width - 4);
			ctx.drawCenteredTextWithShadow(font(), t, x + width / 2, y + (height - 8) / 2, selected ? 0xFFFFFFFF : MUTED);
		}

		@Override
		protected void appendClickableNarrations(NarrationMessageBuilder builder) {
			appendDefaultNarrations(builder);
		}
	}

	/** Заголовок раздела. */
	static final class Header extends ClickableWidget {
		Header(int width, Text label) {
			super(0, 0, width, 16, label);
			this.active = false;
		}

		@Override
		protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
			int y = getY() + 6;
			text(ctx, getMessage(), getX(), y, ACCENT);
			int lx = getX() + font().getWidth(getMessage()) + 6;
			ctx.fill(lx, y + 4, getX() + width, y + 5, 0x335AD2FF);
		}

		@Override
		protected void appendClickableNarrations(NarrationMessageBuilder builder) {
			appendDefaultNarrations(builder);
		}
	}

	/** Ползунок: подпись слева, значение справа, заполненная часть подсвечена. */
	static final class Slider extends SliderWidget {
		private final Text label;
		private final int min, max;
		private final IntSupplier get;
		private final Consumer<Integer> set;
		private final IntFunction<String> format;
		private final Runnable changed;

		Slider(int width, Text label, int min, int max, IntSupplier get, Consumer<Integer> set, IntFunction<String> format, Runnable changed) {
			super(0, 0, width, ROW, label, 0);
			this.label = label;
			this.min = min;
			this.max = max;
			this.get = get;
			this.set = set;
			this.format = format;
			this.changed = changed;
			this.value = (Math.max(min, Math.min(max, get.getAsInt())) - min) / (double) (max - min);
		}

		private int current() {
			return min + (int) Math.round(value * (max - min));
		}

		@Override
		protected void updateMessage() {
		}

		@Override
		protected void applyValue() {
			int v = current();
			if (v == get.getAsInt()) return;
			set.accept(v);
			changed.run();
		}

		@Override
		public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY();
			ctx.fill(x, y, x + width, y + height, bg(this));
			// Шаги ползунка дискретны — заливку рисуем по округлённому значению.
			int fillW = (int) Math.round((current() - min) / (double) (max - min) * width);
			ctx.fill(x, y, x + fillW, y + height, 0x2A5AD2FF);
			ctx.fill(x, y + height - 2, x + fillW, y + height, ACCENT);
			Text value = Text.literal(format.apply(current()));
			int vw = font().getWidth(value);
			text(ctx, fit(label, width - vw - 16), x + 6, y + (height - 8) / 2, TEXT);
			textRight(ctx, value, x + width - 6, y + (height - 8) / 2, 0xFFFFFFFF);
		}
	}

	/** Переключатель: подпись и «рычажок» справа. */
	static final class Toggle extends PressableWidget {
		private final Supplier<Boolean> get;
		private final Consumer<Boolean> set;
		private final Runnable changed;

		Toggle(int width, Text label, Supplier<Boolean> get, Consumer<Boolean> set, Runnable changed) {
			super(0, 0, width, ROW, label);
			this.get = get;
			this.set = set;
			this.changed = changed;
		}

		@Override
		public void onPress(AbstractInput input) {
			set.accept(!get.get());
			changed.run();
		}

		@Override
		protected void drawIcon(DrawContext ctx, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY();
			boolean on = get.get();
			ctx.fill(x, y, x + width, y + height, bg(this));
			text(ctx, fit(getMessage(), width - 40), x + 6, y + (height - 8) / 2, TEXT);
			int sx = x + width - 26, sy = y + (height - 10) / 2;
			ctx.fill(sx, sy, sx + 20, sy + 10, on ? 0xFF2F9DC4 : 0xFF3A4249);
			int kx = on ? sx + 11 : sx + 1;
			ctx.fill(kx, sy + 1, kx + 8, sy + 9, on ? 0xFFFFFFFF : 0xFFAEB7BE);
		}

		@Override
		protected void appendClickableNarrations(NarrationMessageBuilder builder) {
			appendDefaultNarrations(builder);
		}
	}

	/** Выбор из нескольких значений по кругу: подпись слева, значение справа. */
	static final class Choice<E> extends PressableWidget {
		private final E[] values;
		private final Supplier<E> get;
		private final Consumer<E> set;
		private final java.util.function.Function<E, Text> name;
		private final Runnable changed;

		Choice(int width, Text label, E[] values, Supplier<E> get, Consumer<E> set, java.util.function.Function<E, Text> name, Runnable changed) {
			super(0, 0, width, ROW, label);
			this.values = values;
			this.get = get;
			this.set = set;
			this.name = name;
			this.changed = changed;
		}

		@Override
		public void onPress(AbstractInput input) {
			E cur = get.get();
			int i = 0;
			while (i < values.length && values[i] != cur) i++;
			set.accept(values[(i + 1) % values.length]);
			changed.run();
		}

		@Override
		protected void drawIcon(DrawContext ctx, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY();
			ctx.fill(x, y, x + width, y + height, bg(this));
			Text v = Text.literal("< ").append(name.apply(get.get())).append(" >");
			int vw = font().getWidth(v);
			text(ctx, fit(getMessage(), width - vw - 16), x + 6, y + (height - 8) / 2, TEXT);
			textRight(ctx, v, x + width - 6, y + (height - 8) / 2, 0xFFFFFFFF);
		}

		@Override
		protected void appendClickableNarrations(NarrationMessageBuilder builder) {
			appendDefaultNarrations(builder);
		}
	}

	/** Строка цвета: подпись, образец и HEX; нажатие открывает палитру. */
	static final class ColorRow extends PressableWidget {
		private final IntSupplier get;
		private final Runnable open;

		ColorRow(int width, Text label, IntSupplier get, Runnable open) {
			super(0, 0, width, ROW, label);
			this.get = get;
			this.open = open;
		}

		@Override
		public void onPress(AbstractInput input) {
			open.run();
		}

		@Override
		protected void drawIcon(DrawContext ctx, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY();
			ctx.fill(x, y, x + width, y + height, bg(this));
			int rgb = get.getAsInt();
			Text hex = Text.literal(String.format("#%06X", rgb));
			int hw = font().getWidth(hex);
			text(ctx, fit(getMessage(), width - hw - 36), x + 6, y + (height - 8) / 2, TEXT);
			textRight(ctx, hex, x + width - 24, y + (height - 8) / 2, MUTED);
			int sx = x + width - 19, sy = y + 3;
			ctx.fill(sx - 1, sy - 1, sx + 15, sy + 15, 0xFFFFFFFF);
			ctx.fill(sx, sy, sx + 14, sy + 14, 0xFF000000 | rgb);
		}

		@Override
		protected void appendClickableNarrations(NarrationMessageBuilder builder) {
			appendDefaultNarrations(builder);
		}
	}

	/**
	 * Карточка пресета: название и два образца цвета; выбранная обведена. В режиме удаления (корзина
	 * над пресетами) карточки красные и нажатие удаляет пресет.
	 */
	static final class PresetCard extends PressableWidget {
		private final int surface, line;
		private final Supplier<Boolean> selected, deleting;
		private final Runnable apply, delete;

		PresetCard(int width, Text name, int surface, int line, Supplier<Boolean> selected, Runnable apply,
				   Supplier<Boolean> deleting, Runnable delete) {
			super(0, 0, width, ROW, name);
			this.surface = surface;
			this.line = line;
			this.selected = selected;
			this.apply = apply;
			this.deleting = deleting;
			this.delete = delete;
		}

		@Override
		public void onPress(AbstractInput input) {
			if (deleting.get()) {
				delete.run();
			} else {
				apply.run();
			}
		}

		@Override
		protected void drawIcon(DrawContext ctx, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY();
			boolean del = deleting.get();
			boolean sel = !del && selected.get();
			int frame = del ? 0xFFFF6B6B : ACCENT;
			ctx.fill(x, y, x + width, y + height, del ? (isHovered() ? 0x66E04848 : 0x33E04848) : sel ? 0x4033B5E6 : bg(this));
			if (sel || del && isHovered()) {
				ctx.fill(x, y, x + width, y + 1, frame);
				ctx.fill(x, y + height - 1, x + width, y + height, frame);
				ctx.fill(x, y, x + 1, y + height, frame);
				ctx.fill(x + width - 1, y, x + width, y + height, frame);
			}
			int sy = y + 6;
			ctx.fill(x + 5, sy, x + 13, sy + 8, 0xFF000000 | surface);
			ctx.fill(x + 13, sy, x + 17, sy + 8, 0xFF000000 | line);
			int right = del ? 14 : 4;
			text(ctx, fit(getMessage(), width - 21 - right), x + 21, y + (height - 8) / 2, sel ? 0xFFFFFFFF : TEXT);
			if (del) Icon.CLOSE.draw(ctx, x + width - 13, y + (height - 9) / 2, 0xFFFF8A8A);
		}

		@Override
		protected void appendClickableNarrations(NarrationMessageBuilder builder) {
			appendDefaultNarrations(builder);
		}
	}

	/** Обычная текстовая кнопка в том же плоском стиле. */
	static final class FlatButton extends PressableWidget {
		private final Supplier<Text> label;
		private final Runnable action;
		private final int color;

		/** label — подпись может меняться (например, «Точно сбросить?»). */
		FlatButton(int width, Supplier<Text> label, int color, Runnable action) {
			super(0, 0, width, ROW, label.get());
			this.label = label;
			this.action = action;
			this.color = color;
		}

		@Override
		public Text getMessage() {
			return label.get();
		}

		@Override
		public void onPress(AbstractInput input) {
			action.run();
		}

		@Override
		protected void drawIcon(DrawContext ctx, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY();
			ctx.fill(x, y, x + width, y + height, bg(this));
			ctx.drawCenteredTextWithShadow(font(), fit(getMessage(), width - 8), x + width / 2, y + (height - 8) / 2, color);
		}

		@Override
		protected void appendClickableNarrations(NarrationMessageBuilder builder) {
			appendDefaultNarrations(builder);
		}
	}
}
