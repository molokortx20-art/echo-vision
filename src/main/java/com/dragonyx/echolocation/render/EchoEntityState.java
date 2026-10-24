package com.dragonyx.echolocation.render;

/** Яркость и цвет эха, которые EntityRenderState несёт от извлечения до отрисовки существа. */
public interface EchoEntityState {
	float echolocation$getLevel();

	int echolocation$getColor();

	void echolocation$set(float level, int color);
}
