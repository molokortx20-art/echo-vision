package com.dragonyx.echolocation.mixin;

import com.dragonyx.echolocation.render.EchoEntityLayers;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.BatchingRenderCommandQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Все модели, части моделей и предметы проходят здесь — подменяем слой, если объект освещён эхом. */
@Mixin(BatchingRenderCommandQueue.class)
public abstract class BatchingRenderCommandQueueMixin {
	@ModifyVariable(method = {
		"submitModel",
		"submitModelPart",
		"submitItem",
		"submitBlockStateModel",
		"submitCustom(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue$Custom;)V"
	}, at = @At("HEAD"), argsOnly = true)
	private RenderLayer echolocation$echoLayer(RenderLayer layer) {
		return EchoEntityLayers.swap(layer);
	}
}
