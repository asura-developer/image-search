package backend.searchbyimage.search.repository;

import backend.searchbyimage.search.model.NormalizedProductSearchQuery;
import backend.searchbyimage.search.model.ProductSearchPage;
import backend.searchbyimage.search.model.ProductSearchRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Repository
public class ProductSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public ProductSearchPage searchProducts(NormalizedProductSearchQuery query, String queryEmbedding) {
        String sql = buildSearchSql(queryEmbedding != null);

        Query nativeQuery = entityManager.createNativeQuery(sql)
                .setParameter("query", query.normalized())
                .setParameter("prefixQuery", query.normalized() + "%")
                .setParameter("containsQuery", "%" + query.likeQuery() + "%")
                .setParameter("trigramThreshold", trigramThreshold(query))
                .setParameter("platform", normalizeFilter(query.filter().platform()))
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

        int totalResults = rawRows.isEmpty()
                ? 0
                : ((Number) rawRows.get(0)[15]).intValue();
        boolean hasMore = totalResults > (query.page() * query.size()) + rows.size();

        return new ProductSearchPage(rows, hasMore, totalResults);
    }

    @SuppressWarnings("unchecked")
    public List<String> fetchSuggestions(String prefix, int limit) {
        String sql = """
                SELECT suggestion
                FROM (
                    SELECT DISTINCT psd.title AS suggestion, 1 AS priority
                    FROM product_search_documents psd
                    WHERE lower(psd.title) LIKE :prefixQuery

                    UNION

                    SELECT DISTINCT psd.brand AS suggestion, 2 AS priority
                    FROM product_search_documents psd
                    WHERE psd.brand IS NOT NULL AND lower(psd.brand) LIKE :prefixQuery

                    UNION

                    SELECT DISTINCT psd.category_name AS suggestion, 3 AS priority
                    FROM product_search_documents psd
                    WHERE psd.category_name IS NOT NULL AND lower(psd.category_name) LIKE :prefixQuery

                    UNION

                    SELECT DISTINCT psd.shop_name AS suggestion, 4 AS priority
                    FROM product_search_documents psd
                    WHERE psd.shop_name IS NOT NULL AND lower(psd.shop_name) LIKE :prefixQuery
                ) suggestions
                WHERE suggestion IS NOT NULL AND suggestion <> ''
                ORDER BY priority, length(suggestion), suggestion
                LIMIT :limit
                """;

        return entityManager.createNativeQuery(sql)
                .setParameter("prefixQuery", normalizeFilter(prefix) + "%")
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
                            JOIN product_embeddings pe ON pe.product_id = fd.id
                            ORDER BY pe.embedding <=> CAST(:queryEmbedding AS vector), fd.id DESC
                            LIMIT :semanticLimit
                        ),
                        """
                : """
                        semantic_candidates AS (
                            SELECT
                                NULL::BIGINT AS id,
                                NULL::double precision AS semantic_score
                            WHERE FALSE
                        ),
                        """;

        return """
                WITH filtered_docs AS (
                    SELECT
                        psd.product_id AS id,
                        psd.item_id,
                        psd.title,
                        psd.price,
                        psd.image,
                        psd.link,
                        psd.sales_count,
                        psd.location,
                        psd.shop_name,
                        psd.platform_name,
                        psd.category_name,
                        psd.brand,
                        psd.in_stock,
                        COALESCE(psd.popularity_score, 0.0) AS popularity_score,
                        COALESCE(psd.business_score, 0.0) AS business_score,
                        psd.search_vector AS document,
                        psd.search_text,
                        lower(COALESCE(psd.item_id, '')) AS item_id_lower,
                        lower(psd.title) AS title_lower,
                        lower(COALESCE(psd.brand, '')) AS brand_lower,
                        lower(COALESCE(psd.category_name, '')) AS category_lower,
                        lower(COALESCE(psd.shop_name, '')) AS shop_lower,
                        psd.sortable_price
                    FROM product_search_documents psd
                    WHERE (CAST(:platform AS text) IS NULL OR lower(psd.platform_name) = CAST(:platform AS text))
                      AND (CAST(:category AS text) IS NULL OR lower(psd.category_name) = CAST(:category AS text))
                      AND (CAST(:shop AS text) IS NULL OR lower(psd.shop_name) = CAST(:shop AS text))
                      AND (CAST(:minPrice AS double precision) IS NULL OR psd.sortable_price >= CAST(:minPrice AS double precision))
                      AND (CAST(:maxPrice AS double precision) IS NULL OR psd.sortable_price <= CAST(:maxPrice AS double precision))
                ),
                exact_candidates AS (
                    SELECT
                        fd.id,
                        CASE
                            WHEN fd.item_id_lower = :query THEN 1.0
                            WHEN fd.title_lower = :query THEN 0.92
                            WHEN fd.title_lower LIKE :prefixQuery THEN 0.75
                            WHEN fd.brand_lower = :query THEN 0.60
                            WHEN fd.category_lower = :query THEN 0.50
                            ELSE 0.0
                        END AS exact_score
                    FROM filtered_docs fd
                    WHERE fd.item_id_lower = :query
                       OR fd.title_lower = :query
                       OR fd.title_lower LIKE :prefixQuery
                       OR fd.brand_lower = :query
                       OR fd.category_lower = :query
                    ORDER BY exact_score DESC, fd.id DESC
                    LIMIT :candidateLimit
                ),
                lexical_candidates AS (
                    SELECT
                        fd.id,
                        ts_rank_cd(fd.document, websearch_to_tsquery('simple', :query), 32) AS lexical_score
                    FROM filtered_docs fd
                    WHERE fd.document @@ websearch_to_tsquery('simple', :query)
                    ORDER BY lexical_score DESC, fd.id DESC
                    LIMIT :candidateLimit
                ),
                trigram_candidates AS (
                    SELECT
                        fd.id,
                        GREATEST(
                            similarity(fd.title_lower, :query) * 1.0,
                            similarity(fd.brand_lower, :query) * 0.85,
                            similarity(fd.category_lower, :query) * 0.70,
                            similarity(fd.shop_lower, :query) * 0.55
                        ) AS trigram_score
                    FROM filtered_docs fd
                    WHERE GREATEST(
                            similarity(fd.title_lower, :query) * 1.0,
                            similarity(fd.brand_lower, :query) * 0.85,
                            similarity(fd.category_lower, :query) * 0.70,
                            similarity(fd.shop_lower, :query) * 0.55
                        ) >= :trigramThreshold
                    ORDER BY trigram_score DESC, fd.id DESC
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
                        fd.id,
                        fd.item_id,
                        fd.title,
                        fd.price,
                        fd.image,
                        fd.link,
                        fd.sales_count,
                        fd.location,
                        fd.shop_name,
                        fd.platform_name,
                        fd.category_name,
                        fd.brand,
                        fd.in_stock,
                        fd.popularity_score,
                        fd.business_score,
                        fd.document,
                        fd.search_text,
                        fd.item_id_lower,
                        fd.title_lower,
                        fd.brand_lower,
                        fd.category_lower,
                        fd.shop_lower,
                        fd.sortable_price
                ),
                scored AS (
                    SELECT
                        m.*,
                        CASE
                            WHEN m.item_id_lower = :query THEN 'exact_item'
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
                            (m.exact_score * 0.35) +
                            (LEAST(1.0, m.lexical_score * 3.0) * 0.30) +
                            (LEAST(1.0, m.trigram_score) * 0.10) +
                            (LEAST(1.0, GREATEST(0.0, m.semantic_score)) * 0.15) +
                            (LEAST(1.0, GREATEST(0.0, m.popularity_score)) * 0.07) +
                            (LEAST(1.0, GREATEST(0.0, m.business_score)) * 0.03) +
                            CASE WHEN m.in_stock THEN 0.05 ELSE -0.08 END
                        ) AS final_score
                    FROM merged m
                )
                SELECT
                    s.id,
                    s.item_id,
                    s.title,
                    s.price,
                    s.image,
                    s.link,
                    s.sales_count,
                    s.location,
                    s.shop_name,
                    s.platform_name,
                    s.category_name,
                    s.final_score,
                    s.match_type,
                    CASE
                        WHEN s.item_id_lower = :query THEN s.title || ' | ' || s.item_id
                        WHEN s.brand IS NOT NULL AND lower(s.brand) LIKE :containsQuery THEN s.title || ' | ' || s.brand
                        WHEN s.category_name IS NOT NULL AND lower(s.category_name) LIKE :containsQuery THEN s.title || ' | ' || s.category_name
                        WHEN s.shop_name IS NOT NULL AND lower(s.shop_name) LIKE :containsQuery THEN s.title || ' | ' || s.shop_name
                        ELSE s.title
                    END AS highlight,
                    array_to_string(
                        array_remove(ARRAY[
                            CASE WHEN s.item_id_lower = :query THEN 'item_id' END,
                            CASE WHEN lower(s.title) LIKE :containsQuery THEN 'title' END,
                            CASE WHEN lower(COALESCE(s.brand, '')) LIKE :containsQuery THEN 'brand' END,
                            CASE WHEN lower(COALESCE(s.category_name, '')) LIKE :containsQuery THEN 'category' END,
                            CASE WHEN lower(COALESCE(s.shop_name, '')) LIKE :containsQuery THEN 'shop' END,
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
                    CASE WHEN CAST(:sort AS text) = 'newest' THEN s.id END DESC,
                    CASE WHEN CAST(:sort AS text) = 'relevance' THEN s.final_score END DESC,
                    s.final_score DESC,
                    s.id DESC
                LIMIT :limit OFFSET :offset
                """;
    }

    private ProductSearchRow mapRow(Object[] row) {
        return new ProductSearchRow(
                ((Number) row[0]).longValue(),
                (String) row[1],
                (String) row[2],
                (String) row[3],
                (String) row[4],
                (String) row[5],
                (String) row[6],
                (String) row[7],
                (String) row[8],
                (String) row[9],
                (String) row[10],
                row[11] == null ? 0.0 : ((Number) row[11]).doubleValue(),
                (String) row[12],
                (String) row[13],
                parseMatchedFields((String) row[14])
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
