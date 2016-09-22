(ns voke.system.core
  (:require [voke.events :refer [subscribe-to-event]]
            [voke.input :refer [handle-keyboard-events]]
            [voke.schemas :refer [GameState System]]
            [voke.system.ai.system :refer [ai-system]]
            [voke.system.attack :refer [attack-system]]
            [voke.system.collision.system :refer [collision-system]]
            [voke.system.movement :refer [move-system]]
            [voke.system.rendering :refer [render-system]])
  (:require-macros [schema.core :as sm]))

(sm/defn system-to-tick-fn
  "Takes a System map, returns a function of game-state -> game-state."
  [system :- System]
  (sm/fn [state :- GameState]
    (update-in state
               [:entities]
               merge
               ; XXXX assert that the line below contains only entities that already exist in state :entities keys
               (into {}
                     (map (juxt :id identity)
                          ((system :tick-fn) (vals (state :entities))))))))

; smell: collision system is listed first so that its tick function can reset its internal state atoms
; before anything else can happen in each frame.
; should systems have a :before-tick function that serves this purpose?
(def game-systems [collision-system
                   move-system
                   attack-system
                   ai-system
                   render-system])

(def tick-functions
  (map system-to-tick-fn
       (filter :tick-fn game-systems)))

;; Public

(sm/defn initialize-systems!
  [game-state player-entity-id]

  ; Run systems' initalize functions.
  (doseq [initialize-fn (keep identity
                              (map :initialize game-systems))]
    (initialize-fn game-state))

  ; Set up systems' event handlers.
  (doseq [event-handler-map (flatten
                              (keep identity
                                    (map :event-handlers game-systems)))]
    (subscribe-to-event (event-handler-map :event-type)
                        (event-handler-map :fn)))

  ; Listen to keyboard input.
  (handle-keyboard-events player-entity-id))

(sm/defn process-a-tick :- GameState
  "A function from game-state -> game-state, which you can call to make a unit
  of time pass in the game-world."
  [state :- GameState]
  (reduce (fn [state tick-function]
            (tick-function state))
          state
          tick-functions))
