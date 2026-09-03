# BlackForge TaCZ + Leawind Camera Compat v0.1.0

## BlackForge v0.4.0 (build #55 baseline)

- Preserves the confirmed working six-tick eased camera cutaway activation.
- Keeps the capped tube center completely invisible. It begins one block wide,
  tapers to a narrow tube, and tapers back down near the player.
- Activates from five obstruction rays: center, left, right, top, and bottom.
- Fades the center line in 0.25 seconds, the four adjacent lines in 0.5 seconds,
  and the rest of the 3x3 opening in 1 second.
- Smoothly increases opacity across three surrounding rings and submits those
  blocks through the shader-aware translucent-entity path used by Oculus.
- Hard-limits Leawind's maximum third-person zoom to 12 blocks. This limit is
  intentionally not configurable.
- Keeps shader-mode chunk geometry intact so the original blocks remain in
  Photon's shadow pass. Publishes `bfCutaway*` uniforms for the matching
  BlackForge Edition Photon camera-only mask.
- Stops submitting replacement geometry after a vanilla center block reaches
  zero opacity, preventing zero-alpha blocks from being treated as opaque.

### Photon uniform contract

- `bfCutawayActive` - 1 while the camera mask or its return fade is active.
- `bfCutawayStart`, `bfCutawayEnd` - world-space corridor endpoints.
- `bfCutawayRight`, `bfCutawayUp` - camera-plane basis vectors.
- `bfCutawayFade` - center, cardinal-cross, and full-area fade progress.
- `bfCutawayShape` - taper length, end radius, tube radius, and outer fade width.

Photon must consume these only from its main-camera terrain programs. Shadow
programs intentionally remain unchanged so cutaway blocks keep casting shadows.
- Does not restore the removed shader-shadow renderer.

Forge 1.20.1 compatibility fork based conceptually on
`khanhtimn/TACZ-LeawindTPS-Compat` (GPL-3.0).

Target setup:
- Minecraft 1.20.1
- Forge 47.4.21
- TaCZ 1.1.8-hotfix
- Leawind Third Person 2.2.0
- Java 17

## v0.1.0 features

- TaCZ third-person crosshair kept visible.
- Old `GunAnimationStateMachine` mixin is intentionally NOT used.
- Old Leawind `GameStatus` API is intentionally NOT used.
- Camera recoil config toggle; default OFF.
- Vanilla camera collision bypass through `Camera#getMaxZoom`.
- Forward-only target filtering; default front hemisphere ±90°.
- Blocks intersecting the camera-to-player corridor are hidden client-side.
- Hidden blocks remain real blocks in the world:
  - player collision unchanged
  - bullets unchanged
  - interaction/world state unchanged
  - server is never told blocks disappeared
- Neighbor faces next to hidden camera blocks are forced visible during rebuild
  to reduce x-ray-style missing internal faces.

## Client config

`config/blackforge-tacz-leawind.toml`

Defaults:
- cameraRecoil = false
- disableCameraCollision = true
- hideCameraObstructions = true
- hideCorridorRadius = 0.32
- forwardOnlyTargeting = true
- forwardHemisphereDegrees = 90

## Important

This is the first experimental camera-obstruction build. It avoids editing real
world blocks. It must be tested with your exact Embeddium/Oculus/Leawind setup,
because optimized chunk renderers can alter where block render mixins execute.

GPL-3.0 applies to this project.
