package com.dragonyx.echolocation.mixin.sodium;

import com.dragonyx.echolocation.EchoMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium с «Fog Occlusion» не рисует чанки, спрятанные в тумане. Туман слепоты кончается у глаз,
 * и без этой правки отсекался почти весь мир: буфер глубины пустел, а эхо пещер под полом
 * и дальних построек просвечивало насквозь, как рентген. При слепоте ищем чанки на всю дальность прорисовки.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public abstract class RenderSectionManagerMixin {
	@Shadow(remap = false)
	private float getRenderDistance() {
		throw new AssertionError();
	}

	@Inject(method = "getSearchDistance", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void echolocation$fullDistance(CallbackInfoReturnable<Float> cir) {
		if (EchoMod.blindActive()) cir.setReturnValue(this.getRenderDistance());
	}
}
