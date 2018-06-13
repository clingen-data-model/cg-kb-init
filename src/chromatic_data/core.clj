(ns chromatic-data.core
  (:require [chromatic-data.gene :as gene]
            [chromatic-data.owl :as owl]
            [chromatic-data.neo4j :as neo]
            ;;[chromatic-data.cg-dosage :as cg-dosage]
            [chromatic-data.overlaps :as overlaps]
            ;;[chromatic-data.region :as region]
            [chromatic-data.cg-knowledge :as cg-kb]
            [chromatic-data.clinvar-import :as cv]
            [chromatic-data.sv :as sv]
            [chromatic-data.clinvarplus-process-betaxml :as cvp])
  (:gen-class))

(defn -main
  "Main function to kick off data load into Neo4j"
  [& args])

