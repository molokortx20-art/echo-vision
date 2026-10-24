package com.dragonyx.echolocation.mixin;

import com.dragonyx.echolocation.render.EchoEntityLayers;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Любая сущность (мобы, игроки, лодки, рамки, выпавшие предметы) — вместе с бронёй и предметами в руках. */
@Mixin(EntityRenderManager.class)
public abstract class EntityRenderManagerMixin {
	// Прежняя яркость — в локальной переменной вызова: отрисовка бывает вложенной (попугай на плече, наездник).
	@Inject(method = "render", at = @At("HEAD"))
	private void echolocation$begin(CallbackInfo ci, @Local(argsOnly = true) EntityRenderState state,
									@Share("previous") LocalFloatRef previous) {
		previous.set(EchoEntityLayers.begin(EchoEntityLayers.levelOf(state)));
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void echolocation$end(CallbackInfo ci, @Share("previous") LocalFloatRef previous) {
		EchoEntityLayers.end(previous.get());
	}
}
