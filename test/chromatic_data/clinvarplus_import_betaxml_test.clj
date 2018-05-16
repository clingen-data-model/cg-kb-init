(ns chromatic-data.clinvarplus-import-betaxml-test
(:require [clojure.test :refer :all]
          [neo4j-clj.core :as db]
          [chromatic-data.neo4j :as neo]
          [chromatic-data.core :refer :all]))

(def local-db
  (db/connect "bolt://localhost:11002" "neo4j" "atif5546"))

;ENUM QUERIES
(db/defquery methodtype-query "MATCH (n:MethodType) WHERE NOT n.MethodType IN ['curation', 'literature only','reference population', 'provider interpretation','case-control', 'clinical testing','in vitro','in vivo','inferred from source', 'research','phenotyping only','not provided'] RETURN n")
(db/defquery assertiontype-query "MATCH (n:AssertionType) WHERE NOT n.AssertionType IN ['variation to disease', 'variation to included disease', 'variation in modifier gene to disease', 'confers sensitivity', 'confers resistance', 'variant to named protein'] RETURN n")
(db/defquery traittype-query "MATCH (n:Trait) WHERE NOT n.TraitType IN ['Disease', 'DrugResponse', 'BloodGroup', 'Finding', 'PhenotypeInstruction', 'NamedProteinVariant'] RETURN n")
(db/defquery clinsig-query "MATCH (n:ClinicalSignificance) WHERE NOT n.ClinicalSignificance IN ['Pathogenic', 'Likely pathogenic', 'Pathogenic/Likely pathogenic', 'Uncertain significance', 'Likely benign', 'Benign', 'Benign/Likely benign', 'drug response', 'not provided', 'other', 'risk factor', 'association', 'Affects', 'confers sensitivity', 'protective', 'association not found', 'Conflicting data from submitters', 'Conflicting interpretations of pathogenicity'] RETURN n")
(db/defquery variationtype-query "MATCH (n:VariationType) WHERE NOT n.VariationType IN ['Complex', 'CompoundHeterozygote', 'Deletion', 'Diplotype', 'Duplication', 'Haplotype', 'Indel', 'Insertion', 'Inversion', 'Microsatellite', 'Phase unknown', 'Translocation', 'Variation', 'copy number gain', 'copy number loss', 'fusion', 'protein only', 'single nucleotide variant', 'Gene', 'Variation', 'Tandem duplication', 'Structural variant', 'QTL'] RETURN n")
(db/defquery reviewstatus-query "MATCH (n:Level) WHERE NOT n.ReviewStatus IN ['no assertion provided', 'no assertion criteria provided', 'criteria provided, single submitter', 'reviewed by expert panel', 'practice guideline', 'criteria provided, multiple submitters, no conflicts', 'criteria provided, conflicting interpretations'] RETURN n")
(db/defquery recordtype-query "MATCH (n:RecordType) WHERE NOT n.RecordType IN ['interpreted', 'included'] RETURN n")
(db/defquery recordstatus-query "MATCH (n:State) WHERE NOT n.RecordStatus IN ['current', 'replaced', 'removed'] RETURN n")
(db/defquery origin-query "MATCH (n:Origin) WHERE NOT n.Origin IN ['germline', 'somatic', 'de novo', 'unknown', 'not provided', 'inherited', 'maternal', 'paternal', 'uniparental', 'biparental', 'not-reported', 'tested-inconclusive', 'not applicable'] RETURN n")
(db/defquery labListcriteria-query "MATCH (n:LabListCriteria) WHERE NOT n.LabListCriteria IN ['MeetsRequirements', 'SubmittedEvidence', 'SubmissionPercent', 'DiscrepancyResolution', 'ConsentingMechanism'] RETURN n")

;VARIATION RELATIONSHIP QUERIES
(db/defquery has-significance-query "MATCH (p :Variation) WHERE NOT (p)-[:HAS_SIGNIFICANCE]->(:ClinicalSignificance) RETURN p")
(db/defquery has-state-query "MATCH (p :Variation) WHERE NOT (p)-[:HAS_STATE]->() RETURN p")
(db/defquery has-level-query "MATCH (p :Variation) WHERE NOT (p)-[:HAS_LEVEL]->() RETURN p")
(db/defquery has-type-query "MATCH (p :Variation) WHERE NOT (p)-[:HAS_TYPE]->(:VariationType) RETURN p")
(db/defquery has-recordtype-query "MATCH (p:Variation) WHERE NOT (p)-[:HAS_TYPE]->(:RecordType) RETURN p")
(db/defquery is-comprisedof-query "MATCH (p:Variation) WHERE NOT (p)-[:IS_COMPRISED_OF]->() RETURN p")
;Allele RELATIONSHIP QUERIES
(db/defquery has-variationtype-query "MATCH (p :Allele) WHERE NOT (p)-[:HAS_TYPE]->(:VariationType) RETURN p")

;ASSERTION RELATIONSHIP QUERIES
(db/defquery a-has-significance-query "MATCH (p :Assertion ) WHERE NOT (p)-[:HAS_SIGNIFICANCE]->(:ClinicalSignificance) RETURN p")
(db/defquery a-has-state-query "MATCH (p :Assertion ) WHERE NOT (p)-[:HAS_STATE]->() RETURN p")
(db/defquery a-has-level-query "MATCH (p :Assertion ) WHERE NOT (p)-[:HAS_LEVEL]->() RETURN p")
(db/defquery a-has-type-query "MATCH (p :Assertion ) WHERE NOT (p)-[:HAS_TYPE]->(:AssertionType) RETURN p")
(db/defquery a-submittedby-query "MATCH (p :Assertion ) WHERE NOT (p)-[:WAS_SUBMITTED_BY]->() RETURN p")
(db/defquery a-has-trait-query "MATCH (p :Assertion) WHERE NOT (p)-[:HAS_TRAIT]->() RETURN p")
(db/defquery a-has-subject-query "MATCH (p:Assertion) WHERE NOT (p)-[:HAS_SUBJECT]->(:Variation) RETURN p")
;Species must be human
(db/defquery species-query "MATCH (n:Species) WHERE NOT (n.Species = 'Homo sapiens') RETURN n")

;There should be no orphan nodes
(db/defquery orphan-query "MATCH (n)-[r]->() WHERE r IS NULL RETURN n")

;Write unit test to check for properties on Submitter, Allele, Variation and Assertion nodes
(db/defquery submitterid-query "MATCH (n:Submitter) WHERE NOT EXISTS (n.SubmitterID) RETURN n")
(db/defquery submittername-query "MATCH (n:Submitter) WHERE NOT EXISTS (n.SubmitterName) RETURN n")
;Allele
(db/defquery alleleid-query "MATCH (n:Allele) WHERE NOT EXISTS (n.AlleleID) RETURN n")
(db/defquery allelename-query "MATCH (n:Allele) WHERE NOT EXISTS (n.AlleleName) RETURN n")
;Variation
(db/defquery variationid-query "MATCH (n:Variation) WHERE NOT EXISTS (n.VariationID) RETURN n")
(db/defquery variationaccession-query "MATCH (n:Variation) WHERE NOT EXISTS (n.VariationAccession) RETURN n")
(db/defquery variationversion-query "MATCH (n:Variation) WHERE NOT EXISTS (n.VariationVersion) RETURN n")
(db/defquery variationname-query "MATCH (n:Variation) WHERE NOT EXISTS (n.VariationName) RETURN n")
(db/defquery variation-datecreated-query "MATCH (n:Variation) WHERE NOT EXISTS (n.DateCreated) RETURN n")
(db/defquery variation-dateupdated-query "MATCH (n:Variation) WHERE NOT EXISTS (n.DateUpdated) RETURN n")
;Assertion
(db/defquery clinicalAssertionid-query "MATCH (n:Assertion) WHERE NOT EXISTS (n.ClinicalAssertionID) RETURN n")
(db/defquery scvid-query "MATCH (n:Assertion) WHERE NOT EXISTS (n.ScvID) RETURN n")
(db/defquery scvversion-query "MATCH (n:Assertion) WHERE NOT EXISTS (n.ScvVersion) RETURN n")
(db/defquery submissiondate-query "MATCH (n:Assertion) WHERE NOT EXISTS (n.SubmissionDate) RETURN n")
(db/defquery assertion-datecreated-query "MATCH (n:Assertion) WHERE NOT EXISTS (n.DateCreated) RETURN n")
(db/defquery assertion-dateupdated-query "MATCH (n:Assertion) WHERE NOT EXISTS (n.DateUpdated) RETURN n")

(db/defquery assertionunique-query "MATCH (a:Assertion) WITH a.ClinicalAssertionID as id, collect(a) AS nodes WHERE size(nodes) >  1 RETURN nodes")
(db/defquery variationunique-query "MATCH (v:Variation) WITH v.variationID as id, collect(v) AS nodes WHERE size(nodes) >  1 RETURN nodes")
(db/defquery alleleunique-query "MATCH (al:Allele) WITH al.AlleleID as id, collect(al) AS nodes WHERE size(nodes) >  1 RETURN nodes")
(db/defquery submitterunique-query "MATCH (s:Submitter) WITH s.SubmitterID as id, collect(s) AS nodes WHERE size(nodes) >  1 RETURN nodes")

(def methodtype-lookup 
  {:methodtype ()})

(def assertiontype-lookup
  {:assertiontype ()})

(def traittype-lookup
  {:traittype ()})


(deftest enum-test
  (with-open [session (db/get-session local-db)]
  (testing "enums"         
      (is (= (methodtype-query session) ()))
      (is (= (assertiontype-query session) ()))
      (is (= (traittype-query session) ()))
      ;(is (= (clinsig-query session) ()))
      (is (= (variationtype-query session) ()))
      (is (= (origin-query session) ()))
      (is (= (recordtype-query session) ()))
      (is (= (recordstatus-query session) ()))
      (is (= (reviewstatus-query session) ()))
      (is (= (labListcriteria-query session) ())))))

(deftest relationship-test
  (with-open [session (db/get-session local-db)]
  (testing "relationships"    
		  (is (not (= (has-significance-query session) ())))
		  (is (= (has-state-query session) ()))
		  (is (not (= (has-level-query session) ())))
		  (is (= (has-type-query session) ()))
		  (is (= (has-recordtype-query session) ()))
      (is (not (= (is-comprisedof-query session) ())))
      (is (= (species-query session) ()))
      (is (= (has-variationtype-query session) ()))
      (is (= (a-has-significance-query session) ()))
		  (is (= (a-has-state-query session) ()))
		  (is (= (a-has-level-query session) ()))
		  (is (= (a-has-type-query session) ()))
      (is (= (a-submittedby-query session) ()))
      (is (not (= (a-has-trait-query session) ())))
      (is (= (a-has-subject-query session) ()))      
    ))) 

(deftest orphan-nodes-test
  (with-open [session (db/get-session local-db)]
  (testing "orphan nodes"    
           (is (= (orphan-query session) ())))))

(deftest properties-test
  (with-open [session (db/get-session local-db)]
  (testing "properties" 
           (is (= (submitterid-query session) ()))
           (is (= (submittername-query session) ()))
           (is (= (alleleid-query session) ()))
           (is (= (allelename-query session) ()))
           (is (= (variationid-query session) ()))
           ;(is (= (variationaccession-query session) ()))
           ;(is (= (variationversion-query session) ()))
           (is (= (variationname-query session) ()))
           (is (= (variation-datecreated-query session) ()))
           ;(is (= (variation-dateupdated-query session) ()))          
           (is (= (clinicalAssertionid-query session) ()))
           ;(is (= (scvid-query session) ()))
           ;(is (= (scvversion-query session) ()))
           (is (= (submissiondate-query session) ()))
           ;(is (= (assertion-datecreated-query session) ()))
           (is (= (assertion-dateupdated-query session) ()))
           )))

(deftest uniqueid-test
  (with-open [session (db/get-session local-db)]
  (testing "unique id"
           (is (= (assertionunique-query session) ()))
           ;(is (= (variationunique-query session) ()))
           (is (= (alleleunique-query session) ()))
           (is (= (submitterunique-query session) ())))))

(run-tests)