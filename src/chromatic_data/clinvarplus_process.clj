(ns clinvar-clojure-xml-parser.clinvarplus-process
  (:require [clojure.data.xml :as xml]
            [clojure.xml :as cxml]
            [clojure.java.io :as io]
            [clojure.zip :as zip]
            [clojure.data.zip :as dzip]
            [clojure.data.zip.xml :as xdzip :refer [xml-> xml1-> attr attr= text]]
            [clojure.pprint :as pp :refer [pprint]]))


(def cvxml "data/ClinVarFullRelease_2018-03.xml")
;(def cvxml "data/sample.xml")
(def output-file "data/clinvar.edn")
;(def output-file "data/sample.edn")

(defn construct-clinicalassertion
  "Construct clinicalassertion table structure for import to neo"
  [node]
  (let [z (zip/xml-zip node)
        scvs (xml-> z
                    :ClinVarAssertion)]             
    (map #(into {} (filter val {:clinicalassertionid (some-> (xml1-> % (attr :ID)))
                                :submitterid (some-> (xml1-> % :ClinVarAccession (attr :OrgID)))
                                :submittername (some-> (xml1-> % :ClinVarSubmissionID (attr :submitter)))
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
                                :clinicalsignificance
                                (xml1-> % :ClinicalSignificance
                                            :Description
                                            text) 
                                :evidence
                                (xml1-> % :ClinicalSignificance
                                            :Comment
                                            text)                         
                                :reviewstatus (some->(xml1-> % :ClinicalSignificance :ReviewStatus text))
                                :citationid (some->(xml1-> % :ClinicalSignificance :Citation :ID) text)
                                :citationsource (xml1-> % :ClinicalSignificance :Citation :ID (attr :Source))
                                :assertionmethod (some->(xml1-> % :AttributeSet :Attribute (attr= :Type "AssertionMethod")) text)
                                :citationurl (some->(xml1-> % :AttributeSet :Citation :URL) text)
                                :citationid2 (some->(xml1-> % :AttributeSet :Citation :ID) text)
                                :citationsource2 (some->(xml1-> % :AttributeSet :Citation :ID (attr :Source))) 
                                :citationtext (some->(xml1-> % :AttributeSet :Citation :CitationText) text)
                                :methodtype (some->(xml1-> % :ObservedIn :Method :MethodType) text)
                                :origin (some->(xml1-> % :ObservedIn :Sample :Origin) text)
                                :modeofinheritance (some->(xml1-> % :AttributeSet :Attribute (attr= :Type "ModeOfInheritance")) text)
                               }))
         scvs)))

(defn construct-variation
  "Construct variation nodes"
  [node]
  (let [z (zip/xml-zip node)
        id (xml1-> z
                   :ClinVarAssertion
                   (attr :ID))
        var (xml-> z :ReferenceClinVarAssertion
                     :MeasureSet)]
        (map #(into {} (filter val {:clinicalassertionid id
                                    :variationid (attr % :ID)
                                    :variationtype (attr % :Type)
                                    :variationname (some->
                                                    (xml1-> % :Name 
                                                              :ElementValue (attr= :Type "Preferred")) text)                                                                            
                                    :species (some->(xml1-> % :ReferenceClinVarAssertion :ObservedIn :Sample :Species) text)
                                    }))
             var)))

(defn construct-conditions
  "Construct conditions nodes"
  [node]
  (let [z (zip/xml-zip node)
        conds (xml-> z :ReferenceClinVarAssertion)
        id (xml1-> z
                   :ClinVarAssertion
                   (attr :ID))]
        (map #(into {} (filter val {:clinicalassertionid id
                                    :medgencui (some-> (xml1-> % :TraitSet :Trait :XRef (attr= :DB "MedGen"))(attr :ID))   
                                    :mappingvalue (some->
                                                    (xml1-> % :TraitSet
                                                              :Trait 
                                                              :Name 
                                                              :ElementValue) text)                                         
                                    :mappingref (some-> (xml1-> % :TraitSet
                                                              :Trait 
                                                              :Name 
                                                              :ElementValue (attr :Type)))                                                                  
                                    :traittype (some-> (xml1-> % :TraitSet  (attr= :Type "Disease")
                                                                 :Trait
                                                                 (attr :Type)))                                    
                                    }))
             conds)))

(defn construct-allele
  "Construct allele nodes"
  [node]
  (let [z (zip/xml-zip node)
        allele (xml-> z
                    :ReferenceClinVarAssertion
                    :MeasureSet
                    :Measure)
        varid (xml1-> z
                      :ReferenceClinVarAssertion
                      :MeasureSet
                      (attr :ID))]       
        (map #(into {} (filter val {:variationid varid
                                    :alleleid (attr % :ID)                               
                                    :alleletype (attr % :Type)
                                    :allelename (some-> (xml1-> % :Name
                                                                  :ElementValue
                                                                  (attr= :Type "Preferred")) text)
                                    :allelestop (some-> (xml1-> % :SequenceLocation
                                                              (attr :Stop)))
                                    :allelestart (some-> (xml1-> % :SequenceLocation
                                                               (attr :Start)))
                                    :allelechr (some-> (xml1-> % :SequenceLocation
                                                                  (attr :Chr)))
                                    :haploinsufficiency (some-> (xml1-> % :MeasureRelationship
                                                               :AttributeSet
                                                               :Attriute
                                                               (attr= :Type "Haploinsufficiency")))
                                    :triplosensitivity (some-> (xml1-> % :MeasureRelationship
                                                               :AttributeSet
                                                               :Attriute
                                                               (attr= :Type "Triplosensitivity")))}))
        allele)))

(defn construct-clingen-import
  "Deconstruct a ClinVar Set into the region, alterations, and assertions"
  [node]
  ;; {:clinicalassertion (construct-clinicalassertion node)
  ;;  :variation (construct-variation node)
  ;;  :conditions (construct-conditions node)
  ;;  :allele (construct-allele node)}
  (concat (construct-clinicalassertion node)
          (construct-variation node)
          (construct-conditions node)
          (construct-allele node)))

(defn parse-clinvar-xml
  "Import data from ClinVar and store it in an intermediate file"
  [path]
  (with-open [st (io/reader path)
              out (io/writer output-file)]
    (pprint   (->> st
                   xml/parse
                   :content
                   (take 50000)
                   (map construct-clingen-import))out)))


