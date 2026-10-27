## Echo Vision 1.0.4

- Only echo vision is left: the Contours, Block grid and Filled faces styles and "Contours over echo vision" are removed — they showed too much through walls. Mobs are no longer outlined with the glowing effect (it was visible through walls); they are always drawn in the echo style. Sound tracing is a bit lighter as a result.
- Echo color picker: a palette with hue, saturation and brightness plus a HEX field (Settings → Look → Echo color…).
- Echo range per sound type: separate sliders for steps and jumps, mining, placing blocks, voice and other sounds (0–200 %).
- Materials dampen echo: stone, metal and glass reflect brightly, wood a bit less, dirt and sand are muffled, wool, leaves, snow and moss swallow most of the echo. Can be turned off.
- Optional uneven wave edge: the wavefront breaks into soft irregular tongues instead of a perfect circle (off by default).

## Echo Vision 1.0.3

- Much better FPS while blind (with Sodium): the black world beyond the farthest echo is no longer rendered at all, so blindness now costs less than normal play.
- Echo rendering is cheaper again: unlit blocks and faces turned away from the camera are skipped early, faded waves are not recalculated, the wave ring needs less clipping.
- The echo shaders read neighbouring texels only where detail lines can actually appear (lighter on the GPU at high resolutions).
- Fixed doubled option names in settings ("Style: Style: …").

## Echo Vision 1.0.2

- Fixed a crash with glowing entities (for example the Glowing effect on servers): "Can't render an outline for this rendertype!".
- About twice as fast echo rendering: echoes outside the screen and faded surfaces are skipped, block models are no longer rebuilt by every wave (no more stutter spikes).
- Riders, parrots on shoulders and other nested entities keep the correct echo brightness.

## Echo Vision 1.0.1

- Chests, signs, banners, beds and other block entities now show in echo vision.
- Mob armor, held items, dropped items, boats, minecarts, armor stands and item frames show in the echo style.
- Your own hand, held item and body (third person) show in echo vision.
- Works with Better Block Entities: chests no longer stay black.
- Checked on a dedicated server and in the Fabulously Optimized modpack.

## Echo Vision 1.0.0 — first release

- Blindness: the world is pitch black, no sky, no light.
- Every sound sends an echo wave: steps, jumps and landings, mining, combat, mobs, blocks, note blocks.
- Sound travels through the air only — down tunnels, through doorways, around corners — and weakens at every turn. No X-ray through walls.
- Smooth round wavefront ring at a configurable speed of sound.
- Echo Vision style: real block models and textures in grayscale with detail lines; mobs in the same style.
- Active echolocation: press V to click.
- Voice chat: Plasmo Voice and Simple Voice Chat voices create waves.
- Almost 40 settings (K or Mod Menu), English and Russian.
- Sodium compatible.
