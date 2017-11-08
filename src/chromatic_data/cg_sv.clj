(ns chromatic-data.cg-sv
  (:require [clojure-csv.core :as csv]
            [clojure.java.io :as io]
            [chromatic-data.neo4j :as neo]
            [clojure.java.shell :as sh])
  (:import [org.neo4j.driver.v1 Driver GraphDatabase AuthTokens Session StatementResult Record]))


;; TODO Give CNV region type of CNV: SO:0001019, alteration gets marked as copy number gain/loss

(def data-path "/home/tristan/code/chromatic-data/data/cg-sv/")
;; map of 
(def interpretation-mapping {"pathogenic" "pathogenic"
                             "likely pathogenic" "likely_pathogenic"
                             "uncertain" "uncertain_significance"
                             "likely benign" "likely_benign"
                             "benign" "benign"
                             "foo" "bar"})
(def interpretation-types (vals interpretation-mapping))

;; http://purl.obolibrary.org/obo/SO_0001742 -- copy_number_gain
;; http://purl.obolibrary.org/obo/SO_0001743 -- copy_number_loss

(defn create-interpretation-types
  "ensure types exist for interpretation types"
  []
  (neo/session 
   [session]
   (doseq [t interpretation-types]
     (.run session "merge (:clinical_significance {identifier: {id}})" {"id" t}))))

(defn associate-clinical-sig
  "associate (already created) interpretation nodes with appropriate
  clinical significance"
  []
  (neo/session
   [session]
   (doseq [sig (seq interpretation-mapping)]
     (let [mapping {"from" (first sig) "to" (second sig)}]
       (println mapping)
       (.run session "using periodic commit load csv with headers from 'file:///cg-sv.tab' as l fieldterminator '\t' with l where l.clinical_significance = {from} match (i:interpretation {identifier: l.variant_call_id}), (cs:clinical_significance {identifier: {to}}) merge (i)-[:has_clinical_significance]->(cs)" mapping)))))

(defn create-region-nodes
  "Create (new) nodes for regions accessioned in dbvar submission"
  []
  (neo/session
   [session]
   (.run session "using periodic commit load csv with headers from 'file:///cg-sv.tab' as l fieldterminator '\t' merge (r:region {identifier: l.variant_call_id})")
   ;; create alterations
   (.run session "using periodic commit load csv with headers from 'file:///cg-sv.tab' as l fieldterminator '\t' merge (a:alteration {identifier: l.variant_call_id})")
   ;; match alteration to region
   (.run session "using periodic commit load csv with headers from 'file:///cg-sv.tab' as l fieldterminator '\t' match (r:region {identifier: l.variant_call_id}), (a:alteration {identifier: l.variant_call_id} {copy_number: l.copy_number}) merge (r)<-[:variant_of]-(a)")
   ;; add type to alterations
   (.run session "using periodic commit load csv with headers from 'file:///cg-sv.tab' as l fieldterminator '\t' with l where l.variant_call_type = 'copy number loss' match (a:alteration {identifier: l.variant_call_id}), (cnl:sequence_feature {iri: 'http://purl.obolibrary.org/obo/SO_0001743'}) merge (a)-[:is_a]->(cnl)")
   (.run session "using periodic commit load csv with headers from 'file:///cg-sv.tab' as l fieldterminator '\t' with l where l.variant_call_type = 'copy number loss' match (a:alteration {identifier: l.variant_call_id}), (cng:sequence_feature {iri: 'http://purl.obolibrary.org/obo/SO_0001742'}) merge (a)-[:is_a]->(cng)")
   ;; create interpretations
   (.run session "using periodic commit load csv with headers from 'file:///cg-sv.tab' as l fieldterminator '\t' match (a:alteration {identifier: l.variant_call_id}) merge (i:interpretation {identifier: l.variant_call_id}) merge (i)-[:interpretation_of]->(a)")
   ;; update interpretations with experiment_id, needed to link attributions
   (.run session "using periodic commit load csv with headers from 'file:///cg-sv.tab' as l fieldterminator '\t' match (i:interpretation {identifier: l.variant_call_id}) set i.experiment_id = l.experiment_id")
   ;; add attributions to interpretations
   (.run session "using periodic commit load csv with headers from 'file:///cg-sv-exp.tab' as l fieldterminator '\t' with l where not l.site = '' match (i:interpretation {experiment_id: l.experiment_id}) merge (a:agent {identifier: l.site}) merge (i)-[:wasAttributedTo]->(a)")
   ;; add feature context
   (.run session "using periodic commit load csv with headers from 'file:///cg-sv.tab' as l fieldterminator '\t' match (r:region {identifier: l.variant_call_id}) merge (r)-[:has_context]->(cx:region_context {assembly: l.assembly}) set cx = {outer_start: l.outer_start, inner_start: l.inner_start, inner_stop: l.inner_stop, outer_stop: l.outer_stop, chromosome: l.chr, assembly: l.assembly}")))

(defn import-calls
  "Import calls from ClinGen SV submission. Expects calls to be unpacked into data/cg-sv"
  []
  (neo/stage-file (str data-path "VARIANT CALLS.txt") "cg-sv.tab")
  (neo/stage-file (str data-path "EXPERIMENTS.txt") "cg-sv-exp.tab"))

(defn export-mapping-bed
  "export bedfile with build 36 identifiers to be mapped to build 37"
  []
  (neo/session 
   [session]
   (let [variants (neo/query session "match (r:region)-[:has_context]->(cx:region_context {assembly: 'NCBI36'}) where not (cx.inner_start is null or cx.inner_stop is null) return cx.chromosome, cx.inner_start, cx.inner_stop, r.identifier")]
     (spit "/tmp/cg.bed" (csv/write-csv variants :delimiter \tab)))
   (let [variants (neo/query session "match (r:region)-[:has_context]->(cx:region_context {assembly: 'NCBI36'}) where not (cx.outer_start is null or cx.outer_stop is null) return cx.chromosome, cx.outer_start, cx.outer_stop, r.identifier")]
     (spit "/tmp/cg-outer.bed" (csv/write-csv variants :delimiter \tab)))))

(defn remap-bedfile
  "Call NCBI remap service. Assume remap_api.pl exists, is in project bindir
  and perl exists on system and dependencies are installed"
  []
  (sh/sh "perl" "bin/remap_api.pl" "--mode" "asm-asm" "--from" "GCF_000001405.12" "--dest" "GCF_000001405.13" "--annotation" "/tmp/cg.bed" "--annot_out" "/tmp/cg-remap.bed")
  (sh/sh "perl" "bin/remap_api.pl" "--mode" "asm-asm" "--from" "GCF_000001405.12" "--dest" "GCF_000001405.13" "--annotation" "/tmp/cg-outer.bed" "--annot_out" "/tmp/cg-remap-outer.bed"))

(defn import-mapping-bed
  "Import resultant bedfile from NCBI tool"
  []
  ;; Trim the header on the bedfiles
  (sh/sh "sed" "-i" "-e" "1,3d" "/tmp/cg-remap.bed")
  (sh/sh "sed" "-i" "-e" "1,3d" "/tmp/cg-remap-outer.bed")
  (neo/stage-file "/tmp/cg-remap.bed" "cg-remap.bed")
  (neo/stage-file "/tmp/cg-remap-outer.bed" "cg-remap-outer.bed")
  (neo/session
   [sesson]
   (.run sesson "load csv from 'file:///cg-remap.bed' as l fieldterminator '\t' match (r:region {identifier: l[3]}) merge (r)-[:has_context]->(cx:region_context {assembly: 'GRCh37'}) set cx.chromosome = l[0], cx.inner_start = l[1], cx.inner_stop = l[2]")
   (.run sesson "load csv from 'file:///cg-remap-outer.bed' as l fieldterminator '\t' match (r:region {identifier: l[3]}) merge (r)-[:has_context]->(cx:region_context {assembly: 'GRCh37'}) set cx.chromosome = l[0], cx.inner_start = l[1], cx.inner_stop = l[2]")))





