package backend.searchbyimage.search.repository;

import backend.searchbyimage.search.model.NormalizedProductSearchQuery;
import backend.searchbyimage.search.model.ProductSearchPage;
import backend.searchbyimage.search.model.ProductSearchRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Repository
public class ProductSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public ProductSearchPage searchProducts(NormalizedProductSearchQuery query) {
        String sql = """
                WITH search_docs AS (
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
                        psd.search_vector AS document,
                        psd.search_text,
                        lower(psd.title) AS title_lower,
                        lower(COALESCE(psd.brand, '')) AS brand_lower,
                        lower(COALESCE(psd.category_name, '')) AS category_lower,
                        lower(COALESCE(psd.shop_name, '')) AS shop_lower,
                        psd.sortable_price
                    FROM product_search_documents psd
                ),
                ranked AS (
                    SELECT
                        sd.*,
                        ts_rank_cd(sd.document, websearch_to_tsquery('simple', :query)) AS fulltext_score,
                        GREATEST(
                            similarity(sd.title_lower, :query) * 1.0,
                            similarity(sd.brand_lower, :query) * 0.85,
                            similarity(sd.category_lower, :query) * 0.7,
                            similarity(sd.shop_lower, :query) * 0.6
                        ) AS trigram_score,
                        CASE
                            WHEN sd.title_lower = :query THEN 9.0
                            WHEN sd.title_lower LIKE :prefixQuery THEN 7.0
                            WHEN sd.search_text LIKE :containsQuery THEN 4.0
                            ELSE 0.0
                        END AS lexical_score,
                        CASE
                            WHEN sd.title_lower = :query THEN 'exact_title'
                            WHEN sd.title_lower LIKE :prefixQuery THEN 'prefix_title'
                            WHEN sd.brand_lower = :query THEN 'exact_brand'
                            WHEN sd.category_lower = :query THEN 'exact_category'
                            WHEN sd.shop_lower = :query THEN 'exact_shop'
                            WHEN GREATEST(
                                similarity(sd.title_lower, :query) * 1.0,
                                similarity(sd.brand_lower, :query) * 0.85,
                                similarity(sd.category_lower, :query) * 0.7,
                                similarity(sd.shop_lower, :query) * 0.6
                            ) >= :trigramThreshold THEN 'fuzzy'
                            WHEN sd.document @@ websearch_to_tsquery('simple', :query) THEN 'full_text'
                            ELSE 'contains'
                        END AS match_type
                    FROM search_docs sd
                    WHERE (
                        sd.document @@ websearch_to_tsquery('simple', :query)
                        OR sd.search_text LIKE :containsQuery
                        OR similarity(sd.title_lower, :query) >= :trigramThreshold
                        OR similarity(sd.brand_lower, :query) >= :trigramThreshold
                        OR similarity(sd.category_lower, :query) >= :trigramThreshold
                        OR similarity(sd.shop_lower, :query) >= :trigramThreshold
                    )
                    AND (:platform IS NULL OR lower(sd.platform_name) = :platform)
                    AND (:category IS NULL OR lower(sd.category_name) = :category)
                    AND (:shop IS NULL OR lower(sd.shop_name) = :shop)
                    AND (:minPrice IS NULL OR sd.sortable_price >= :minPrice)
                    AND (:maxPrice IS NULL OR sd.sortable_price <= :maxPrice)
                )
                SELECT
                    r.id,
                    r.item_id,
                    r.title,
                    r.price,
                    r.image,
                    r.link,
                    r.sales_count,
                    r.location,
                    r.shop_name,
                    r.platform_name,
                    r.category_name,
                    (r.lexical_score + (r.fulltext_score * 10.0) + (r.trigram_score * 6.0)) AS score,
                    r.match_type,
                    CASE
                        WHEN r.brand IS NOT NULL AND lower(r.brand) LIKE :containsQuery
                            THEN r.title || ' | ' || r.brand
                        WHEN r.match_type = 'fuzzy' AND r.brand IS NOT NULL AND similarity(lower(r.brand), :query) >= :trigramThreshold
                            THEN r.title || ' | ' || r.brand
                        WHEN r.category_name IS NOT NULL AND lower(r.category_name) LIKE :containsQuery
                            THEN r.title || ' | ' || r.category_name
                        WHEN r.shop_name IS NOT NULL AND lower(r.shop_name) LIKE :containsQuery
                            THEN r.title || ' | ' || r.shop_name
                        ELSE r.title
                    END AS highlight,
                    array_to_string(
                        array_remove(ARRAY[
                            CASE WHEN lower(r.title) LIKE :containsQuery THEN 'title' END,
                            CASE WHEN lower(COALESCE(r.brand, '')) LIKE :containsQuery THEN 'brand' END,
                            CASE WHEN lower(COALESCE(r.category_name, '')) LIKE :containsQuery THEN 'category' END,
                            CASE WHEN lower(COALESCE(r.shop_name, '')) LIKE :containsQuery THEN 'shop' END,
                            CASE WHEN lower(COALESCE(r.location, '')) LIKE :containsQuery THEN 'location' END
                        ], NULL),
                        ','
                    ) AS matched_fields
                FROM ranked r
                ORDER BY
                    CASE WHEN :sort = 'price_asc' THEN r.sortable_price END ASC NULLS LAST,
                    CASE WHEN :sort = 'price_desc' THEN r.sortable_price END DESC NULLS LAST,
                    CASE WHEN :sort = 'newest' THEN r.id END DESC,
                    CASE WHEN :sort = 'title' THEN lower(r.title) END ASC,
                    CASE WHEN :sort = 'relevance' THEN (r.lexical_score + (r.fulltext_score * 10.0) + (r.trigram_score * 6.0)) END DESC,
                    r.id DESC
                LIMIT :limit OFFSET :offset
                """;

        List<ProductSearchRow> mappedRows = entityManager.createNativeQuery(sql)
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
                .setParameter("limit", query.size() + 1)
                .setParameter("offset", query.page() * query.size())
                .getResultList()
                .stream()
                .map(row -> mapRow((Object[]) row))
                .toList();

        boolean hasMore = mappedRows.size() > query.size();
        List<ProductSearchRow> rows = hasMore ? mappedRows.subList(0, query.size()) : mappedRows;
        int totalResults = query.page() * query.size() + rows.size() + (hasMore ? 1 : 0);

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
}
