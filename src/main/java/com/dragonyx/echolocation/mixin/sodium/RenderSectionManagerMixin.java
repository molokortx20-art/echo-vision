package com.dragonyx.echolocation.mixin.sodium;

import com.dragonyx.echolocation.EchoMod;
import com.dragonyx.echolocation.render.EchoRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Дальность, на которой Sodium ищет и рисует чанки.
 * <p>
 * С «Fog Occlusion» Sodium не рисует чанки в тумане. Туман слепоты кончается у глаз, и без этой правки
 * отсекался почти весь мир: буфер глубины пустел, а эхо пещер под полом и дальних построек просвечивало
 * насквозь, как рентген. Но и весь мир на полную дальность прорисовки не нужен — он всё равно чёрный.
 * Поэтому при слепоте чанки рисуются ровно до самого далёкого эха (с запасом): ближе них глубина нужна,
 * дальше — только лишняя работа видеокарте.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public abstract class RenderSectionManagerMixin {
	@Shadow(remap = false)
	private float getRenderDistance() {
		throw new AssertionError();
	}

	@Inject(method = "getSearchDistance", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void echolocation$echoDistance(CallbackInfoReturnable<Float> cir) {
		if (EchoMod.blindActive()) cir.setReturnValue(Math.min(this.getRenderDistance(), EchoRenderer.worldReach()));
	}
}
