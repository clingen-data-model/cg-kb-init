(ns chromatic-data.owl
  (:require [clojure.java.io :as io]
            [clojure-csv.core :as csv]
            [clojure.set :as set]
            [chromatic-data.neo4j :as neo])
  (:import [org.semanticweb.owlapi.model IRI OWLLiteral OWLClass OWLException OWLOntology OWLOntologyManager OWLObjectSomeValuesFrom OWLPropertyRange]
           [org.semanticweb.owlapi.io OWLOntologyDocumentSource]
           org.semanticweb.owlapi.apibinding.OWLManager
           org.semanticweb.owlapi.search.EntitySearcher
           [org.semanticweb.owlapi.reasoner OWLReasonerFactory OWLReasoner
            InferenceType Node]
           org.semanticweb.elk.owlapi.ElkReasonerFactory
           ;; [org.semanticweb.HermiT Reasoner ReasonerFactory]
           ;; uk.ac.manchester.cs.jfact.JFactFactory
           [org.semanticweb.owlapi.util InferredAxiomGenerator
            InferredOntologyGenerator
            InferredSubClassAxiomGenerator
            InferredEquivalentClassAxiomGenerator]))

(def owl-manager (OWLManager/createOWLOntologyManager))
(def owl-data-factory (.getOWLDataFactory owl-manager))
(def reasoner-factory (new ElkReasonerFactory))

(def key-mappings {"iri" "iri"
                   "curie" "curie"
                   "rdfs:label" "label"
                   "<http://www.w3.org/2004/02/skos/core#prefLabel>" "label"
                   "<http://purl.obolibrary.org/obo/IAO_0000115>" "definition"
                   "<http://www.geneontology.org/formats/oboInOwl#hasExactSynonym>" "synonym"
                   "<http://www.ebi.ac.uk/efo/definition>" "definition"
                   "<http://datamodel.clinicalgenome.org/terms/CG_000062>" "score"
                   "<http://datamodel.clinicalgenome.org/terms/CG_000104>" "short_label"})

;; From an OWL ontology, we need to extract and represent in 
;; neo4j: 
;; - all direct class relationships
;; - certain annotation properties
;;   - iri
;;   - label
;;   - description
;;   - (xrefs, class equiv, synonyms?)
;; - These have type constraints that should be enforced, though
;;   they are all imported as an array of strings.
;; - Write classes and properties to csv
;; - Execute statements enforcing type constraints
;; Load classes and import annotations from mondo, hpo
;; update annotations from orphanet
;; indexes on iri, label


(defn open-ontology
  [path]
  (let [stream (io/input-stream path)]
    (.loadOntologyFromOntologyDocument owl-manager stream)))

(defn get-ann-value
  "Return value part of owl annotation, minus extra type data"
  [ann]
  (let [val (.getValue ann)]
    (if (instance? OWLLiteral val)
      (.getLiteral val)
      nil)))

(defn iri 
  "Return the IRI of a class in string form"
  [class]
  (-> class .getIRI .toString))

(defn get-annotations-str
  "Return collection of annotations describing enity in ontology
  When encountering multiple annotations of the same type, take
  only one"
  [ontology entity]
  (let [annotations (EntitySearcher/getAnnotations entity ontology)
        base {"iri" (iri entity)
              "curie" (-> entity .getIRI .getShortForm)}]
    (reduce (fn [m ann]
              (let [value (get-ann-value ann)
                    k (-> ann .getProperty str)]
                (assoc m k value)))
     base annotations)))

(defn get-annotations
  "Return collection of annotations describing entity in ontology
  each annotation is returned as a string or an array of strings."
  [ontology entity]
  (let [annotations (EntitySearcher/getAnnotations entity ontology)
        base {"iri" (iri entity)}]
    (reduce (fn [m ann]
              (let [value (get-ann-value ann)
                    k (-> ann .getProperty str)
                    current (m k)]
                (if current
                  (if (vector? current)
                    (assoc m k (into current (vector value)))
                    (assoc m k (vector current value)))
                 (assoc m k value))))
            base annotations)))


(defn get-mapped-annotations
  "Get annotations, filtered and mapped according to key-mappings"
  [ontology entity]
  (let [annotations (get-annotations-str ontology entity)
        filtered-ann (select-keys annotations (keys key-mappings))]
    (set/rename-keys filtered-ann key-mappings)))

(defn get-superclasses
  "Return the superclasses of entity as a seq of IRI strings"
  [reasoner entity]
  (let [super (.getSuperClasses reasoner entity true)
        entities (mapcat #(.getEntities %) super)]
    (map iri entities)))

(defn get-equivalences
  "return the equivalent classes of an entity as a seq of IRI strings
  less the original class"
  [reasoner entity]
  (let [equiv (.getEquivalentClasses reasoner entity)
        entities (.getEntities equiv)]
    (remove #(= (iri entity) %) (map iri entities))))

(defn write-classes
  "Write classes and relevant annotation to neo4j"
  [ontology]
  (let [classes (.getClassesInSignature ontology)
        annotations (map #(get-mapped-annotations ontology %) classes)]
    (neo/session
     [session]
     (.run session "unwind {props} as properties merge (n:RDFClass {iri: properties.iri}) set n += properties"
           {"props" annotations}))))

(defn update-classes
  "Write classes and relevant annotation to neo4j"
  [ontology]
  (let [classes (.getClassesInSignature ontology)
        annotations (map #(get-mapped-annotations ontology %) classes)]
    (neo/session
     [session]
     (.run session "unwind {props} as properties match (n:RDFClass {iri: properties.iri}) set n += properties"
           {"props" annotations}))))

(defn write-subclasses
  "Write subclass relationships to neo4j"
  [ontology reasoner]
  (let [classes  (.getClassesInSignature ontology)
        ;; vector of pairs [subclass superclass]
        relationships (reduce 
                       #(into %1 (map vector (-> %2 iri repeat)
                                      (get-superclasses reasoner %2)))
                       [] classes)]
    (neo/session
     [session]
     (.run session "unwind {rels} as rel match (c1:RDFClass {iri: head(rel)}), (c2:RDFClass {iri: last(rel)}) merge (c1)-[:subClassOf]->(c2)" {"rels" relationships}))))

(defn write-equivalences
  "Write connections between equivalent classes to neo4j"
  [ontology reasoner]
  (let [classes (.getClassesInSignature ontology)
        relationships (reduce #(into %1 (map vector (-> %2 iri repeat)
                                             (get-equivalences reasoner %2)))
                              [] classes)]
    (neo/session
     [session]
     (.run session "unwind {rels} as rel match (c1:RDFClass {iri: head(rel)}), (c2:RDFClass {iri: last(rel)}) merge (c1)-[:equivalentTo]-(c2)" {"rels" relationships}))))

(defn write-relationships
  "Write relationships between classes to neo4j"
  [ontology reasoner]
  (write-subclasses ontology reasoner)
  (write-equivalences ontology reasoner))


(defn import-ontology
  "Write classes and relationships from ontology specified at ontology-path
  into Neo4j, close ontology after"
  [ontology-path]
  (let [ontology (open-ontology ontology-path)
        reasoner (.createReasoner reasoner-factory ontology)]
    (write-classes ontology)
    (write-relationships ontology reasoner)
    (.dispose reasoner)
    (.removeOntology owl-manager ontology)))

(defn update-class-metadata
  "Update annotations for classes already in Neo4j from ontology on ontology-path
  close ontolgy afterward."
  [ontology-path]
  (let [ontology (open-ontology ontology-path)]
    (update-classes ontology)
    (.removeOntology owl-manager ontology)))

(defn import-ontology-classes
  "Write classes from ontology specified at ontology-path into Neo4j,
  close ontology after"
  [ontology-path]
  (let [ontology (open-ontology ontology-path)]
    (write-classes ontology)
    (.removeOntology owl-manager ontology)))

(defn write-csv
  "Write csv of ontology terms, labels, and descriptions to file"
  [ontology filename]
  (let [classes (.getClassesInSignature ontology)
        annotations (map #(get-mapped-annotations ontology %) classes)
        headers (vec (vals key-mappings))
        rows (mapv #(mapv (fn [k] (get % k "")) headers) annotations)
        csv-str (csv/write-csv (cons headers rows))]
    (spit filename csv-str)))
