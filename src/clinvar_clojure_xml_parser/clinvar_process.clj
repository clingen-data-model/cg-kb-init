(ns clinvar-clojure-xml-parser.clinvar-process
  (:require [clojure.data.xml :as xml]
            [clojure.xml :as cxml]
            [clojure.java.io :as io]
            [clojure.zip :as zip]
            [clojure.data.zip :as dzip]
            [clojure.data.zip.xml :as xdzip :refer [xml-> xml1-> attr attr= text]]
            [clojure.pprint :as pp :refer [pprint]]))


(def cvxml "data/ClinVarFullRelease_2018-03.xml")
(def output-file "data/clinvar.edn")

(defn import-clinvar
  "Read the ClinVar XML file, generate appropriate messages, and send to exchange"
  []
  (xml/parse (io/input-stream cvxml)))

(defn construct-clinicalassertion
  "Construct clinicalassertion table structure for import to neo"
  [node]
  (let [z (zip/xml-zip node)
        scvs (xml-> z
                    :ClinVarAssertion)             
        id (xml1-> z
                   :ClinVarAssertion
                   (attr :ID))]
    (map #(into {} (filter val {:clinicalassertionid (str id)
                                :submitterid (some-> (xml1-> % :ClinVarAccession (attr :OrgID)))
                                :submissiondate (some-> (xml1-> % :ClinVarSubmissionID (attr :submitterDate)))
                                :dateupdated (some->
                                             (xml1-> % :ClinVarAccession (attr :DateUpdated)))
                                :scvid (some->
                                             (xml1-> % :ClinVarAccession (attr :Acc)))
                                :scvversion (some->
                                             (xml1-> % :ClinVarAccession (attr :Version))
                                             Integer/parseInt)
                                :datelastevaluated (some->
                                                     (xml1-> %  (attr :DateLastEvaluated)))                               
                                :srecordstatus (xml1-> %
                                                      :RecordStatus
                                                      text)                     
                                :evidence
                                (xml1-> % :ClinicalSignificance
                                            :Description
                                            text)                         
                                :reviewstatus (xml1-> % :ClinicalSignificance :ReviewStatus text)
                                :citationid (some->(xml1-> % :ClinicalSignificance :Citation :ID) text)
                                :citationsource (xml1-> % :ClinicalSignificance :Citation :ID (attr :Source))
                                :assertionmethod (some->(xml1-> % :AttributeSet :Attribute (attr= :Type "AssertionMethod")) text)
                                :citationurl (some->(xml1-> % :AttributeSet :Citation :URL) text)
                                :citationid2 (some->(xml1-> % :AttributeSet :Citation :ID) text)
                               }))
         scvs)))

(defn construct-clingen-import
  "Deconstruct a ClinVar Set into the region, alterations, and assertions"
  [node]
  ;;(concat (conj (construct-clinicalassertion node)
          ;(construct-assertion-method node)
          ;(construct-assertion-annot node)
          ;))
  (concat (conj (construct-clinicalassertion node)
          ;(construct-assertion-method node)
          ;(construct-assertion-annot node)
          )))

(defn parse-clinvar-xml
  "Import variants from ClinVar, filter for CNVs"
  [path]
  (with-open [st (io/reader path)
              out (io/writer output-file)]
    (pprint   (->> st
                   xml/parse
                   :content 
                   (take 5000)
                   (map construct-clingen-import)) out)))

