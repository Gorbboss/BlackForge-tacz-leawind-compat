# BlackForge TaCZ + Leawind Camera Compat v0.1.0

## BlackForge v0.4.7 wedge-wall correction

- Removes the closed air-generated enclosure introduced in v0.4.6.
- Restores the v0.4.5 black-concrete boundary behavior where the cutaway meets
  real terrain.
- Extends only an activated wedge's outer radial wall backward toward the
  camera through air.
- Rejects axial air faces, preventing front caps, rear caps, and geometry that
  can cover the camera.
- Keeps bottom boundary suppression unless the boundary is below the player.
- Continues to use BlackForge Unbound 1.8; its uniform contract is unchanged.

## BlackForge v0.4.6 air-safe boundary test

- Restores the transition layers to their original block textures with normal
  transparency; they are no longer replaced by black concrete.
- Builds the black-concrete boundary from the complete mathematical cutaway
  volume, including air positions, so side openings cannot reveal terrain.
- Suppresses downward-facing black-concrete boundary faces unless their fake
  boundary position is below the player's feet.
- Keeps the eight-sector wedges and last-obstruction endpoint from v0.4.5.
- Use BlackForge Unbound 1.8 for the matching shader package.

## BlackForge v0.4.5 eight-sector cutaway test

- Splits the 3x3 corridor into eight camera-plane sectors surrounding its
  center core.
- A blocked sector opens fully and its two neighboring sectors open at reduced
  width, producing a tapered three-slice opening instead of clearing the whole
  corridor.
- Finds the final obstructing block across the 3x3 probe grid and ends the
  corridor just beyond it; blocks closer to the player remain untouched.
- Renders the three transition layers as client-only black-concrete geometry
  while their existing proportional smoothing changes opacity.
- Uses collision shapes for the final-obstruction scan so non-colliding grass
  and similar vegetation do not extend the corridor.
- Use BlackForge Unbound 1.7 for the matching eight-sector shader mask.

## BlackForge v0.4.4 black-concrete boundary lining

- Removes the experimental untextured black boundary treatment.
- Renders only the inward-facing boundary quads with Minecraft's actual
  `minecraft:block/black_concrete` atlas texture.
- The lining is client-only geometry: it never places blocks and has no
  collision, interaction, drops, or server state.
- Generates the same lining with or without shaders while leaving original
  terrain in shader shadow passes.
- Use BlackForge Unbound 1.6 for the matching shader package.

## BlackForge v0.4.3 smoothed cutaway and black boundary

- Keeps the center corridor and active camera-clearance box instant.
- Moves each outer block toward its distance-based target at five percentage
  points per tick: a complete transition takes one second and partial changes
  take proportionally less time.
- Restores every departing cutaway block over one second from its current
  visibility, including blocks that were fully invisible.
- Draws an experimental camera-only black boundary face around the cutaway.

## BlackForge v0.4.2 directional spatial cutaway

- Replaces all time-based fades with transparency based only on distance from
  the central 3x3 corridor.
- Probes a 3x3 ray grid. Any hit opens the central corridor; surrounding
  transparency expands only on the obstructed right, left, up, or down side.
- Corner probe hits activate both adjoining sides.
- When the camera is at least one block above the player, an independent 2x2x2
  camera-clearance box becomes fully transparent.
- Use BlackForge Unbound 1.4 or newer for the matching shader mask.

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
- `bfCutawayCameraBlock` - integer world position of the camera's occupied block.
- `bfCutawaySides` - right, left, up, and down directional activation.
- `bfCutawayFlags` - corridor and overhead-clearance activation.
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
