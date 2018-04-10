(ns chromatic-data.gene
  (:require [clojure-csv.core :as csv]
            [clojure.string :as string]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [chromatic-data.neo4j :as neo])
  (:import [org.neo4j.driver.v1 Driver GraphDatabase AuthTokens Session StatementResult Record]))


;; Necessary actions to import all entities described here (requires scigraph based so first):
;; read-genes
;; label-genes
;; import-exons


;; TODO import all regions with inner_start and outer_stop set to same value where start, stop
;; are known
(defn read-genes
  [genes-path]
  (let [hgncdata (json/read (io/reader genes-path))]
    ((hgncdata "response") "docs")))

(defn create-genes
  [genes-path]
  (let [genes (read-genes genes-path)]
    (neo/session
     [session]
     (.run
      session
      "unwind {props} as properties merge (n:Gene:Region {uuid: properties.uuid}) set n = properties"
      {"props" genes}))))


(defn label-genes
  "attach to protein-coding genes the appropriate SO label"
  []
  (neo/session
   [session]
   (neo/query session "match (r:Gene {locus_group: 'protein-coding gene'}), (sf:sequence_feature {iri:'http://purl.obolibrary.org/obo/SO_0001217'}) merge (r)-[:is_a]->(sf)")))

(defmacro doseq-indexed [index-sym [item-sym coll] & body]
  `(doseq [[~item-sym ~index-sym]
           (map vector ~coll (range))]
     ~@body))

(defn retrieve-hgnc-ids
  "Return a set of HGNC-IDs in the genes database"
  []
  (let [result (neo/session
                [s]
                (neo/hquery s "match (g:Gene) return g.refseq_id as refseq"))]
    (into #{} (map #(get % "refseq") result))))

(defn create-exons
  "Import exons from Ensembl Biomart"
  [path]
  (with-open [csv-file (io/reader path)]
    (let [exon-table (-> csv-file csv/parse-csv rest)
          exon-list (map #(zipmap ["gene_id" "start" "stop" "seq" "id"] %) exon-table)]
      (neo/session
       [session]
       (doseq [batch (partition-all 1000 exon-list)]
         (.writeTransaction
          session
          (neo/tx
           ["unwind $exons as exon match (g:Gene {ensembl_gene_id: exon.gene_id}) merge (e:Region:Exon {iri: exon.id + '-' + exon.seq , sequence: exon.seq}) merge (cx:RegionContext {start: exon.start, stop: exon.stop, iri: 'CXGRCh38' + exon.id + '-' + exon.seq}) merge (e)-[:has_context]->(cx) merge (g)-[:has_exon]->(e)"
            {"exons" batch}])))))))

;; ;; TODO label build when creating exons
;; (defn create-exons
;;   "Import exons from UCSC download"
;;   [path]
;;   ; Per https://www.biostars.org/p/93011/
;;   ; Recommend indexing ucsc_id in genes first.
;;   (with-open [csv-file (io/reader path)]
;;     (let [exons-csv (csv/parse-csv csv-file :delimiter \tab)
;;           exons (map #(->> % (zipmap ["chr" "start" "stop" "id"])
;;                            (merge {"gene" (re-find #"NM_[0-9]*" (get % 3))
;;                                    "seq" (second (re-find #"exon_(\d+)" (get % 3)))}))
;;                      exons-csv)
;;           gene-ids (retrieve-hgnc-ids)
;;           filtered-exons (filter #(contains? gene-ids (% "gene")) exons)]
;;       (println (count filtered-exons))
;;       (clojure.pprint/pprint (take 5 filtered-exons))
;;       (neo/session
;;        [session]
;;        (doseq [batch (partition-all 1000 filtered-exons)]
;;          (.writeTransaction
;;           session
;;           (neo/tx 
;;            ["unwind $exons as exon match (g:Gene {refseq_accession: exon.gene}) merge (e:Region:Exon {iri: exon.gene + \"-\" + exon.seq})<-[:has_exon]-(g) set e.sequence = exon.seq merge (cx:RegionContext {id: exon.id})<-[:has_context]-(e) set cx += {chromosome: exon.chr, start: exon.start, stop: exon.stop}" 
;;             {"exons" batch}]))))
;;       )))

(defn create-chromosomes
  "Import chromosomes and map regions to them"
  []
  (let [chromosomes (concat (map str (range 1 23)) ["X" "Y"])
        arms ["p" "q"]]
    (neo/session
     [session]
     (doseq [c chromosomes]
       (.run session "merge (c:Chromosome {label: $chr})" {"chr" c})
       (.run session "match (c:Chromosome {label: $chr}) match (r:Region)-[:has_context]->(cx:RegionContext {chromosome: $chr}) merge (r)<-[:has_member]-(c)" {"chr" c})
       (.run session "match (c:Chromosome {label: $chr}) match (r:Region)-[:has_context]->(cx:RegionContext {chromosome: (\"chr\" + $chr)}) merge (r)<-[:has_member]-(c)" {"chr" c})
       (doseq [a arms]
         (.run session "match (c:Chromosome {label: $chr}) match (g:Gene) where g.location starts with $loc match (e:Exon)<-[:has_exon]-(g) merge (c)-[:has_member]->(g) merge (c)-[:has_member]->(e)" {"chr" c, "loc" (str c a)}))))))


;; Import integer types as integers
;; TODO rewrite with chromatic-data/neo4j interaction macro
;; (defn import-exons
;;   "Import exons from ucsc gene annotations into neo4j database"
;;   []
;;   (let [driver (GraphDatabase/driver "bolt://localhost" (AuthTokens/basic "neo4j" "clingen"))
;;         session (.session driver)
;;         ucsc-genes (rest (csv/parse-csv (slurp "data/ucsc-hg.bed") :delimiter \tab))]
;;     (doseq [gene ucsc-genes]
;;       (let [ucsc-id (get gene 0)
;;             chrom (get gene 1)
;;             exons (map vector (string/split (get gene 8) #",") (string/split (get gene 9) #","))
;;             query (format "match (g:region {ucsc_id: '%s'}), (exontype:sequence_feature {iri:'http://purl.obolibrary.org/obo/SO_0000147'}) optional match (g)<-[:member_of]-(ex:region)-[:is_a]->(exontype) where ex is null return g.symbol as symbol, g.ucsc_id as ucsc_id" ucsc-id)
;;             result (iterator-seq (.run session query))]
;;         (doseq [record result]
;;           (doseq-indexed i [exon exons]
;;                          (let [import-stmt (format "match (g:region {ucsc_id: '%s'}), (ann:sequence_feature {iri:'http://purl.obolibrary.org/obo/SO_0000147'}) create (ex:region {sequence: %s}), (ex_cx:region_context {start: %s, end: %s, assembly: 'GRCh37', chrom: '%s'}) merge (ex)-[:member_of]->(g) merge (ex)-[:is_a]->(ann) merge (ex_cx)-[:member_of]->(ex)" ucsc-id i (first exon) (second exon) chrom)]
;;                            (.run session import-stmt))))))
;;     (.close session)
;;     (.close driver)))


