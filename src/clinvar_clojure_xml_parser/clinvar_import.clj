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
  (let [props (walk/stringify-keys (dissoc i :scvversion :scvid :clinicalassertionid :assertionmethod))]
    (.writeTransaction 
     session 
     (neo/tx     
       ["CREATE (a:Assertion {SCVID: $SCVID,ClinicalAssertionID: $ClinicalAssertionID, 
        SCVVersion: $SCVVersion,DateUpdated: $DateUpdated,SubmitterID: $SubmitterID,SubmissionDate: $SubmissionDate, Evidence: $Evidence})
        CREATE (o:Observation {ClinicalAssertionID: $ClinicalAssertionID})
        CREATE (c:AssertionCriteria {AssertionMethod: $AssertionMethod})
        MERGE (s:State {RecordStatus: $RecordStatus})
        MERGE (l:Level {ReviewStatus: $ReviewStatus})  
        MERGE (b:Submitter {SubmitterID: $SubmitterID})     
        CREATE (a)-[:BASED_ON]->(o)
        CREATE (a)-[:HAS_STATE]->(s)
        CREATE (a)-[:HAS_LEVEL]->(l)
        CREATE (a)-[:WAS_SUBMITTED_BY]->(b)
        CREATE (a)-[:HAS_ASSERTION_CRITERIA]->(c)-[:IS_PUBLISHED_IN]->(:Citation {CitationURL: $CitationURL})
        CREATE (a)-[:HAS_ASSERTION_CRITERIA]->(c)-[:IS_PUBLISHED_IN]->(:Citation {CitationID: $CitationID})"      
        {"SCVID" (:scvid i) "SCVVersion" (:scvversion i) "ClinicalAssertionID" (:clinicalassertionid i) 
        "DateUpdated" (:dateupdated i) "SubmitterID" (:submitterid i) "SubmissionDate" (:submissiondate i) "RecordStatus" (:srecordstatus i) 
        "ReviewStatus" (:reviewstatus i) "Evidence" (:evidence i) "AssertionMethod" (:assertionmethod i) 
        "CitationURL" (:citationurl i) "CitationID" (:citationid i) "props" props}]))))

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
             ;(import-assertionmethod n session)
             (println (str "no match for " (:type n)))))))))
