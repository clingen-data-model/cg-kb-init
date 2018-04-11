(ns clinvar-clojure-xml-parser.clinvar-import
  (:require [clojure.java.io :as io]
            [clinvar-clojure-xml-parser.neo4j :as neo]
            [clojure.edn :as edn]
            [clojure.walk :as walk])
  (:import java.io.PushbackReader
           java.util.UUID))

(defn import-clinicalassertion
  "Import clinicalassertion"
  [i session]
  (let [props (walk/stringify-keys (dissoc i :scvid :clinicalassertionid))]
    (.writeTransaction 
     session 
     (neo/tx     
       ["CREATE (a:Assertion {ClinicalAssertionID: $ClinicalAssertionID, SCVID: $SCVID, SCVVersion: $SCVVersion,
        SubmissionDate: $SubmissionDate,DateUpdated: $DateUpdated})
        FOREACH (x IN CASE WHEN $ClinicalSignificance IS NULL THEN [] ELSE [1] END |
        CREATE(cs: ClinicalSignificance {ClinicalSignificance:$ClinicalSignificance,Evidence: $Evidence,DateLastEvaluated: $DateLastEvaluated})
        CREATE (a)-[:HAS_SIGNIFICANCE]->(cs))
        FOREACH (x IN CASE WHEN $ClinicalAssertionID IS NULL THEN [] ELSE [1] END | 
        MERGE (o:Observation {ClinicalAssertionID: $ClinicalAssertionID})
        CREATE (a)-[:BASED_ON]->(o)
        CREATE (o)-[:HAS_METHOD]->(:MethodType{MethodType: $MethodType})) 
        FOREACH (x IN CASE WHEN $AssertionMethod IS NULL THEN [] ELSE [1] END |
        MERGE (c:AssertionCriteria {AssertionMethod: $AssertionMethod}))
        FOREACH (x IN CASE WHEN $RecordStatus IS NULL THEN [] ELSE [1] END |
        MERGE (s:State {RecordStatus: $RecordStatus})
        CREATE (a)-[:HAS_STATE]->(s))
        FOREACH (x IN CASE WHEN $ReviewStatus IS NULL THEN [] ELSE [1] END |
        MERGE (l:Level {ReviewStatus: $ReviewStatus})
        CREATE (a)-[:HAS_LEVEL]->(l)) 
        FOREACH (x IN CASE WHEN $SubmitterID IS NULL THEN [] ELSE [1] END | 
        MERGE (b:Submitter {SubmitterID: $SubmitterID, SubmitterName: $SubmitterName})     
        CREATE (a)-[:WAS_SUBMITTED_BY]->(b))
        FOREACH (x IN CASE WHEN $CitationURL IS NULL OR $AssertionMethod IS NULL THEN [] ELSE [1] END |
        MERGE (a)-[:HAS_ASSERTION_CRITERIA]->(:AssertionMethod {Name: $AssertionMethod})-[:IS_PUBLISHED_IN]->(:Citation {CitationURL: $CitationURL}))
        FOREACH (x IN CASE WHEN $CitationID IS NULL OR $AssertionMethod IS NULL THEN [] ELSE [1] END |
        MERGE (a)-[:HAS_ASSERTION_CRITERIA]->(:AssertionMethod {Name: $AssertionMethod})-[:IS_PUBLISHED_IN]->(:Citation {CitationID: $CitationID}))
        FOREACH (x IN CASE WHEN $CitationText IS NULL OR $AssertionMethod IS NULL THEN [] ELSE [1] END |
        MERGE (a)-[:HAS_ASSERTION_CRITERIA]->(:AssertionMethod {Name: $AssertionMethod})-[:IS_PUBLISHED_IN]->(:Citation {CitationText: $CitationText}))"
        {"SCVID" (:scvid i) "SCVVersion" (:scvversion i) "ClinicalAssertionID" (:clinicalassertionid i) 
        "DateUpdated" (:dateupdated i) "SubmitterID" (:submitterid i) "SubmitterName" (:submittername i) "SubmissionDate" (:submissiondate i) "RecordStatus" (:srecordstatus i) 
        "ReviewStatus" (:reviewstatus i) "ClinicalSignificance" (:clinicalsignificance i) "Evidence" (:evidence i) "AssertionMethod" (:assertionmethod i) 
        "DateLastEvaluated" (:datelastevaluated i) "CitationURL" (:citationurl i) "CitationID" (:citationid i) "CitationText" (:citationtext i) "MethodType" (:methodtype i) "props" props}]))))

(defn import-variation
   "Import variation"
  [i session]
  (let [props (walk/stringify-keys (dissoc i :variationid :clinicalassertionid))]
    (.writeTransaction 
     session 
     (neo/tx 
       ["FOREACH (x IN CASE WHEN $VariationType IS NULL OR $VariationID IS NULL THEN [] 
         ELSE [1] END |
         MERGE (v:Variation {VariationID: $VariationID,VariationName:$VariationName,VariationType: $VariationType})               
         MERGE (t:Vtype {VariationType: $VariationType})          
         MERGE (v)-[:HAS_TYPE]->(t))
         FOREACH (x IN CASE WHEN $ClinicalAssertionID IS NULL THEN [] ELSE [1] END |
         MERGE (:Assertion {ClinicalAssertionID: $ClinicalAssertionID})-[:HAS_SUBJECT]->(v))
         FOREACH (x IN CASE WHEN $AlleleID IS NULL THEN [] ELSE [1] END |
         MERGE (:Allele {AlleleID: $AlleleID})-[:IS_COMPRISED_OF]->(v))"
         {"ClinicalAssertionID" (:clinicalassertionid i) "AlleleID" (:alleleid i) "VariationID" (:variationid i) "VariationName" (:variationname i) "VariationType" (:variationtype i) "props" props}])
)))

(defn import-conditions
   "Import conditions"
  [i session]
  (let [props (walk/stringify-keys (dissoc i :medgencui))]
    ;(println (str "clinicalassertionid: " (:clinicalassertionid i)))
    (.writeTransaction 
     session 
     (neo/tx 
       ["FOREACH (x IN CASE WHEN $MedgenCui IS NULL THEN [] ELSE [1] END |    
         CREATE (sc:Condition {SourceID: $MedgenCui,Name: $MappingValue,TraitType: $TraitType}))         
         FOREACH (x IN CASE WHEN $ClinicalAssertionID IS NULL THEN [] ELSE [1] END |  
         MERGE (:Assertion {ClinicalAssertionID: $ClinicalAssertionID})-[:IS_ASSOCIATED_WITH]->(sc)                
         MERGE (:Assertion {ClinicalAssertionID: $ClinicalAssertionID})-[:IS_SUBMITTER_ASSOCIATED_WITH]->(sc))"
        { "ClinicalAssertionID" (:clinicalassertionid i) "MedgenCui" (:medgencui i) "MappingValue" (:mappingvalue i) "TraitType" (:traittype i) "props" props}])
)))

;Create Allele-Type relationships and add name to Allele nodes
;Create Annotation relationships
(defn import-allels
   "Import allels"
  [i session]
  (let [props (walk/stringify-keys (dissoc i :alleleid))]
    (.writeTransaction 
     session 
     (neo/tx 
       ["CREATE (al:Allele {AlleleID: $AlleleID,AlleleName: $AlleleName})
        FOREACH (x IN CASE WHEN $AlleleType IS NULL THEN [] ELSE [1] END | 
        MERGE (t:VariationType {VariationType: $AlleleType})
        CREATE (al)-[:HAS_TYPE]->(t))"
        { "AlleleID" (:alleleid i) "AlleleName" (:allelename i) "AlleleType" (:alleletype i) "props" props}]))))

(defn import-clinvar-data
  "Import ClinVar CNVs from intermediate format in EDN"
  []
  (with-open [r (PushbackReader. (io/reader "data/clinvar.edn"))]
    (neo/session
     [session]
     (let [interps (edn/read r)]
       (doseq [i interps]
         (doseq [n i]
           ;(case (:type n)
            (import-clinicalassertion n session)
            (import-variation n session)
            (import-conditions n session)
            (import-allels n session)
            ;(println (str "no match for " (:type n)))
       ))))))
