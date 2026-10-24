package com.dragonyx.echolocation.echo;

/** Монотонное время в секундах с запуска мода. */
public final class EchoClock {
	private static final long START = System.nanoTime();

	private EchoClock() {}

	public static double now() {
		return (System.nanoTime() - START) / 1e9;
	}
}
