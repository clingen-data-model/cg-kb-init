(defproject chromatic-data "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories {"local" ~(str (.toURI (java.io.File. "maven_repository")))}
  :plugins [[lein-localrepo "0.5.3"]]
  :repl-options {:port 9999
                 :host "0.0.0.0"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [cheshire "5.8.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/java.jdbc "0.5.0"]
                 [clojure-csv/clojure-csv "2.0.1"]
                 [org.neo4j.driver/neo4j-java-driver "1.6.1"]
                 [org.apache.kafka/kafka-clients "0.10.1.0"]
                 [org.clojure/data.xml "0.1.0-beta2"]
                 [org.clojure/data.zip "0.1.2"]
                 [clj-http "2.3.0"]
                 [com.velisco/clj-ftp "0.3.9"]
                 [org.clojure/core.async "0.3.443"]
                 [org.apache.jena/jena-core "3.8.0"]
                 [mount "0.1.12"]]
  :resource-paths ["resources/sqyljdbc4.jar" "resources/sqljdbc.jar"]
  :jvm-opts ["-Djava.util.logging.config.file=logging.properties"]
  :main ^:skip-aot chromatic-data.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
