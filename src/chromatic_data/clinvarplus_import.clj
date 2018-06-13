(ns chromatic-data.clinvarplus-import
  (:require [clojure.java.io :as io]
            [clinvar-clojure-xml-parser.neo4j :as neo]
            [clojure.edn :as edn]
            [clojure.walk :as walk])
  (:import java.io.PushbackReader
           java.util.UUID))

(defn create-constraints
  "Create-constraints"
  [i session]     
  (let [props (walk/stringify-keys (dissoc i :clinicalassertionid :alleleid :variationid))]
    (.writeTransaction 
     session 
     (neo/tx 
       ["CREATE CONSTRAINT ON (a:Assertion) ASSERT a.SCVID IS UNIQUE"]))))

(defn create-indexes
  "Create-indexes"
  [i session]     
  (let [props (walk/stringify-keys (dissoc i :scvid :clinicalassertionid))]
    (.writeTransaction 
     session 
     (neo/tx 
       ["CREATE INDEX ON :State(RecordStatus)
         CREATE INDEX ON :Level(ReviewStatus)
         CREATE INDEX ON :ClinicalSignificance(ClinicalSignificance)
         CREATE INDEX ON :AssertionType(AssertionType)"]))))

(defn import-clinicalassertion
  "Import clinicalassertion"
  [i session]
  ;walk/stringify-keys Recursively transforms all map keys from keywords to strings.
  ;dissoc Returns a transient map that doesn't contain a mapping for key(s).
  (let [props (walk/stringify-keys (dissoc i :clinicalassertionid :medgencui :variationid))]
    ;(println "props: " props)
    (.writeTransaction 
     session 
     (neo/tx     
       ["FOREACH (x IN CASE WHEN $ClinicalAssertionID IS NULL THEN [] ELSE [1] END |
        MERGE(a:Assertion {ClinicalAssertionID: $ClinicalAssertionID}) 
        ON CREATE SET 
        a.SCVID= $SCVID,
        a.SCVVersion=$SCVVersion,
        a.SubmissionDate=$SubmissionDate,
        a.DateUpdated=$DateUpdated     
        FOREACH (x IN CASE WHEN $ClinicalSignificance IS NULL THEN [] ELSE [1] END |
        MERGE(cs: ClinicalSignificance {ClinicalSignificance:$ClinicalSignificance})
        FOREACH (x IN CASE WHEN $Evidence IS NULL THEN [] ELSE [1] END | 
        SET cs.Evidence = $Evidence)
        FOREACH (x IN CASE WHEN $DateLastEvaluated IS NULL THEN [] ELSE [1] END | 
        SET cs.DateLastEvaluated = $DateLastEvaluated)
        MERGE (a)-[:HAS_SIGNIFICANCE]->(cs))
        MERGE (o:Observation {ClinicalAssertionID: $ClinicalAssertionID})
        MERGE (a)-[:BASED_ON]->(o)
        FOREACH (x IN CASE WHEN $MethodType IS NULL THEN [] ELSE [1] END | 
        MERGE (m:MethodType{MethodType: $MethodType})
        MERGE (o)-[:HAS_METHOD]->(m))
        FOREACH (x IN CASE WHEN $Origin IS NULL THEN [] ELSE [1] END |
        MERGE (or:Origin {Origin: $Origin})
        MERGE (o)-[:HAS_ORIGIN]->(or))
        FOREACH (x IN CASE WHEN $CitationID IS NULL THEN [] ELSE [1] END |
        MERGE (o)-[:IS_PUBLISHED_IN]->(:Citation {CitationID: $CitationID, CitationSource: $CitationSource}))
        FOREACH (x IN CASE WHEN $RecordStatus IS NULL THEN [] ELSE [1] END |
        MERGE (s:State {RecordStatus: $RecordStatus})
        MERGE (a)-[:HAS_STATE]->(s))
        FOREACH (x IN CASE WHEN $ReviewStatus IS NULL THEN [] ELSE [1] END |
        MERGE (l:Level {ReviewStatus: $ReviewStatus})
        MERGE (a)-[:HAS_LEVEL]->(l)) 
        FOREACH (x IN CASE WHEN $ModeOfInheritance IS NULL THEN [] ELSE [1] END |
        MERGE (mi:MOI {ModeOfInheritance: $ModeOfInheritance})
        MERGE (a)-[:HAS_MOI]->(mi))
        FOREACH (x IN CASE WHEN $SubmitterID IS NULL THEN [] ELSE [1] END | 
        MERGE (b:Submitter {SubmitterID: $SubmitterID, SubmitterName: $SubmitterName})     
        MERGE (a)-[:WAS_SUBMITTED_BY]->(b))                                                           
        FOREACH (x IN CASE WHEN $AssertionMethod IS NULL THEN [] ELSE [1] END |
        MERGE (am:AssertionMethod {AssertionMethod: $AssertionMethod})
        MERGE (a)-[:HAS_ASSERTION_CRITERIA]->(am)
        FOREACH (x IN CASE WHEN $CitationURL IS NULL THEN [] ELSE [1] END |
        MERGE (am)-[:IS_PUBLISHED_IN]->(:Citation {CitationURL: $CitationURL}))
        FOREACH (x IN CASE WHEN $CitationID2 IS NULL THEN [] ELSE [1] END |
        MERGE (am)-[:IS_PUBLISHED_IN]->(:Citation {CitationID2: $CitationID2, CitationSource2: $CitationSource2}))
        FOREACH (x IN CASE WHEN $CitationText IS NULL THEN [] ELSE [1] END |
        MERGE (am)-[:IS_PUBLISHED_IN]->(:Citation {CitationText: $CitationText}))))"
        {"ClinicalAssertionID" (:clinicalassertionid i) "SCVID" (:scvid i) "SCVVersion" (:scvversion i)  
        "DateUpdated" (:dateupdated i) "SubmitterID" (:submitterid i) "SubmitterName" (:submittername i) "SubmissionDate" (:submissiondate i) "RecordStatus" (:srecordstatus i) 
        "ReviewStatus" (:reviewstatus i) "ClinicalSignificance" (:clinicalsignificance i) "Evidence" (:evidence i) "AssertionMethod" (:assertionmethod i) 
        "DateLastEvaluated" (:datelastevaluated i) "CitationURL" (:citationurl i) "CitationID2" (:citationid2 i) "CitationSource2" (:citationsource2 i) 
        "CitationID" (:citationid i) "CitationSource" (:citationsource i) "CitationText" (:citationtext i) "MethodType" (:methodtype i) "MedgenCui" (:medgencui i) "MappingValue" (:mappingvalue i) 
        "TraitType" (:traittype i) "AlleleID" (:alleleid i) "AlleleName" (:allelename i) "VariationID" (:variationid i) "VariationName" (:variationname i) 
        "VariationType" (:variationtype i) "AlleleType" (:alleletype i) "ModeOfInheritance" (:modeofinheritance i) "Origin" (:origin i) "props" props}]))))

(defn import-variation
   "Import variation"
  [i session]
  (let [props (walk/stringify-keys (dissoc i :variationid))]
    (.writeTransaction 
     session 
     (neo/tx 
       ["MATCH (a:Assertion {ClinicalAssertionID: $ClinicalAssertionID})
        FOREACH (x IN CASE WHEN $VariationID IS NULL THEN [] ELSE [1] END |
        MERGE (v:Variation {VariationID: $VariationID})
        SET v.VariationName = $VariationName 
        FOREACH (x IN CASE WHEN $VariationType IS NULL THEN [] ELSE [1] END |             
        MERGE (vt:VariationType {VariationType: $VariationType})          
        MERGE (v)-[:HAS_VAR_TYPE]->(vt))
        MERGE (a)-[:HAS_SUBJECT]->(v))"
        {"ClinicalAssertionID" (:clinicalassertionid i) "VariationID" (:variationid i) 
         "VariationName" (:variationname i) "VariationType" (:variationtype i) "props" props}]))))

;Create Allele-Type relationships and add name to Allele nodes
;Create Annotation relationships
(defn import-allels
   "Import allels"
  [i session]
  ;walk/stringify-keys Recursively transforms all map keys from keywords to strings.
  (let [props (walk/stringify-keys (dissoc i :alleleid))]
    (.writeTransaction 
     session 
     (neo/tx 
       ["MATCH (v:Variation {VariationID: $VariationID})
        FOREACH (x IN CASE WHEN $AlleleID IS NULL THEN [] ELSE [1] END |     
        MERGE (al:Allele {AlleleID: $AlleleID})
        SET al.AlleleName = $AlleleName
        FOREACH (x IN CASE WHEN $AlleleType IS NULL THEN [] ELSE [1] END | 
        MERGE (at:AlleleType {AlleleType: $AlleleType})               
        MERGE (al)-[:HAS_ALLELE_TYPE]->(at))
        MERGE (v)-[:IS_COMPRISED_OF]->(al))"
        {"AlleleID" (:alleleid i) "AlleleName" (:allelename i) "AlleleType" (:alleletype i) 
         "VariationID" (:variationid i) "props" props}]))))

(defn import-conditions
   "Import conditions"
  [i session]
  (let [props (walk/stringify-keys (dissoc i :medgencui))]
    (.writeTransaction 
     session 
     (neo/tx 
       ["MATCH (a:Assertion {ClinicalAssertionID: $ClinicalAssertionID})
         FOREACH (x IN CASE WHEN $MedgenCui IS NULL THEN [] ELSE [1] END |
         MERGE (c:Condition {Source: 'MedGen', SourceID: $MedgenCui})            
         MERGE (a)-[:IS_ASSOCIATED_WITH]->(c))
         FOREACH (x IN CASE WHEN $MappingValue IS NULL THEN [] ELSE [1] END |
         MERGE (sc:Condition {Name:$MappingValue}) 
         SET sc.TraitType = $TraitType               
         MERGE (a)-[:IS_SUBMITTER_ASSOCIATED_WITH]->(sc))"
        {"ClinicalAssertionID" (:clinicalassertionid i) "MedgenCui" (:medgencui i) 
         "MappingValue" (:mappingvalue i) "TraitType" (:traittype i) "props" props}])
)))

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
           (create-constraints n session)
           ;(create-indexes n session)
           (import-clinicalassertion n session)
           (import-variation n session)
           (import-allels n session)
           (import-conditions n session)
           ;(println (str "no match for " (:type n)))
       ))))))
