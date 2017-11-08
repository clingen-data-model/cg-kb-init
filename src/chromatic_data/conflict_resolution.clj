(ns chromatic-data.conflict-resolution
  (:require [chromatic-data.neo4j :as neo]
            [clojure-csv.core :as csv]))


(def query "match (dval:RDFClass {iri: 'http://datamodel.clinicalgenome.org/terms/CG_000092'})<-[:has_predicate]-(a:GeneDosageAssertion)-[:has_subject]->(g:Gene)-[:has_exon]-(e:Exon)<-[:occluded_by]-(r:Region)<-[:has_region]-(v:Variation)<-[:interpretation_of]-(i:VariantPathogenicityAssertion)-[:has_significance]->(sig:RDFClass) match (r)-[:has_context]->(cx:RegionContext)-[:mapped_on]->(:Assembly {label: 'GRCh38'}) match (r)<-[:has_member]-(c:Chromosome) match (agent:Agent)<-[:created_by]-(i) where (v)-[:has_type]->(:RDFClass {iri: 'http://purl.obolibrary.org/obo/SO_0001912'}) and sig.iri in ['http://datamodel.clinicalgenome.org/terms/CG_000109', 'http://datamodel.clinicalgenome.org/terms/CG_000108', 'http://datamodel.clinicalgenome.org/terms/CG_000107'] return distinct(v.iri), sig.label, cx.outer_start, cx.inner_start, cx.inner_stop, cx.outer_stop, c.label, g.symbol, agent.iri, v.`copy-number`;")
; Start query:
;match (dval:RDFClass {iri: "http://datamodel.clinicalgenome.org/terms/CG_000092"})<-[:has_predicate]-(a:GeneDosageAssertion)-[:has_subject]->(g:Gene)-[:has_exon]-(e:Exon)<-[:occluded_by]-(r:Region)<-[:has_region]-(v:Variation)<-[:interpretation_of]-(i:VariantPathogenicityAssertion)-[:has_significance]->(sig:RDFClass) match (r)-[:has_context]->(cx:RegionContext)-[:mapped_on]->(:Assembly {label: "GRCh38"}) match (r)<-[:has_member]-(c:Chromosome) match (agent:Agent)<-[:created_by]-(i) where (v)-[:has_type]->(:RDFClass {iri: "http://purl.obolibrary.org/obo/SO_0001912"}) and sig.iri in ["http://datamodel.clinicalgenome.org/terms/CG_000109", "http://datamodel.clinicalgenome.org/terms/CG_000108", "http://datamodel.clinicalgenome.org/terms/CG_000107"] return distinct(v.iri), sig.label, cx.outer_start, cx.inner_start, cx.inner_stop, cx.outer_stop, c.label, g.symbol, agent.iri, v.`copy-number` limit 10;



(defn gen-conflict-report
  "Generate a report on conflicts of variants against the dosage map"
  [dest-file]
  (neo/session 
   [session]
   (let [results (neo/hquery session query)
         headers (-> results first keys)
         table (map (fn [n] (map #(str (n %)) headers)) results)
         csv-str (csv/write-csv (cons headers table))]
    (spit dest-file csv-str))))
