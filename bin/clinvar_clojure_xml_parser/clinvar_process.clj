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
                                ;:assertionmethod (some->(xml1-> % :AttributeSet :Attribute (attr= :Type "AssertionMethod")) text)
                                ;:citationurl (some->(xml1-> % :AttributeSet :Citation :URL) text)
                                ;:citationid (some->(xml1-> % :AttributeSet :Citation :ID) text)
                               }))
         scvs)))

(defn construct-assertion-method
  "Construct assertion-method table structure for import to neo"
  [node]
  (let [z (zip/xml-zip node)
        assertionm (xml-> z
                     :ClinVarAssertion)             
        id (xml1-> z
                   :ClinVarAssertion
                   (attr :ID))]
        (map #(into {} (filter val {:clinicalassertionid (str id)
                                    :assertionmethod (some->(xml1-> % :AttributeSet :Attribute (attr= :Type "AssertionMethod")) text)
                                    :citationurl (some->(xml1-> % :AttributeSet :Citation :URL) text)
                                    :citationid (some->(xml1-> % :AttributeSet :Citation :ID) text)
                                    }))
             assertionm)))

(defn construct-assertion-annot
  "Construct assertion-annot table structure for import to neo"
  [node]
  (let [z (zip/xml-zip node)
        assertionan (xml-> z
                      :ClinVarAssertion)             
        id (xml1-> z
                   :ClinVarAssertion
                   (attr :ID))]
        (map #(into {} (filter val {:clinicalassertionid (str id)
                                    :citationid (some->(xml1-> % :ClinicalSignificance :Citation :ID) text)
                                    :citationsource (xml1-> % :ClinicalSignificance :Citation :ID (attr :Source))
                                    }))
             assertionan)))
                                   
  

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

(defn clinvar-cnvs
  "Import variants from ClinVar, filter for CNVs"
  [path]
  (with-open [st (io/reader path)
              out (io/writer output-file)]
    (pprint   (->> st
                   xml/parse
                   :content 
                   (take 5000)
                   (map construct-clingen-import)) out)))

