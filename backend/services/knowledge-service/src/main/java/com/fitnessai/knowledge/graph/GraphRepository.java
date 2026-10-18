package com.fitnessai.knowledge.graph;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Raw Cypher over a deliberately generic schema: (:Entity {key, name, type})
 * joined by [:RELATES {type, sources}]. The relation name is a property, not
 * a Cypher relationship type, because it originates from LLM output and
 * relationship types can't be parameterised. Each relationship's "sources"
 * list holds "scope|documentId" tags, which drive both read visibility and
 * clean removal when a document is deleted.
 */
@Repository
public class GraphRepository {

    private final Neo4jClient neo4j;

    public GraphRepository(Neo4jClient neo4j) {
        this.neo4j = neo4j;
    }

    public void ensureSchema() {
        neo4j.query("CREATE CONSTRAINT entity_key IF NOT EXISTS FOR (e:Entity) REQUIRE e.key IS UNIQUE").run();
    }

    public void upsert(List<Triple> triples, String source) {
        if (triples.isEmpty()) {
            return;
        }
        List<Map<String, Object>> rows = triples.stream()
                .map(t -> Map.<String, Object>of(
                        "sKey", GraphKeys.key(t.subject()), "sName", t.subject().trim(),
                        "sType", Triple.normalizedType(t.subjectType()),
                        "oKey", GraphKeys.key(t.object()), "oName", t.object().trim(),
                        "oType", Triple.normalizedType(t.objectType()),
                        "rel", t.normalizedRelation()))
                .toList();
        neo4j.query("""
                        UNWIND $rows AS row
                        MERGE (s:Entity {key: row.sKey}) ON CREATE SET s.name = row.sName, s.type = row.sType
                        MERGE (o:Entity {key: row.oKey}) ON CREATE SET o.name = row.oName, o.type = row.oType
                        MERGE (s)-[r:RELATES {type: row.rel}]->(o)
                          ON CREATE SET r.sources = [$source]
                          ON MATCH SET r.sources = CASE WHEN $source IN r.sources
                                                        THEN r.sources ELSE r.sources + $source END
                        """)
                .bind(rows).to("rows")
                .bind(source).to("source")
                .run();
    }

    /**
     * Facts touching entities named in the query, limited to relationships
     * with at least one source in a readable scope. Matching is on whole
     * words: entity "arm" matches "my arm hurts" but not "warm up".
     */
    public List<String> factsFor(String query, List<String> readableScopes, int limit) {
        String padded = " " + GraphKeys.key(query) + " ";
        List<String> scopePrefixes = readableScopes.stream().map(s -> s + "|").toList();
        return neo4j.query("""
                        MATCH (e:Entity)
                        WHERE size(e.key) >= 3 AND $q CONTAINS (' ' + e.key + ' ')
                        WITH e LIMIT 10
                        MATCH (e)-[r:RELATES]-(:Entity)
                        WHERE any(src IN r.sources WHERE any(p IN $prefixes WHERE src STARTS WITH p))
                        WITH DISTINCT r
                        RETURN startNode(r).name AS subject, r.type AS relation, endNode(r).name AS object
                        LIMIT $limit
                        """)
                .bind(padded).to("q")
                .bind(scopePrefixes).to("prefixes")
                .bind(limit).to("limit")
                .fetch().all().stream()
                .map(row -> row.get("subject") + " " + row.get("relation") + " " + row.get("object"))
                .toList();
    }

    /** Remove one document's contributions; drop relationships and entities left with nothing. */
    public void removeSource(String source) {
        neo4j.query("""
                        MATCH ()-[r:RELATES]->() WHERE $source IN r.sources
                        SET r.sources = [s IN r.sources WHERE s <> $source]
                        WITH r WHERE size(r.sources) = 0
                        DELETE r
                        """)
                .bind(source).to("source").run();
        neo4j.query("MATCH (e:Entity) WHERE NOT (e)--() DELETE e").run();
    }

    /** Remove every contribution from one scope (all of a deleted user's documents). */
    public void removeScope(String scope) {
        String prefix = scope + "|";
        neo4j.query("""
                        MATCH ()-[r:RELATES]->() WHERE any(s IN r.sources WHERE s STARTS WITH $prefix)
                        SET r.sources = [s IN r.sources WHERE NOT s STARTS WITH $prefix]
                        WITH r WHERE size(r.sources) = 0
                        DELETE r
                        """)
                .bind(prefix).to("prefix").run();
        neo4j.query("MATCH (e:Entity) WHERE NOT (e)--() DELETE e").run();
    }

    /** Admin variant: remove a document's contributions regardless of which scope it was indexed under. */
    public void removeDocumentEverywhere(String documentId) {
        String suffix = "|" + documentId;
        neo4j.query("""
                        MATCH ()-[r:RELATES]->() WHERE any(s IN r.sources WHERE s ENDS WITH $suffix)
                        SET r.sources = [s IN r.sources WHERE NOT s ENDS WITH $suffix]
                        WITH r WHERE size(r.sources) = 0
                        DELETE r
                        """)
                .bind(suffix).to("suffix").run();
        neo4j.query("MATCH (e:Entity) WHERE NOT (e)--() DELETE e").run();
    }
}
