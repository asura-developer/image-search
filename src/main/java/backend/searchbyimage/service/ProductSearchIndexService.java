package backend.searchbyimage.service;

import backend.searchbyimage.repository.ProductRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductSearchIndexService {

    private final JdbcTemplate jdbcTemplate;
    private final ProductRepository productRepository;

    public ProductSearchIndexService(JdbcTemplate jdbcTemplate, ProductRepository productRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.productRepository = productRepository;
    }

    @Transactional
    public int rebuildAll() {
        return jdbcTemplate.update(upsertSql(null));
    }

    @Transactional
    public boolean syncProduct(Long productId) {
        return jdbcTemplate.update(upsertSql(" WHERE p.id = ?"), productId) > 0;
    }

    @Transactional
    public int syncProductsByShop(Long shopId) {
        List<Long> productIds = productRepository.findIdsByShopId(shopId);
        int synced = 0;
        for (Long productId : productIds) {
            if (syncProduct(productId)) {
                synced++;
            }
        }
        return synced;
    }

    @Transactional
    public int syncProductsByCategory(Long categoryId) {
        List<Long> productIds = productRepository.findIdsByCategoryId(categoryId);
        int synced = 0;
        for (Long productId : productIds) {
            if (syncProduct(productId)) {
                synced++;
            }
        }
        return synced;
    }

    @Transactional
    public void deleteProduct(Long productId) {
        jdbcTemplate.update("DELETE FROM product_search_documents WHERE product_id = ?", productId);
    }

    private String upsertSql(String whereClause) {
        return """
                INSERT INTO product_search_documents (
                    product_id,
                    item_id,
                    title,
                    price,
                    image,
                    link,
                    sales_count,
                    location,
                    shop_name,
                    platform_name,
                    category_name,
                    brand,
                    in_stock,
                    popularity_score,
                    business_score,
                    search_text,
                    search_vector,
                    sortable_price,
                    updated_at
                )
                SELECT
                    p.id,
                    p.item_id,
                    p.title,
                    p.price,
                    p.image,
                    p.link,
                    p.sales_count,
                    p.location,
                    s.shop_name,
                    pf.name,
                    COALESCE(c.category_name, psm.category_name),
                    pd.brand,
                    COALESCE(pd.in_stock, TRUE),
                    LEAST(
                        1.0,
                        LN(
                            1 + GREATEST(
                                CASE
                                    WHEN NULLIF(regexp_replace(COALESCE(p.sales_count, ''), '[^0-9]', '', 'g'), '') IS NULL
                                        THEN 0
                                    ELSE CAST(NULLIF(regexp_replace(COALESCE(p.sales_count, ''), '[^0-9]', '', 'g'), '') AS numeric)
                                END,
                                CASE
                                    WHEN NULLIF(regexp_replace(COALESCE(pd.sales_volume, ''), '[^0-9]', '', 'g'), '') IS NULL
                                        THEN 0
                                    ELSE CAST(NULLIF(regexp_replace(COALESCE(pd.sales_volume, ''), '[^0-9]', '', 'g'), '') AS numeric)
                                END,
                                CASE
                                    WHEN NULLIF(regexp_replace(COALESCE(pd.review_count, ''), '[^0-9]', '', 'g'), '') IS NULL
                                        THEN 0
                                    ELSE CAST(NULLIF(regexp_replace(COALESCE(pd.review_count, ''), '[^0-9]', '', 'g'), '') AS numeric)
                                END
                            )
                        ) / 10.0
                    ),
                    0.0,
                    lower(
                        unaccent(
                            concat_ws(
                                ' ',
                                p.title,
                                COALESCE(pd.brand, ''),
                                COALESCE(c.category_name, psm.category_name, ''),
                                COALESCE(s.shop_name, ''),
                                COALESCE(p.location, ''),
                                COALESCE(pd.full_title, ''),
                                COALESCE(pd.full_description, ''),
                                COALESCE(psm.search_keyword, '')
                            )
                        )
                    ),
                    setweight(to_tsvector('simple', unaccent(COALESCE(p.title, ''))), 'A') ||
                    setweight(to_tsvector('simple', unaccent(COALESCE(p.item_id, ''))), 'A') ||
                    setweight(to_tsvector('simple', unaccent(COALESCE(pd.brand, ''))), 'A') ||
                    setweight(to_tsvector('simple', unaccent(COALESCE(c.category_name, psm.category_name, ''))), 'B') ||
                    setweight(to_tsvector('simple', unaccent(COALESCE(s.shop_name, ''))), 'B') ||
                    setweight(to_tsvector('simple', unaccent(COALESCE(p.location, ''))), 'C') ||
                    setweight(to_tsvector('simple', unaccent(COALESCE(psm.search_keyword, ''))), 'C') ||
                    setweight(to_tsvector('simple', unaccent(COALESCE(pd.full_title, ''))), 'C') ||
                    setweight(to_tsvector('simple', unaccent(COALESCE(pd.full_description, ''))), 'D'),
                    CASE
                        WHEN NULLIF(regexp_replace(COALESCE(pd.original_price, p.price, ''), '[^0-9.]', '', 'g'), '') IS NULL
                            THEN NULL
                        ELSE CAST(NULLIF(regexp_replace(COALESCE(pd.original_price, p.price, ''), '[^0-9.]', '', 'g'), '') AS numeric)
                    END,
                    NOW()
                FROM products p
                LEFT JOIN product_details pd ON pd.product_id = p.id
                LEFT JOIN product_search_meta psm ON psm.product_id = p.id
                LEFT JOIN shops s ON s.id = p.shop_id
                LEFT JOIN platforms pf ON pf.id = p.platform_id
                LEFT JOIN categories c ON c.id = p.category_fk
                """ + (whereClause == null ? "" : whereClause) + """
                ON CONFLICT (product_id) DO UPDATE SET
                    item_id = EXCLUDED.item_id,
                    title = EXCLUDED.title,
                    price = EXCLUDED.price,
                    image = EXCLUDED.image,
                    link = EXCLUDED.link,
                    sales_count = EXCLUDED.sales_count,
                    location = EXCLUDED.location,
                    shop_name = EXCLUDED.shop_name,
                    platform_name = EXCLUDED.platform_name,
                    category_name = EXCLUDED.category_name,
                    brand = EXCLUDED.brand,
                    in_stock = EXCLUDED.in_stock,
                    popularity_score = EXCLUDED.popularity_score,
                    business_score = EXCLUDED.business_score,
                    search_text = EXCLUDED.search_text,
                    search_vector = EXCLUDED.search_vector,
                    sortable_price = EXCLUDED.sortable_price,
                    updated_at = EXCLUDED.updated_at
                """;
    }
}
