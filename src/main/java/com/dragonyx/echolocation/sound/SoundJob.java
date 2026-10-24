package com.dragonyx.echolocation.sound;

/** Один звук, который нужно «распространить» по миру. */
public final class SoundJob {
	public final double x, y, z;
	public final int originX, originY, originZ;
	public final float range;
	public final float intensity;
	public final int color;
	/** Время звука в секундах (EchoClock), от него считается приход волны. */
	public final double startTime;
	public final float wallAbsorption;
	public final boolean wallPenetration;
	public final int maxNodes;
	public final WaveResult result;

	public SoundJob(double x, double y, double z, float range, float intensity, int color, double startTime,
					float wallAbsorption, boolean wallPenetration, int maxReveals) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.originX = (int) Math.floor(x);
		this.originY = (int) Math.floor(y);
		this.originZ = (int) Math.floor(z);
		this.range = range;
		this.intensity = intensity;
		this.color = color;
		this.startTime = startTime;
		this.wallAbsorption = wallAbsorption;
		this.wallPenetration = wallPenetration;
		this.maxNodes = 400_000;
		this.result = new WaveResult(maxReveals);
	}
}
