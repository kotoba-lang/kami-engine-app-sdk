(ns kami-ui-sdk
  "KAMI UI SDK — portable (.cljc) core logic ported from kami-engine's
   *live* `kami-ui-sdk/*.js` files (kami-ui.js, kami-motion.js,
   kami-effect.js, kami-sound.js, kami-engine-audio.js, kami-rtc.js —
   never deleted, still present in `kotoba-lang/kami-engine` today).

   Provenance note: unlike most of this migration (which restores
   *deleted* Rust crates), this is a JS -> CLJC port of source that is
   still live. ADR-2607010930, `com-junkawasaki/root`.

   Each source file has a matching namespace under `kami-ui-sdk.*`.
   Only the pure, portable logic was ported (data transforms, easing /
   spring / envelope math, state derivation, parametric curves); DOM
   manipulation (`document.createElement`, `addEventListener`,
   `requestAnimationFrame` loops), the Web Audio graph construction
   itself, and the WebRTC/`getUserMedia` plumbing have no CLJC
   equivalent and were excluded. Each namespace docstring lists what
   was excluded and, for `kami-ui-sdk.sound` / `kami-ui-sdk.engine-audio`
   / `kami-ui-sdk.rtc`, documents the relationship to the sibling
   restorations `kotoba-lang/audio` and `kotoba-lang/rtc`.

   | Namespace                   | Ported from            |
   |------------------------------|------------------------|
   | `kami-ui-sdk.ui`             | `kami-ui.js`            |
   | `kami-ui-sdk.motion`         | `kami-motion.js`        |
   | `kami-ui-sdk.effect`         | `kami-effect.js`        |
   | `kami-ui-sdk.sound`          | `kami-sound.js`         |
   | `kami-ui-sdk.engine-audio`   | `kami-engine-audio.js`  |
   | `kami-ui-sdk.rtc`            | `kami-rtc.js`           |")
