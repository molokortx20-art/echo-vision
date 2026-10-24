package com.dragonyx.echolocation.echo;

import com.dragonyx.echolocation.config.EchoConfig;
import com.dragonyx.echolocation.sound.SoundJob;
import com.dragonyx.echolocation.sound.WaveResult;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Всё, что сейчас «подсвечено» эхом: грани блоков, формы неполных блоков, существа и источники звука. */
public final class EchoField {
	public static final EchoField INSTANCE = new EchoField();

	/** Длительность вспышки фронта волны, с. */
	public static final float FLASH = 0.18f;
	/** Сколько секунд виден значок источника звука. */
	public static final double SOURCE_LIFE = 0.9;

	public final Long2ObjectOpenHashMap<BlockEcho> blocks = new Long2ObjectOpenHashMap<>();
	public final Int2ObjectOpenHashMap<Echo> entities = new Int2ObjectOpenHashMap<>();
	public final List<SourceMark> sources = new ArrayList<>();

	public void clear() {
		blocks.clear();
		entities.clear();
		sources.clear();
	}

	/**
	 * Подсветка одной грани (или формы целиком) двумя волнами: [0] — последняя, [1] — предыдущая,
	 * чтобы новая волна не гасила ещё светящуюся старую.
	 * <p>
	 * Хранится не яркость грани, а сама волна: источник, время звука, дальность, сила и путь звука
	 * до четырёх углов грани по воздуху (по туннелям, в обход стен). По ним яркость и фронт считаются
	 * в каждой точке: на прямой видимости — точное расстояние (волна круглая), за поворотом — путь
	 * по туннелю, плавно, без разрывов между соседними гранями.
	 */
	public static final class Echo {
		/** Момент прихода фронта к центру грани — для сравнения волн и существ. */
		public final double[] arrival = {Double.NaN, Double.NaN};
		/** Яркость в центре грани (сила × затухание) — для сравнения волн. */
		public final float[] intensity = new float[2];
		public final int[] color = new int[2];
		public final float[] sx = new float[2], sy = new float[2], sz = new float[2];
		public final double[] start = new double[2];
		public final float[] range = new float[2];
		/** Сила звука без затухания с расстоянием. */
		public final float[] base = new float[2];
		/** Путь звука до углов грани (в порядке углов рендера); у формы неполного блока [0] — до центра клетки. */
		public final float[][] path = new float[2][4];

		/**
		 * @param euclid расстояние по прямой от источника до центра грани
		 * @param corners путь звука до углов грани: 4 значения с позиции {@code from}
		 */
		void offer(SoundJob job, float euclid, float[] corners, int from, int col, float speed, float dur) {
			float avg = (corners[from] + corners[from + 1] + corners[from + 2] + corners[from + 3]) * 0.25f;
			float d = effectiveDistance(euclid, avg);
			double arr = job.startTime + d / speed;
			float in = falloff(loudnessDistance(d, euclid), job.range) * job.intensity;
			if (Double.isNaN(arrival[0])) {
				set(0, job, arr, in, col, corners, from);
				return;
			}
			float existing = level(0, arr, dur, false);
			if (in < existing) return;
			if (Math.abs(arr - arrival[0]) < 0.05) {
				// Тот же фронт пришёл вторым путём — оставляем более ранний и яркий.
				if (arr < arrival[0] || in > intensity[0]) set(0, job, Math.min(arr, arrival[0]), Math.max(in, intensity[0]), col, corners, from);
				return;
			}
			arrival[1] = arrival[0];
			intensity[1] = intensity[0];
			color[1] = color[0];
			sx[1] = sx[0];
			sy[1] = sy[0];
			sz[1] = sz[0];
			start[1] = start[0];
			range[1] = range[0];
			base[1] = base[0];
			System.arraycopy(path[0], 0, path[1], 0, 4);
			set(0, job, arr, in, col, corners, from);
		}

		private void set(int k, SoundJob job, double arr, float in, int col, float[] corners, int from) {
			arrival[k] = arr;
			intensity[k] = in;
			color[k] = col;
			sx[k] = (float) job.x;
			sy[k] = (float) job.y;
			sz[k] = (float) job.z;
			start[k] = job.startTime;
			range[k] = job.range;
			base[k] = job.intensity;
			System.arraycopy(corners, from, path[k], 0, 4);
		}

		/** Яркость волны k в момент now в центре грани. */
		public float level(int k, double now, float dur, boolean flash) {
			if (Double.isNaN(arrival[k])) return 0f;
			return envelope((float) (now - arrival[k]), dur, flash) * intensity[k];
		}

		public float level(double now, float dur, boolean flash) {
			return Math.max(level(0, now, dur, flash), level(1, now, dur, flash));
		}

		public int colorAt(double now, float dur) {
			return level(0, now, dur, false) >= level(1, now, dur, false) ? color[0] : color[1];
		}

		boolean dead(double now, float dur) {
			// Запас в 1 с: у дальних вершин грани фронт приходит чуть позже, чем к центру.
			return !(now - arrival[0] < dur + 1) && !(now - arrival[1] < dur + 1);
		}
	}

	/** Доля «крюка» в обход препятствий, которая добавляется к затуханию: за каждым поворотом звук слабее. */
	public static final float TURN_LOSS = 0.6f;

	/**
	 * Расстояние, которое прошла волна до точки: путь по воздуху (по сетке блоков он длиннее прямой
	 * примерно на 8 %) сравнивается с прямой. Почти прямой путь — значит прямая видимость, и берём
	 * точное расстояние, чтобы волна была круглой; иначе — путь в обход, без скачка на границе.
	 */
	public static float effectiveDistance(float euclid, float path) {
		return Math.max(euclid, path - (1.2f + euclid * 0.09f));
	}

	/** Расстояние для затухания: обход препятствий глушит звук сильнее, чем такой же путь по прямой. */
	public static float loudnessDistance(float d, float euclid) {
		return d + (d - euclid) * TURN_LOSS;
	}

	/** Яркость через age секунд после прихода фронта: вспышка и плавное угасание. */
	public static float envelope(float age, float dur, boolean flash) {
		if (age < 0 || age >= dur) return 0f;
		float k = 1 - age / dur;
		float v = k * k;
		if (flash && age < FLASH) {
			float f = 1 - age / FLASH;
			v += 1.6f * f * f;
		}
		return v;
	}

	public static final class BlockEcho {
		public final long pos;
		/** 0–5 — грани по Direction.getId(), 6 — форма неполного блока целиком. */
		public final Echo[] slots = new Echo[7];
		/** Маски рёбер-контуров для граней 0–5. */
		public final byte[] edges = new byte[6];
		public VoxelShape shape;
		/** Квадраты модели блока для эхо-зрения: [0–5] по сторонам, [6] — без стороны. Сбрасывается новой волной. */
		public List<BakedQuad>[] quads;

		BlockEcho(long pos) {
			this.pos = pos;
		}
	}

	public record SourceMark(double x, double y, double z, double start, int color, float intensity) {}

	public void addWave(SoundJob job, Iterable<Entity> nearby) {
		EchoConfig cfg = EchoConfig.get();
		WaveResult r = job.result;
		float dur = cfg.echoDuration;
		float speed = cfg.soundSpeed;
		float range = job.range;
		int limit = cfg.maxBlocks;

		for (int i = 0; i < r.size; i++) {
			float d = r.dist[i];
			// Грань целиком за пределом звука не нужна, но с запасом в блок: её ближний край ещё может быть внутри.
			if (d > range + 1.0f) continue;
			long p = r.pos[i];
			BlockEcho be = blocks.get(p);
			if (be == null) {
				if (blocks.size() >= limit) continue;
				be = new BlockEcho(p);
				blocks.put(p, be);
			}
			int f = r.face[i];
			be.quads = null; // блок мог измениться с прошлой волны
			if (f == 6) {
				if (r.shape[i] != null) be.shape = r.shape[i];
			} else {
				be.edges[f] = r.edges[i];
			}
			Echo e = be.slots[f];
			if (e == null) e = be.slots[f] = new Echo();
			int color = cfg.colorMode == EchoConfig.ColorMode.DISTANCE ? EchoColors.byDistance(d, range) : job.color;
			e.offer(job, distanceToFace(p, f, job), r.corners, i * 4, color, speed, dur);
		}

		if (cfg.showEntities) {
			for (Entity entity : nearby) {
				BlockPos bp = entity.getBlockPos();
				float d = r.air.get(bp.asLong());
				if (d < 0) d = r.air.get(bp.up().asLong());
				if (d < 0) {
					Box b = entity.getBoundingBox();
					double dx = b.getCenter().x - job.x, dy = b.getCenter().y - job.y, dz = b.getCenter().z - job.z;
					// Источник звука — само существо (рычание, шаги).
					if (dx * dx + dy * dy + dz * dz < Math.max(1.0, b.getLengthX() * b.getLengthX())) d = 0;
				}
				if (d < 0) continue;
				float in = falloff(d, range) * job.intensity;
				if (in <= 0.02f) continue;
				Echo e = entities.computeIfAbsent(entity.getId(), id -> new Echo());
				int color = cfg.colorMode == EchoConfig.ColorMode.DISTANCE ? EchoColors.byDistance(d, range) : job.color;
				float[] path = {d, d, d, d};
				e.offer(job, d, path, 0, color, speed, dur * 1.2f);
			}
		}

		if (cfg.showSources) {
			sources.add(new SourceMark(job.x, job.y, job.z, job.startTime, job.color, job.intensity));
		}
	}

	/** Расстояние по прямой от источника до центра грани (face 6 — до центра блока). */
	private static float distanceToFace(long pos, int face, SoundJob job) {
		double cx = BlockPos.unpackLongX(pos) + 0.5, cy = BlockPos.unpackLongY(pos) + 0.5, cz = BlockPos.unpackLongZ(pos) + 0.5;
		switch (face) {
			case 0 -> cy -= 0.5;
			case 1 -> cy += 0.5;
			case 2 -> cz -= 0.5;
			case 3 -> cz += 0.5;
			case 4 -> cx -= 0.5;
			case 5 -> cx += 0.5;
			default -> {}
		}
		double dx = cx - job.x, dy = cy - job.y, dz = cz - job.z;
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** Затухание с расстоянием: у ближних поверхностей почти полная яркость, к краю дальности — ноль. */
	public static float falloff(float d, float range) {
		float k = 1f - d / range;
		if (k <= 0) return 0;
		return (float) Math.pow(k, 0.7);
	}

	public void prune() {
		double now = EchoClock.now();
		float dur = EchoConfig.get().echoDuration;
		ObjectIterator<Long2ObjectMap.Entry<BlockEcho>> it = blocks.long2ObjectEntrySet().fastIterator();
		while (it.hasNext()) {
			BlockEcho be = it.next().getValue();
			boolean alive = false;
			for (int i = 0; i < 7; i++) {
				Echo e = be.slots[i];
				if (e == null) continue;
				if (e.dead(now, dur)) {
					be.slots[i] = null;
				} else {
					alive = true;
				}
			}
			if (!alive) it.remove();
		}
		ObjectIterator<Int2ObjectMap.Entry<Echo>> eit = entities.int2ObjectEntrySet().fastIterator();
		while (eit.hasNext()) {
			if (eit.next().getValue().dead(now, dur * 1.2f)) eit.remove();
		}
		Iterator<SourceMark> sit = sources.iterator();
		while (sit.hasNext()) {
			if (now - sit.next().start() > SOURCE_LIFE) sit.remove();
		}
	}

	/** Яркость эха блока (по самой яркой из его граней) — для блок-сущностей вроде сундуков. */
	public float blockLevel(long pos) {
		BlockEcho be = blocks.get(pos);
		if (be == null) return 0f;
		EchoConfig cfg = EchoConfig.get();
		double now = EchoClock.now();
		float best = 0f;
		for (Echo e : be.slots) {
			if (e != null) best = Math.max(best, e.level(now, cfg.echoDuration, cfg.wavefrontFlash));
		}
		return best * cfg.brightness;
	}

	/** Яркость обводки существа сейчас (0, если его не слышно). */
	public float entityLevel(int id) {
		Echo e = entities.get(id);
		if (e == null) return 0f;
		EchoConfig cfg = EchoConfig.get();
		return e.level(EchoClock.now(), cfg.echoDuration * 1.2f, cfg.wavefrontFlash);
	}

	public int entityColor(int id) {
		Echo e = entities.get(id);
		if (e == null) return 0xFFFFFF;
		return e.colorAt(EchoClock.now(), EchoConfig.get().echoDuration * 1.2f);
	}
}
