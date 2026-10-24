package com.dragonyx.echolocation.mixin;

import com.dragonyx.echolocation.echo.EchoField;
import com.dragonyx.echolocation.render.EchoEntityLayers;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Блок-сущности (сундуки, таблички, кровати, шалкеры, головы, баннеры) рисуются не моделью блока — освещаем их здесь. */
@Mixin(BlockEntityRenderManager.class)
public abstract class BlockEntityRenderManagerMixin {
	@Unique
	private float echolocation$previous;

	@Inject(method = "render", at = @At("HEAD"))
	private void echolocation$begin(CallbackInfo ci, @Local(argsOnly = true) BlockEntityRenderState state) {
		echolocation$previous = EchoEntityLayers.begin(EchoField.INSTANCE.blockLevel(state.pos.asLong()));
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void echolocation$end(CallbackInfo ci) {
		EchoEntityLayers.end(echolocation$previous);
	}
}
