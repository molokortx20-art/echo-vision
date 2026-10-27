package com.dragonyx.echolocation.render;

import com.dragonyx.echolocation.EchoMod;
import com.dragonyx.echolocation.config.EchoConfig;
import com.dragonyx.echolocation.echo.EchoClock;
import com.dragonyx.echolocation.echo.EchoField;
import com.dragonyx.echolocation.echo.EchoMaterials;
import com.dragonyx.echolocation.echo.EchoStats;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3fc;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Рисует эхо-зрение: открытые грани, до которых дошёл звук, проступают из тьмы своей текстурой
 * в оттенках серого, а по ним бежит светящееся кольцо фронта волны.
 * <p>
 * Яркость считается в каждой вершине по её собственному расстоянию до источника,
 * поэтому фронт идёт по граням плавно, а не скачками по блокам.
 * Смешивание аддитивное, без тумана: эхо видно сквозь слепоту, но заслонённое тёмными блоками скрыто.
 */
public final class EchoRenderer {
	private static final RenderPipeline PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
			.withLocation(Identifier.of(EchoMod.MOD_ID, "pipeline/echo"))
			.withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
			.withBlend(BlendFunction.LIGHTNING)
			.withCull(false)
			.withDepthWrite(false)
			.build());
	private static final RenderLayer LAYER = RenderLayer.of("echolocation_echo",
		RenderSetup.builder(PIPELINE).expectedBufferSize(1 << 20).build());

	/** Эхо-зрение: настоящие модели открытых блоков через свой шейдер (серый + линии деталей текстуры). */
	private static final RenderPipeline SURFACE_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
			.withLocation(Identifier.of(EchoMod.MOD_ID, "pipeline/echo_surface"))
			.withVertexShader(Identifier.of(EchoMod.MOD_ID, "core/echo_surface"))
			.withFragmentShader(Identifier.of(EchoMod.MOD_ID, "core/echo_surface"))
			.withSampler("Sampler0")
			// UV2 — не свет: координаты точки на грани и маска углов для контуров (см. surfaceQuad).
			.withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_LIGHT, VertexFormat.DrawMode.QUADS)
			.withBlend(BlendFunction.ADDITIVE)
			.withCull(false)
			.withDepthWrite(false)
			.withDepthBias(-1.0f, -10.0f)
			.build());
	private static final RenderLayer SURFACE_LAYER = RenderLayer.of("echolocation_surface",
		RenderSetup.builder(SURFACE_PIPELINE)
			.texture("Sampler0", SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, RenderLayers.BLOCK_SAMPLER)
			.textureTransform(EchoLookUniform.TRANSFORM)
			.expectedBufferSize(1 << 20)
			.build());
	/** Затенение граней как в ванилле: низ, верх, север, юг, запад, восток. */
	private static final float[] SHADE = {0.5f, 1.0f, 0.8f, 0.8f, 0.6f, 0.6f};
	private static final Random MODEL_RANDOM = Random.create();
	/** Better Block Entities рисует сундуки и прочие блок-сущности как часть мира, а не отдельным рендером. */
	private static final boolean BAKED_BLOCK_ENTITIES = FabricLoader.getInstance().isModLoaded("betterblockentities");

	private static BufferAllocator surfaceAllocator;
	private static VertexConsumerProvider.Immediate surfaceImmediate;
	private static VertexConsumer surface;
	/** Множитель яркости линий (значков источников): они лишь подчёркивают поверхности. */
	private static final float LINE_GAIN = 0.45f;
	/** Полуширина линий значков источников, блоков. */
	private static final float HALF_WIDTH = 0.025f;

	/** Отступ заливки и кольца от грани блока, чтобы не мерцать вместе с ней. */
	private static final float EPS = 0.004f;
	/** Радиус шара, описанного вокруг блока. */
	private static final float BLOCK_RADIUS = 0.87f;
	private static final Vector3f FORWARD = new Vector3f();
	/** Яркость вершин квадрата (переиспользуется: отрисовка идёт в одном потоке). */
	private static final float[] LV = new float[4], PX = new float[4], PY = new float[4], PZ = new float[4];
	private static final int CIRCLE_SEGMENTS = 28;

	private static BufferAllocator allocator;
	private static VertexConsumerProvider.Immediate immediate;

	// Состояние текущего кадра.
	private static VertexConsumer vc;
	private static Matrix4f mat;
	private static double camX, camY, camZ;
	private static double now;
	private static float dur, speed, brightness, ringWidth;
	private static boolean flash;
	/** Отражающая способность материала текущего блока (1 — без учёта материалов). */
	private static float matGain = 1f;
	/**
	 * Режим «один цвет»: поверхности красятся текущим цветом из настроек, а не цветом, с которым родилась
	 * волна, — так смена цвета в настройках видна сразу. Кольцо фронта и значки — цветом линий.
	 */
	private static boolean single;
	private static int singleColor, lineColor;
	/** Неровность края волны, блоков (0 — ровный круг). */
	private static float noiseAmp;
	/** Ширина кольца фронта на текущей грани, в единицах s. */
	private static float band;

	/** Вершины текущей грани (координаты относительно камеры). */
	private static final float[] qx = new float[4], qy = new float[4], qz = new float[4];
	/** Текущая грань (0–5, 6 — форма неполного блока) и угол её блока относительно камеры. */
	private static int curFace;
	private static float curX, curY, curZ;
	/** Сетка дробления грани для кольца: координаты и «сколько фронт прошёл» в узлах. */
	private static final int MAX_SPLIT = 8;
	private static final float[] gx = new float[(MAX_SPLIT + 1) * (MAX_SPLIT + 1)];
	private static final float[] gy = new float[gx.length], gz = new float[gx.length], gs = new float[gx.length];
	// Буферы отсечения многоугольника.
	private static final float[] cx = new float[8], cy = new float[8], cz = new float[8], cs = new float[8];
	private static final float[] tx = new float[8], ty = new float[8], tz = new float[8], ts = new float[8];

	private EchoRenderer() {}

	public static void init() {
		// Загружает класс заранее, чтобы пайплайн попал в общий список до загрузки шейдеров.
	}

	public static void render(WorldRenderContext ctx) {
		long t0 = EchoStats.ENABLED ? System.nanoTime() : 0;
		renderEchoes(ctx);
		if (EchoStats.ENABLED) EchoStats.frame(System.nanoTime() - t0);
	}

	private static void renderEchoes(WorldRenderContext ctx) {
		EchoConfig cfg = EchoConfig.get();
		EchoField field = EchoField.INSTANCE;
		if (!cfg.echoEnabled || (field.blocks.isEmpty() && field.sources.isEmpty())) {
			now = EchoClock.now();
			updateReach(0f);
			return;
		}

		if (immediate == null) {
			allocator = new BufferAllocator(1 << 21);
			immediate = VertexConsumerProvider.immediate(allocator);
			surfaceAllocator = new BufferAllocator(1 << 21);
			surfaceImmediate = VertexConsumerProvider.immediate(surfaceAllocator);
		}
		ClientWorld world = MinecraftClient.getInstance().world;
		if (world == null) return;

		Vec3d cam = ctx.worldState().cameraRenderState.pos;
		camX = cam.x;
		camY = cam.y;
		camZ = cam.z;
		Vector3f fwd = FORWARD.set(0, 0, -1).rotate(ctx.worldState().cameraRenderState.orientation);
		float cosView = viewCone();
		now = EchoClock.now();
		dur = cfg.echoDuration;
		speed = cfg.soundSpeed;
		brightness = cfg.brightness;
		ringWidth = cfg.ringWidth;
		flash = cfg.wavefrontFlash;
		matGain = 1f;
		noiseAmp = cfg.waveNoise * 1.5f;
		single = cfg.colorMode == EchoConfig.ColorMode.SINGLE;
		singleColor = cfg.echoColor;
		lineColor = cfg.lineColor;

		mat =ctx.matrices().peek().getPositionMatrix();
		vc = immediate.getBuffer(LAYER);
		surface = surfaceImmediate.getBuffer(SURFACE_LAYER);

		float farthest = 0f;
		for (EchoField.BlockEcho be : field.blocks.values()) {
			// Сначала дешёвая проверка: блок, на котором ничего не светится, пропускаем сразу.
			if (!anyLit(be)) continue;
			int bx = BlockPos.unpackLongX(be.pos), by = BlockPos.unpackLongY(be.pos), bz = BlockPos.unpackLongZ(be.pos);
			float rx = (float) (bx - camX), ry = (float) (by - camY), rz = (float) (bz - camZ);
			float len = dist(rx + 0.5f, ry + 0.5f, rz + 0.5f);
			if (len > farthest) farthest = len;
			// Блоки вне поля зрения не рисуем (конус чуть шире экрана, с запасом на размер блока).
			if (!inView(rx + 0.5f, ry + 0.5f, rz + 0.5f, len, fwd, cosView)) continue;
			if (cfg.materialEcho) {
				refreshState(world, be);
				matGain = be.material;
			}

			for (int f = 0; f < 6; f++) {
				EchoField.Echo e = be.slots[f];
				// Грань, повёрнутая от камеры, не видна — её не рисуем.
				if (e == null || !lit(e) || !facesCamera(f, rx, ry, rz)) continue;
				int color = single ? singleColor : e.colorAt(now, dur);
				setFace(f, rx, ry, rz);
				beginFace(f, rx, ry, rz);
				surfaceFace(world, be, f, e, rx, ry, rz, color);
				if (cfg.wavefrontRing) ring(e, single ? lineColor : color);
			}

			EchoField.Echo whole = be.slots[6];
			if (whole != null && be.shape != null && lit(whole)) {
				beginFace(6, rx, ry, rz);
				surfaceWhole(world, be, whole, rx, ry, rz, single ? singleColor : whole.colorAt(now, dur));
			}
		}

		matGain = 1f;
		for (EchoField.SourceMark s : field.sources) {
			double t = (now - s.start()) / EchoField.SOURCE_LIFE;
			if (t < 0 || t > 1) continue;
			// Свои шаги и щелчки: кольцо вокруг головы видно с ребра и легло бы полосой через весь экран.
			double dx = s.x() - camX, dy = s.y() - camY, dz = s.z() - camZ;
			if (dx * dx + dy * dy + dz * dz < 2.5 * 2.5) continue;
			sourceRing((float) (s.x() - camX), (float) (s.y() - camY), (float) (s.z() - camZ),
				(float) (0.15 + 0.85 * t), single ? lineColor : s.color(), (float) (1 - t) * (float) (1 - t) * s.intensity() * brightness);
		}

		updateReach(farthest);

		long tDraw = EchoStats.ENABLED ? System.nanoTime() : 0;
		surfaceImmediate.draw();
		immediate.draw();
		if (EchoStats.ENABLED) {
			EchoStats.draw(System.nanoTime() - tDraw);
			EchoStats.vertices(vertices);
		}
		vertices = 0;
		vc = null;
		surface = null;
	}

	// ---------- отсечение ----------

	/** Косинус половины угла конуса, в котором что-то видно на экране (диагональ кадра с запасом); -1 — не отсекать. */
	private static float viewCone() {
		MinecraftClient client = MinecraftClient.getInstance();
		int w = client.getWindow().getFramebufferWidth(), h = client.getWindow().getFramebufferHeight();
		if (w <= 0 || h <= 0) return -1f;
		double aspect = (double) w / h;
		// Запас 1.35 по тангенсу — на эффекты поля зрения (бег, скорость) и моды приближения.
		double tanHalf = Math.tan(Math.toRadians(client.options.getFov().getValue()) * 0.5) * 1.35;
		double tanDiag = tanHalf * Math.sqrt(1 + aspect * aspect);
		return (float) (1 / Math.sqrt(1 + tanDiag * tanDiag));
	}

	/**
	 * Шар радиусом в полблока по диагонали вокруг точки c (относительно камеры) хоть краем в конусе обзора.
	 * Без тригонометрии: если даже самая «удачная» точка шара не проходит, блок точно не виден.
	 */
	private static boolean inView(float cx, float cy, float cz, float len, Vector3f fwd, float cosView) {
		if (cosView <= 0 || len < 2.5f) return true;
		float d = cx * fwd.x + cy * fwd.y + cz * fwd.z;
		return d + BLOCK_RADIUS >= (len - BLOCK_RADIUS) * cosView;
	}

	private static boolean anyLit(EchoField.BlockEcho be) {
		for (EchoField.Echo e : be.slots) {
			if (e != null && lit(e)) return true;
		}
		return false;
	}

	// ---------- дальность эха для отсечения чанков ----------

	/** На сколько блоков в секунду сжимается дальность, когда дальние эхо погасли. */
	private static final float REACH_SHRINK = 24f;
	private static volatile float reach;
	private static double reachTime;

	/**
	 * Дальность самого далёкого светящегося эха от камеры. Растёт сразу, сжимается плавно — чтобы
	 * Sodium не дёргал дальность поиска чанков каждый кадр.
	 */
	private static void updateReach(float farthest) {
		double dt = Math.min(0.5, Math.max(0, now - reachTime));
		reachTime = now;
		reach = Math.max(farthest, reach - (float) (dt * REACH_SHRINK));
	}

	/**
	 * Докуда в слепоте нужен мир: дальше самого далёкого эха всё равно сплошная тьма. Ближе — нужен:
	 * без его глубины эхо за стенами просвечивало бы. Запас — на фронт, который ещё идёт к стенам.
	 */
	public static float worldReach() {
		EchoConfig cfg = EchoConfig.get();
		float min = Math.max(32f, cfg.visionRadius + 16f);
		if (!cfg.echoEnabled) return min;
		return Math.max(min, reach + 24f);
	}

	/** Грань светится хоть одной волной (с запасом: углы грани освещаются чуть раньше и позже центра). */
	private static boolean lit(EchoField.Echo e) {
		float slack = (2.5f + noiseAmp) / speed;
		for (int k = 0; k < 2; k++) {
			double age = now - e.arrival[k];
			if (age >= -slack && age <= dur + slack) return true; // NaN не проходит
		}
		return false;
	}

	// ---------- эхо-зрение: модели блоков ----------

	/**
	 * Сверяет блок с миром после новой волны: блок мог смениться. Новый блок — модель соберётся заново,
	 * и материал пересчитается.
	 */
	private static void refreshState(ClientWorld world, EchoField.BlockEcho be) {
		if (be.state != null && !be.stale) return;
		be.stale = false;
		BlockState state = world.getBlockState(BlockPos.fromLong(be.pos));
		if (state == be.state) return;
		be.state = state;
		be.quads = null;
		be.material = EchoMaterials.reflectivity(state);
	}

	@SuppressWarnings("unchecked")
	private static List<BakedQuad>[] quadsOf(ClientWorld world, EchoField.BlockEcho be) {
		refreshState(world, be);
		if (be.quads != null) return be.quads;
		BlockState state = be.state;
		List<BakedQuad>[] out = new List[7];
		for (int i = 0; i < 7; i++) out[i] = new ArrayList<>();
		be.particle = null;
		if (state.getRenderType() != BlockRenderType.INVISIBLE) {
			BlockStateModel model = MinecraftClient.getInstance().getBlockRenderManager().getModel(state);
			MODEL_RANDOM.setSeed(state.getRenderingSeed(BlockPos.fromLong(be.pos)));
			boolean any = false;
			for (BlockModelPart part : model.getParts(MODEL_RANDOM)) {
				for (Direction d : Direction.values()) any |= out[d.ordinal()].addAll(part.getQuads(d));
				any |= out[6].addAll(part.getQuads(null));
			}
			// Блок-сущность без модели блока (сундук, табличка): обычно её рисует свой рендер, и эхо
			// ловит его в EchoEntityLayers. Но Better Block Entities вшивает их в сам мир — тогда
			// рисуем форму блока его же текстурой (частицы), иначе сундук остался бы чёрным кубом.
			if (!any && state.hasBlockEntity() && BAKED_BLOCK_ENTITIES) be.particle = model.particleSprite();
		}
		be.quads = out;
		return out;
	}

	/** Форма блок-сущности текстурой частиц — для Better Block Entities (см. quadsOf). */
	private static void surfaceShape(EchoField.BlockEcho be, EchoField.Echo e, float x, float y, float z, int color) {
		Sprite s = be.particle;
		for (Box b : be.shape.getBoundingBoxes()) {
			float x0 = x + (float) b.minX, x1 = x + (float) b.maxX;
			float y0 = y + (float) b.minY, y1 = y + (float) b.maxY;
			float z0 = z + (float) b.minZ, z1 = z + (float) b.maxZ;
			// Текстура ложится по координатам внутри блока, как у обычных граней.
			shapeQuad(s, e, color, 0.5f, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, (float) b.minX, (float) b.maxX, (float) b.minZ, (float) b.maxZ);
			shapeQuad(s, e, color, 1.0f, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, (float) b.minX, (float) b.maxX, (float) b.minZ, (float) b.maxZ);
			shapeQuad(s, e, color, 0.8f, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, (float) b.minX, (float) b.maxX, (float) b.minY, (float) b.maxY);
			shapeQuad(s, e, color, 0.8f, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, (float) b.minX, (float) b.maxX, (float) b.minY, (float) b.maxY);
			shapeQuad(s, e, color, 0.6f, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, (float) b.minZ, (float) b.maxZ, (float) b.minY, (float) b.maxY);
			shapeQuad(s, e, color, 0.6f, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, (float) b.minZ, (float) b.maxZ, (float) b.minY, (float) b.maxY);
		}
	}

	private static void shapeQuad(Sprite s, EchoField.Echo e, int color, float shade,
								  float ax, float ay, float az, float bx, float by, float bz,
								  float cx2, float cy2, float cz2, float dx, float dy, float dz,
								  float u0, float u1, float v0, float v1) {
		float[] px = {ax, bx, cx2, dx}, py = {ay, by, cy2, dy}, pz = {az, bz, cz2, dz};
		float[] us = {u0, u1, u1, u0}, vs = {v0, v0, v1, v1};
		float[] lv = LV;
		boolean any = false;
		for (int j = 0; j < 4; j++) {
			lv[j] = pointLevel(e, px[j], py[j], pz[j]);
			any |= lv[j] >= 0.003f;
		}
		if (!any) return;
		float r = ((color >> 16) & 0xFF) / 255f * shade, g = ((color >> 8) & 0xFF) / 255f * shade, b = (color & 0xFF) / 255f * shade;
		int rgb = (int) (r * 255) << 16 | (int) (g * 255) << 8 | (int) (b * 255);
		for (int j = 0; j < 4; j++) {
			int a = (int) (Math.min(1f, lv[j] * 0.5f) * 255);
			surface.vertex(mat, px[j], py[j], pz[j])
				.texture(s.getFrameU(us[j]), s.getFrameV(1 - vs[j]))
				.color(a << 24 | rgb)
				.light(0, 0);
		}
	}

	/** Открытая грань полного блока: квадраты модели с этой стороны. */
	private static void surfaceFace(ClientWorld world, EchoField.BlockEcho be, int f, EchoField.Echo e,
									float x, float y, float z, int color) {
		List<BakedQuad>[] quads = quadsOf(world, be);
		int mask = be.edges[f];
		for (BakedQuad q : quads[f]) surfaceQuad(q, e, x, y, z, color, f, mask);
		// У некоторых моделей грани лежат в общем списке без стороны.
		if (quads[f].isEmpty()) {
			for (BakedQuad q : quads[6]) if (q.face().ordinal() == f) surfaceQuad(q, e, x, y, z, color, f, mask);
		}
	}

	/** Неполный блок (трава, забор, ступени): вся модель. */
	private static void surfaceWhole(ClientWorld world, EchoField.BlockEcho be, EchoField.Echo e, float x, float y, float z, int color) {
		List<BakedQuad>[] quads = quadsOf(world, be);
		if (be.particle != null) {
			surfaceShape(be, e, x, y, z, color);
			return;
		}
		for (List<BakedQuad> list : quads) {
			for (BakedQuad q : list) surfaceQuad(q, e, x, y, z, color, 6, 0);
		}
	}

	/**
	 * Координаты точки p (внутри блока, 0–1) на грани f — как у углов рендера: грани по Y — (X, Z),
	 * по Z — (X, Y), по X — (Z, Y); маска углов из SoundPropagator идёт в том же порядке (−U, +U, −V, +V).
	 * Упаковано в UV2: u·256 и маска в старших битах, v·256. У неполных блоков (f = 6) контуров нет.
	 */
	private static int packU(int f, float u, int mask) {
		return f == 6 ? 0 : Math.round(clamp01(u) * 256) | mask << 9;
	}

	private static int packV(int f, float v) {
		return f == 6 ? 0 : Math.round(clamp01(v) * 256);
	}

	private static void surfaceQuad(BakedQuad q, EchoField.Echo e, float x, float y, float z, int color, int f, int mask) {
		float[] lv = LV, px = PX, py = PY, pz = PZ;
		boolean any = false;
		for (int j = 0; j < 4; j++) {
			Vector3fc p = q.getPosition(j);
			px[j] = x + p.x();
			py[j] = y + p.y();
			pz[j] = z + p.z();
			lv[j] = pointLevel(e, px[j], py[j], pz[j]);
			any |= lv[j] >= 0.003f;
		}
		if (!any) return;
		vertices += 4;
		float shade = q.shade() ? SHADE[q.face().ordinal()] : 1f;
		float r = ((color >> 16) & 0xFF) / 255f * shade, g = ((color >> 8) & 0xFF) / 255f * shade, b = (color & 0xFF) / 255f * shade;
		int rgb = (int) (r * 255) << 16 | (int) (g * 255) << 8 | (int) (b * 255);
		for (int j = 0; j < 4; j++) {
			long uv = q.getTexcoords(j);
			int a = (int) (Math.min(1f, lv[j] * 0.5f) * 255);
			float lx = px[j] - x, ly = py[j] - y, lz = pz[j] - z;
			float u = f < 4 ? lx : lz, v = f < 2 ? lz : ly;
			surface.vertex(mat, px[j], py[j], pz[j])
				.texture(Vector2f.getX(uv), Vector2f.getY(uv))
				.color(a << 24 | rgb)
				.light(packU(f, u, mask), packV(f, v));
		}
	}

	// ---------- геометрия грани ----------

	/** Заполняет qx/qy/qz четырьмя углами грани f блока с относительными координатами (x, y, z). */
	private static void setFace(int f, float x, float y, float z) {
		switch (f) {
			case 0 -> quadCorners(x, y - EPS, z, x + 1, y - EPS, z, x + 1, y - EPS, z + 1, x, y - EPS, z + 1);
			case 1 -> quadCorners(x, y + 1 + EPS, z, x + 1, y + 1 + EPS, z, x + 1, y + 1 + EPS, z + 1, x, y + 1 + EPS, z + 1);
			case 2 -> quadCorners(x, y, z - EPS, x + 1, y, z - EPS, x + 1, y + 1, z - EPS, x, y + 1, z - EPS);
			case 3 -> quadCorners(x, y, z + 1 + EPS, x + 1, y, z + 1 + EPS, x + 1, y + 1, z + 1 + EPS, x, y + 1, z + 1 + EPS);
			case 4 -> quadCorners(x - EPS, y, z, x - EPS, y, z + 1, x - EPS, y + 1, z + 1, x - EPS, y + 1, z);
			default -> quadCorners(x + 1 + EPS, y, z, x + 1 + EPS, y, z + 1, x + 1 + EPS, y + 1, z + 1, x + 1 + EPS, y + 1, z);
		}
	}

	private static void quadCorners(float x0, float y0, float z0, float x1, float y1, float z1,
									float x2, float y2, float z2, float x3, float y3, float z3) {
		qx[0] = x0; qy[0] = y0; qz[0] = z0;
		qx[1] = x1; qy[1] = y1; qz[1] = z1;
		qx[2] = x2; qy[2] = y2; qz[2] = z2;
		qx[3] = x3; qy[3] = y3; qz[3] = z3;
	}

	/** Камера в начале координат: грань видна, только если камера с её внешней стороны. */
	private static boolean facesCamera(int f, float x, float y, float z) {
		return switch (f) {
			case 0 -> y > 0;
			case 1 -> y + 1 < 0;
			case 2 -> z > 0;
			case 3 -> z + 1 < 0;
			case 4 -> x > 0;
			default -> x + 1 < 0;
		};
	}

	// ---------- волны в вершинах ----------

	/** Запоминает, какая грань какого блока сейчас рисуется: от этого зависит, как читать путь звука по углам. */
	private static void beginFace(int f, float x, float y, float z) {
		curFace = f;
		curX = x;
		curY = y;
		curZ = z;
	}

	/** Прямое расстояние от источника волны k до точки p (камера в начале координат). */
	private static float euclidTo(EchoField.Echo e, int k, float px, float py, float pz) {
		return dist(px - (float) (e.sx[k] - camX), py - (float) (e.sy[k] - camY), pz - (float) (e.sz[k] - camZ));
	}

	/**
	 * Путь звука до точки p текущей грани: на прямой видимости — точное расстояние (волна круглая),
	 * за поворотом — путь по воздуху, плавно интерполированный между углами грани. Соседние грани
	 * делят углы, поэтому фронт идёт по туннелям без разрывов.
	 */
	private static float waveDistance(EchoField.Echo e, int k, float px, float py, float pz, float euclid) {
		float[] c = e.path[k];
		float path;
		if (curFace == 6) {
			path = c[0] + dist(px - curX - 0.5f, py - curY - 0.5f, pz - curZ - 0.5f);
		} else {
			float lx = clamp01(px - curX), ly = clamp01(py - curY), lz = clamp01(pz - curZ);
			float u, v;
			if (curFace < 2) { u = lx; v = lz; }
			else if (curFace < 4) { u = lx; v = ly; }
			else { u = lz; v = ly; }
			path = c[0] * (1 - u) * (1 - v) + c[1] * u * (1 - v) + c[2] * u * v + c[3] * (1 - u) * v;
		}
		float d = EchoField.effectiveDistance(euclid, path);
		if (noiseAmp > 0) d += noiseAmp * (2 * edgeNoise(e, k, px, py, pz) - 1);
		return d;
	}

	// ---------- неровный край волны ----------

	/** Частота шума: пятна размером в пару блоков. */
	private static final float NOISE_FREQ = 0.43f;

	/**
	 * Плавный шум 0–1 в точке мира: у каждой волны свой узор (зерно — её источник), поэтому край
	 * идёт неровными «языками», но одинаково на соседних гранях и без мерцания между кадрами.
	 */
	private static float edgeNoise(EchoField.Echo e, int k, float px, float py, float pz) {
		float x = (float) (px + camX) * NOISE_FREQ, y = (float) (py + camY) * NOISE_FREQ, z = (float) (pz + camZ) * NOISE_FREQ;
		int seed = Float.floatToIntBits(e.sx[k] * 0.071f) * 31 + Float.floatToIntBits(e.sy[k] * 0.071f) * 17
			+ Float.floatToIntBits(e.sz[k] * 0.071f);
		int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
		float fx = smooth(x - x0), fy = smooth(y - y0), fz = smooth(z - z0);
		float a = lerp(hash(x0, y0, z0, seed), hash(x0 + 1, y0, z0, seed), fx);
		float b = lerp(hash(x0, y0 + 1, z0, seed), hash(x0 + 1, y0 + 1, z0, seed), fx);
		float c = lerp(hash(x0, y0, z0 + 1, seed), hash(x0 + 1, y0, z0 + 1, seed), fx);
		float d = lerp(hash(x0, y0 + 1, z0 + 1, seed), hash(x0 + 1, y0 + 1, z0 + 1, seed), fx);
		return lerp(lerp(a, b, fy), lerp(c, d, fy), fz);
	}

	private static float hash(int x, int y, int z, int seed) {
		int h = seed ^ x * 0x27d4eb2d ^ y * 0x165667b1 ^ z * 0x5bd1e995;
		h ^= h >>> 15;
		h *= 0x2c1b3c6d;
		h ^= h >>> 12;
		return (h & 0xFFFFFF) / (float) 0xFFFFFF;
	}

	private static float smooth(float t) {
		return t * t * (3 - 2 * t);
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	/** Сколько блоков фронт волны k уже прошёл за точкой p (отрицательно — ещё не дошёл). */
	private static float behindFront(EchoField.Echo e, int k, float px, float py, float pz) {
		if (Double.isNaN(e.arrival[k])) return Float.NEGATIVE_INFINITY;
		return (float) ((now - e.start[k]) * speed) - waveDistance(e, k, px, py, pz, euclidTo(e, k, px, py, pz));
	}

	/**
	 * Яркость точки p текущей грани: вспышка и угасание — по моменту прихода фронта в эту точку,
	 * затухание — по пройденному пути, причём обход препятствий глушит сильнее прямого пути.
	 */
	private static float pointLevel(EchoField.Echo e, float px, float py, float pz) {
		float best = 0f;
		float slack = (2.5f + noiseAmp) / speed;
		for (int k = 0; k < 2; k++) {
			// Волна уже погасла на всей грани (или ещё не дошла) — не считаем расстояния. NaN тоже отсеивается.
			double age0 = now - e.arrival[k];
			if (!(age0 >= -slack && age0 <= dur + slack)) continue;
			float euclid = euclidTo(e, k, px, py, pz);
			float d = waveDistance(e, k, px, py, pz, euclid);
			float age = (float) (now - e.start[k]) - d / speed;
			float v = EchoField.envelope(age, dur, flash)
				* EchoField.falloff(EchoField.loudnessDistance(d, euclid), e.range[k]) * e.base[k];
			if (v > best) best = v;
		}
		return best * brightness * matGain;
	}

	private static float clamp01(float v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}

	// ---------- линии ----------

	/** Отрезок как лента, развёрнутая к камере, чуть подвинутая к ней, чтобы не утонуть в соседних гранях. */
	private static void line(float ax, float ay, float az, float bx, float by, float bz, float la, float lb, int color) {
		if (la < 0.003f && lb < 0.003f) return;
		float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
		float md = dist(mx, my, mz);
		if (md < 1e-4f) return;
		float dx = bx - ax, dy = by - ay, dz = bz - az;
		// Ширина в мире, но не тоньше ~1.5 пикселя вдали.
		float hw = Math.max(HALF_WIDTH, md * 0.0012f);
		float px = dy * mz - dz * my, py = dz * mx - dx * mz, pz = dx * my - dy * mx;
		float pl = dist(px, py, pz);
		if (pl < 1e-6f) return;
		px *= hw / pl;
		py *= hw / pl;
		pz *= hw / pl;
		float pull = (0.02f + md * 0.002f) / md;
		ax -= ax * pull; ay -= ay * pull; az -= az * pull;
		bx -= bx * pull; by -= by * pull; bz -= bz * pull;

		int ca = rgba(color, la * LINE_GAIN), cb = rgba(color, lb * LINE_GAIN);
		vertex(ax - px, ay - py, az - pz, ca);
		vertex(ax + px, ay + py, az + pz, ca);
		vertex(bx + px, by + py, bz + pz, cb);
		vertex(ax - px, ay - py, az - pz, ca);
		vertex(bx + px, by + py, bz + pz, cb);
		vertex(bx - px, by - py, bz - pz, cb);
	}


	// ---------- кольцо фронта волны ----------

	/**
	 * Полоса грани, где фронт прошёл от 0 до ширины кольца назад: яркая кромка с мягким хвостом.
	 * Грань, по которой сейчас идёт фронт, дробится на сетку (чем ближе к камере, тем мельче) —
	 * так кольцо остаётся окружностью, а не многоугольником из отрезков по блоку.
	 */
	private static void ring(EchoField.Echo e, int color) {
		float mx = (qx[0] + qx[2]) * 0.5f, my = (qy[0] + qy[2]) * 0.5f, mz = (qz[0] + qz[2]) * 0.5f;
		float camDist = dist(mx, my, mz);
		int n = camDist < 10 ? MAX_SPLIT : camDist < 24 ? 4 : camDist < 48 ? 2 : 1;
		for (int k = 0; k < 2; k++) {
			if (Double.isNaN(e.arrival[k])) continue;
			// Фронт далеко от центра грани — кольца на ней нет, углы можно не считать.
			float atCenter = (float) ((now - e.arrival[k]) * speed);
			if (atCenter < -1.5f - noiseAmp || atCenter > ringWidth + 1.5f + noiseAmp) continue;
			// Быстрая проверка по углам (с запасом на изгиб фронта внутри грани).
			float s0 = behindFront(e, k, qx[0], qy[0], qz[0]), s1 = behindFront(e, k, qx[1], qy[1], qz[1]);
			float s2 = behindFront(e, k, qx[2], qy[2], qz[2]), s3 = behindFront(e, k, qx[3], qy[3], qz[3]);
			float min = Math.min(Math.min(s0, s1), Math.min(s2, s3));
			float max = Math.max(Math.max(s0, s1), Math.max(s2, s3));
			if (max < -0.3f || min > ringWidth + 0.3f) continue;
			// Кольцо тускнеет по мере того, как фронт уходит к пределу слышимости.
			float front = (float) ((now - e.start[k]) * speed);
			float strength = EchoField.falloff(front, e.range[k]) * e.base[k] * brightness * matGain * 1.3f;
			if (strength < 0.003f) continue;
			int c = k == 0 || single ? color : e.color[1];

			int w = n + 1;
			for (int j = 0; j <= n; j++) {
				float v = (float) j / n;
				for (int i = 0; i <= n; i++) {
					float u = (float) i / n;
					int g = j * w + i;
					gx[g] = bilerp(qx, u, v);
					gy[g] = bilerp(qy, u, v);
					gz[g] = bilerp(qz, u, v);
					gs[g] = behindFront(e, k, gx[g], gy[g], gz[g]);
				}
			}
			for (int j = 0; j < n; j++) {
				for (int i = 0; i < n; i++) {
					int a = j * w + i, b = a + 1, d = a + w, cc = d + 1;
					float lo = Math.min(Math.min(gs[a], gs[b]), Math.min(gs[d], gs[cc]));
					float hi = Math.max(Math.max(gs[a], gs[b]), Math.max(gs[d], gs[cc]));
					// Ширина кольца — по поверхности, а не по радиусу: на стене, в которую волна бьёт в лоб,
					// иначе вышел бы размытый диск. Градиент s вдоль грани = синус угла падения фронта.
					float gu = (gs[b] - gs[a]) * n, gv = (gs[d] - gs[a]) * n;
					band = ringWidth * Math.max(0.08f, Math.min(1f, (float) Math.sqrt(gu * gu + gv * gv)));
					if (hi < 0 || lo > band) continue;
					if (lo >= 0 && hi <= band) {
						// Клетка целиком внутри кольца — резать не нужно.
						ringCell(a, b, cc, d, strength, c);
						continue;
					}
					ringTriangle(a, b, cc, strength, c);
					ringTriangle(a, cc, d, strength, c);
				}
			}
		}
	}

	/** Точка грани по долям (u, v) между её углами 0-1-2-3. */
	private static float bilerp(float[] q, float u, float v) {
		return q[0] * (1 - u) * (1 - v) + q[1] * u * (1 - v) + q[2] * u * v + q[3] * (1 - u) * v;
	}

	private static void ringCell(int a, int b, int c, int d, float strength, int color) {
		int ca = ringColor(color, gs[a], strength), cb = ringColor(color, gs[b], strength);
		int cc = ringColor(color, gs[c], strength), cd = ringColor(color, gs[d], strength);
		vertex(gx[a], gy[a], gz[a], ca);
		vertex(gx[b], gy[b], gz[b], cb);
		vertex(gx[c], gy[c], gz[c], cc);
		vertex(gx[a], gy[a], gz[a], ca);
		vertex(gx[c], gy[c], gz[c], cc);
		vertex(gx[d], gy[d], gz[d], cd);
	}

	private static void ringTriangle(int a, int b, int c, float strength, int color) {
		int n = 3;
		cx[0] = gx[a]; cy[0] = gy[a]; cz[0] = gz[a]; cs[0] = gs[a];
		cx[1] = gx[b]; cy[1] = gy[b]; cz[1] = gz[b]; cs[1] = gs[b];
		cx[2] = gx[c]; cy[2] = gy[c]; cz[2] = gz[c]; cs[2] = gs[c];
		n = clip(n, 0f, true);
		if (n < 3) return;
		n = clip(n, band, false);
		if (n < 3) return;
		int c0 = ringColor(color, cs[0], strength);
		for (int i = 1; i < n - 1; i++) {
			vertex(cx[0], cy[0], cz[0], c0);
			vertex(cx[i], cy[i], cz[i], ringColor(color, cs[i], strength));
			vertex(cx[i + 1], cy[i + 1], cz[i + 1], ringColor(color, cs[i + 1], strength));
		}
	}

	private static int ringColor(int color, float s, float strength) {
		float t = Math.max(0f, Math.min(1f, s / band));
		// Тонкая яркая кромка (уходит в белый) и короткий тусклый хвост за ней.
		float u = 1 - t;
		float v = strength * (t < 0.2f ? 2.2f : 0.35f * u * u * u);
		return rgba(color, v);
	}

	/** Отсечение многоугольника cx..cs по s ≥ bound (keepAbove) или s ≤ bound. */
	private static int clip(int n, float bound, boolean keepAbove) {
		int m = 0;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			boolean inI = keepAbove ? cs[i] >= bound : cs[i] <= bound;
			boolean inJ = keepAbove ? cs[j] >= bound : cs[j] <= bound;
			if (inI) {
				tx[m] = cx[i]; ty[m] = cy[i]; tz[m] = cz[i]; ts[m] = cs[i]; m++;
			}
			if (inI != inJ) {
				float t = (bound - cs[i]) / (cs[j] - cs[i]);
				tx[m] = cx[i] + (cx[j] - cx[i]) * t;
				ty[m] = cy[i] + (cy[j] - cy[i]) * t;
				tz[m] = cz[i] + (cz[j] - cz[i]) * t;
				ts[m] = bound;
				m++;
			}
		}
		System.arraycopy(tx, 0, cx, 0, m);
		System.arraycopy(ty, 0, cy, 0, m);
		System.arraycopy(tz, 0, cz, 0, m);
		System.arraycopy(ts, 0, cs, 0, m);
		return m;
	}

	// ---------- источник звука ----------

	/** Небольшое горизонтальное кольцо, расходящееся от источника звука. */
	private static void sourceRing(float x, float y, float z, float r, int color, float level) {
		if (level < 0.003f) return;
		float px = x + r, pz = z;
		for (int i = 1; i <= CIRCLE_SEGMENTS; i++) {
			double a = Math.PI * 2 * i / CIRCLE_SEGMENTS;
			float nx = x + (float) Math.cos(a) * r, nz = z + (float) Math.sin(a) * r;
			line(px, y, pz, nx, y, nz, level, level, color);
			px = nx;
			pz = nz;
		}
	}

	// ---------- вершины ----------

	/** Цвет с яркостью level: альфа до 1, выше — вспышка уходит в белый. */
	private static int rgba(int color, float level) {
		float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;
		if (level > 1f) {
			float k = Math.min(1f, (level - 1f) / 1.6f);
			r += (1 - r) * k;
			g += (1 - g) * k;
			b += (1 - b) * k;
		}
		int a = (int) (Math.min(1f, Math.max(0f, level)) * 255);
		return a << 24 | (int) (r * 255) << 16 | (int) (g * 255) << 8 | (int) (b * 255);
	}

	private static void vertex(float x, float y, float z, int argb) {
		vc.vertex(mat, x, y, z).color(argb);
		vertices++;
	}

	/** Счётчик вершин кадра для EchoStats. */
	private static int vertices;

	private static float dist(float x, float y, float z) {
		return (float) Math.sqrt(x * x + y * y + z * z);
	}
}
