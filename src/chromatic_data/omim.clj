(ns chromatic-data.omim
  (require [clojure-csv.core :as csv]
           [clojure.java.io :as io]
           [chromatic-data.neo4j :as neo]
           [clojure.pprint :as pp :refer [pprint]]))


(defn list-titles
  [titles]
  (doseq [n (range 0 (count titles))]
    (println n ": " (nth titles n))))

(defn entrez-to-pheno
  "Map an imput row from the OMIM genemap2 table to a set of entrez ids and
  associated phenotypes"
  [row]
  (if (< 12 (count row))
    (vector (nth row 9)
            (vec (map 
                  #(str "http://purl.obolibrary.org/obo/OMIM_" %)
                  (re-seq #"\d{4,}" (nth row 12)))))
    [nil nil]))

(defn import-genemap2
  "Import omim-provided genemap2 file into neo4j"
  []
  (let [path "data/genemap2.txt"]
      (println "importing genemap2")
    (let [csv (-> path io/reader (csv/parse-csv :delimiter \tab))
          titles (nth csv 3)
          recs (drop 4 csv)
          gene-pheno-list (vec (filter #(and (not= "" (first %)) (second %))
                                       (map entrez-to-pheno recs)))]
      (neo/session
       [session]
       (.run session "UNWIND $recs as r
    MATCH (g:Gene {entrez_id: r[0]})
    MATCH (c:RDFClass) WHERE c.iri in r[1]
    MERGE (g)-[:has_related_phenotype]->(c)"
             {"recs" gene-pheno-list})))))
