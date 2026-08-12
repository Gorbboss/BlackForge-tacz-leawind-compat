# BlackForge TaCZ + Leawind Camera Compat v0.1.0

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
