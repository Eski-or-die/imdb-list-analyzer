;;; 'Math Tools' file provides basic mathematical functions for list analytics:;;;  sum, mean, dot-product, variance, stdev, correlation.;;;;;;  These functions strive for numerical stability at the expense of speed.;;;  Pre- and post-contracts are used extensively to guard against improper;;;  use of the functions.;;;;;; Esa Junttila 2015-11-01 (originally 2013-07-02);;;(ns imdb-list-analyzer.math-tools  (:require [imdb-list-analyzer.common :as com :refer :all])  (:import java.util.Collections));(set! *warn-on-reflection* true)  ; enable only for testing(defn non-neg-num?  "Is the argument a non-negative number?"  [x]  (and (number? x) (<= 0 x)))(defn non-pos-num?  "Is the argument a non-positive number?"  [x]  (and (number? x) (>= 0 x)))(defn neg-num?  "Is the argument a negative number?"  [x]  (and (number? x) (neg? x)))(defn pos-num?  "Is the argument a positive number?"  [x]  (and (number? x) (pos? x)))(defn log-n  "Return the 'base' logarithm of x"  ^double  [x base]  {:pre  [(non-neg-num? x) (pos-num? base)]   :post (number? %)}  (if (= (double base) 1.0)    Double/NaN    (/ (Math/log x) (Math/log base))))(defn log-2  "Return the 2-base logarithm of x"  ^double  [x]  {:pre  [(non-neg-num? x)]   :post (number? %)}  (log-n x 2))(defn sum  "Compute the sum of a collection of numbers.  Return zero if the collection is empty."  [coll]  {:pre  [(coll? coll) (every? number? coll)]   :post (number? %)}  (reduce + coll))(defn mean  "Compute the mean of a collection of numbers.   Return NaN if the collection is empty."  [coll]  {:pre  [(coll? coll) (every? number? coll)]   :post (number? %)}  (if (empty? coll)    Double/NaN    (/ (sum coll) (count coll))))(defn dot-product  "Compute the dot product between two equal-size collections of numbers.   Return zero if the collections are empty."  [coll1 coll2]  {:pre  [(coll? coll1)          (coll? coll2)          (= (count coll1) (count coll2))          (every? number? coll1)          (every? number? coll2)]   :post (number? %)}  (sum (map * coll1 coll2)))(defn variance  "Compute the sample variance from a collection of at least two numbers."  [coll]  {:pre  [(coll? coll)          (>= (count coll) 2)          (every? number? coll)]   :post [(or (>= % 0.0) (Double/isNaN %))]}  (let [mus (repeat (count coll) (mean coll))        diff (map - coll mus)]    (/ (dot-product diff diff) (dec (count coll)))))(defn stdev  "Compute the sample standard deviation from a collection of at least two numbers."  [coll]  {:pre  [(coll? coll)          (>= (count coll) 2)          (every? number? coll)]   :post [(or (>= % 0.0) (Double/isNaN %))]}  (Math/sqrt (variance coll)))(defn- std-z-score  "Compute the standard z-scores for a sample of numbers.   That is, deviations from mean in stdev units (sigma)."  [coll]  (let [n (count coll)        mu (mean coll)        sigma (stdev coll)]    (if (zero? sigma)      (repeat n Double/NaN)      (map #(/ (- % mu) sigma) coll))))(defn correlation  "Compute the Pearson product-moment correlation coefficient between two   collections of numbers. The size of collections must be at least 2."  ^double  [coll1 coll2]  {:pre  [(coll? coll1)          (coll? coll2)          (= (count coll1) (count coll2))          (>= (count coll1) 2)          (every? number? coll1)          (every? number? coll2)]   :post [(or (<= -1.0 % +1.0) (Double/isNaN %))]}  (let [n (count coll1)        corr (/ (dot-product (std-z-score coll1) (std-z-score coll2)) (dec n))]    (cond                                                   ; handle small numerical instabilities like '1.0000000000000002'      (> corr +1.0) +1.0      (< corr -1.0) -1.0      :else corr)))(defn entropy  "Compute Shannon's information-theoretic entropy from the probabilities  or empirical frequencies of a probability distribution. The result  is represented as bits (base 2 logarithms are used.)"  ^double  [freqs]  {:pre  [(coll? freqs) (every? non-neg-num? freqs)]   :post (pos-num? %)}  (let [non-zero-freqs (filter pos? freqs)        total (sum non-zero-freqs)        pr (map #(/ % total) non-zero-freqs)        pr-log-2 (map #(log-2 %) pr)]    (if (or (empty? non-zero-freqs) (zero? total))      Double/NaN      (Math/abs ^double (- (dot-product pr pr-log-2))))))"Empirical discrete probability distribution:sorted cumulative probabilities (most probable first) and the empirical mean"(defrecord EmpiricalDistr [symbcumuprobs empirical-mean]); FIXME: combine EmpiricalDistribs!(defn gen-emp-distr  "Create an empirical discrete probability distribution from a sample"  [samples]  {:pre [(coll? samples) (not (empty? samples))]}  (let [freqs (frequencies samples)        empir-mean (/ (dot-product (map first freqs) (map second freqs)) (count samples))        sorted-freqs (reverse (sort-by val freqs))        cumu-sums (reductions + (vals sorted-freqs))        normalizer (last cumu-sums)]    (->EmpiricalDistr      (map vector           (keys sorted-freqs)           (map #(/ % normalizer) cumu-sums))      empir-mean)))(defn sample-distr  "Return an infinite lazy seq of random samples from an empirical  discrete probability distribution"  [emp-distr]  (map    (fn [rnd]      (first                                                ; retrieve 'symbol' from the occurrence        (com/find-first          (fn [[_ cum-prob]] (<= rnd cum-prob))          (:symbcumuprobs emp-distr))))    (repeatedly rand)))(defn normalize-freq  "Normalize frequencies into probabilities"  [freqs]  (let [norm-sum (long (reduce + freqs))]    (map #(/ % norm-sum) freqs)));; Prototypes of cumulative probability functions on empirical discrete distributions(defn ecdf  "Discrete cumulative probability function on an empirical distribution (simple)"  [x distr-points distr-freq]  {:pre  [(number? x)          (every? coll? [distr-points distr-freq])          (not (empty? distr-points))          (= (count distr-points) (count distr-freq))          (every? pos-num? (map - (rest distr-points) (butlast distr-points)))          (every? non-neg-num? distr-freq)]   :post [non-neg-num? %]}  (let [probs (normalize-freq distr-freq)        cumu-sums (reductions + 0 probs)        idx (Collections/binarySearch (map double distr-points) (double x))]    (if (non-neg-num? idx)      (nth cumu-sums (inc idx))                             ; exact match, average of min/max      (nth cumu-sums (dec (Math/abs idx))))))               ; intermediate value, round down(def extrap-method #{:linear :flat :disable})(defn- linear-interp-raw  [x1 y1 x2 y2 x]  (+ y1 (* (- x x1) (/ (- y2 y1) (- x2 x1)))))(defn linear-interp  "Linear interpolation of x based on x1->y1 and x2->b.  For example (= (linear-interp 2 10 12 15 8) 13)  "  ([x1 y1 x2 y2 x]   (linear-interp x1 y1 x2 y2 x :disable))  ([x1 y1 x2 y2 x extrap-method]   {:pre  [(every? number? [x1 y1 x2 y2 x])           (<= x1 x2)]    :post (number? %)}   (if (<= x1 x x2)     (linear-interp-raw x1 y1 x2 y2 x)     (case extrap-method       :linear (linear-interp-raw x1 y1 x2 y2 x)       :flat (if (< x x1) y1 y2)       (throw (IllegalArgumentException. "extrapolation disabled"))))))(defrecord EmpDistr [points counts probs cumu-probs mean])(defn generate-emp-distr  "Generate an empirical probability distribution from a frequency.  (generate-emp-distr (frequencies [30 30 40 50 20 80 40 30]))  => #user.EmpDistr{:points (20 30 40 50 80), :counts (1 3 2 1 1), :probs (1/8 3/8 1/4 1/8 1/8), :cumu-probs (0 1/8 1/2 3/4 7/8 1N), :mean 40N}"  [freqs]  (let [fs (sort-by key freqs)        points (keys fs)        counts (vals fs)        probs (normalize-freq counts)        cumu-probs (reductions + 0 probs)        ]    (map->EmpDistr {                    :points     points                    :counts     counts                    :probs      probs                    :cumu-probs cumu-probs                    :mean       (dot-product probs points)})))(defn smooth-ecdf  ([x emp-distr] (smooth-ecdf x emp-distr false))  ([x emp-distr truncate-extrema]   {:pre [(every? #{1} (map - (rest (:points emp-distr)) (butlast (:points emp-distr))))]}   (let [xs (:points emp-distr)         ys (:cumu-probs emp-distr)         first-x (first xs)         last-x (last xs)         low-limit (- first-x (if truncate-extrema 0 (/ 2))) ; first interval [1.0;  1.5] or [0.5;  1.5]         high-limit (+ last-x (if truncate-extrema 0 (/ 2))) ; last interval  [9.5; 10.0] or [9.5; 10.5]         z (+ (max (min x high-limit) low-limit) (/ 2))     ; shift by 0.5 for convenience         z-base (long (max (min (Math/floor z) last-x) first-x))         idx-base (Collections/binarySearch xs z-base)         at-start (= idx-base 0)         at-end (>= idx-base (dec (count xs)))         left-x (- (nth xs idx-base) (if (and truncate-extrema at-start) 0 (/ 2)))         left-y (nth ys idx-base)         right-x (+ (nth xs idx-base) (if (and truncate-extrema at-end) 0 (/ 2)))         right-y (nth ys (min (inc idx-base) (dec (count ys))))         ]     (linear-interp left-x left-y right-x right-y x :flat))))(defn old-smooth-ecdf  "Smooth cumulative probability function for an empirical discrete distribution.  The 'distr-points' must be a consecutive sequence of integers i, i+1, i+2,...  Each point x 'covers' an interval [x - 0.5; x + 0.5), and the cumulative  probability is reached at x + 0.5.  The intermediate points are interpolated linearly on probability densities.  Optionally extreme points may be handled otherwise:    With truncate-extrema disabled (default),       0.0 cumu-prob reached at -0.5 from the first point;       1.0 cumu-prob reached at +0.5 from the last point.    With truncate-extrema enabled,      0.0 cumu-prob reached at the first point;      1.0 cumu-prob reached at the last point;      linear slope at extremes is twice the probability density.  Examples:    (def points [(/ 2 5) (/ 3 5) 1 (/ 7 5) (/ 3 2) (/ 8 5) (/ 21 10)                 (/ 5 2) (/ 17 5) 4 (/ 23 5) 5 (/ 27 5) (/ 11 2) 8])    (map #(smooth-ecdf % '(1 2 3 4 5) '(20 15 35 25 5) false) points)    => (0N 1/50 1/10 9/50 1/5 43/200 29/100 7/20 133/200 33/40 191/200 39/40 199/200 1N 1N)    (map #(smooth-ecdf % '(1 2 3 4 5) '(20 15 35 25 5) true) points)    => (0N 0N   0N   4/25 1/5 43/200 29/100 7/20 133/200 33/40 24/25   1N    1N      1N 1N)  "  ([x distr-points distr-freq] (old-smooth-ecdf x distr-points distr-freq false))  ([x distr-points distr-freq truncate-extrema]   {:pre [(every? #{1} (map - (rest distr-points) (butlast distr-points)))]}   (let [firstp (first distr-points)         lastp (last distr-points)         low-limit (- firstp (if truncate-extrema 0 (/ 2))) ; first interval [1.0;  1.5] or [0.5;  1.5]         high-limit (+ lastp (if truncate-extrema 0 (/ 2))) ; last interval  [9.5; 10.0] or [9.5; 10.5]         y (+ (max (min x high-limit) low-limit) (/ 2))     ; shift by 0.5 for convenience         y-base (long (max (min (Math/floor y) lastp) firstp))         idx-base (Collections/binarySearch distr-points y-base)         probs (normalize-freq distr-freq)         cumu-sums (reductions + 0 probs)         at-start (= idx-base 0)         at-end (>= idx-base (dec (count distr-points)))         lin-multi (cond                                    ; determine slope-multiplier for linear interpolation                     (and truncate-extrema at-start) (* 2 (- y y-base (/ 2)))                     (and truncate-extrema at-end) (* 2 (- y y-base))                     :else (- y y-base))]     (+       (nth cumu-sums idx-base)                             ; base probability (left-side cumulative)       (* lin-multi (nth probs idx-base))))))               ; linear addition on top of base