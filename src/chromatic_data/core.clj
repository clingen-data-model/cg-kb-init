(ns chromatic-data.core
  (:require [chromatic-data.gene :as gene]
            [chromatic-data.owl :as owl]
            ;; TODO redo the orpha integration code with Bolt driver
            ;; [chromatic-data.orpha :as orpha]
;;            [chromatic-data.cg-sv :as cg-sv]
            [chromatic-data.mycode-sv :as mycode-sv]
            [chromatic-data.mycode-pheno :as mycode-pheno]
            [chromatic-data.neo4j :as neo]
            ;;[chromatic-data.cg-dosage :as cg-dosage]
            [chromatic-data.overlaps :as overlaps]
            [chromatic-data.region :as region]
            [chromatic-data.cg-knowledge :as cg-kb]
            [chromatic-data.clinvar-import :as cv]
            [chromatic-data.sv :as sv])
  (:gen-class))

(defn -main
  "Main function to kick off data load into Neo4j"
  [& args])

