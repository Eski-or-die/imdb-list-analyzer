;; Testing IMDb data handling.;; Tested on Clojure 1.5.1;;;; Esa Junttila;; 2013-06-30(ns imdb-list-analyzer.imdb-data-test  (:require [clojure.test :refer :all]            [imdb-list-analyzer.imdb-data :refer :all]))(deftest csv-read  (testing "Read all 1388 rows from the example CSV file."    (is (= (count (read-raw-data "resources/example_ratings.csv")) 1388))))(deftest example-imdb-data-read  (testing "Read all 1388 rows in parsed form."    (is (= (count (read-imdb-data "resources/example_ratings.csv")) 1388))))