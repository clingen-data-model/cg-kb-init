(ns chromatic-data.rdf
  (:require [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [chromatic-data.neo4j :as neo])
  (:import [org.apache.jena.rdf.model Model ModelFactory Literal Resource]))



(defn import-resources
  "Import resources defined in the model to neo4j"
  [model]
  (let [subjects (iterator-seq (.listSubjects model))
        objects (filter #(instance? Resource %) (iterator-seq (.listObjects model)))
        iris (distinct (map #(.getURI %) (concat subjects objects)))
        batches (partition-all 1000 (remove nil? iris))]
    (with-open [s (neo/create-session)]
      (doseq [b batches]
        (.run s "unwind $iris as iri merge (:Resource {iri: iri})" {"iris" b})))))

(defn import-literals
  "Import literal annotations into neo4j"
  [model]
  (let [statements (iterator-seq (.listStatements model))
        filtered-statements (filter #(->> % .getObject (instance? Literal)) statements)
        properties (reduce (fn [m s]
                             (let [subj (-> s .getSubject .getURI)
                                   pred (-> s .getPredicate .getLocalName)
                                   ;; TODO consider being smarter about data types
                                   ;; just using string rep for now for all
                                   val (-> s .getObject .getLexicalForm)]
                               (assoc m subj (assoc (m subj {}) pred val))))
                           {} filtered-statements)
        batches (partition-all 1000 properties)]
    (with-open [s (neo/create-session)]
      (doseq [b batches]
        (.run s "unwind $props as p match (r:Resource {iri: p[0]}) set r = p[1]"
              {"props" b})))))

(defn resource-statements
  "return a lazy-seq of resource-to-resource statements in the given statement list"
  [statements]
  (let [filtered (filter #(->> % .getObject (instance? Resource)) statements)
        stmts (map #(array-map "subject" (-> % .getSubject .getURI)
                              "predicate" (-> % .getPredicate .getLocalName)
                              "object" (-> % .getObject .getURI))
                  filtered)]
    (filter #(and (get % "subject") (get % "object")) stmts)))

(defn import-relationships
  "import the specified relationships  into neo4j. Import all relationships if 
  types is not defined, otherwise import only the types specified"
  [model]
  (let [statements (resource-statements (iterator-seq (.listStatements model)))
        batched-statements (partition-all 1000 statements)]
    (doseq [s batched-statements]
      (let [grouped-statements (group-by #(get % "predicate") s)]
        (with-open [s (neo/create-session)]
          (doseq [[predicate group] grouped-statements]
            (let [query (str "UNWIND $statements AS stmt "
                             "MATCH (s:Resource {iri: stmt.subject}) "
                             "MATCH (o:Resource {iri: stmt.object}) "
                             "MERGE (s)-[:" predicate "]->(o)")]
              (.run s query {"statements" group}))))))))

(defn detect-syntax
  "Detect the RDF syntax based on the file extension"
  [filename]
  (let [ext (re-find #"\.\w+$" filename)]
    (case ext
      ".xml" "RDF/XML"
      ".ttl" "TURTLE"
      ".owl" "RDF/XML"
      ".jsonld" "JSON-LD")))

;;[org.eclipse.rdf4j.rio Rio RDFFormat]
;; org.eclipse.rdf4j.model.Resource
(defn import-rdf
  "Import RDF data as-is into Neo4j"
  [filename]
  (with-open [f (io/input-stream filename)
              m (ModelFactory/createDefaultModel)]
    ;; TODO detect serialization
    (.read m f nil (detect-syntax filename))
    (import-resources m)
    (import-relationships m)
    (import-literals m)))
