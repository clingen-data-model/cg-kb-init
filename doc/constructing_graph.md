# Build the 


## connect genes to proper annotation

(assumes of course that genes are the only records with symbols, and that the only genes are protein coding genes)

    match (ann:sequence_feature {iri: "http://purl.obolibrary.org/obo/SO_0001217"}), (g:region) where not g.symbol is null merge (g)-[:is_a]->(ann)


## Incorporate location data from UCSC

download gene location positions from UCSC using ucsc identifiers. Use 

http://genome.ucsc.edu/cgi-bin/hgTables?command=start

    using periodic commit load csv with headers from "file:///ucsc-hg.bed" as line fieldterminator "\t" match (g:region {ucsc_id: line.name}) create (cx:region_context {chromosome: line.chrom, start: line.txStart, end: line.txEnd, strand: line.strand, assembly: "GRCh37"}) merge (g)<-[:position_of]-(cx)

Next, load exon data: Will likely have to use clojure for this thanks to the structure of it
