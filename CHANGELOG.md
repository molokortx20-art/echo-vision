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
