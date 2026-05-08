package backend.searchbyimage.search.repository;

import backend.searchbyimage.search.model.NormalizedProductSearchQuery;
import backend.searchbyimage.search.model.ProductSearchPage;
import backend.searchbyimage.search.model.ProductSearchRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Repository
public class ProductSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public ProductSearchPage searchProducts(NormalizedProductSearchQuery query, String queryEmbedding) {
        Query nativeQuery = entityManager.createNativeQuery(buildSearchSql(queryEmbedding != null))
                .setParameter("query", query.normalized())
                .setParameter("prefixQuery", query.normalized() + "%")
                .setParameter("containsQuery", "%" + query.likeQuery() + "%")
                .setParameter("tsQuery", query.normalized())
                .setParameter("trigramThreshold", trigramThreshold(query))
                .setParameter("category", normalizeFilter(query.filter().category()))
                .setParameter("shop", normalizeFilter(query.filter().shop()))
                .setParameter("minPrice", query.filter().minPrice())
                .setParameter("maxPrice", query.filter().maxPrice())
                .setParameter("sort", query.filter().effectiveSort())
                .setParameter("candidateLimit", candidateLimit(query))
                .setParameter("semanticLimit", semanticLimit(query))
                .setParameter("limit", query.size())
                .setParameter("offset", query.page() * query.size());

        if (queryEmbedding != null) {
            nativeQuery.setParameter("queryEmbedding", queryEmbedding);
        }

        List<Object[]> rawRows = nativeQuery.getResultList();
        List<ProductSearchRow> rows = rawRows.stream()
                .map(this::mapRow)
                .toList();

        int totalResults = rawRows.isEmpty() ? 0 : ((Number) rawRows.get(0)[13]).intValue();
        boolean hasMore = totalResults > (query.page() * query.size()) + rows.size();

        return new ProductSearchPage(rows, hasMore, totalResults);
    }

    @SuppressWarnings("unchecked")
    public ProductSearchPage searchSimilarProducts(String queryEmbedding, double threshold, int page, int perPage) {
        int offset = (page - 1) * perPage;
        List<Object[]> rawRows = entityManager.createNativeQuery("""
                        SELECT
                            psd.product_id::text AS id,
                            psd.title,
                            psd.product_url,
                            psd.image_url,
                            psd.company,
                            psd.category_id::text AS category_id,
                            psd.category_title,
                            psd.category_slug,
                            psd.original_price,
                            1 - (pe.embedding <=> CAST(:queryEmbedding AS vector)) AS score,
                            'image' AS match_type,
                            psd.title AS highlight,
                            'image,semantic' AS matched_fields,
                            COUNT(*) OVER() AS total_count
                        FROM product_embeddings pe
                        JOIN product_search_documents psd ON psd.product_id = pe.product_id
                        WHERE psd.image_url IS NOT NULL
                          AND 1 - (pe.embedding <=> CAST(:queryEmbedding AS vector)) >= :threshold
                        ORDER BY pe.embedding <=> CAST(:queryEmbedding AS vector), psd.updated_at DESC NULLS LAST
                        LIMIT :limit OFFSET :offset
                        """)
                .setParameter("queryEmbedding", queryEmbedding)
                .setParameter("threshold", threshold)
                .setParameter("limit", perPage)
                .setParameter("offset", offset)
                .getResultList();

        List<ProductSearchRow> rows = rawRows.stream()
                .map(this::mapRow)
                .toList();
        int totalResults = rawRows.isEmpty()
                ? countSimilarProducts(queryEmbedding, threshold)
                : ((Number) rawRows.get(0)[13]).intValue();
        boolean hasMore = totalResults > offset + rows.size();

        return new ProductSearchPage(rows, hasMore, totalResults);
    }

    private int countSimilarProducts(String queryEmbedding, double threshold) {
        Number total = (Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM product_embeddings pe
                        JOIN product_search_documents psd ON psd.product_id = pe.product_id
                        WHERE psd.image_url IS NOT NULL
                          AND 1 - (pe.embedding <=> CAST(:queryEmbedding AS vector)) >= :threshold
                        """)
                .setParameter("queryEmbedding", queryEmbedding)
                .setParameter("threshold", threshold)
                .getSingleResult();
        return total == null ? 0 : total.intValue();
    }

    @SuppressWarnings("unchecked")
    public List<String> fetchSuggestions(String prefix, int limit) {
        String normalizedPrefix = normalizeFilter(prefix);
        return entityManager.createNativeQuery("""
                        SELECT suggestion
                        FROM (
                            SELECT DISTINCT psd.title AS suggestion, 1 AS priority
                            FROM product_search_documents psd
                            WHERE lower(psd.title) LIKE :prefixQuery

                            UNION

                            SELECT DISTINCT psd.company AS suggestion, 2 AS priority
                            FROM product_search_documents psd
                            WHERE psd.company IS NOT NULL AND lower(psd.company) LIKE :prefixQuery

                            UNION

                            SELECT DISTINCT psd.category_title AS suggestion, 3 AS priority
                            FROM product_search_documents psd
                            WHERE lower(psd.category_title) LIKE :prefixQuery OR lower(psd.category_slug) LIKE :prefixQuery

                            UNION

                            SELECT DISTINCT psd.subcategory_title AS suggestion, 4 AS priority
                            FROM product_search_documents psd
                            WHERE lower(psd.subcategory_title) LIKE :prefixQuery OR lower(psd.subcategory_slug) LIKE :prefixQuery

                            UNION

                            SELECT DISTINCT psd.leaf_category_title AS suggestion, 5 AS priority
                            FROM product_search_documents psd
                            WHERE lower(psd.leaf_category_title) LIKE :prefixQuery OR lower(psd.leaf_category_slug) LIKE :prefixQuery
                        ) suggestions
                        WHERE suggestion IS NOT NULL AND suggestion <> ''
                        ORDER BY priority, length(suggestion), suggestion
                        LIMIT :limit
                        """)
                .setParameter("prefixQuery", normalizedPrefix + "%")
                .setParameter("limit", limit)
                .getResultList();
    }

    private String buildSearchSql(boolean includeSemantic) {
        String semanticCte = includeSemantic
                ? """
                        semantic_candidates AS (
                            SELECT
                                fd.id,
                                GREATEST(0.0, 1 - (pe.embedding <=> CAST(:queryEmbedding AS vector))) AS semantic_score
                            FROM filtered_docs fd
                            JOIN product_embeddings pe ON pe.product_id = fd.id::uuid
                            ORDER BY pe.embedding <=> CAST(:queryEmbedding AS vector), fd.updated_at DESC NULLS LAST
                            LIMIT :semanticLimit
                        ),
                        """
                : """
                        semantic_candidates AS (
                            SELECT NULL::text AS id, NULL::double precision AS semantic_score
                            WHERE FALSE
                        ),
                        """;

        return """
                WITH filtered_docs AS (
                    SELECT
                        psd.product_id::text AS id,
                        psd.title,
                        psd.product_url,
                        psd.image_url,
                        psd.company,
                        psd.category_id::text AS category_id,
                        psd.category_title,
                        psd.category_slug,
                        psd.original_price,
                        psd.sortable_price,
                        psd.updated_at,
                        lower(COALESCE(psd.product_id::text, '')) AS id_lower,
                        lower(COALESCE(psd.title, '')) AS title_lower,
                        lower(COALESCE(psd.company, '')) AS company_lower,
                        lower(COALESCE(psd.category_title, '')) AS category_lower,
                        lower(COALESCE(psd.category_slug, '')) AS category_slug_lower,
                        lower(COALESCE(psd.subcategory_title, '')) AS subcategory_lower,
                        lower(COALESCE(psd.subcategory_slug, '')) AS subcategory_slug_lower,
                        lower(COALESCE(psd.leaf_category_title, '')) AS leaf_category_lower,
                        lower(COALESCE(psd.leaf_category_slug, '')) AS leaf_category_slug_lower,
                        psd.search_text,
                        psd.search_vector AS document
                    FROM product_search_documents psd
                    WHERE (CAST(:category AS text) IS NULL
                           OR lower(psd.category_id::text) = CAST(:category AS text)
                           OR lower(psd.category_slug) = CAST(:category AS text)
                           OR lower(psd.category_title) = CAST(:category AS text)
                           OR lower(psd.subcategory_slug) = CAST(:category AS text)
                           OR lower(psd.subcategory_title) = CAST(:category AS text)
                           OR lower(psd.leaf_category_slug) = CAST(:category AS text)
                           OR lower(psd.leaf_category_title) = CAST(:category AS text))
                      AND (CAST(:shop AS text) IS NULL OR lower(COALESCE(psd.company, '')) = CAST(:shop AS text))
                      AND (CAST(:minPrice AS numeric) IS NULL OR psd.sortable_price >= CAST(:minPrice AS numeric))
                      AND (CAST(:maxPrice AS numeric) IS NULL OR psd.sortable_price <= CAST(:maxPrice AS numeric))
                ),
                exact_candidates AS (
                    SELECT
                        fd.id,
                        CASE
                            WHEN fd.id_lower = :query THEN 1.0
                            WHEN fd.title_lower = :query THEN 0.92
                            WHEN fd.title_lower LIKE :prefixQuery THEN 0.75
                            WHEN fd.company_lower = :query THEN 0.60
                            WHEN fd.category_lower = :query OR fd.category_slug_lower = :query THEN 0.55
                            WHEN fd.subcategory_lower = :query OR fd.subcategory_slug_lower = :query THEN 0.50
                            WHEN fd.leaf_category_lower = :query OR fd.leaf_category_slug_lower = :query THEN 0.48
                            ELSE 0.0
                        END AS exact_score
                    FROM filtered_docs fd
                    WHERE fd.id_lower = :query
                       OR fd.title_lower = :query
                       OR fd.title_lower LIKE :prefixQuery
                       OR fd.company_lower = :query
                       OR fd.category_lower = :query
                       OR fd.category_slug_lower = :query
                       OR fd.subcategory_lower = :query
                       OR fd.subcategory_slug_lower = :query
                       OR fd.leaf_category_lower = :query
                       OR fd.leaf_category_slug_lower = :query
                    ORDER BY exact_score DESC, fd.updated_at DESC NULLS LAST
                    LIMIT :candidateLimit
                ),
                lexical_candidates AS (
                    SELECT
                        fd.id,
                        ts_rank_cd(fd.document, websearch_to_tsquery('simple', :tsQuery), 32) AS lexical_score
                    FROM filtered_docs fd
                    WHERE fd.document @@ websearch_to_tsquery('simple', :tsQuery)
                    ORDER BY lexical_score DESC, fd.updated_at DESC NULLS LAST
                    LIMIT :candidateLimit
                ),
                trigram_candidates AS (
                    SELECT
                        fd.id,
                        GREATEST(
                            similarity(fd.title_lower, :query) * 1.0,
                            similarity(fd.company_lower, :query) * 0.75,
                            similarity(fd.category_lower, :query) * 0.70,
                            similarity(fd.subcategory_lower, :query) * 0.62,
                            similarity(fd.leaf_category_lower, :query) * 0.60
                        ) AS trigram_score
                    FROM filtered_docs fd
                    WHERE GREATEST(
                            similarity(fd.title_lower, :query) * 1.0,
                            similarity(fd.company_lower, :query) * 0.75,
                            similarity(fd.category_lower, :query) * 0.70,
                            similarity(fd.subcategory_lower, :query) * 0.62,
                            similarity(fd.leaf_category_lower, :query) * 0.60
                        ) >= :trigramThreshold
                    ORDER BY trigram_score DESC, fd.updated_at DESC NULLS LAST
                    LIMIT :candidateLimit
                ),
                """ + semanticCte + """
                all_candidates AS (
                    SELECT id, exact_score, 0.0::double precision AS lexical_score, 0.0::double precision AS trigram_score, 0.0::double precision AS semantic_score
                    FROM exact_candidates
                    UNION ALL
                    SELECT id, 0.0::double precision, lexical_score, 0.0::double precision, 0.0::double precision
                    FROM lexical_candidates
                    UNION ALL
                    SELECT id, 0.0::double precision, 0.0::double precision, trigram_score, 0.0::double precision
                    FROM trigram_candidates
                    UNION ALL
                    SELECT id, 0.0::double precision, 0.0::double precision, 0.0::double precision, semantic_score
                    FROM semantic_candidates
                ),
                merged AS (
                    SELECT
                        fd.*,
                        MAX(ac.exact_score) AS exact_score,
                        MAX(ac.lexical_score) AS lexical_score,
                        MAX(ac.trigram_score) AS trigram_score,
                        MAX(ac.semantic_score) AS semantic_score
                    FROM all_candidates ac
                    JOIN filtered_docs fd ON fd.id = ac.id
                    GROUP BY
                        fd.id, fd.title, fd.product_url, fd.image_url, fd.company, fd.category_id,
                        fd.category_title, fd.category_slug, fd.original_price, fd.sortable_price,
                        fd.updated_at, fd.id_lower, fd.title_lower, fd.company_lower, fd.category_lower,
                        fd.category_slug_lower, fd.subcategory_lower, fd.subcategory_slug_lower,
                        fd.leaf_category_lower, fd.leaf_category_slug_lower, fd.search_text, fd.document
                ),
                scored AS (
                    SELECT
                        m.*,
                        CASE
                            WHEN m.id_lower = :query THEN 'exact_id'
                            WHEN m.title_lower = :query THEN 'exact_title'
                            WHEN m.title_lower LIKE :prefixQuery THEN 'prefix_title'
                            WHEN m.exact_score > 0.0 THEN 'exact_attribute'
                            WHEN m.lexical_score > 0.0 AND m.semantic_score > 0.0 THEN 'hybrid'
                            WHEN m.semantic_score > 0.0 THEN 'semantic'
                            WHEN m.trigram_score >= :trigramThreshold THEN 'fuzzy'
                            WHEN m.lexical_score > 0.0 THEN 'full_text'
                            ELSE 'related'
                        END AS match_type,
                        (
                            (m.exact_score * 0.38) +
                            (LEAST(1.0, m.lexical_score * 3.0) * 0.32) +
                            (LEAST(1.0, m.trigram_score) * 0.12) +
                            (LEAST(1.0, GREATEST(0.0, m.semantic_score)) * 0.18)
                        ) AS final_score
                    FROM merged m
                )
                SELECT
                    s.id,
                    s.title,
                    s.product_url,
                    s.image_url,
                    s.company,
                    s.category_id,
                    s.category_title,
                    s.category_slug,
                    s.original_price,
                    s.final_score,
                    s.match_type,
                    CASE
                        WHEN s.id_lower = :query THEN s.title || ' | ' || s.id
                        WHEN s.company IS NOT NULL AND lower(s.company) LIKE :containsQuery THEN s.title || ' | ' || s.company
                        WHEN s.category_title IS NOT NULL AND lower(s.category_title) LIKE :containsQuery THEN s.title || ' | ' || s.category_title
                        ELSE s.title
                    END AS highlight,
                    array_to_string(
                        array_remove(ARRAY[
                            CASE WHEN s.id_lower = :query THEN 'id' END,
                            CASE WHEN lower(COALESCE(s.title, '')) LIKE :containsQuery THEN 'title' END,
                            CASE WHEN lower(COALESCE(s.company, '')) LIKE :containsQuery THEN 'company' END,
                            CASE WHEN lower(COALESCE(s.category_title, '')) LIKE :containsQuery THEN 'category' END,
                            CASE WHEN s.semantic_score > 0.0 THEN 'semantic' END
                        ], NULL),
                        ','
                    ) AS matched_fields,
                    COUNT(*) OVER() AS total_count
                FROM scored s
                ORDER BY
                    CASE WHEN CAST(:sort AS text) = 'price_asc' THEN s.sortable_price END ASC NULLS LAST,
                    CASE WHEN CAST(:sort AS text) = 'price_desc' THEN s.sortable_price END DESC NULLS LAST,
                    CASE WHEN CAST(:sort AS text) = 'title' THEN lower(s.title) END ASC,
                    CASE WHEN CAST(:sort AS text) = 'newest' THEN s.updated_at END DESC NULLS LAST,
                    CASE WHEN CAST(:sort AS text) = 'relevance' THEN s.final_score END DESC,
                    s.final_score DESC,
                    s.updated_at DESC NULLS LAST
                LIMIT :limit OFFSET :offset
                """;
    }

    private ProductSearchRow mapRow(Object[] row) {
        return new ProductSearchRow(
                (String) row[0],
                (String) row[1],
                (String) row[2],
                (String) row[3],
                (String) row[4],
                (String) row[5],
                (String) row[6],
                (String) row[7],
                (BigDecimal) row[8],
                row[9] == null ? 0.0 : ((Number) row[9]).doubleValue(),
                (String) row[10],
                (String) row[11],
                parseMatchedFields((String) row[12])
        );
    }

    private List<String> parseMatchedFields(String matchedFields) {
        if (matchedFields == null || matchedFields.isBlank()) {
            return List.of("title");
        }
        return new ArrayList<>(Arrays.asList(matchedFields.split(",")));
    }

    private String normalizeFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private double trigramThreshold(NormalizedProductSearchQuery query) {
        if (query.normalized().length() <= 3) {
            return 0.55;
        }
        if (query.normalized().length() <= 6) {
            return 0.4;
        }
        return 0.3;
    }

    private int candidateLimit(NormalizedProductSearchQuery query) {
        int windowSize = (query.page() + 1) * query.size();
        return Math.max(windowSize * 4, 80);
    }

    private int semanticLimit(NormalizedProductSearchQuery query) {
        int windowSize = (query.page() + 1) * query.size();
        return Math.max(windowSize * 2, 40);
    }
}
