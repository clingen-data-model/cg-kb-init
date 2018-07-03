(ns chromatic-data.actionability
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.pprint :as pp :refer [pprint]]))

(def act-path "data/actionability.json")

(defn import-actionability
  "Import actionability records from actionability API (not data exchange message"
  []
  (let [records (json/parse-stream (io/reader act-path))
        r (-> records first second first (get "ActionabilityDocID"))]
    (let [id (get r "value")
          props (get r "properties")
          genes (map #(get-in % ["Gene" "properties" "HGNCId" "value"])
                     (get-in props ["Genes" "items"]))
          diseases (map #(get-in % ["OmimID" "value"])
                        (get-in props ["Syndrome" "properties" "OmimIDs" "items"]))
          outcomes (map #(get % "Outcome")
                        (get-in props ["Score" "properties" "Final Scores" "properties"
                                       "Outcomes" "items"]))]
      (pprint id)
      (pprint (keys props))
      (pprint genes)
      (pprint diseases)
      (doseq [o outcomes]
        (let [title (get o "value")
              props (get o "properties")
              severity (get-in props ["Severity" "value"])
              likelihood (get-in props ["Likelihood" "value"])]
          (pprint title)
          (pprint severity)
          (pprint likelihood))))))
