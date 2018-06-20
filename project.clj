(defproject chromatic-data "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories {"local" ~(str (.toURI (java.io.File. "maven_repository")))}
  :plugins [[lein-localrepo "0.5.3"]]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/java.jdbc "0.5.0"]
                 [clojure-csv/clojure-csv "2.0.1"]
                 [net.sourceforge.owlapi/owlapi-distribution "4.2.5"]
                 [org.semanticweb.elk/elk-distribution "0.4.3"]
                 [cheshire "5.7.1"]
                 ;; [circleci/clj-yaml "0.5.5"]
                 ;; [clj-http "2.1.0"]
                 [org.neo4j.driver/neo4j-java-driver "1.6.1"]
                 [org.apache.kafka/kafka-clients "0.10.1.0"]
                 [org.clojure/data.xml "0.1.0-beta2"]
                 [org.clojure/data.zip "0.1.2"]
                 [clj-http "2.3.0"]
                 [com.velisco/clj-ftp "0.3.9"]
                 [org.clojure/core.async "0.3.443"]
                 [org.eclipse.rdf4j/rdf4j-runtime "2.2.2"]
                 [grafter "0.9.0"]]
  :resource-paths ["resources/sqyljdbc4.jar" "resources/sqljdbc.jar"]
  :jvm-opts ["-Djava.util.logging.config.file=logging.properties"]
  :main ^:skip-aot chromatic-data.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
