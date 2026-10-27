package com.dragonyx.echolocation.sound;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.World;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Распространение звука по сетке блоков.
 * <p>
 * Алгоритм Дейкстры с ведёрной очередью (стоимости в десятых долях блока) по 26 соседям:
 * звук огибает углы, проходит в двери и коридоры, а сквозь твёрдые блоки — с затуханием,
 * которое зависит от материала (шерсть глушит, листва и стекло почти пропускают).
 * Каждая поверхность, к которой подошёл фронт волны, попадает в результат вместе с длиной пути.
 */
public final class SoundPropagator {
	private static final byte UNKNOWN = 0;
	private static final byte AIR = 1;
	private static final byte PARTIAL = 2;
	private static final byte SOLID = 3;
	private static final byte BARRIER = 4;

	/** Шаги к 26 соседям и их цена (×10): грань 10, ребро 14, угол 17. */
	private static final int[][] STEPS;
	private static final int[] STEP_COST;
	/** Шесть граней: dx, dy, dz. Индекс совпадает с Direction.getId(). */
	public static final int[][] FACES = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
	public static final byte WHOLE = 6;

	static {
		int n = 0;
		int[][] steps = new int[26][];
		int[] cost = new int[26];
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					int axes = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
					if (axes == 0) continue;
					steps[n] = new int[]{dx, dy, dz};
					cost[n] = axes == 1 ? 10 : axes == 2 ? 14 : 17;
					n++;
				}
			}
		}
		STEPS = steps;
		STEP_COST = cost;
	}

	private record StateInfo(byte kind, int crossCost) {}

	private static final Map<BlockState, StateInfo> STATE_CACHE = new ConcurrentHashMap<>();

	// Рабочие массивы переиспользуются: расчёт идёт в одном фоновом потоке.
	private int[] stamp = new int[0];
	private int[] dist = new int[0];
	private byte[] kind = new byte[0];
	private short[] cross = new short[0];
	private int generation;
	private IntArrayList[] buckets = new IntArrayList[0];

	public static void clearCache() {
		STATE_CACHE.clear();
	}

	/**
	 * @param job параметры звука; заполняет {@code job.result}
	 */
	public void propagate(World world, SoundJob job) {
		final int range10 = Math.max(10, Math.round(job.range * 10));
		final int half = (int) Math.ceil(job.range) + 2;
		final int side = half * 2 + 1;
		final int volume = side * side * side;
		ensureCapacity(volume, range10 + 1);
		if (++generation == Integer.MAX_VALUE) {
			java.util.Arrays.fill(stamp, 0);
			generation = 1;
		}
		final int gen = generation;
		final int wall10 = Math.round(job.wallAbsorption * 10);
		final boolean penetrate = job.wallPenetration;

		final int ox = job.originX, oy = job.originY, oz = job.originZ;
		final BlockPos.Mutable mpos = new BlockPos.Mutable();
		final WaveResult out = job.result;
		final ChunkLookup chunks = new ChunkLookup(world);

		int originIdx = index(half, half, half, side);
		byte originKind = classify(world, chunks, mpos, ox, oy, oz, originIdx, gen, wall10);
		dist[originIdx] = 0;
		buckets[0].add(originIdx);
		if (originKind == SOLID || originKind == PARTIAL) {
			out.addBlock(BlockPos.asLong(ox, oy, oz), WHOLE, 0f, shapeAt(world, mpos.set(ox, oy, oz)), (byte) 0, originIdx);
		}

		int visited = 0;
		for (int d = 0; d <= range10; d++) {
			IntArrayList bucket = buckets[d];
			for (int bi = 0; bi < bucket.size(); bi++) {
				int idx = bucket.getInt(bi);
				if (dist[idx] != d) continue; // устаревшая запись
				if (++visited > job.maxNodes) {
					clearBuckets(d, range10);
					computeCorners(out, side);
					return;
				}
				int lx = idx % side;
				int ly = (idx / side) % side;
				int lz = idx / (side * side);
				int wx = ox + lx - half, wy = oy + ly - half, wz = oz + lz - half;
				byte k = kind[idx];
				boolean isOrigin = idx == originIdx;

				if (k == AIR || k == PARTIAL) {
					if (k == PARTIAL && !isOrigin) {
						out.addBlock(BlockPos.asLong(wx, wy, wz), WHOLE, d / 10f, shapeAt(world, mpos.set(wx, wy, wz)), (byte) 0, idx);
					}
					out.addAir(BlockPos.asLong(wx, wy, wz), d / 10f);
				}

				boolean fromSolid = k == SOLID;
				for (int s = 0; s < 26; s++) {
					int[] st = STEPS[s];
					int nx = lx + st[0], ny = ly + st[1], nz = lz + st[2];
					if (nx <= 0 || ny <= 0 || nz <= 0 || nx >= side - 1 || ny >= side - 1 || nz >= side - 1) continue;
					boolean faceStep = STEP_COST[s] == 10;
					if (fromSolid && !faceStep) continue;
					int nIdx = index(nx, ny, nz, side);
					byte nk = kindAt(world, chunks, mpos, ox, oy, oz, half, nx, ny, nz, nIdx, gen, wall10);
					int nd;
					if (nk == AIR || nk == PARTIAL) {
						if (!faceStep && !diagonalOpen(world, chunks, mpos, ox, oy, oz, half, side, lx, ly, lz, st, gen, wall10)) continue;
						nd = d + (fromSolid ? (isOrigin ? 5 : 10) : Math.max(4, STEP_COST[s] + cross[nIdx]));
					} else if (nk == SOLID) {
						if (!faceStep) continue;
						if (!fromSolid) {
							// Поверхность стены, к которой подошёл звук.
							int faceId = faceTowards(-st[0], -st[1], -st[2]);
							byte edges = edgeMask(world, chunks, mpos, ox, oy, oz, half, side, faceId, nx, ny, nz, lx, ly, lz, gen, wall10);
							out.addBlock(BlockPos.asLong(wx + st[0], wy + st[1], wz + st[2]), (byte) faceId, d / 10f + 0.5f, null, edges, idx);
						}
						if (!penetrate) continue;
						nd = d + cross[nIdx];
					} else {
						continue;
					}
					if (nd > range10) continue;
					if (dist[nIdx] < 0 || nd < dist[nIdx]) {
						dist[nIdx] = nd;
						buckets[nd].add(nIdx);
					}
				}
			}
			bucket.clear();
		}
		computeCorners(out, side);
		out.complete = true;
	}

	/** Расстояние от центра клетки до угла её грани: √0.75. */
	private static final float CELL_TO_CORNER = 0.8660254f;
	/** Касательные осей углов грани (t1, t2) и знаки углов в порядке рендера: (−,−), (+,−), (+,+), (−,+). */
	private static final int[][][] CORNER_AXES = {
		{{1, 0, 0}, {0, 0, 1}}, {{1, 0, 0}, {0, 0, 1}},
		{{1, 0, 0}, {0, 1, 0}}, {{1, 0, 0}, {0, 1, 0}},
		{{0, 0, 1}, {0, 1, 0}}, {{0, 0, 1}, {0, 1, 0}}
	};
	private static final int[][] CORNER_SIGNS = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};

	/**
	 * Путь звука до каждого угла каждой открытой грани — минимум по четырём воздушным клеткам
	 * вокруг угла. Считается после поиска, когда все расстояния уже окончательные.
	 */
	private void computeCorners(WaveResult out, int side) {
		int gen = generation;
		for (int i = 0; i < out.size; i++) {
			int a = out.airIdx[i];
			int f = out.face[i];
			float own = stamp[a] == gen && dist[a] >= 0 ? dist[a] / 10f : out.dist[i];
			if (f == WHOLE) {
				for (int c = 0; c < 4; c++) out.corners[i * 4 + c] = own;
				continue;
			}
			int ax = a % side, ay = (a / side) % side, az = a / (side * side);
			int[] t1 = CORNER_AXES[f][0], t2 = CORNER_AXES[f][1];
			for (int c = 0; c < 4; c++) {
				int u = CORNER_SIGNS[c][0], v = CORNER_SIGNS[c][1];
				float best = own;
				for (int m = 1; m < 4; m++) {
					int du = (m & 1) != 0 ? u : 0, dv = (m & 2) != 0 ? v : 0;
					int x = ax + t1[0] * du + t2[0] * dv;
					int y = ay + t1[1] * du + t2[1] * dv;
					int z = az + t1[2] * du + t2[2] * dv;
					if (x < 0 || y < 0 || z < 0 || x >= side || y >= side || z >= side) continue;
					int n = index(x, y, z, side);
					if (stamp[n] != gen || dist[n] < 0 || (kind[n] != AIR && kind[n] != PARTIAL)) continue;
					best = Math.min(best, dist[n] / 10f);
				}
				out.corners[i * 4 + c] = best + CELL_TO_CORNER;
			}
		}
	}

	/**
	 * Касательные направления рёбер грани в порядке битов маски: (−U, +U, −V, +V), где U и V — оси
	 * грани в том же порядке, что у углов рендера: грани по Y — X и Z, по Z — X и Y, по X — Z и Y.
	 */
	private static final int[][][] TANGENTS = new int[6][][];

	static {
		int[][] y = {{-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}};
		int[][] z = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}};
		int[][] x = {{0, 0, -1}, {0, 0, 1}, {0, -1, 0}, {0, 1, 0}};
		TANGENTS[0] = y;
		TANGENTS[1] = y;
		TANGENTS[2] = z;
		TANGENTS[3] = z;
		TANGENTS[4] = x;
		TANGENTS[5] = x;
	}

	/**
	 * Какие из четырёх рёбер грани — настоящий угол (контур), а какие лежат внутри ровной поверхности.
	 * Ребро внутреннее, если сосед по касательной тоже твёрдый и его такая же грань открыта.
	 * Контур рисуется на самой грани, поэтому у угла его рисуют обе грани — каждая со своей стороны.
	 */
	private byte edgeMask(World world, ChunkLookup chunks, BlockPos.Mutable mpos, int ox, int oy, int oz, int half, int side,
						  int faceId, int sx, int sy, int sz, int ax, int ay, int az, int gen, int wall10) {
		int[][] tangents = TANGENTS[faceId];
		byte mask = 0;
		for (int i = 0; i < 4; i++) {
			int[] t = tangents[i];
			int nx = sx + t[0], ny = sy + t[1], nz = sz + t[2];
			int px = ax + t[0], py = ay + t[1], pz = az + t[2];
			boolean internal = false;
			if (inside(nx, ny, nz, side) && inside(px, py, pz, side)) {
				byte n = kindAt(world, chunks, mpos, ox, oy, oz, half, nx, ny, nz, index(nx, ny, nz, side), gen, wall10);
				byte p = kindAt(world, chunks, mpos, ox, oy, oz, half, px, py, pz, index(px, py, pz, side), gen, wall10);
				internal = n == SOLID && p != SOLID && p != BARRIER;
			}
			if (!internal) mask |= (byte) (1 << i);
		}
		return mask;
	}

	private static boolean inside(int x, int y, int z, int side) {
		return x > 0 && y > 0 && z > 0 && x < side - 1 && y < side - 1 && z < side - 1;
	}

	private boolean diagonalOpen(World world, ChunkLookup chunks, BlockPos.Mutable mpos, int ox, int oy, int oz, int half, int side,
								 int lx, int ly, int lz, int[] st, int gen, int wall10) {
		// По диагонали звук идёт, только если открыты все промежуточные клетки по осям.
		if (st[0] != 0 && !open(world, chunks, mpos, ox, oy, oz, half, side, lx + st[0], ly, lz, gen, wall10)) return false;
		if (st[1] != 0 && !open(world, chunks, mpos, ox, oy, oz, half, side, lx, ly + st[1], lz, gen, wall10)) return false;
		if (st[2] != 0 && !open(world, chunks, mpos, ox, oy, oz, half, side, lx, ly, lz + st[2], gen, wall10)) return false;
		return true;
	}

	private boolean open(World world, ChunkLookup chunks, BlockPos.Mutable mpos, int ox, int oy, int oz, int half, int side,
						 int x, int y, int z, int gen, int wall10) {
		byte k = kindAt(world, chunks, mpos, ox, oy, oz, half, x, y, z, index(x, y, z, side), gen, wall10);
		return k == AIR || k == PARTIAL;
	}

	private byte kindAt(World world, ChunkLookup chunks, BlockPos.Mutable mpos, int ox, int oy, int oz, int half,
						int lx, int ly, int lz, int idx, int gen, int wall10) {
		if (stamp[idx] == gen) return kind[idx];
		return classify(world, chunks, mpos, ox + lx - half, oy + ly - half, oz + lz - half, idx, gen, wall10);
	}

	private byte classify(World world, ChunkLookup chunks, BlockPos.Mutable mpos, int x, int y, int z, int idx, int gen, int wall10) {
		stamp[idx] = gen;
		dist[idx] = -1;
		BlockState state = chunks.state(mpos.set(x, y, z));
		byte k;
		int c = 0;
		if (state == null) {
			k = BARRIER;
		} else {
			StateInfo info = STATE_CACHE.computeIfAbsent(state, SoundPropagator::describe);
			k = info.kind;
			c = k == SOLID ? Math.max(10, info.crossCost * wall10 / 100) : info.crossCost;
		}
		kind[idx] = k;
		cross[idx] = (short) Math.min(Short.MAX_VALUE, c);
		return k;
	}

	/** crossCost: для SOLID — множитель материала ×10 (умножается на поглощение), для AIR и PARTIAL — надбавка к шагу (у воды отрицательная). */
	private static StateInfo describe(BlockState state) {
		if (state.isAir()) return new StateInfo(AIR, 0);
		VoxelShape collision;
		VoxelShape outline;
		try {
			collision = state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
			outline = state.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
		} catch (Throwable t) {
			return new StateInfo(SOLID, 100);
		}
		if (Block.isShapeFullCube(collision)) {
			int factor;
			if (state.isIn(BlockTags.WOOL)) factor = 400;
			else if (state.isIn(BlockTags.LEAVES)) factor = 25;
			else if (state.isIn(BlockTags.IMPERMEABLE)) factor = 60; // стекло
			else if (state.isIn(BlockTags.ICE)) factor = 70;
			else factor = 100;
			return new StateInfo(SOLID, factor);
		}
		// Вода проводит звук лучше воздуха (звук уходит дальше), лава его глушит.
		FluidState fluid = state.getFluidState();
		if (!fluid.isEmpty() && collision.isEmpty()) {
			if (fluid.isIn(FluidTags.WATER)) return new StateInfo(AIR, -3);
			if (fluid.isIn(FluidTags.LAVA)) return new StateInfo(AIR, 15);
		}
		if (outline.isEmpty()) return new StateInfo(AIR, 0);
		double vol = 0;
		for (var box : collision.getBoundingBoxes()) {
			vol += box.getLengthX() * box.getLengthY() * box.getLengthZ();
		}
		return new StateInfo(PARTIAL, (int) Math.round(Math.min(1.0, vol) * 10));
	}

	private static VoxelShape shapeAt(World world, BlockPos pos) {
		try {
			BlockState state = world.getBlockState(pos);
			VoxelShape shape = state.getOutlineShape(world, pos);
			return shape.isEmpty() ? null : shape;
		} catch (Throwable t) {
			return null;
		}
	}

	private static int faceTowards(int dx, int dy, int dz) {
		if (dy < 0) return 0;
		if (dy > 0) return 1;
		if (dz < 0) return 2;
		if (dz > 0) return 3;
		if (dx < 0) return 4;
		return 5;
	}

	private static int index(int x, int y, int z, int side) {
		return x + side * (y + side * z);
	}

	private void ensureCapacity(int volume, int bucketCount) {
		if (stamp.length < volume) {
			stamp = new int[volume];
			dist = new int[volume];
			kind = new byte[volume];
			cross = new short[volume];
			generation = 0;
		}
		if (buckets.length < bucketCount) {
			IntArrayList[] nb = java.util.Arrays.copyOf(buckets, bucketCount);
			for (int i = buckets.length; i < bucketCount; i++) nb[i] = new IntArrayList();
			buckets = nb;
		}
	}

	private void clearBuckets(int from, int to) {
		for (int i = from; i <= to && i < buckets.length; i++) buckets[i].clear();
	}

	/** Быстрый доступ к блокам по чанкам; незагруженные чанки — непроходимая граница. */
	private static final class ChunkLookup {
		private final World world;
		private long lastKey = Long.MIN_VALUE;
		private WorldChunk last;

		ChunkLookup(World world) {
			this.world = world;
		}

		BlockState state(BlockPos pos) {
			if (world.isOutOfHeightLimit(pos.getY())) return null;
			int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
			long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
			if (key != lastKey) {
				lastKey = key;
				WorldChunk chunk = world.getChunk(cx, cz);
				last = chunk instanceof EmptyChunk ? null : chunk;
			}
			return last == null ? null : last.getBlockState(pos);
		}
	}
}
