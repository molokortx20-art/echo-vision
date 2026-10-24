package com.dragonyx.echolocation.sound;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.minecraft.util.shape.VoxelShape;

import java.util.Arrays;

/** Что услышала одна звуковая волна: поверхности и расстояния до них по пути звука. */
public final class WaveResult {
	public final int limit;
	public int size;
	public long[] pos = new long[256];
	public byte[] face = new byte[256];
	/** Маска рёбер-контуров грани (биты по {@link SoundPropagator#TANGENTS}). */
	public byte[] edges = new byte[256];
	public float[] dist = new float[256];
	public VoxelShape[] shape = new VoxelShape[256];
	/**
	 * Путь звука до четырёх углов грани (по 4 на запись, порядок углов как у рендера), блоков.
	 * Соседние грани получают в общем углу одно и то же значение — поле непрерывно, и фронт
	 * волны огибает углы туннелей без разрывов. Для формы неполного блока [0] — путь до центра клетки.
	 */
	public float[] corners = new float[1024];
	/** Локальный индекс воздушной клетки, из которой звук подошёл к грани (служебное, для расчёта углов). */
	int[] airIdx = new int[256];
	/** Длина пути до каждой проходимой клетки — по ней находим, когда волна дошла до существ. */
	public final Long2FloatOpenHashMap air = new Long2FloatOpenHashMap();
	public boolean complete;

	public WaveResult(int limit) {
		this.limit = limit;
		air.defaultReturnValue(-1f);
	}

	void addBlock(long p, byte f, float d, VoxelShape s, byte edgeMask, int air) {
		if (size >= limit) return;
		if (size == pos.length) {
			int n = size * 2;
			pos = Arrays.copyOf(pos, n);
			face = Arrays.copyOf(face, n);
			edges = Arrays.copyOf(edges, n);
			dist = Arrays.copyOf(dist, n);
			shape = Arrays.copyOf(shape, n);
			corners = Arrays.copyOf(corners, n * 4);
			airIdx = Arrays.copyOf(airIdx, n);
		}
		pos[size] = p;
		face[size] = f;
		edges[size] = edgeMask;
		dist[size] = d;
		shape[size] = s;
		airIdx[size] = air;
		size++;
	}

	void addAir(long p, float d) {
		air.put(p, d);
	}
}
