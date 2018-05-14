(ns chromatic-data.clinvarplus-process-betaxml-test
(:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [tupelo.core :as t]
            [chromatic-data.core :refer :all]
            [chromatic-data.clinvarplus-process-betaxml :as process])
  (:import java.io.PushbackReader
             java.util.UUID))

(def cvxml "data/variation_archive_20180418.xml")
(def output (process/parse-clinvar-xml cvxml))
(def output-file "data/sample.edn")

(defn construct-variation-test
    [outputmap]
    (is (t/contains-key? outputmap :vreviewstatus))
		(is (t/contains-val? outputmap "no assertion criteria provided"))
	  (is (t/contains-key? outputmap :valleleid))
    (is (t/contains-val? outputmap "19343"))
	  (is (t/contains-key? outputmap :vrecordtype))
	  (is (t/contains-key? outputmap :variationversion))
	  (is (t/contains-key? outputmap :variationid))
	  (is (t/contains-key? outputmap :vdatelastevaluated))
	  (is (t/contains-key? outputmap :species))
		(is (t/contains-key? outputmap :variationtype)) 
		(is (t/contains-key? outputmap :variationname))
		(is (t/contains-key? outputmap :vdatecreated))
		(is (t/contains-key? outputmap :clinicalsignificance))
		(is (t/contains-key? outputmap :vdateLastupdated)))

(defn construct-assertionmap-test
    [outputmap]
	    (is (t/contains-key? outputmap :methodtype))			
		  (is (t/contains-key? outputmap :scvversion))
		  (is (t/contains-key? outputmap :assertiontype))
		  (is (t/contains-key? outputmap :srecordstatus))
		  (is (t/contains-key? outputmap :scvid))
			(is (t/contains-key? outputmap :variationid)) 			
			(is (t/contains-key? outputmap :clinicalassertionid))
			(is (t/contains-key? outputmap :datelastevaluated))
			(is (t/contains-key? outputmap :submitterid))
      (is (t/contains-key? outputmap :submissiondate))
      (is (t/contains-key? outputmap :submittername))
	    (is (t/contains-key? outputmap :origin))
	    (is (t/contains-key? outputmap :datecreated))
	    (is (t/contains-key? outputmap :dateupdated))
      (is (t/contains-key? outputmap :clinicalsignificance))
      (is (t/contains-key? outputmap :reviewstatus)))

(defn construct-condition-test
  [outputmap]
  (is (t/contains-key? outputmap :clinicalassertionid))
	(is (t/contains-key? outputmap :medgencui))
	(is (t/contains-key? outputmap :medgenname))
	(is (t/contains-key? outputmap :mappingvalue))
	(is (t/contains-key? outputmap :mappingref))
	(is (t/contains-key? outputmap :mappingtype))
	(is (t/contains-key? outputmap :traittype)))

(defn construct-allele-test
  [outputmap]
  (is (t/contains-key? outputmap :alleleid))
	(is (t/contains-key? outputmap :variationid))
	(is (t/contains-key? outputmap :varianttype))
	(is (t/contains-key? outputmap :allelename))
	(is (t/contains-key? outputmap :allelelength)))
	

(defn assertiontype-test
  [outputmap]
  (is (t/contains-val? outputmap "variation to disease")))
  
 (deftest map-construction-test
  (testing "variationmap,assertionmap construction"
      (with-open [r (PushbackReader. (io/reader output-file))]
      (let [outputlist (edn/read r)]
      (doseq [i outputlist]
        (doseq [outputmap i]         
            (print outputmap)	
            (if (t/contains-key? outputmap :valleleid)
	            (construct-variation-test outputmap))
            (if (t/contains-key? outputmap :scvid)
              (construct-assertionmap-test outputmap))
            (if (t/contains-key? outputmap :traittype)
              (construct-condition-test outputmap))
            (if (t/contains-key? outputmap :alleleid)
              (construct-allele-test outputmap))
            (if (t/contains-key? outputmap :assertiontype)
              (assertiontype-test outputmap))
            
			   ))))))
 

(run-tests)