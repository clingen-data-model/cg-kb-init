(ns chromatic-data.schema
  (:require [chromatic-data.neo4j :as neo]))

(def constraints [["Entity" "uuid"]
                  ["Assertion" "uuid"]
                  ["Intervention" "uuid"]
                  ["RDFClass" "iri"]
                  ["Agent" "uuid"]
                  ["Gene" "uuid"]
                  ["Assertion" "iri"]
                  ["ActionabilityAssertion" "uuid"]
                  ["ActionabilityInterventionAssertion" "uuid"]
                  ["ActionabilityScore" "uuid"]
                  ["Activity" "uuid"]
                  ["Condition" "uuid"]
                  ["GeneDiseaseAssertion" "uuid"]
                  ["GeneDosageAssertion" "uuid"]
                  ["Interpretation" "iri"]
                  ["Phenotype" "iri"]
                  ["Drug" "iri"]
                  ["Note" "uuid"]
                  ["Exon" "iri"]
                  ["Region" "iri"]
                  ["Variation" "iri"]
                  ["RegionContext" "iri"]
                  ["Interpretation" "iri"]
                  ["GeneDosageAssertion" "iri"]
                  ["Agent" "iri"]])

(def indexes [["Agent" "facebook_token"]
              ["Agent" "email"]
              ["Agent" "remember_token"]
              ["Agent" "reset_password_token"]
              ["RDFClass" "label"]
              ["Condition" "label"]
              ["Phenotype" "label"]
              ["Drug" "label"]
              ["Drug" "search_label"]
              ["Condition" "search_label"]
              ["Gene" "symbol"]
              ["Gene" "search_label"]
              ["Gene" "ucsc_id"]
              ["Gene" "refseq_accession"]
              ["Gene" "ensembl_gene_id"]
              ["Assertion" "perm_id"]])

(defn cg-indexes
  "Define indexes and constraints for ClinGen search in new Neo database"
  []
  (neo/session
   [session]
   (doseq [constraint constraints]
     (.run session (str "create constraint on (i:" 
                        (first constraint) ") assert i."
                        (second constraint)
                        " is unique")))
   (doseq [index indexes]
     (.run session (str "create index on :" (first index) "(" (second index) ")")))))
