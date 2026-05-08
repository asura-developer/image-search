package backend.searchbyimage;

import backend.searchbyimage.domain.Category;
import backend.searchbyimage.domain.LeafCategory;
import backend.searchbyimage.domain.Product;
import backend.searchbyimage.domain.ProductDetail;
import backend.searchbyimage.domain.ProductDetailVariant;
import backend.searchbyimage.domain.ProductScrapeResult;
import backend.searchbyimage.domain.ScrapeRun;
import backend.searchbyimage.domain.Subcategory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication
@EntityScan(basePackageClasses = {
        Category.class,
        Subcategory.class,
        LeafCategory.class,
        ScrapeRun.class,
        Product.class,
        ProductScrapeResult.class,
        ProductDetail.class,
        ProductDetailVariant.class
})
public class SearchByImageApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchByImageApplication.class, args);
    }

}
