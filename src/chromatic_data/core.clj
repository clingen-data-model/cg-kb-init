(ns chromatic-data.core
  (:require [chromatic-data.cg-knowledge :as cg-kb])
  (:gen-class))

(defn -main
  "Main function to kick off data load into Neo4j"
  [& args]
  (println "updating neo4j")
  (cg-kb/update-kb))

