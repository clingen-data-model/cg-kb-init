(ns chromatic-data.neo4j
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh])
  (:import [org.neo4j.driver.v1 Driver GraphDatabase AuthTokens Session StatementResult Record TransactionWork]))

(def neo4j-import-path (str 
                        (or (System/getenv "NEO4J_PATH")  "/usr/share/neo4j")
                        "/import/"))

(defn stage-file
  "Stage a file for import to Neo4j, trim lines from the front, optionally"
  [path target-name & {:keys [trim] :or {trim 0}}]
  (let [source (:out (sh/sh "sed" (format "1,%dd" trim) path))
        target (io/file neo4j-import-path target-name)]
    (spit target source)))

(defn get-val
  "Use the NEO4j type system to identify the type of the value in neo4j
  and retrieve it using the appropriate method. Return val
  if type is unrecognized"
  [v]
  ;; NULL, BOOLEAN, INTEGER, FLOAT, BYTES, STRING, LIST, MAP, STRUCT
  (let [type (-> v .type .name)]
    (case type
      "INTEGER" (.asInt v)
      "STRING" (.asString v)
      "FLOAT" (.asNumber v)
      "NULL" nil
      v)))

;; Create a neo4j session, do something in it, clean up after
(defmacro session
  "Connect to neo4j, make driver and session accessible to code
  clean up after"
  [[s] & body]
  `(let [driver# (GraphDatabase/driver (System/getenv "NEO4J_SERVER_PATH") (AuthTokens/basic (System/getenv "NEO4J_USER") (System/getenv "NEO4J_PASS")))
         ~s (.session driver#)
         result# (do ~@body)]

     (.close ~s)
     (.close driver#)
     result#))

(defn query
  "Run a cypher query and return the result as a seq of seqs"
  [session q]
  (let [result (iterator-seq (.run session q))]
    (map (fn [record] (map #(get-val %) (.values record))) result)))

(defn result-hash
  "Take the result of a cypher query and return the result mapped to clojure types"
  [result]
  (map (fn [record] (zipmap (.keys record) (map #(get-val %) (.values record)))) result))

(defn hquery
  "Run a cypher query and return the result as a seq of maps"
  ([session q]
   (let [result (iterator-seq (.run session q))]
     (result-hash result)))
  ([session q args]
   (let [result (iterator-seq (.run session q args))]
     (result-hash result))))

(defn run-in-tx
  "Run s if string, otherwise apply s to args"
  [t s]
  (if (string? s) (.run t s) (.run t (first s) (second s))))

(defn tx
  "Create a transation with one or more statements"
  [stmt & more]
  (proxy [TransactionWork] [] (execute [tx]
                                (let [res (run-in-tx tx stmt)]
                                  (doseq [s more]
                                    (run-in-tx tx s))
                                  res))))


