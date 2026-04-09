package backend.searchbyimage.search.index;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SearchOptimizationInitializer {

    private static final Logger log = LoggerFactory.getLogger(SearchOptimizationInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public SearchOptimizationInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void initialize() {
        run("CREATE EXTENSION IF NOT EXISTS pg_trgm");
        run("CREATE EXTENSION IF NOT EXISTS unaccent");
        run("CREATE EXTENSION IF NOT EXISTS vector");
        run("""
                CREATE TABLE IF NOT EXISTS product_search_documents (
                    product_id BIGINT PRIMARY KEY,
                    item_id VARCHAR(100),
                    title VARCHAR(500) NOT NULL,
                    price VARCHAR(50),
                    image TEXT,
                    link TEXT NOT NULL,
                    sales_count VARCHAR(50),
                    location VARCHAR(255),
                    shop_name VARCHAR(255),
                    platform_name VARCHAR(20),
                    category_name VARCHAR(255),
                    brand VARCHAR(255),
                    in_stock BOOLEAN NOT NULL DEFAULT TRUE,
                    popularity_score DOUBLE PRECISION NOT NULL DEFAULT 0,
                    business_score DOUBLE PRECISION NOT NULL DEFAULT 0,
                    search_text TEXT NOT NULL,
                    search_vector tsvector NOT NULL,
                    sortable_price NUMERIC,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
        run("""
                ALTER TABLE product_search_documents
                ADD COLUMN IF NOT EXISTS in_stock BOOLEAN NOT NULL DEFAULT TRUE
                """);
        run("""
                ALTER TABLE product_search_documents
                ADD COLUMN IF NOT EXISTS popularity_score DOUBLE PRECISION NOT NULL DEFAULT 0
                """);
        run("""
                ALTER TABLE product_search_documents
                ADD COLUMN IF NOT EXISTS business_score DOUBLE PRECISION NOT NULL DEFAULT 0
                """);
        run("""
                CREATE INDEX IF NOT EXISTS idx_product_search_documents_vector
                ON product_search_documents
                USING GIN (search_vector)
                """);
        run("""
                CREATE INDEX IF NOT EXISTS idx_product_search_documents_item_id
                ON product_search_documents (item_id)
                """);
        run("""
                CREATE INDEX IF NOT EXISTS idx_product_search_documents_platform
                ON product_search_documents (platform_name)
                """);
        run("""
                CREATE INDEX IF NOT EXISTS idx_product_search_documents_category
                ON product_search_documents (category_name)
                """);
        run("""
                CREATE INDEX IF NOT EXISTS idx_product_search_documents_shop
                ON product_search_documents (shop_name)
                """);
        run("""
                CREATE INDEX IF NOT EXISTS idx_product_search_documents_in_stock
                ON product_search_documents (in_stock)
                """);
        run("""
                CREATE INDEX IF NOT EXISTS idx_product_search_documents_price
                ON product_search_documents (sortable_price)
                """);

        run("""
                CREATE INDEX IF NOT EXISTS idx_product_search_documents_title_trgm
                ON product_search_documents
                USING GIN (lower(title) gin_trgm_ops)
                """);
        run("""
                CREATE INDEX IF NOT EXISTS idx_product_search_documents_location_trgm
                ON product_search_documents
                USING GIN (lower(location) gin_trgm_ops)
                """);
        run("""
                CREATE INDEX IF NOT EXISTS idx_product_search_documents_brand_trgm
                ON product_search_documents
                USING GIN (lower(brand) gin_trgm_ops)
                """);
        run("""
                CREATE INDEX IF NOT EXISTS idx_product_search_documents_category_trgm
                ON product_search_documents
                USING GIN (lower(category_name) gin_trgm_ops)
                """);
        run("""
                CREATE INDEX IF NOT EXISTS idx_product_search_documents_shop_trgm
                ON product_search_documents
                USING GIN (lower(shop_name) gin_trgm_ops)
                """);
    }

    private void run(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            log.warn("Search optimization SQL failed: {}", e.getMessage());
        }
    }
}
