package backend.searchbyimage.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProductSearchIndexService {

    private final JdbcTemplate jdbcTemplate;

    public ProductSearchIndexService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public int rebuildAll() {
        jdbcTemplate.update("TRUNCATE product_search_documents");
        return jdbcTemplate.update(upsertSql(""));
    }

    @Transactional
    public boolean syncProduct(String productId) {
        UUID.fromString(productId);
        return jdbcTemplate.update(upsertSql("WHERE p.id = CAST(? AS uuid)"), productId) > 0;
    }

    @Transactional
    public void deleteProduct(String productId) {
        UUID.fromString(productId);
        jdbcTemplate.update("DELETE FROM product_search_documents WHERE product_id = CAST(? AS uuid)", productId);
    }

    private String upsertSql(String whereClause) {
        return """
                WITH latest_details AS (
                    SELECT DISTINCT ON (pd.product_id)
                        pd.product_id,
                        pd.product_title,
                        pd.supplier_name,
                        pd.original_price,
                        pd.original_price_usd,
                        pd.promotional_price,
                        pd.promotional_price_usd,
                        pd.price_text,
                        pd.scraped_at,
                        pd.created_at
                    FROM product_details pd
                    ORDER BY pd.product_id, pd.scraped_at DESC NULLS LAST, pd.created_at DESC
                )
                INSERT INTO product_search_documents (
                    product_id,
                    title,
                    product_url,
                    image_url,
                    company,
                    category_id,
                    category_title,
                    category_slug,
                    subcategory_title,
                    subcategory_slug,
                    leaf_category_title,
                    leaf_category_slug,
                    original_price,
                    sortable_price,
                    search_text,
                    search_vector,
                    updated_at
                )
                SELECT
                    p.id,
                    COALESCE(NULLIF(p.title, ''), ld.product_title),
                    p.product_url,
                    p.image_url,
                    COALESCE(NULLIF(p.company, ''), ld.supplier_name),
                    c.uuid,
                    c.category_title,
                    c.slug,
                    sc.sub_category_title,
                    sc.sub_category_slug,
                    lc.leaf_category_title,
                    COALESCE(lc.leaf_category_slug, lc.slug),
                    COALESCE(ld.original_price, ld.promotional_price),
                    COALESCE(ld.promotional_price_usd, ld.original_price_usd, ld.promotional_price, ld.original_price),
                    lower(
                        unaccent(
                            concat_ws(
                                ' ',
                                p.id::text,
                                p.title,
                                ld.product_title,
                                p.company,
                                ld.supplier_name,
                                c.category_title,
                                c.slug,
                                sc.sub_category_title,
                                sc.sub_category_slug,
                                lc.leaf_category_title,
                                lc.leaf_category_slug,
                                ld.price_text
                            )
                        )
                    ),
                    setweight(to_tsvector('simple', unaccent(COALESCE(p.title, ld.product_title, ''))), 'A') ||
                    setweight(to_tsvector('simple', unaccent(COALESCE(p.company, ld.supplier_name, ''))), 'B') ||
                    setweight(to_tsvector('simple', unaccent(COALESCE(c.category_title, ''))), 'B') ||
                    setweight(to_tsvector('simple', unaccent(COALESCE(sc.sub_category_title, ''))), 'C') ||
                    setweight(to_tsvector('simple', unaccent(COALESCE(lc.leaf_category_title, ''))), 'C'),
                    GREATEST(
                        COALESCE(p.updated_at, p.created_at, '-infinity'::timestamptz),
                        COALESCE(ld.scraped_at, ld.created_at, '-infinity'::timestamptz)
                    )
                FROM products p
                LEFT JOIN latest_details ld ON ld.product_id = p.id
                LEFT JOIN categories c ON c.uuid = p.category_id
                LEFT JOIN subcategories sc ON sc.uuid = p.subcategory_id
                LEFT JOIN leaf_categories lc ON lc.id = p.leaf_category_id
                """ + whereClause + """
                ON CONFLICT (product_id) DO UPDATE SET
                    title = EXCLUDED.title,
                    product_url = EXCLUDED.product_url,
                    image_url = EXCLUDED.image_url,
                    company = EXCLUDED.company,
                    category_id = EXCLUDED.category_id,
                    category_title = EXCLUDED.category_title,
                    category_slug = EXCLUDED.category_slug,
                    subcategory_title = EXCLUDED.subcategory_title,
                    subcategory_slug = EXCLUDED.subcategory_slug,
                    leaf_category_title = EXCLUDED.leaf_category_title,
                    leaf_category_slug = EXCLUDED.leaf_category_slug,
                    original_price = EXCLUDED.original_price,
                    sortable_price = EXCLUDED.sortable_price,
                    search_text = EXCLUDED.search_text,
                    search_vector = EXCLUDED.search_vector,
                    updated_at = EXCLUDED.updated_at
                """;
    }
}
