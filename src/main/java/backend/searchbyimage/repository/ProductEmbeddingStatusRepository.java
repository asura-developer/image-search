package backend.searchbyimage.repository;

import backend.searchbyimage.domain.ProductEmbeddingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductEmbeddingStatusRepository extends JpaRepository<ProductEmbeddingStatus, Long> {

    Optional<ProductEmbeddingStatus> findByProductId(Long productId);
}
