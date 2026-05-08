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
        run("CREATE EXTENSION IF NOT EXISTS pgcrypto");

        run("""
                CREATE TABLE IF NOT EXISTS product_embeddings (
                    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                    product_id uuid NOT NULL UNIQUE REFERENCES products(id) ON DELETE CASCADE,
                    embedding vector(512) NOT NULL,
                    source_image_url text,
                    model_version text NOT NULL DEFAULT 'clip-vit-base-patch32',
                    created_at timestamptz NOT NULL DEFAULT now()
                )
                """);
        run("""
                CREATE INDEX IF NOT EXISTS product_embeddings_product_id_idx
                ON product_embeddings(product_id)
                """);
        run("""
                CREATE INDEX IF NOT EXISTS product_embeddings_vector_idx
                ON product_embeddings
                USING ivfflat (embedding vector_cosine_ops)
                WITH (lists = 100)
                """);

        run("""
                CREATE TABLE IF NOT EXISTS product_search_documents (
                    product_id uuid PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
                    title text,
                    product_url text,
                    image_url text,
                    company text,
                    category_id uuid,
                    category_title text,
                    category_slug text,
                    subcategory_title text,
                    subcategory_slug text,
                    leaf_category_title text,
                    leaf_category_slug text,
                    original_price numeric(12, 2),
                    sortable_price numeric(12, 2),
                    search_text text NOT NULL DEFAULT '',
                    search_vector tsvector NOT NULL DEFAULT ''::tsvector,
                    updated_at timestamptz NOT NULL DEFAULT now()
                )
                """);
        run("""
                CREATE INDEX IF NOT EXISTS product_search_documents_vector_idx
                ON product_search_documents
                USING GIN (search_vector)
                """);
        run("""
                CREATE INDEX IF NOT EXISTS product_search_documents_price_idx
                ON product_search_documents(sortable_price)
                """);
        run("""
                CREATE INDEX IF NOT EXISTS product_search_documents_updated_at_idx
                ON product_search_documents(updated_at DESC NULLS LAST)
                """);
        run("""
                CREATE INDEX IF NOT EXISTS product_search_documents_category_slug_idx
                ON product_search_documents((lower(category_slug)))
                """);
        run("""
                CREATE INDEX IF NOT EXISTS product_search_documents_subcategory_slug_idx
                ON product_search_documents((lower(subcategory_slug)))
                """);
        run("""
                CREATE INDEX IF NOT EXISTS product_search_documents_leaf_category_slug_idx
                ON product_search_documents((lower(leaf_category_slug)))
                """);
        run("""
                CREATE INDEX IF NOT EXISTS product_search_documents_title_trgm_idx
                ON product_search_documents
                USING GIN ((lower(title)) gin_trgm_ops)
                """);
        run("""
                CREATE INDEX IF NOT EXISTS product_search_documents_company_trgm_idx
                ON product_search_documents
                USING GIN ((lower(company)) gin_trgm_ops)
                """);
        run("""
                CREATE INDEX IF NOT EXISTS product_search_documents_category_trgm_idx
                ON product_search_documents
                USING GIN ((lower(category_title)) gin_trgm_ops)
                """);

        run("""
                CREATE INDEX IF NOT EXISTS products_title_trgm_idx
                ON products
                USING GIN ((lower(title)) gin_trgm_ops)
                """);
        run("""
                CREATE INDEX IF NOT EXISTS products_company_trgm_idx
                ON products
                USING GIN ((lower(company)) gin_trgm_ops)
                """);
        run("""
                CREATE INDEX IF NOT EXISTS products_updated_at_idx
                ON products(updated_at DESC NULLS LAST)
                """);
        run("""
                CREATE INDEX IF NOT EXISTS products_image_url_present_idx
                ON products(id)
                WHERE image_url IS NOT NULL
                """);

        run("""
                CREATE INDEX IF NOT EXISTS categories_title_trgm_idx
                ON categories
                USING GIN ((lower(category_title)) gin_trgm_ops)
                """);
        run("""
                CREATE INDEX IF NOT EXISTS categories_slug_lower_idx
                ON categories((lower(slug)))
                """);
        run("""
                CREATE INDEX IF NOT EXISTS subcategories_title_trgm_idx
                ON subcategories
                USING GIN ((lower(sub_category_title)) gin_trgm_ops)
                """);
        run("""
                CREATE INDEX IF NOT EXISTS subcategories_slug_lower_idx
                ON subcategories((lower(sub_category_slug)))
                """);
        run("""
                CREATE INDEX IF NOT EXISTS leaf_categories_title_trgm_idx
                ON leaf_categories
                USING GIN ((lower(leaf_category_title)) gin_trgm_ops)
                """);
        run("""
                CREATE INDEX IF NOT EXISTS leaf_categories_slug_lower_idx
                ON leaf_categories((lower(leaf_category_slug)))
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
