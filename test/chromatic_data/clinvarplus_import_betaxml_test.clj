(ns chromatic-data.clinvarplus-import-betaxml-test
(:require [clojure.test :refer :all]
          [neo4j-clj.core :as db]
          [chromatic-data.neo4j :as neo]
          [chromatic-data.core :refer :all]))

(def local-db
  (db/connect "bolt://localhost:7687" "neo4j" "atif5546"))

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

(run-tests)