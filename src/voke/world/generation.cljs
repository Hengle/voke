(ns voke.world.generation
  (:require [cljs.spec :as s]
            [cljs.spec.test :as stest]
            [taoensso.tufte :as tufte :refer-macros [p profiled profile]]
            [voke.util :refer [bound-between rand-nth-weighted]]))

(s/def ::cell #{:empty :full})
(s/def ::width nat-int?)
(s/def ::height nat-int?)
(s/def ::grid (s/coll-of (s/coll-of ::cell)))

(s/def ::history (s/coll-of ::cell))
(s/def ::generated-level (s/keys :req [::grid ::history]))

(defn full-grid [w h]
  (vec (repeat h
               (vec (repeat w :full)))))

(s/fdef full-grid
  :args (s/cat :w nat-int? :h nat-int?)
  :ret ::grid)

(defn count-empty-spaces [grid]
  (apply +
         (map (fn [line]
                (count
                  (filter #(= % :empty) line)))
              grid)))

(s/fdef count-empty-spaces
  :args (s/cat :grid ::grid)
  :ret nat-int?)

(defn ^:export drunkards-walk [w h num-empty-cells]
  (loop [grid (full-grid w h)
         historical-active-cells []
         x (rand-int w)
         y (rand-int h)
         empty-cells 0]

    (if (= empty-cells num-empty-cells)
      {::grid    grid
       ::history historical-active-cells}

      (let [cell-was-full? (= (get-in grid [y x]) :full)
            horizontal-direction-to-center (if (< x (/ w 2)) :east :west)
            vertical-direction-to-center (if (< y (/ h 2)) :south :north)
            direction (rand-nth-weighted
                        (into {}
                              (map (fn [direction]
                                     (if (#{horizontal-direction-to-center vertical-direction-to-center}
                                           direction)
                                       [direction 1.4]
                                       [direction 1.0]))
                                   [:north :south :east :west])))]

        (recur (assoc-in grid [y x] :empty)
               (conj historical-active-cells [[x y] :full])
               (case direction
                 :east (bound-between (inc x) 0 (dec w))
                 :west (bound-between (dec x) 0 (dec w))
                 x)
               (case direction
                 :north (bound-between (dec y) 0 (dec h))
                 :south (bound-between (inc y) 0 (dec h))
                 y)
               (if cell-was-full?
                 (inc empty-cells)
                 empty-cells))))))

(s/fdef drunkards-walk
  :args (s/cat :w nat-int?
               :h nat-int?
               :num-empty-cells nat-int?)
  :ret ::generated-level)

(defn array->grid [an-array]
  (into []
        (for [row an-array]
          (into []
                (for [full? row]
                  (if full? :full :empty))))))

(defn -make-js-row [width full-probability]
  (let [arr (make-array width)]
    (loop [i 0]
      (when (< i width)
        (aset arr i (if (< (rand) full-probability)
                      true
                      false))
        (recur (inc i))))
    arr))

(defn -make-js-grid [width height full-probability]
  (let [arr (make-array height)]
    (loop [i 0]
      (when (< i height)
        (aset arr i (-make-js-row width full-probability))
        (recur (inc i))))
    arr))

(defn -get-neighbors [js-grid x y w h]
  (let [neighbors #js []]
    (loop [i (dec x)
           j (dec y)]
      (when (and (< i (+ x 2))
                 (< j (+ y 2)))

        (cond
          (and (identical? i x)
               (identical? j y)) nil

          (or (< i 0)
              (>= i w)
              (< j 0)
              (>= j h)) (.push neighbors true)

          :else (.push neighbors (-> js-grid
                                     (aget j)
                                     (aget i))))

        (if (identical? i (inc x))
          (recur (dec x)
                 (inc j))
          (recur (inc i)
                 j))))

    neighbors))

(defn ^:export automata
  [w h initial-wall-probability iterations]
  (let [js-initial-grid (-make-js-grid w h initial-wall-probability)
        cljs-initial-grid (array->grid js-initial-grid)
        new-value-at-position (fn [grid x y]
                                (let [cell-is-full? (-> grid
                                                        (aget y)
                                                        (aget x))
                                      neighbors (-get-neighbors grid x y w h)
                                      num-full-neighbors (.-length (.filter neighbors #(= % true)))]

                                  (cond
                                    (and cell-is-full?
                                         (> num-full-neighbors 2)) true
                                    (and (not cell-is-full?)
                                         (> num-full-neighbors 5)) true
                                    :else false)))]

    (loop [i 0
           grid js-initial-grid
           active-cells []]
      (if (= i iterations)
        {::grid         (array->grid grid)
         ::initial-grid cljs-initial-grid
         ::history      active-cells}

        (let [x (rand-int w)
              y (rand-int h)
              new-value (new-value-at-position grid x y)]
          (recur (inc i)
                 (do (-> grid
                         (aget y)
                         (aset x new-value))
                     grid)
                 (conj active-cells [[x y] new-value])))))))


#_(stest/instrument [`drunkards-walk
                     `full-grid
                     `count-empty-spaces])

(comment
  (tufte/add-basic-println-handler! {})

  (let [grid (full-grid 30 30)]
    (js/console.profile "drunkard")
    (dotimes [_ 10]
      (drunkards-walk grid 150))
    (js/console.profileEnd))

  (identical? 1 1)

  (profile
    {}
    (let [grid (full-grid 30 30)]
      (dotimes [_ 100]
        (p :drunkard
           (drunkards-walk grid 150)
           nil))))

  (profile
    {}
    (dotimes [_ 100]
      (p :automata
         (automata 50 50 0.45 500)
         nil)))
  )

