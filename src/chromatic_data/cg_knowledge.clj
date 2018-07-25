(ns chromatic-data.cg-knowledge
  (:require [chromatic-data.schema :as schema]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [chromatic-data.neo4j :as neo]
            [clojure-csv.core :as csv]
            [chromatic-data.owl :as owl]
            [chromatic-data.fetch :as fetch]
            [chromatic-data.gene :as gene]
            [chromatic-data.pw-curation-import :as pw]
            [chromatic-data.ncbi-dosage-import :as dosage]
            [chromatic-data.clinvar-process :as cv-proc]
            [chromatic-data.clinvar-import :as cv-import]
            [chromatic-data.omim :as omim]
            [chromatic-data.rdf :as rdf])
  (:import java.io.PushbackReader))

;; List of external assets to retrieve and the method to be used to import them
(def external-data
  [
   ["https://github.com/The-Sequence-Ontology/SO-Ontologies/raw/master/so.owl" "data/so.owl" :import-rdf]
   ["http://purl.obolibrary.org/obo/mondo.owl" "data/mondo.owl" :import-rdf]
   ;;["ftp://ftp.ebi.ac.uk/pub/databases/genenames/new/json/locus_groups/protein-coding_gene.json" "data/protein-coding_gene.json" :import-genes]
                                        ;["http://www.ensembl.org/biomart/martservice?query=%3C?xml%20version=%221.0%22%20encoding=%22UTF-8%22?%3E%3C!DOCTYPE%20Query%3E%3CQuery%20%20virtualSchemaName%20=%20%22default%22%20formatter%20=%20%22CSV%22%20header%20=%20%220%22%20uniqueRows%20=%20%221%22%20count%20=%20%22%22%20datasetConfigVersion%20=%20%220.6%22%20%3E%3CDataset%20name%20=%20%22hsapiens_gene_ensembl%22%20interface%20=%20%22default%22%20%3E%3CFilter%20name%20=%20%22biotype%22%20value%20=%20%22protein_coding%22/%3E%3CAttribute%20name%20=%20%22ensembl_gene_id%22%20/%3E%3CAttribute%20name%20=%20%22exon_chrom_start%22%20/%3E%3CAttribute%20name%20=%20%22exon_chrom_end%22%20/%3E%3CAttribute%20name%20=%20%22rank%22%20/%3E%3CAttribute%20name%20=%20%22ensembl_exon_id%22%20/%3E%3C/Dataset%3E%3C/Query%3E" "data/ensembl-gene-exons.csv" :import-exons]
                                        ; ["ftp://ftp.ncbi.nlm.nih.gov/pub/clinvar/xml/ClinVarFullRelease_00-latest.xml.gz" "data/clinvar.xml.gz" :import-clinvar]
   ;;["http://www.orphadata.org/data/ORDO/ordo_orphanet.owl.zip" "data/ordo_orphanet.owl.zip" :update-ontology]
   
   ;;["http://purl.obolibrary.org/obo/doid.owl" "data/doid.owl" :update-ontology]
   ;;["http://data.bioontology.org/ontologies/RXNORM/submissions/12/download?apikey=8b5b7825-538d-40e0-9e9e-5ab9274a9aeb" "data/rxnorm.ttl" :import-ontology-classes]
   ;;["https://www.clinicalgenome.org/curated-json-for-search/" "data/curated-json-for-search.json" :pw-curations]
   ;;["https://data.omim.org/downloads/U7rx7IRhSIah-gm1M-yBDA/genemap2.txt" "data/genemap2.txt" :omim-genes]
   ;;["ftp://ftp.ncbi.nlm.nih.gov/pub/dbVar/clingen/ClinGen_gene_curation_list.tsv" "data/ClinGen_gene_curation_list.tsv" :gene-dosage]
   ])

(def asset-import-functions
  {:import-ontology owl/import-ontology
   :import-genes gene/create-genes
   :update-ontology owl/update-class-metadata
   :pw-curations pw/import-cg-data
   :gene-dosage dosage/import-gene-dosage
   :import-ontology-classes owl/import-ontology-classes
   :import-exons gene/create-exons
   :omim-genes omim/import-genemap2
   :import-rdf rdf/import-rdf})


(def post-update-queries
  ;; TODO update to reflect disease grouping around MONDO ids
  ["match (n:Resource)-[:type]->(:Resource {iri: 'http://www.w3.org/2002/07/owl#Class'}) set n :RDFClass;"
"match (s:Resource) where s.iri starts with 'http://purl.obolibrary.org/obo/MONDO' set s :DiseaseConcept:Disease:Condition"
   "match (g:GeneDiseaseAssertion)-[rel:has_object]->(r:RDFClass)-[:equivalentTo]-(d:DiseaseConcept) merge (g)-[:has_object]->(d) delete rel"
   "match (c:RDFClass)<-[:has_object]-(a:Assertion) set c :Disease:Condition"
   "match (c:RDFClass {iri: 'http://datamodel.clinicalgenome.org/terms/CG_000001'})<-[:subClassOf*]-(s:RDFClass) set s :Interpretation"
   "match (c:RDFClass) where c.iri contains 'RXNORM' set c :Drug"
   "match (c:Condition) set c.search_label = toUpper(c.label)"
   "match (d:Drug) set d.search_label = toUpper(d.label)"
   "match (g:Gene)<-[:has_subject]-(a:Assertion) with g, max(a.date) as date set g.last_curated = date"
   "match (g:Condition)<-[:has_object]-(a:Assertion) with g, max(a.date) as date set g.last_curated = date"
   "match (g:Gene) set g.num_curations = 0"
   "match (c:Condition) set c.num_curations = 0"
   "match (g:Gene)<- [:has_subject]- (a:Assertion) with g, count (a) as assertions set g.num_curations = assertions"
   "match (c:Condition)<- [:has_object]- (a:Assertion) with c, count (a) as assertions set c.num_curations = assertions"
   "match (g:Gene) set g.search_label = g.symbol"
   "match (g:Gene) where g.alias_symbol is not null  set g.search_label = g.symbol + \" \" + reduce(s = \"\", x in g.alias_symbol | s + x + \" ,\")"
   "match (g:Gene) where g.entrez_id is not null set g.iri = 'https://www.ncbi.nlm.nih.gov/gene/' + g.entrez_id"
   "match (c:Condition) set c.search_label = toUpper(c.label) + \" \" + toUpper(c.synonym)"])

(defn run-post-update-queries
  "Label nodes and prepopulate properties for search"
  [queries]
  (neo/session
   [s]
   (doseq [q queries]
     (println "running: " q)
     (.run s q))))

(defn select-data-source
"from the external-data array, select the data sources that match the given
  category"
  [external-data-list & tags]
  (map #(get % 1) (filter #(some (set tags) %) external-data-list)))

(defn get-ontology-name
  "Trim extensions (eg .zip) from path to ontology"
  [ontology-path]
  (first (re-find #"^.+(\.owl|\.ttl)" ontology-path)))

(defn trim-zip-ext
  "Trim the zip extension (gz/zip) to retrieve the final file name. Return
  input if none"
  [path]
  (if-let [r (re-find #"^(.+)(\.zip|\.gz)$" path)]
       (second r)
       path))

(defn import-asset
  [[_ path type]]
  (let [trimmed-path (trim-zip-ext path)
        import-function (type asset-import-functions)]
    (println "Importing " trimmed-path)
    (import-function trimmed-path)))

(defn init-kb
  "Build the ClinGen Knowledgebase from scratch"
  [& optsarr]
  (schema/cg-indexes)
  (let [opts (set optsarr)]
    (when (:refresh opts)
      (println "retrieving remote assets")
      (fetch/fetch-all-remote-assets external-data))
    (rdf/import-rdf "ontology/clingen.owl")
    (when-not (:base-only opts) (doseq [a external-data]
                                  (import-asset a)))
    (when-not (:skip-post opts) (run-post-update-queries post-update-queries))))

(defn update-kb
  "Download and update the aspects of the ClinGen KB that need periodic refreshment"
  [& opts]
  (let [items (filter #(contains? #{:import-rdf} (nth % 2)) external-data)]
    (fetch/fetch-all-remote-assets items)
    (doseq [i items]
      (import-asset i))
    (run-post-update-queries post-update-queries)))
