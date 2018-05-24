# cg-kb-init

This is a Clojure library for building the back-end Neo4j database for both the ClinGen Knowledge Base and the ClinVar+ projects. It will construct a Neo4j database from information in OWL ontologies, HGNC genes, and the ClinVar XML.

## Installation

This software requires Java 1.8 and Leiningen. All other dependencies are declared in project.clj.

## Usage

Currently the Leiningen REPL is the way to use this code; at some point this code should be extended to run from the command line, with configuration files to specify how the progrma is to be run.

chromatic-data.cg-knowledge is the main namespace containing the core functionality. By running the kb-init function, the files specified in external-data will be downloaded and imported into neo4j. To control the size of the import, comment or uncomment files that you do not wish to load. Running kb-init :refresh will download the files from their original sources.

## Organization

in src/chromatic_data

* cg_knowledge.clj -- main file for loading data into the knowledgebase
* cg_sv.clj -- *deprecated* Load CSV data from CG dosage map submission
* clinvar_import.clj -- Import the intermediate files generated by clinvar\_process.clj 
* clinvar_process.clj -- Generate intermediate files on variant interpretations from the ClinVar XML.
* conflict_resolution.clj -- Contains query for reporting conflicts between structural variants.
* core.clj -- stub, to be expanded to manage interaction from the command line.
* fetch.clj -- retrieve and decompress resources from the network.
* gene.clj -- import genes and metadata from HGNC, exons from Ensembl.
* ncbi_dosage_import -- *deprecated* import gene dosage data from NCBI FTP site.
* neo4j.clj -- helper functions for interacting with Neo4j database.
* overlaps.clj -- *deprecated* function for computing overlaps between regions and storing the result as edges in neo4j.
* owl.clj -- functions for reading OWL files, extracing the useful information, and storing the resulting info in neo4j.
* pw_curation_import.clj -- function for retrieving information on curations pulled from Processwire, and importing into neo4j.
* schema.clj -- Defines indexes and constraints for neo4j.
* sv.clj -- utility functions for mapping structural variants across genome builds; compute overlaps between structural variants.
* clinvarplus_process_betaxml.clj - This file parse ClinVar XML and generate an intermediate file (.edn).
* clinvar_import_betaxml.clj -- Import the intermediate files generated by clinvarplus_process_betaxml.clj to neo4J database.

in test/chromatic_data

* clinvarplus_process_betaxml_test.clj: This junit test case is wrtten to test each function in clinvarplus_process_betaxml.clj
* clinvar_import_betaxml_test.clj: This is a test case that used more than 45 assertion to validate data in neo4j using cypher queries.

