(ns chromatic-data.clinvar-import
  (:require [clojure.java.io :as io]
            [chromatic-data.neo4j :as neo]
            [clojure.edn :as edn]
            [clojure.walk :as walk])
  (:import java.io.PushbackReader
           java.util.UUID))

;; TODO change to ID
(defn import-region
  "Import region defined by clinvar CNV"
  [i session]
  (let [props (walk/stringify-keys (dissoc i :type :id :region))]
    (.writeTransaction 
     session 
     (neo/tx 
      ["merge (r:Region {iri: $region}) merge (cx:RegionContext {iri: $id}) set cx += $props merge (r)-[:has_context]->(cx)"
       {"id" (:id i) "region" (:region i) "props" props}]))))

(defn import-variation
  "Importing alteration defined by clinvar CNV, inc"
  [i session]
  (let [props (walk/stringify-keys (dissoc i :id :type :region))]
    (.writeTransaction
     session
     (neo/tx
      ["match (t:RDFClass {iri: $type}) merge (r:Region {iri: $region}) merge (v:Variation {iri: $id}) set v += $props merge (v)-[:has_type]->(t) merge (v)-[:has_region]->(r)" 
       {"props" props "region" (:region i) "type" (:type i) "id" (:id i)}]))))

;; TODO change to variation
(defn import-interpretation
  "Import interpretation defined by clinvar CNV, includes Neo4j session"
  [i session]
  (let [props (walk/stringify-keys (dissoc i :agent :id :type :variation :clinical-significance))
        uuid (-> i :id .getBytes UUID/nameUUIDFromBytes str)]
    (.writeTransaction
     session
     (neo/tx 
      ["match (s:RDFClass {iri: $clinsig}), (v:Variation {iri: $variant}) merge (i:VariantPathogenicityAssertion:Assertion {uuid: $uuid, iri: $id}) set i += $props merge (a:Agent {iri: $agent}) merge (i)-[:created_by]->(a) merge (i)-[:has_significance]->(s) merge (i)-[:interpretation_of]->(v)"
       {"id" (:id i) "uuid" uuid "props" props "clinsig" (:clinical_significance i) "variant" (:variation i) "agent" (:agent i)}]))))

(defn import-clinvar-cnvs
  "Import ClinVar CNVs from intermediate format in EDN"
  []
  (with-open [r (PushbackReader. (io/reader "data/clinvar_interps.edn"))]
    (neo/session
     [session]
     (let [interps (edn/read r)]
       (doseq [i interps]
         (doseq [n i]
           (case (:type n)
             "http://purl.obolibrary.org/obo/SEPIO_0000190" (import-interpretation n session)
             "http://datamodel.clinicalgenome.org/terms/CG_000117" (import-region n session)
             ("http://purl.obolibrary.org/obo/SO_0001912"
              "http://purl.obolibrary.org/obo/SO_0001911")
             (import-variation n session)
             (println (str "no match for " (:type n))))))))))
