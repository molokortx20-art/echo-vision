package com.dragonyx.echolocation.echo;

import com.dragonyx.echolocation.EchoMod;

/**
 * Замер затрат мода: -Dechovision.perf=true — раз в 5 секунд пишет в лог, сколько времени
 * ушло на отрисовку эха в кадре, на приём волн в тике и на расчёт распространения.
 */
public final class EchoStats {
	public static final boolean ENABLED = Boolean.getBoolean("echovision.perf");
	private static final long PERIOD = 5_000_000_000L;

	private static long periodStart = System.nanoTime();
	private static long renderNanos, renderMax, frames;
	private static long tickNanos, tickMax, ticks;
	private static volatile long propagateNanos, propagateMax, waves;
	private static int peakBlocks;

	private EchoStats() {}

	public static void frame(long nanos) {
		renderNanos += nanos;
		renderMax = Math.max(renderMax, nanos);
		frames++;
		peakBlocks = Math.max(peakBlocks, EchoField.INSTANCE.blocks.size());
		long now = System.nanoTime();
		if (now - periodStart < PERIOD) return;
		EchoMod.LOGGER.info(String.format(
			"[perf] frame: avg %.3f ms, max %.3f ms (%d frames) | tick: avg %.3f ms, max %.3f ms | propagation: %d waves, avg %.2f ms, max %.2f ms | blocks peak %d",
			ms(renderNanos) / Math.max(1, frames), ms(renderMax), frames,
			ms(tickNanos) / Math.max(1, ticks), ms(tickMax),
			waves, ms(propagateNanos) / Math.max(1, waves), ms(propagateMax), peakBlocks));
		periodStart = now;
		renderNanos = renderMax = frames = 0;
		tickNanos = tickMax = ticks = 0;
		propagateNanos = propagateMax = waves = 0;
		peakBlocks = 0;
	}

	public static void tick(long nanos) {
		tickNanos += nanos;
		tickMax = Math.max(tickMax, nanos);
		ticks++;
	}

	/** Из фонового потока распространения. */
	public static synchronized void propagation(long nanos) {
		propagateNanos += nanos;
		propagateMax = Math.max(propagateMax, nanos);
		waves++;
	}

	private static double ms(long nanos) {
		return nanos / 1_000_000.0;
	}
}
