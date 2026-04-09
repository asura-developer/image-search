package backend.searchbyimage.search;

import backend.searchbyimage.search.model.NormalizedProductSearchQuery;
import backend.searchbyimage.search.model.ProductSearchFilter;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class ProductQueryNormalizer {

    private static final Pattern SPECIAL_CHARS = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}\\s\\-']");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    public NormalizedProductSearchQuery normalize(String raw, ProductSearchFilter filter, int defaultSize, int maxSize) {
        String safeRaw = raw == null ? "" : raw.trim();
        String cleaned = SPECIAL_CHARS.matcher(safeRaw).replaceAll(" ");
        String unaccented = stripAccents(cleaned);
        String normalized = WHITESPACE.matcher(unaccented).replaceAll(" ").trim().toLowerCase();

        List<String> tokens = normalized.isBlank()
                ? List.of()
                : Arrays.stream(normalized.split(" "))
                .filter(token -> token.length() >= 2)
                .map(this::sanitizeToken)
                .filter(token -> !token.isBlank())
                .toList();

        String tsQuery = tokens.stream()
                .map(token -> token + ":*")
                .reduce((left, right) -> left + " & " + right)
                .orElse("");

        String phraseQuery = tokens.stream()
                .reduce((left, right) -> left + " <-> " + right)
                .orElse("");

        ProductSearchFilter safeFilter = filter == null
                ? new ProductSearchFilter(null, null, null, null, null, null, null, null)
                : filter;

        return new NormalizedProductSearchQuery(
                raw,
                normalized,
                tsQuery,
                phraseQuery,
                normalized,
                tokens,
                safeFilter,
                safeFilter.effectivePage(),
                safeFilter.effectiveSize(defaultSize, maxSize)
        );
    }

    private String stripAccents(String text) {
        return DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
    }

    private String sanitizeToken(String token) {
        return token.replace("'", "");
    }
}
