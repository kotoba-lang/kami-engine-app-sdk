(ns kami-ui-sdk.ui
  "Ported from kami-engine's live `kami-ui-sdk/kami-ui.js` (JS, not
   deleted Rust — see `kami-ui-sdk` root docstring). ADR-2607010930.

   `kami-ui.js` is a zero-dependency DOM-overlay UI-component library
   (StatusBar, ControlHint, LabelOverlay, FileLoader, Toast, Badge,
   Legend, Panel, Field, Button) for kami-engine WebGPU apps. Nearly
   all of it is DOM construction (`document.createElement`,
   `el.style.*`, `appendChild`) which has no CLJC equivalent.

   Ported (pure, portable):
   - `theme` — the `THEME` constant map (colors, radii, shadows,
     Splatoon-inspired accent palette), data only.
   - `position-style` — pure port of `_positionElement`'s switch
     statement: `(position) -> style-map` (`:top`/`:left`/`:right`/
     `:bottom`/translate), instead of mutating `el.style`.
   - `label-viewport` / `project-node` / `visible-labels` — pure port
     of `LabelOverlay`'s per-frame `update()` math: camera-relative
     viewport bounds, LOD font-size formula
     (`clamp(8, 16, 18000/zoom)`), and per-node world -> screen
     projection + visibility culling + label-pool index assignment,
     without touching `requestAnimationFrame`, `window.__kami_cam_*`
     globals, or any DOM pool of `<div>`s.
   - `button-style` — pure port of `Button`'s style derivation
     (border/background/color/box-shadow from `{:active? :variant
     :color}`), covering both the initial render and the
     `setActive(next)` update path.
   - `toast-color` — pure port of the `Toast`/`colors` lookup
     (`:info`/`:success`/`:error` -> accent color).
   - `toast-sound-name` — pure port of the `soundMap` lookup used to
     decide which `KamiSound.play(...)` preset a toast triggers.

   Excluded (DOM-only, no CLJC equivalent):
   - All `document.createElement`/`appendChild`/`el.style.*` calls in
     every component constructor (`StatusBar`, `ControlHint`,
     `FileLoader`, `Badge`, `Legend`, `Panel`, `Field`, `Button`,
     `LabelOverlay`'s pooled `<div>`s).
   - `_loadFont` (injects a `<link>` for Google Fonts).
   - `FileLoader`'s `FileReader`/`change`-event wiring.
   - `init()`'s `document.body.style.*` side effects.
   - `Button`'s `mouseenter`/`mouseleave` hover-transform listeners.

   No overlap with `kotoba-lang/rtc` or `kotoba-lang/audio` — this is
   DOM/HUD UI logic, a distinct domain.")

;; ─── Theme ───────────────────────────────────────────────────────

(def theme
  "Pure data port of the JS `THEME` constant."
  {:bg "#f0ead6"
   :card-bg "rgba(255,255,255,0.92)"
   :card-border "#dfe6e9"
   :text-primary "#2d3436"
   :text-secondary "#636e72"
   :text-muted "#b2bec3"
   :shadow "0 4px 16px rgba(0,0,0,0.08)"
   :shadow-small "0 2px 8px rgba(0,0,0,0.06)"
   :radius "16px"
   :radius-small "12px"
   :font "'Nunito', system-ui, -apple-system, sans-serif"
   :accent {:red "#fa5757" :blue "#33bfff" :green "#66e673" :gold "#ffbf33"
             :purple "#b366f2" :mint "#26d9b3" :orange "#ff8c4d" :pink "#f272b3"}})

;; ─── Positioning ─────────────────────────────────────────────────

(defn position-style
  "Pure port of `_positionElement`: `position` keyword -> CSS style
   map, `margin` defaulting to the JS `'16px'`."
  ([position] (position-style position "16px"))
  ([position margin]
   (case (keyword position)
     :top-left {:top margin :left margin}
     :top-right {:top margin :right margin}
     :bottom-left {:bottom margin :left margin}
     :bottom-right {:bottom margin :right margin}
     :top-center {:top margin :left "50%" :transform "translateX(-50%)"}
     :bottom-center {:bottom margin :left "50%" :transform "translateX(-50%)"}
     {})))

;; ─── LabelOverlay projection math ────────────────────────────────

(defn label-viewport
  "Pure port of the camera/viewport-bounds portion of `LabelOverlay`'s
   `update()`. `{:cam-x :cam-z :zoom :width :height}` -> viewport
   bounds + LOD font size."
  [{:keys [cam-x cam-z zoom width height] :or {cam-x 0 cam-z 0 zoom 1000}}]
  (let [aspect (/ width height)
        half-w (* zoom aspect)
        half-h zoom
        font-size (max 8 (min 16 (/ 18000.0 zoom)))]
    {:v-left (- cam-x half-w) :v-right (+ cam-x half-w)
     :v-top (- cam-z half-h) :v-bottom (+ cam-z half-h)
     :font-size font-size}))

(defn project-node
  "Pure port of the per-node body of `LabelOverlay.update()`'s loop:
   given a node `{:n :x :z}`, the `label-viewport` bounds, and canvas
   `width`/`height`, returns `{:visible? :sx :sy}` (screen position,
   `sy` already offset -4px as in the JS)."
  [{:keys [x z]} {:keys [v-left v-right v-top v-bottom]} width height]
  (if (or (< x v-left) (> x v-right) (< z v-top) (> z v-bottom))
    {:visible? false}
    (let [sx (* (/ (- x v-left) (- v-right v-left)) width)
          sy (- (* (/ (- z v-top) (- v-bottom v-top)) height) 4)]
      {:visible? true :sx sx :sy sy})))

(defn visible-labels
  "Pure port of `LabelOverlay.update()`'s pool-filling loop: given
   `nodes`, a viewport, canvas size, and `max-labels` (the pool size),
   returns up to `max-labels` `{:node :sx :sy :font-size}` maps for the
   nodes that are in-view, in encounter order (mirroring the JS's
   `idx < MAX` early `break`)."
  [nodes viewport width height max-labels]
  (let [font-size (:font-size viewport)]
    (->> nodes
         (keep (fn [node]
                 (let [{:keys [visible? sx sy]} (project-node node viewport width height)]
                   (when visible?
                     (assoc node :sx sx :sy sy :font-size font-size)))))
         (take max-labels)
         vec)))

;; ─── Button style ────────────────────────────────────────────────

(defn button-style
  "Pure port of `Button`'s initial style + `setActive` update logic:
   `{:active? :variant :color}` -> `{:border-color :background :color
   :box-shadow}`."
  [{:keys [active? variant color] :or {color (get-in theme [:accent :blue])}}]
  (let [primary? (= variant :primary)]
    {:border-color (if active? color (:card-border theme))
     :background (cond active? color
                        primary? (:text-primary theme)
                        :else "#fff")
     :color (if (or active? primary?) "#fff" (:text-primary theme))
     :box-shadow (if active? (:shadow-small theme) "none")}))

;; ─── Toast helpers ───────────────────────────────────────────────

(defn toast-color
  "Pure port of the `colors` lookup in `Toast`."
  [toast-type]
  (get {:info (get-in theme [:accent :blue])
        :success (get-in theme [:accent :green])
        :error (get-in theme [:accent :red])}
       (keyword (or toast-type :info))
       (get-in theme [:accent :blue])))

(defn toast-sound-name
  "Pure port of the `soundMap` lookup in `Toast`."
  [toast-type]
  (get {:info "pop" :success "success" :error "error"}
       (keyword (or toast-type :info))
       "pop"))
