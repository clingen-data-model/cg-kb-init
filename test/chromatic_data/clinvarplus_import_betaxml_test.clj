(ns chromatic-data.clinvarplus-import-betaxml-test
(:require [clojure.test :refer :all]
          [neo4j-clj.core :as db]
          [chromatic-data.neo4j :as neo]
          [chromatic-data.core :refer :all]))

(def local-db
  (db/connect "bolt://localhost:7687" "neo4j" "atif5546"))

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
(db/defquery a-has-state-query "MATCH (p :Assertion ) WHERE NOT (p)-[:HAS_STATE]->(:RecordStatus) RETURN p")
(db/defquery a-has-level-query "MATCH (p :Assertion ) WHERE NOT (p)-[:HAS_LEVEL]->(:ReviewStatus) RETURN p")
(db/defquery a-has-type-query "MATCH (p :Assertion ) WHERE NOT (p)-[:HAS_TYPE]->(:AssertionType) RETURN p")
(db/defquery a-submittedby-query "MATCH (p :Assertion ) WHERE NOT (p)-[:WAS_SUBMITTED_BY]->() RETURN p")
(db/defquery a-has_trait-query "MATCH (p :Assertion ) WHERE NOT (p)-[:HAS_TRAIT]->(:TraitType) RETURN p")
(db/defquery a-has_subject-query "MATCH (p:Assertion) WHERE NOT (p)-[:HAS_SUBJECT]->(:Variation) RETURN p")
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

(def methodtype-lookup 
  {:methodtype (:methodtype "")})

(def assertiontype-lookup
  {:assertiontype (:assertiontype "")})

(def traittype-lookup
  {:traittype (:traittype "")})


(deftest enum-test
  (with-open [session (db/get-session local-db)]
  (testing "enums"         
      (is (= (methodtype-query session methodtype-lookup)))
      (is (= (assertiontype-query session assertiontype-lookup)))
      (is (= (traittype-query session traittype-lookup)))
      (is (= (clinsig-query session {:ClinicalSignificance (:ClinicalSignificance "")})))
      (is (= (variationtype-query session {:VariationType (:VariationType "")})))
      (is (= (origin-query session {:Origin (:Origin "")})))
      (is (= (recordtype-query session {:RecordType (:RecordType "")})))
      (is (= (recordstatus-query session {:RecordStatus (:RecordStatus "")})))
      (is (= (reviewstatus-query session {:ReviewStatus (:ReviewStatus "")})))
      (is (= (labListcriteria-query session {:LabListCriteria (:LabListCriteria "")}))))))

(deftest relationship-test
  (with-open [session (db/get-session local-db)]
  (testing "relationships"    
		  (is (= (has-significance-query session {:Variation (:Variation "")})))
		  (is (= (has-state-query session {:Variation (:Variation "")})))
		  (is (= (has-level-query session {:Variation (:Variation "")})))
		  (is (= (has-type-query session {:Variation (:Variation "")})))
		  (is (= (has-recordtype-query session {:Variation (:Variation "")})))
      (is (= (is-comprisedof-query session {:Variation (:Variation "")})))
      (is (= (species-query session {:Species (:Species "")})))
      (is (= (has-variationtype-query session {:Allele (:Allele "")})))
      (is (= (a-has-significance-query session {:Assertion (:Assertion "")})))
		  (is (= (a-has-state-query session {:Assertion (:Assertion "")})))
		  (is (= (a-has-level-query session {:Assertion (:Assertion "")})))
		  (is (= (a-has-type-query session {:Assertion (:Assertion "")})))
      (is (= (a-submittedby-query session {:Assertion (:Assertion "")})))
      (is (= (a-has_trait-query session {:Assertion (:Assertion "")})))
      (is (= (a-has_subject-query session {:Assertion (:Assertion "")})))      
    ))) 

(deftest orphan-nodes-test
  (with-open [session (db/get-session local-db)]
  (testing "orphan nodes"    
           (is (= (orphan-query session ""))))))

(deftest properties-test
  (with-open [session (db/get-session local-db)]
  (testing "properties" 
           (is (= (submitterid-query session {:Submitter (:Submitter "")})))
           (is (= (submittername-query session {:Submitter (:Submitter "")})))
           (is (= (alleleid-query session {:Allele (:Allele "")})))
           (is (= (allelename-query session {:Allele (:Allele "")})))
           (is (= (variationid-query session {:Variation (:Variation "")})))
           (is (= (variationaccession-query session {:Variation (:Variation "")})))
           (is (= (variationversion-query session {:Variation (:Variation "")})))
           (is (= (variationname-query session {:Variation (:Variation "")})))
           (is (= (variation-datecreated-query session {:Variation (:Variation "")})))
           (is (= (variation-dateupdated-query session {:Variation (:Variation "")})))          
           (is (= (clinicalAssertionid-query session {:Assertion (:Assertion "")})))
           (is (= (scvid-query session {:Assertion (:Assertion "")})))
           (is (= (scvversion-query session {:Assertion (:Assertion "")})))
           (is (= (submissiondate-query session {:Assertion (:Assertion "")})))
           (is (= (assertion-datecreated-query session {:Assertion (:Assertion "")})))
           (is (= (assertion-dateupdated-query session {:Assertion (:Assertion "")})))
           )))

(run-tests)