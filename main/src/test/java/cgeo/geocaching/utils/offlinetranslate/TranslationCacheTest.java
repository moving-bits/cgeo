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

    @Test
    public void finishingClearsOnlyTheSelectedGeocache() {
        final TranslationCache cache = new TranslationCache(2000);
        final TranslationCache.Scope first = cache.openScope("GC1");
        final TranslationCache.Scope second = cache.openScope("GC10");
        first.put("en:de:source", "first translation");
        second.put("en:de:source", "second translation");
        cache.put("global:en:de:source", "unrelated translation");
        first.close(true);
        assertThat(cache.openScope("GC1").get("en:de:source")).isNull();
        assertThat(second.get("en:de:source")).isEqualTo("second translation");
        assertThat(cache.get("global:en:de:source")).isEqualTo("unrelated translation");
    }

    @Test
    public void lateResultCannotRepopulateFinishedScope() {
        final TranslationCache cache = new TranslationCache(2000);
        final TranslationCache.Scope scope = cache.openScope("GC1");
        scope.put("en:de:source", "translation");
        scope.close(true);
        scope.put("en:de:source", "late result");
        assertThat(cache.openScope("GC1").get("en:de:source")).isNull();
        assertThat(cache.sizeInBytes()).isZero();
    }

    @Test
    public void recreationPreservesEntriesButRejectsOldCallbacks() {
        final TranslationCache cache = new TranslationCache(2000);
        final TranslationCache.Scope oldActivity = cache.openScope("GC1");
        oldActivity.put("en:de:source", "translation");
        oldActivity.close(false);
        oldActivity.put("en:de:source", "stale result");
        final TranslationCache.Scope recreated = cache.openScope("GC1");
        assertThat(recreated.get("en:de:source")).isEqualTo("translation");
        recreated.put("en:de:other", "new translation");
        assertThat(recreated.get("en:de:other")).isEqualTo("new translation");
    }

    @Test
    public void clearingScopeReleasesItsBudget() {
        final TranslationCache cache = new TranslationCache(2000);
        final TranslationCache.Scope scope = cache.openScope("GC1");
        scope.put("source", "translation");
        assertThat(cache.sizeInBytes()).isPositive();
        scope.close(true);
        assertThat(cache.sizeInBytes()).isZero();
    }

    @Test
    public void recreatedActivityCanClearEntriesWithoutTranslatingAgain() {
        final TranslationCache cache = new TranslationCache(2000);
        final TranslationCache.Scope oldActivity = cache.openScope("GC1");
        oldActivity.put("source", "translation");
        oldActivity.close(false);
        cache.openScope("GC1").close(true);
        assertThat(cache.sizeInBytes()).isZero();
    }

    @Test
    public void languagePairsRemainSeparateWithinScope() {
        final TranslationCache cache = new TranslationCache(2000);
        final TranslationCache.Scope scope = cache.openScope("GC1");
        scope.put("en:de:source", "German");
        scope.put("en:fr:source", "French");
        assertThat(scope.get("en:de:source")).isEqualTo("German");
        assertThat(scope.get("en:fr:source")).isEqualTo("French");
    }
}
