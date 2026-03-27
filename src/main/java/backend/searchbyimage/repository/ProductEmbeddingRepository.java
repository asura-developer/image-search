package backend.searchbyimage.repository;

import backend.searchbyimage.domain.ProductEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductEmbeddingRepository extends JpaRepository<ProductEmbedding, Long> {

    Optional<ProductEmbedding> findByProductId(Long productId);

    boolean existsByProductId(Long productId);

    @Modifying
    @Query(value = """
            INSERT INTO product_embeddings (product_id, embedding, source_image_url, model_version, created_at)
            VALUES (:productId, cast(:embedding as vector), :sourceImageUrl, :modelVersion, NOW())
            """, nativeQuery = true)
    void insertEmbedding(
            @Param("productId") Long productId,
            @Param("embedding") String embedding,
            @Param("sourceImageUrl") String sourceImageUrl,
            @Param("modelVersion") String modelVersion
    );

    /**
     * Find similar products using pgvector cosine distance.
     * Lower distance = more similar. Cosine distance = 1 - cosine_similarity.
     */
    @Query(value = """
            SELECT pe.product_id, 1 - (pe.embedding <=> cast(:queryVector as vector)) as similarity
            FROM product_embeddings pe
            WHERE 1 - (pe.embedding <=> cast(:queryVector as vector)) >= :threshold
            ORDER BY pe.embedding <=> cast(:queryVector as vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findSimilarProducts(
            @Param("queryVector") String queryVector,
            @Param("threshold") double threshold,
            @Param("limit") int limit
    );
}
