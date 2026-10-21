package cgeo.geocaching.utils.offlinetranslate;

import org.junit.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class TranslationCacheTest {
    @Test
    public void evictsLeastRecentlyUsedText() {
        final TranslationCache cache = new TranslationCache(420);
        cache.put("one", "1");
        cache.put("two", "2");
        assertThat(cache.get("one")).isEqualTo("1");
        cache.put("three", "3");
        assertThat(cache.get("two")).isNull();
        assertThat(cache.get("one")).isEqualTo("1");
        assertThat(cache.get("three")).isEqualTo("3");
        assertThat(cache.sizeInBytes()).isLessThanOrEqualTo(420);
    }

    @Test
    public void replacementReleasesPreviousWeight() {
        final TranslationCache cache = new TranslationCache(1000);
        cache.put("source", new String(new char[100]));
        final long previousSize = cache.sizeInBytes();
        cache.put("source", "small");
        assertThat(cache.sizeInBytes()).isLessThan(previousSize);
        cache.put("other", new String(new char[100]));
        assertThat(cache.get("source")).isEqualTo("small");
        assertThat(cache.get("other")).isNotNull();
        assertThat(cache.sizeInBytes()).isLessThanOrEqualTo(1000);
    }

    @Test
    public void oversizedValueDoesNotEvictUsefulEntries() {
        final TranslationCache cache = new TranslationCache(512);
        cache.put("small", "text");
        cache.put("large", new String(new char[300]));
        assertThat(cache.get("large")).isNull();
        assertThat(cache.get("small")).isEqualTo("text");
        assertThat(cache.sizeInBytes()).isLessThanOrEqualTo(512);
    }

    @Test
    public void budgetAlsoIncludesSourceText() {
        final TranslationCache cache = new TranslationCache(512);
        final String largeSource = new String(new char[300]);
        cache.put(largeSource, "x");
        assertThat(cache.get(largeSource)).isNull();
        assertThat(cache.sizeInBytes()).isZero();
    }

    @Test
    public void oversizedReplacementRemovesOldValue() {
        final TranslationCache cache = new TranslationCache(512);
        cache.put("source", "old translation");
        cache.put("source", new String(new char[300]));
        assertThat(cache.get("source")).isNull();
        assertThat(cache.sizeInBytes()).isZero();
    }
}
