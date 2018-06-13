(ns clinvar-clojure-xml-parser.clinvarplus-process-betaxml
  (:require [clojure.data.xml :as xml]
            [clojure.xml :as cxml]
            [clojure.java.io :as io]
            [clojure.zip :as zip]
            [clojure.data.zip :as dzip]
            [clojure.data.zip.xml :as xdzip :refer [xml-> xml1-> attr attr= text]]
            [clojure.pprint :as pp :refer [pprint]]))


(def cvxml "data/variation_archive_20180418.xml")
;(def cvxml "data/sample.xml")
(def output-file "data/clinvarbeta.edn")
;(def output-file "data/sample.edn")

(defn clinsig=
  "returns nil unless variant type equals the argument"
  [node pred]
  (xml1-> (zip/xml-zip node)
          :VariationArchive
          :InterpretedRecord
          :ClinicalAssertionList
          :ClinicalAssertion
          :Interpretation
          :Description
          text))

(defn evidence=
  "returns nil unless variant type equals the argument"
  [node pred]
  (xml1-> (zip/xml-zip node)
          :VariationArchive
          :InterpretedRecord
          :ClinicalAssertionList
          :ClinicalAssertion
          :Interpretation          
          text))

;construct variation node
(defn construct-variation
  "Construct variation nodes"
  [node]
  (let [z (zip/xml-zip node)
       var (xml-> z :VariationArchive)] 
      (map #(into {} (filter val {:variationid (attr % :VariationID)                                  
                                  :variationtype (attr % :VariationType)
                                  :variationversion (attr % :Version)
                                  :variationname (attr % :VariationName)
                                  :variationaccession (attr % :VariationAccession)
                                  :vdatecreated (attr % :DateCreated)
                                  :vdateLastupdated (attr % :DateLastUpdated)
                                  :vrecordstatus (attr % :RecordStatus)
                                  :vrecordtype (attr % :RecordType)                                   
                                  :clinvarclinsig (some->(xml1-> % :InterpretedRecord
                                                                   :Interpretations                                                                   
                                                                   :Interpretation
                                                                   :Description) text)
                                 :vdatelastevaluated (some->(xml1-> % :InterpretedRecord
                                                                      :Interpretations
                                                                      :Interpretation
                                                                      (attr :DateLastEvaluated)))
                                 :vreviewstatus (some->(xml1-> % :InterpretedRecord
                                                                 :ReviewStatus) text)
                                 :species (some->(xml1-> % :Species) text)
                                 :valleleid (some->(xml1-> % :InterpretedRecord :SimpleAllele (attr :AlleleID)))
                                 :valleleid2 (some->(xml1-> % :InterpretedRecord :Haplotype :SimpleAllele (attr :AlleleID)))
                                 :valleleid3 (some->(xml1-> % :IncludedRecord :SimpleAllele (attr :AlleleID)))
                                 }))
           var)))

;construct clinicalassertion node
(defn construct-clinicalassertion
  "Construct clinicalassertion table structure for import to neo"
  [node]
  (let [z (zip/xml-zip node)
    scvs (xml-> z                   
                :VariationArchive
                :InterpretedRecord
                :ClinicalAssertionList
                :ClinicalAssertion)
    variationid (xml1-> z                   
               :VariationArchive
               (attr :VariationID))]            
    (map #(into {} (filter val {
                                :clinicalassertionid (attr % :ID) 
                                :variationid variationid
                                :submissiondate (attr % :SubmissionDate)
                                :assertiontype (some->(xml1-> % :Assertion) text) 
                                :submitterid (some-> (xml1-> % :ClinVarAccession (attr :OrgID)))
                                :submittername (some-> (xml1-> % :ClinVarAccession (attr :SubmitterName)))
                                :datecreated (some->(xml1-> % (attr :DateCreated)))                                
                                :dateupdated (some->
                                             (xml1-> % (attr :DateLastUpdated)))
                                :scvid (some->
                                             (xml1-> % :ClinVarAccession (attr :Accession)))
                                :scvversion (some->
                                             (xml1-> % :ClinVarAccession (attr :Version))
                                             Integer/parseInt)
                                :datelastevaluated (some->
                                                     (xml1-> %  :Interpretation (attr :DateLastEvaluated)))                               
                                :srecordstatus (some->(xml1-> % :RecordStatus text))   
                                :reviewstatus (some->(xml1-> %  :ReviewStatus text))
                                :citationid (xml1-> % 
                                           :ObservedInList 
                                           :ObservedIn
																					 :ObservedData 
													                 :Citation :ID text)
                                :citationsource (xml1-> % :ObservedInList 
                                                          :ObservedIn
                                                          :ObservedData 
                                                          :Citation :ID (attr :Source))
                                :clinicalsignificance (some-> (xml1-> % :Interpretation
                                                                 :Description
                                                                 text)) 
                                :evidence (some->(xml1-> % :Interpretation
                                            :Comment 
                                            text))    
                                :assertionmethod (some->(xml1-> % :AttributeSet :Attribute (attr= :Type "AssertionMethod")) text)                                                
                                :citationurl (some->(xml1-> % :AttributeSet :Citation :URL) text)
                                :citationid2 (some->(xml1-> % :AttributeSet :Citation :ID) text)
                                :citationsource2 (some->(xml1-> % :AttributeSet :Citation :ID (attr :Source))) 
                                :citationtext (some->(xml1-> % :AttributeSet :Citation :CitationText) text)
                                :methodtype (some->(xml1-> % :ObservedInList :ObservedIn :Method :MethodType) text)
                                :origin (some->(xml1-> % :ObservedInList :ObservedIn :Sample :Origin) text)
                                :modeofinheritance (some->(xml1-> % :AttributeSet :Attribute (attr= :Type "ModeOfInheritance")) text)
                               }))
         scvs)))

(defn construct-conditions
  "Construct conditions nodes"
  [node]
  (let [z (zip/xml-zip node)
        conds (xml-> z :VariationArchive
                       :InterpretedRecord
                       :TraitMappingList
                       :TraitMapping)]
        (map #(into {} (filter val {:clinicalassertionid (attr % :ClinicalAssertionID) 
                                    :medgencui (some-> (xml1-> % :MedGen (attr :CUI))) 
                                    :medgenname (some-> (xml1-> % :MedGen (attr :Name))) 
                                    :mappingvalue (attr % :MappingValue)                                         
                                    :mappingref (attr % :MappingRef)  
                                    :mappingtype (attr % :MappingType)
                                    :traittype   (attr % :TraitType)                               
                                    }))
             conds)))

(defn construct-allele
  "Construct allele nodes"
  [node]
  (let [z (zip/xml-zip node)
        allele (xml-> z
                    :VariationArchive
                    :InterpretedRecord
                    :SimpleAllele) 
        submittedassembly (xml1-> z
                          :VariationArchive
                          :InterpretedRecord
                          :ClinicalAssertion
                          :ClinVarSubmissionID
                          (attr :submittedAssembly))]
        (map #(into {} (filter val {:alleleid (attr % :AlleleID)
                                    :variationid (attr % :VariationID)                                     
                                    :varianttype (some-> (xml1-> % :VariantType) text)                                    
                                    :allelename (some-> (xml1-> % :Name) text)                                     
                                    :allelelength (some-> (xml1-> % :Location :SequenceLocation (attr :variantLength)))                                    
                                    :submittedassembly submittedassembly
                                    :haploinsufficiency (some-> (xml1-> % 
                                                               :GeneList
                                                               :Gene
                                                               :Haploinsufficiency)text)
                                    :triplosensitivity (some-> (xml1-> % 
                                                               :GeneList
                                                               :Gene
                                                               :Triplosensitivity)text)                          
                                    
                                    }))
        allele)))

(defn construct-includedallele
  "Construct allele nodes"
  [node]
  (let [z (zip/xml-zip node)
        allele (xml-> z
                    :VariationArchive
                    :IncludedRecord
                    :SimpleAllele) 
        submittedassembly (xml1-> z
                          :VariationArchive
                          :InterpretedRecord
                          :ClinicalAssertion
                          :ClinVarSubmissionID
                          (attr :submittedAssembly))]
        (map #(into {} (filter val {:alleleid (attr % :AlleleID)
                                    :variationid (attr % :VariationID)                                     
                                    :varianttype (some-> (xml1-> % :VariantType) text)                                    
                                    :allelename (some-> (xml1-> % :Name) text)                                     
                                    :allelelength (some-> (xml1-> % :Location :SequenceLocation (attr :variantLength)))                                    
                                    :submittedassembly submittedassembly
                                    :haploinsufficiency (some-> (xml1-> % 
                                                               :GeneList
                                                               :Gene
                                                               :Haploinsufficiency)text)
                                    :triplosensitivity (some-> (xml1-> % 
                                                               :GeneList
                                                               :Gene
                                                               :Triplosensitivity)text)                          
                                    
                                    }))
        allele)))


(defn construct-haplotype
  "Construct allele nodes"
  [node]
  (let [z (zip/xml-zip node)         
        haplotype (xml-> z
                    :VariationArchive
                    :InterpretedRecord
                    :Haplotype) 
        alleleid2 (xml1-> z
                    :VariationArchive
                    :InterpretedRecord
                    :Haplotype
                    :SimpleAllele (attr :AlleleID)) 
        submittedassembly (xml1-> z
                          :VariationArchive
                          :InterpretedRecord
                          :ClinicalAssertion
                          :ClinVarSubmissionID
                          (attr :submittedAssembly))]
        (map #(into {} (filter val {:variationid (attr % :VariationID)                                    
                                    :alleleid alleleid2                                                                    
                                    :varianttype (some-> (xml1-> % :VariationType) text)                                      
                                    :allelename (some-> (xml1-> % :Name) text)                                      
                                    :allelelength (some-> (xml1-> % :Location :SequenceLocation (attr :variantLength))) 
                                    :submittedassembly submittedassembly                                  
                                    }))
        haplotype)))

(defn construct-regions
  "Construct a basic, flat allele for import to neo"
  [node]
  (let [z (zip/xml-zip node)
        locs (xml-> z
                    :VariationArchive
                    :InterpretedRecord
                    :SimpleAllele
                    :Location
                    :SequenceLocation)]
        (map #(into {} (filter val {:assembly (attr % :Assembly)
                                    :chromosome (attr % :Chr)
                                    :inner_start (attr % :innerStart)
                                    :outer_start (attr % :outerStart)
                                    :start (attr % :start)
                                    :inner_stop (attr % :innerStop)
                                    :outer_stop (attr % :outerStop)
                                    :stop (attr % :stop)
                                    :alternate_allele (attr % :alternateAllele)
                                    :reference_allele (attr % :referenceAllele)}))
         locs)))

(defn construct-clingen-import
  "Deconstruct a ClinVar Set into the region, alterations, and assertions"
  [node]
  ;; {:clinicalassertion (construct-clinicalassertion node)
  ;;  :variation (construct-variation node)
  ;;  :conditions (construct-conditions node)
  ;;  :allele (construct-allele node)}
  (concat   
  ;(construct-citation node)
  (construct-variation node)
  (construct-clinicalassertion node)
  (construct-conditions node)
  (construct-allele node)
  (construct-haplotype node)
  (construct-includedallele node)
  (construct-regions node)))

(defn parse-clinvar-xml
  "Import data from ClinVar and store it in an intermediate file"
  [path]
  (with-open [st (io/reader path)
              out (io/writer output-file)]
    (pprint   (->> st
                   xml/parse
                   :content
                   (filter #(= (evidence= % "Converted during submission to Pathogenic.")))
                   (take 500)
                   (map construct-clingen-import))out)))


