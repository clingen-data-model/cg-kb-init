(ns chromatic-data.clinvarplus-process-betaxml
  (:require [clojure.data.xml :as xml]
            [clojure.xml :as cxml]
            [clojure.java.io :as io]
            [clojure.zip :as zip]
            [clojure.data.zip :as dzip]
            [clojure.string :as str]
            [clojure.data.zip.xml :as xdzip :refer [xml-> xml1-> attr attr= text]]
            [clojure.pprint :as pp :refer [pprint]]
            [clojure.core.async :as async :refer [>! <! >!! <!! go chan buffer pipeline
                                                  close! thread alts! alts!! timeout]]))


(def cvxml "data/variation_archive_20180418.xml")
;;(def cvxml "data/sample.xml")
(def output-file "data/clinvarbeta.edn")
;;(def output-file "data/sample.edn")


(def batch-size 100)


(defn clinsig=
  "returns nil unless variant type equals the argument"
  [node pred]
  (xml1-> :VariationArchive
          :InterpretedRecord
          :ClinicalAssertionList
          :ClinicalAssertion
          :Interpretation
          :Description
          text))

(defn evidence=
  "returns nil unless variant type equals the argument"
  [node pred]
  (xml1-> :VariationArchive
          :InterpretedRecord
          :ClinicalAssertionList
          :ClinicalAssertion
          :Interpretation
          :Comment          
          text) pred)

;construct variation node
(defn construct-variation
  "Construct variation nodes"
  [node]
  (let [var (xml-> node :VariationArchive)] 
    (map #(into {} (filter val {:variationid (attr % :VariationID)                                  
                                :variationtype (attr % :VariationType)
                                :variationversion (attr % :Version)
                                :variationname (attr % :VariationName)
                                :variationaccession (attr % :VariationAccession)
                                :vdatecreated (attr % :DateCreated)
                                :vdateLastupdated (attr % :DateLastUpdated)
                                :vrecordstatus (attr % :RecordStatus)
                                :vrecordtype (attr % :RecordType)                                   
                                :clinicalsignificance (some->(xml1-> % :InterpretedRecord
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
  (let [scvs (xml-> node                   
                    :VariationArchive
                    :InterpretedRecord
                    :ClinicalAssertionList
                    :ClinicalAssertion)
        variationid (xml1-> node                   
                            :VariationArchive
                            (attr :VariationID))
        evidence (xml1-> node :VariationArchive
                         :InterpretedRecord
                         :ClinicalAssertionList
                         :ClinicalAssertion
                         :Interpretation
                         :Comment text)
        clinicalsignificance (xml1-> node :VariationArchive
                                     :InterpretedRecord
                                     :ClinicalAssertionList
                                     :ClinicalAssertion
                                     :Interpretation
                                     :Description text)]            
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
                                :clinicalsignificance (if (not (some-> evidence (str/includes? "Converted during submission to"))) (do clinicalsignificance) (str/replace evidence #"Converted during submission to |\." {"Converted during submission to " " " "." ""}))                   
                                :evidence (when (not (some-> evidence (str/includes? "Converted during submission to"))) evidence)     
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

(defn construct-citations
  "Construct citation nodes"
  [node]
  (let [assertions (xml-> node
                          :VariationArchive
                          :InterpretedRecord
                          :ClinicalAssertionList
                          :ClinicalAssertion)]
    
    (for [a assertions
          :let [assertion-id (attr a :ID)
                citations (xml-> a 
                                 :ObservedInList
                                 :ObservedIn
                                 :ObservedData)]
          :when (not-empty citations)]
      (apply merge (map #(into {} (filter val {:clinicalassertionid assertion-id
                                               :citationid (some->(xml1-> % :Citation :ID) text)
                                               :citationsource (some->(xml1-> % :Citation :ID (attr :Source)))
                                               }))citations)))))

(defn construct-conditions
  "Construct conditions nodes"
  [node]
  (let [conds (xml-> node :VariationArchive
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
  (let [allele (xml-> node
                      :VariationArchive) 
        submittedassembly (xml1-> node
                                  :VariationArchive
                                  :InterpretedRecord
                                  :ClinicalAssertion
                                  :ClinVarSubmissionID
                                  (attr :submittedAssembly))]
    (map #(into {} (filter val {:variationid (attr % :VariationID)    
                                :alleleid  (some-> (xml1-> % :InterpretedRecord :SimpleAllele (attr :AlleleID)))                                                                    
                                :varianttype (some-> (xml1-> % :InterpretedRecord :SimpleAllele :VariantType) text)                                    
                                :allelename (some-> (xml1-> % :InterpretedRecord :SimpleAllele :Name) text)                                     
                                :allelelength (some-> (xml1-> % :InterpretedRecord :SimpleAllele :Location :SequenceLocation (attr :variantLength)))                                    
                                :submittedassembly submittedassembly
                                :haploinsufficiency (some-> (xml1-> % :InterpretedRecord :SimpleAllele
                                                                    :GeneList
                                                                    :Gene
                                                                    :Haploinsufficiency)text)
                                :triplosensitivity (some-> (xml1-> % :InterpretedRecord :SimpleAllele
                                                                   :GeneList
                                                                   :Gene
                                                                   :Triplosensitivity)text)                          
                                
                                }))
         allele)))

(defn construct-includedallele
  "Construct allele nodes"
  [node]
  (let [allele (xml-> node
                      :VariationArchive
                      :IncludedRecord
                      :SimpleAllele) 
        submittedassembly (xml1-> node
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
  (let [haplotype (xml-> node
                         :VariationArchive) 
        submittedassembly (xml1-> node
                                  :VariationArchive
                                  :InterpretedRecord
                                  :ClinicalAssertion
                                  :ClinVarSubmissionID
                                  (attr :submittedAssembly))]
    (map #(into {} (filter val {:variationid (attr % :VariationID)                                    
                                :alleleid (some->>(xml1-> %											                    
                                                          :InterpretedRecord
                                                          :Haplotype
                                                          :SimpleAllele (attr :AlleleID)))                                                                                                      
                                :varianttype (some-> (xml1-> % :InterpretedRecord :Haplotype :VariationType) text)                                      
                                :allelename (some-> (xml1-> % :InterpretedRecord :Haplotype :Name) text)                                      
                                :allelelength (some-> (xml1-> % :InterpretedRecord :Haplotype :Location :SequenceLocation (attr :variantLength))) 
                                :submittedassembly submittedassembly                                  
                                }))
         haplotype)))

(defn construct-regions
  "Construct a basic, flat allele for import to neo"
  [node]
  (let [locs (xml-> node
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
   (construct-variation node)
   (construct-clinicalassertion node)
   (construct-citations node)
   (construct-conditions node)
   (construct-allele node)
   (construct-haplotype node)
   (construct-includedallele node)
   (construct-regions node)))

(defn write-cvp-file
  "Emit a file containing records"
  [records out-file]
  (thread
    (spit out-file (prn-str records))))

(defn write-cvp-recs
  "Asynchronously write records to output file"
  [out-port]
  (loop [n 0
         recs ()]
    (let [record (<!! out-port)]
      (if-not record
        (write-cvp-file recs (str "data/cvp/cvp-batch-" n ".edn"))
        (let [end-batch? (and (= 0 (mod n batch-size)) (< 0 n))]
          (when end-batch?
            (pprint n)
            (write-cvp-file (conj recs record) (str "data/cvp/cvp-batch-" n ".edn")))
          (recur (+ n 1)
                 (if end-batch? () (conj recs record))))))))

(defn read-cvp-recs
  [xml-reader in-port]
  (thread
    (doseq [r (->> xml-reader xml/parse :content)]
      (let [z (zip/xml-zip r)]
        (>!! in-port z)))
    (close! in-port)))

(defn parse-clinvar-xml
  "Import data from ClinVar and store it in an intermediate file"
  [path]
  (with-open [st (io/reader path)]
    (let [xml-records (chan 10)
          edn-records (chan)]
      (println "Starting to write clinvar-plus to channel")
      (read-cvp-recs st xml-records)
      (println "Starting tranformation pipeline")
      (pipeline 16 edn-records (map construct-clingen-import) xml-records)
      (println "Writing cvp recs to files")
      (write-cvp-recs edn-records)
      ;; For some reason I can't fix the race condition where the stream closes
      ;; before thread operations are complete. This is an ugly hack, but it
      ;; solves the problem
      (Thread/sleep 100))))


