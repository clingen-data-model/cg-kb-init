(ns chromatic-data.overlaps
  (:require [chromatic-data.neo4j :as neo])
  (:import [org.neo4j.driver.v1 Driver GraphDatabase AuthTokens Session StatementResult Record]))


;; exon http://purl.obolibrary.org/obo/SO_0000147

;; Algorithm:
;; Query for ordered list of regions using a specific build by chromosome
;; Iterate through the ordered list
;; When the start of a region is encountered, add it to the 'open' list
;; (the open list contains also a sorted list of close positions of 'open' regions.)
;; Before adding it to the list, 'close' any open regions that end before the start
;; of the region being added.
;; closing entails:
;; computing the reciprocal overlap between the region being closed and any remaining open regions
;; removing the closed region from the open list
;; at end of iteration, close all remaining open regions.
;; process can be structured as a reduction
;; with the reduction returning at every iteration the list of 'open' regions.
;; and the code closing the list at the very end.

;; (defn close-regions
;;   "Close the list of open regions to end-coordinate, return the list of remaining
;;   open regions"
;;   [region-list end-coordinate]
;;   (loop [regions region-list]
;;     (let [region (first region-list)
;;           remaining (rest region-list)]
;;       (println "closing region" region)
;;       ;; TODO, begin here designing function
;;       (if (seq remaining)
;;         (recur remaining))
;;       remaining)))

(defn select-regions-to-close
  "Select the list of regions to close, then pass that to close-regions"
  [open-list end-coordinate]
  (println "closing regions before: " end-coordinate)
  (let [pairs-to-close (take-while #(< (first %) end-coordinate) open-list)
        keys-to-remove (map first pairs-to-close)
        regions-to-close (map second pairs-to-close)
        remaining-regions (apply (partial dissoc open-list) keys-to-remove)]
    (println "regions-to-close" regions-to-close)
    (println "keys-to-remove" keys-to-remove)
    ;; (close-regions regions-to-close)
    remaining-regions))

(defn open-region
  "Append a region to the list of open regions, closing open regions as necessary
  first. Return list of remaining open regions"
  [open-list new-region]
  (println (format "opening region: %s" new-region))
  (let [remaining-open (select-regions-to-close open-list (new-region "cx.start"))]
    (assoc remaining-open (new-region "cx.end") new-region)))

(defn compute-overlaps
  "Populate the graph with information about overlaps among a set of regions
  specified by a Cypher query"
  []
  (neo/session
   [session]
   (let [query-str "match (cx:region_context)-->(r:region) where cx.chromosome = '22' return cx.start, cx.end, cx.chromosome, ID(cx) order by cx.start limit 10"
         regions (neo/hquery session query-str)
         end-regions (reduce open-region (sorted-map) regions)]
     (println (format "regions at end: %s" end-regions)))))
