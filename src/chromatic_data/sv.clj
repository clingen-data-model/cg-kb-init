(ns chromatic-data.sv
  (:require [chromatic-data.neo4j :as neo]
            [clojure.pprint :as pp]
            [clojure.walk :as walk]
            [clojure.java.io :as io]
            [clojure.core.async :as async]
            [clojure-csv.core :as csv]
            [clojure.java.shell :refer [sh]]))

(def default-build "GRCh38")

(def ncbi-map {"GRCh37" "GCF_000001405.13"
              "GRCh38" "GCF_000001405.26"})

(def remap-path "bin/remap_api.pl")

(defn retrieve-regions
  "Retrieve the list of regions for use in query"
  [session chr]
  (walk/keywordize-keys (neo/hquery session "match (r:Region)-[:has_context]->(cx:RegionContext) match (a:Assembly {label: $build}) match (c:Chromosome {label: $chr}) where (r)<-[:has_member]-(c) and (cx)-[:mapped_on]->(a) return r.iri as iri, coalesce(cx.start, cx.inner_start, cx.outer_start) as start, coalesce(cx.stop, cx.inner_stop, cx.outer_stop) as stop order by start"
                                    {"build" default-build, "chr" chr})))

(defn map-regions-to-rows
  "Map a set of coordinates (inner, outer, or not, specified by prefix) to 
  rows suitable for export to bedfile"
  [regions prefix]
  (let [st (str "cx." prefix "start")
        stop (str "cx." prefix "stop")]
    (map #(vec [(% "c.label")
                (% st)
                (% stop)
                (str prefix "-" (% "r.iri"))])
         (filter #(and (% st) (% stop))
                 regions))))


(defn export-liftover-regions
  "Remap variants lacking mappings on one assembly to another"
  [from to outfile]
  (neo/session
   [session]
   (let [regions (neo/hquery session "match (r:Region)-[:has_context]->(cx:RegionContext)-[:mapped_on]->(a:Assembly {label: $from}) where not (r)-[:has_context]->(:RegionContext)-[:mapped_on]->(:Assembly {label: $to}) match (r)<-[:has_member]-(c:Chromosome) return r.iri, cx.iri, cx.outer_start, cx.start, cx.inner_start, cx.inner_stop, cx.stop, cx.outer_stop, c.label" 
                       {"from" from, "to" to})
         regions-for-bed (mapcat #(map-regions-to-rows regions %) ["inner_" "outer_" ""])]
     (println (take 5 regions-for-bed))
     ;(println (take 5 regions))
     (spit outfile (csv/write-csv regions-for-bed :delimiter \tab)))))

(defn compose-region-import-map
  "Given a list of regions to import, compose into lazy seq of
  hashes suitable for neo import"
  [regions target-build]
  (map (fn [[chr start stop prefix-id]] 
         (let [[_ prefix id] (re-find #"(\w*)-+(.*)" prefix-id)
               suffix (re-find #"\w+$" id)]
           {"region_iri" id
            (str prefix "start") (Integer/parseInt start)
            (str prefix "stop") (Integer/parseInt stop)
            "iri" (str "https://search.clinicalgenome.org/kb/contextual_regions/"
                         suffix target-build)}))
       regions))

(defn import-liftover-regions
  "Import bedfile generated from liftover script"
  [infile target-build]
  (with-open [csv-reader (io/reader infile)]
    (let [regions (-> csv-reader (csv/parse-csv :delimiter \tab)
                      (compose-region-import-map target-build))
          region-batch (partition-all 1000 regions)]
      (neo/session
       [session]
       (println (take 5 regions))
       (doseq [r region-batch]
         (println "merging 1000 more regions")
         (.writeTransaction 
          session
          (neo/tx
           ["unwind $regions as region match (r:Region {iri: region.region_iri}) match (a:Assembly {label: $asm}) merge (cx:RegionContext {iri: region.iri}) set cx += region merge (r)-[:has_context]->(cx) merge (cx)-[:mapped_on]->(a)"
            {"regions" r "asm" target-build}])))))))

(defn run-liftover-script
  "Execute NCBI remap tool"
  [start-build target-build source-file target-file]
  (println "Running NCBI remap tool")
  (let [cmd  ["perl" remap-path 
              "--mode" "asm-asm" "--from" 
              (ncbi-map start-build) 
              "--dest"
              (ncbi-map target-build)
              "--annotation"
              source-file
              "--annot_out"
              target-file]]
    (println cmd)
    (println (apply sh cmd))))

(defn liftover-regions
  "Move regions from one build to another"
  [start-build target-build]
  (let [source-file "data/liftover-start.bed"
        target-file "data/liftover-target.bed"]
    (export-liftover-regions start-build target-build source-file)
    (run-liftover-script start-build target-build source-file target-file)
    (Thread/sleep 500)
    (import-liftover-regions target-file target-build)))



;; TODO
;; -test this
;; -compute forward occlusion (currently only reports occlusion by already started
;;  fields)
(defn overlaps
  "Return % overlaps between regions by id [first second % occlusion of first by second"
  [remaining active overlap-list]

  (let [current (first remaining)
        new-remaining (rest remaining)
        new-active (doall (filter #(< (:start current) (:stop %)) active))
        size (- (:stop current) (:start current))
        new-overlaps (for [r new-active
                            :let [occl (- (min (:stop current) (:stop r))
                                          (:start current))
                                  pct-occl (double (/ occl size))
                                  o-size (- (:stop r) (:start r))
                                  o-pct-occl (double (/ occl o-size))]]
                       [(:iri current) (:iri r) pct-occl])
        other-overlaps (for [r new-active
                             :let [occl (- (min (:stop current) (:stop r))
                                           (:start current))
                                   o-size (- (:stop r) (:start r))
                                   o-pct-occl (double (/ occl o-size))]]
                         [(:iri r) (:iri current) o-pct-occl])
        total-overlaps (doall (concat overlap-list new-overlaps other-overlaps))]
    (if (seq new-remaining)
      (recur new-remaining (conj new-active current) total-overlaps)
      total-overlaps)))

(defn merge-overlaps
  "Merge result of overlap list into neo4j"
  [session overlap-list]
  (doseq [ol (partition-all 1000 overlap-list)]
    (println "merging next 1000")
    (.run session "unwind $overlaps as o match (r1:Region {iri: o[0]}) match (r2:Region {iri: o[1]}) merge (r1)-[:occluded_by {value: o[2]}]->(r2)" {"overlaps" ol})))

(defn compute-overlaps
  "Compute overlaps between regions on a given chromosome"
  [chr]
  (neo/session
   [session]
   (let [regions (retrieve-regions session chr)
         overlap-list (overlaps regions [] [])]
     (println "num overlaps: " (count overlap-list) " in " chr)
     ;; (with-open [w (io/writer "data/overlaps.edn")]
     ;;   (pp/pprint overlap-list w))
     (merge-overlaps session overlap-list))))

;; DONT FORGET to populate id for all regions, and build an index on id

(defn compute-all-overlaps
  "compute overlaps over all chromosomes"
  []
  (let [chromosomes (concat (map str (range 1 23)) ["X" "Y"])]
    (doseq [c chromosomes]
      (async/go (compute-overlaps c)))))
