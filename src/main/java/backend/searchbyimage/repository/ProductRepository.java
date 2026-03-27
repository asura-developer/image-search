package backend.searchbyimage.repository;

import backend.searchbyimage.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByItemId(String itemId);

    @Query("SELECT p FROM Product p WHERE p.detailsScraped = true AND p.image IS NOT NULL")
    List<Product> findProductsWithImages();

    @Query(value = """
            SELECT p.id FROM products p
            LEFT JOIN product_embeddings pe ON p.id = pe.product_id
            WHERE p.image IS NOT NULL AND pe.id IS NULL
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findProductIdsWithoutEmbeddings(int limit);
}
