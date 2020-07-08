(ns chromatic-data.pw-curation-import
  (:require [clojure.data.json :as json]
            [chromatic-data.neo4j :as neo]
            [clojure.java.io :as io])
  (:import [org.neo4j.driver Session TransactionConfig]))

(def iri-prefixes {:omim "http://purl.obolibrary.org/obo/OMIM_"})

;; IRIs to link to nodes relevant for actionability
(def act-iris {:top "http://datamodel.clinicalgenome.org/terms/CG_000082"
               :interv "http://datamodel.clinicalgenome.org/terms/CG_000081"
               :efficacy {0 "http://datamodel.clinicalgenome.org/terms/CG_000061"
                          1 "http://datamodel.clinicalgenome.org/terms/CG_000060"
                          2 "http://datamodel.clinicalgenome.org/terms/CG_000059"
                          3 "http://datamodel.clinicalgenome.org/terms/CG_000058"}
               :evidence {"E" "http://datamodel.clinicalgenome.org/terms/CG_000057"
                          "D" "http://datamodel.clinicalgenome.org/terms/CG_000056"
                          "C" "http://datamodel.clinicalgenome.org/terms/CG_000055"
                          "B" "http://datamodel.clinicalgenome.org/terms/CG_000054"
                          "A" "http://datamodel.clinicalgenome.org/terms/CG_000053"}
               :likelihood {0 "http://datamodel.clinicalgenome.org/terms/CG_000051"
                            1 "http://datamodel.clinicalgenome.org/terms/CG_000050"
                            2 "http://datamodel.clinicalgenome.org/terms/CG_000049"
                            3 "http://datamodel.clinicalgenome.org/terms/CG_000048"}
               :severity {0 "http://datamodel.clinicalgenome.org/terms/CG_000046"
                          1 "http://datamodel.clinicalgenome.org/terms/CG_000045"
                          2 "http://datamodel.clinicalgenome.org/terms/CG_000044"
                          3 "http://datamodel.clinicalgenome.org/terms/CG_000043"}
               :safety {1 "http://datamodel.clinicalgenome.org/terms/CG_000089"
                        2 "http://datamodel.clinicalgenome.org/terms/CG_000088"
                        0 "http://datamodel.clinicalgenome.org/terms/CG_000090"
                        3 "http://datamodel.clinicalgenome.org/terms/CG_000087"}})

;; List of subscores paired with categories
(def act-subscores {"scoreSeverity" :severity, "scoreEffectiveness" :efficacy, "scoreLikelihood" :likelihood, "scoreIntervention" :safety})

(def gene-disease-iris {"Definitive" "http://datamodel.clinicalgenome.org/terms/CG_000063"
                        "Limited" "http://datamodel.clinicalgenome.org/terms/CG_000066"
                        "Moderate" "http://datamodel.clinicalgenome.org/terms/CG_000065"
                        "No Reported Evidence" "http://datamodel.clinicalgenome.org/terms/CG_000067"
                        "Strong" "http://datamodel.clinicalgenome.org/terms/CG_000064"
                        "Contradictory (refuted)" "http://datamodel.clinicalgenome.org/terms/CG_000085"
                        "Contradictory (disputed)" "http://datamodel.clinicalgenome.org/terms/CG_000084"})

(defn get-act-score-iri
  "Get the appropriate iri(s), given a score and a category"
  [cat score]
  (if  (< 1 (count score))
    [((cat act-iris) (read-string (subs score 0 1)))
     ((:evidence act-iris) (subs score 1 2))]
    [((cat act-iris) (read-string (subs score 0 1)))]))

(defn import-actionability-score
  "import subscore for actionability curation"
  [id score session]
  (let [score-id (str (java.util.UUID/randomUUID))
        category (act-subscores (first score))
        interpretations (get-act-score-iri category (second score))]
    (.run session "match (i:ActionabilityInterventionAssertion {uuid: {id}}) merge (s:ActionabilityScore:Assertion:Entity {uuid: {scoreid}}) merge (i)<-[:was_generated_by]-(s) merge (s)-[:has_subject]->(i)"
          {"id" id, "scoreid" score-id})
    ; Grab main score
    (.run session "match (s:Assertion {uuid: {scoreid}}), (c:Resource {iri: {iri}})  merge (s)-[:has_predicate]->(c)"
          {"iri" (first interpretations), "scoreid" score-id})
    ; Grab evidence score (if exists)
    (when (second interpretations) 
      (.run session "match (s:Assertion {uuid: {scoreid}}), (c:Resource {iri: {iri}})  merge (s)-[:has_evidence_strength]->(c)"
            {"iri" (second interpretations), "scoreid" score-id}))
    ))

(defn import-actionability-intervention
  "import score from actionability curation"
  [score id session]
  (let [ac (second score)
        newid (str (java.util.UUID/randomUUID))
        intervid (str (java.util.UUID/randomUUID))
        interv (ac "outcomeIntervention")
        scores (select-keys ac (keys act-subscores))]
     ;; TODO make idempotent
     ;; Create root intervention assertion
    (.run session "match (top:ActionabilityAssertion {uuid: {id}}) merge (i:ActionabilityInterventionAssertion:Assertion:Entity {uuid: {newid}}) merge (interv:Intervention:Entity {label: {interv}}) merge (top)<-[:was_generated_by]-(i) merge (i)-[:has_subject]->(top) merge (i)-[:has_object]->(interv)" 
          {"newid" newid, "intervid" intervid, "interv" interv, "id" id})
    (doseq [s scores]
      (import-actionability-score newid s session))))

(defn get-moi [curation]
  (let [score (json/read-str (if (empty? (get curation "scoreJsonSerialized"))
                               (get curation "scoreJsonSerializedSop5")
                               (get curation "scoreJsonSerialized")))
        moi (or (get-in score ["scoreJson" "ModeOfInheritance"])
                  (get-in score ["data" "ModeOfInheritance"]))]
    (str "http://purl.obolibrary.org/obo/HP_"
         (second (re-find #"\(HP:(.*)\)" moi)))))

(defn create-gene-disease-node
  "Create node with gene-disease relationship pair"
  [curation session label perm-id]
  (let [genes (vec (map #((second %) "curie") (curation "genes")))
        conditions (vec (map #((second %) "iri") (filter #(= "MONDO" (get % "ontology" "MONDO"))
                                                         (get curation "conditions"))))
        mondo-conditions (vec (filter #(re-find #"MONDO" %) conditions))
        moi (get-moi curation)
        id (str (java.util.UUID/randomUUID))]
    (println genes)
    (println mondo-conditions)
    (println moi)
    ;; (.run session 
    ;;       (str  "match (g:Gene)<-[:has_subject]-(a:" label ")-[:has_object]->(c:Resource) where g.hgnc_id in {genes} and c.iri in {conditions} detach delete a;")
    ;;       {"genes" genes, "id" id, "conditions" conditions, "permid" perm-id})
    (.run session 
          (str  "match (g:Gene), (c:Resource), (moi:Resource) where g.hgnc_id in {genes} and c.iri in {conditions} and moi.iri = $moi
merge (a:" label  " {perm_id: {permid}}) on create set a.uuid = {id} merge (a)-[:has_subject]->(g) merge (a)-[:has_object]->(c) merge (a)-[:has_mode_of_inheritance]->(moi)")
          {"genes" genes, "id" id, "conditions" mondo-conditions, "permid" perm-id, "moi" moi})
    id))


(defn import-actionability
  "import actionability curation"
  [record session]
  (let [perm-id (first record)
        curation (second record)
        id (create-gene-disease-node curation session "ActionabilityAssertion:Assertion:Entity" perm-id)
        date (curation "dateISO8601")
        file ((-> (curation "file") first second ) "location")]
    ;; TODO, make import from ProcessWire idempotent
    (.run session 
          "match (a:ActionabilityAssertion {perm_id: {id}}), (i:Resource {iri: {top}}) merge (a)-[:has_predicate]->(i) set a.date = {date} set a.file = {file}"
          {"id" perm-id, "top" (:top act-iris), "date" date, "file" file})
    (doseq [score (curation "scores")]
      (import-actionability-intervention score id session))))

(defn import-gene-disease
  "import gene disease curation"
  [record session]
  ;;(clojure.pprint/pprint record)
  (let [perm-id (first record)
        curation (second record)
        id (create-gene-disease-node curation session "GeneDiseaseAssertion:Assertion:Entity" perm-id)
        score ((-> (curation "scores") first second) "label")
        interp-iri (some #(if (.contains score (first %)) (second %)) gene-disease-iris)
        score-json (curation "scoreJsonSerialized")
        score-json-sop5 (curation "scoreJsonSerializedSop5")
        date (curation "dateISO8601")
        affiliation (curation "affiliation")]
    (.run session "match (a:Assertion {perm_id: {id}}), (i:Resource {iri: {interp}}) merge (a)-[:has_predicate]->(i) merge (aff:Agent:Entity {iri: $affid}) merge (a)-[:wasAttributedto]->(aff) set aff.label = $afflabel set a.score_string = {scorejson} set a.score_string_sop5 = {scorejsonsop5} set a.date = {date}"
          {"interp" interp-iri
           "scorejson" score-json
           "scorejsonsop5" score-json-sop5
           "id" perm-id, "date" date
           "affid" (str "https://search.clinicalgenome.org/kb/agents/" (get affiliation "id"))
           "afflabel" (get affiliation "name")}
          (TransactionConfig/empty))))

(defn update-affiliation 
  [record session]
  (let [perm-id (first record)
        curation (second record)
        affiliation (curation "affiliation")]
    (.run session "match (a:Assertion {perm_id: {id}}) merge (aff:Agent:Entity {iri: $affid}) merge (a)-[:wasAttributedto]->(aff) set aff.label = $afflabel"
          {"id" perm-id,, "affid" (str "https://search.clinicalgenome.org/kb/agents/" (get affiliation "id")), "afflabel" (get affiliation "name")}))  )

(defn import-cg-data
  "Import exported JSON file to neo4j"
  [path]
  (let [data (json/read (io/reader path))]
    (neo/session
     [session]
     (doseq [curation-record data]
       (let [type ((second curation-record) "type")]
         (case type
           "actionability" (println "skipping actionability curation")
           "clinicalValidity" (import-gene-disease curation-record session)
           ;;"clinicalValidity" (update-affiliation curation-record session)
           ))))))
