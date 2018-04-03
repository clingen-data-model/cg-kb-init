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
  (let [props (walk/stringify-keys (dissoc i :scvversion :scvid :clinicalassertionid))]
    (.writeTransaction 
     session 
     (neo/tx     
       ["MERGE (a:Assertion {SCVID: $SCVID,ClinicalAssertionID: $ClinicalAssertionID, 
        SCVVersion: $SCVVersion,DateUpdated: $DateUpdated,SubmissionDate: $SubmissionDate, Evidence: $Evidence})
        CREATE (o:Observation {ClinicalAssertionID: $ClinicalAssertionID})
        MERGE (s:State {RecordStatus: $RecordStatus})
        MERGE (l:Level {ReviewStatus: $ReviewStatus})
        CREATE (a)-[:BASED_ON]->(o)
        CREATE (a)-[:HAS_STATE]->(s)
        CREATE (a)-[:HAS_LEVEL]->(l)"       
        {"SCVID" (:scvid i) "SCVVersion" (:scvversion i) "ClinicalAssertionID" (:clinicalassertionid i) 
        "DateUpdated" (:dateupdated i) "SubmissionDate" (:submissiondate i) "RecordStatus" (:srecordstatus i) 
        "ReviewStatus" (:reviewstatus i) "Evidence" (:evidence i) "AssertionMethod" (:assertionmethod i) "props" props}]))))

(defn import-assertionmethod
  "Importing assertionmethod"
  [i session]
  (let [props (walk/stringify-keys (dissoc i :clinicalassertionid :assertionmethod))]
    (.writeTransaction
     session
     (neo/tx
      ["MERGE (a:Assertion {ClinicalAssertionID: $ClinicalAssertionID})
        MERGE (c:AssertionCriteria {AssertionMethod: $AssertionMethod})
        CREATE (a)-[:HAS_ASSERTION_CRITERIA]->(c)"
       {"AssertionMethod" (:assertionmethod i) "props" props}])))) 

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
