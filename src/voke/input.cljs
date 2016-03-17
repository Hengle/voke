(ns voke.input
  (:require [cljs.core.async :refer [chan <! put!]]
            [goog.events :as events]
            [voke.state :refer [update-entity!]]
            [voke.util :refer [in?]])
  (:import [goog.events KeyCodes])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(def move-key-mappings {KeyCodes.W :up
                        KeyCodes.A :left
                        KeyCodes.S :down
                        KeyCodes.D :right})
(def fire-key-mappings {KeyCodes.DOWN  :down
                        KeyCodes.LEFT  :left
                        KeyCodes.UP    :up
                        KeyCodes.RIGHT :right})
(def key-mappings (merge move-key-mappings fire-key-mappings))

(defn listen-to-keyboard-inputs [event-chan]
  (events/removeAll (.-body js/document))

  (doseq [[goog-event-type event-type-suffix] [[(.-KEYDOWN events/EventType) "down"]
                                               [(.-KEYUP events/EventType) "up"]]]

    (events/listen
      (.-body js/document)
      goog-event-type
      (fn [event]
        (let [code (.-keyCode event)]
          (when (contains? key-mappings code)
            (.preventDefault event)
            (let [event-type (cond
                               (contains? move-key-mappings code) (keyword (str "move-key-" event-type-suffix))
                               (contains? fire-key-mappings code) (keyword (str "fire-key-" event-type-suffix)))]
              (put! event-chan {:type      event-type
                                :direction (key-mappings code)}))))))))

;;; Public

(defn handle-keyboard-events [player-id]
  "Listens to keyboard events, and queues modifications to the `player-id` entity
  whenever a move key or fire key is pressed/raised."
  (let [event-chan (chan)]
    (listen-to-keyboard-inputs event-chan)

    (go-loop []
      (let [msg (<! event-chan)
            direction (msg :direction)

            update-entity-args (case (msg :type)
                                 :move-key-down [[:input :intended-move-direction] conj direction]
                                 :move-key-up [[:input :intended-move-direction] disj direction]
                                 :fire-key-down [[:input :intended-fire-direction]
                                                 (fn [fire-directions]
                                                   (if (in? fire-directions direction)
                                                     fire-directions
                                                     (conj fire-directions direction)))]
                                 :fire-key-up [[:input :intended-fire-direction]
                                               (fn [fire-directions]
                                                 (filterv #(not= direction %) fire-directions))])]

        (update-entity! player-id :keyboard-input (fn [entity]
                                                    (apply update-in entity update-entity-args)))
        (recur)))))
