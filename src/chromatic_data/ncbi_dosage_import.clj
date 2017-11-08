(ns chromatic-data.ncbi-dosage-import
  (require [chromatic-data.neo4j :as neo]
           [clojure-csv.core :as csv]
           [clojure.java.io :as io]))

(def iri-prefixes {:omim "http://purl.obolibrary.org/obo/OMIM_"})

(def gene-dosage-hap-iris {"30"                           "http://datamodel.clinicalgenome.org/terms/CG_000094"
                           "3"                           "http://datamodel.clinicalgenome.org/terms/CG_000092"
                           "2"
                           "http://datamodel.clinicalgenome.org/terms/CG_000093"
                           "1"
                           "http://datamodel.clinicalgenome.org/terms/CG_000095"
                           "0"
                           "http://datamodel.clinicalgenome.org/terms/CG_000096"})

(def gene-dosage-trip-iris {"3" "http://datamodel.clinicalgenome.org/terms/CG_000097"
                            "2"
                            "http://datamodel.clinicalgenome.org/terms/CG_000098"
                            "1"
                            "http://datamodel.clinicalgenome.org/terms/CG_000099"
                            "0"
                            "http://datamodel.clinicalgenome.org/terms/CG_000100"})

(defn create-dosage-assertion
  "Create a gene dosage assertion given score, gene and (optional) pheno, date, phenotype and (effective) copy number (H)aplo or (T)triplo"
  [score gene-id pheno date session cn]
  (let [uuid (str (java.util.UUID/randomUUID))
        perm-id (str cn gene-id)]
    (.run session "match (r:Gene {entrez_id: {gene}}), (i:RDFClass {iri: {i}})
 merge (a:GeneDosageAssertion:Assertion:Entity {perm_id: {id}})
 on create set a.uuid = {uuid}
 set a.date = {date}
 merge (a)-[:has_subject]->(r)
 merge (a)-[rel:has_predicate]->(i)"
          {"gene" gene-id, "uuid" uuid, "i" score, "date" date, "id" perm-id})
    (when pheno 
      (.run session "match (a:GeneDosageAssertion {uuid: {uuid}}), (c:Condition {iri: {condiri}}) merge (a)-[:has_object]->(c)" {"uuid" uuid, "condiri" (str (:omim iri-prefixes) pheno)}))))

(defn import-gene-dosage
  "import gene dosage information from ClinGen"
  [path]
  (neo/session
   [session]
   ;; First six lines of dosage csv are comments
   (let [dosage-csv (nthrest (csv/parse-csv
                              (io/reader path)
                              :delimiter \tab) 
                             6)]
     (doseq [[_ gene-id _ _ 
              hap-score _ happub1 happub2 happub3
              trip-score _ trippub1 trippub2 trippub3
              date happheno trippheno]
             dosage-csv]
       (let [hap-assert (gene-dosage-hap-iris hap-score)
             trip-assert (gene-dosage-trip-iris trip-score)]
         (when hap-assert
           (create-dosage-assertion hap-assert gene-id happheno date session "H"))
         (when trip-assert
           (create-dosage-assertion trip-assert gene-id trippheno date session "T")))))))
