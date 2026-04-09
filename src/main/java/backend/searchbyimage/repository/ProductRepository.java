package backend.searchbyimage.repository;

import backend.searchbyimage.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
            LEFT JOIN product_embedding_status pes ON p.id = pes.product_id
            WHERE p.image IS NOT NULL
              AND pe.id IS NULL
              AND (
                    pes.id IS NULL
                    OR pes.status IN ('PENDING', 'RETRY')
                  )
              AND (
                    pes.next_retry_at IS NULL
                    OR pes.next_retry_at <= NOW()
                  )
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findProductIdsWithoutEmbeddings(int limit);

    @Query("select p.id from Product p where p.shop.id = :shopId")
    List<Long> findIdsByShopId(@Param("shopId") Long shopId);

    @Query("select p.id from Product p where p.category.id = :categoryId")
    List<Long> findIdsByCategoryId(@Param("categoryId") Long categoryId);
}
