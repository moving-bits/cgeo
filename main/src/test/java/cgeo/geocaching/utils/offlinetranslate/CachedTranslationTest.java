package cgeo.geocaching.utils.offlinetranslate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import org.junit.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class CachedTranslationTest {
    @Test
    public void cachedTextDoesNotRunAnotherNativeTranslation() {
        final TestScheduler scheduler = new TestScheduler();
        final TestTranslator translator = new TestTranslator(scheduler);
        final TranslationCache.Scope scope = new TranslationCache(2000).openScope("GC1");
        final List<String> results = new ArrayList<>();
        TranslatorUtils.translateCached(scope, translator, "source", scheduler, results::add, error -> results.add("error"));
        scheduler.triggerActions();
        TranslatorUtils.translateCached(scope, translator, "source", scheduler, results::add, error -> results.add("error"));
        scheduler.triggerActions();
        assertThat(results).containsExactly("translated", "translated");
        assertThat(translator.nativeCalls).isEqualTo(1);
    }

    @Test
    public void cancellationStillReachesNativeQueueThroughCacheWrapper() {
        final TestScheduler scheduler = new TestScheduler();
        final TestTranslator translator = new TestTranslator(scheduler);
        final TranslationCache.Scope scope = new TranslationCache(2000).openScope("GC1");
        final List<String> results = new ArrayList<>();
        final Disposable task = TranslatorUtils.translateCached(scope, translator, "source", scheduler,
                results::add, error -> results.add("error"));
        task.dispose();
        scheduler.triggerActions();
        assertThat(translator.nativeCalls).isZero();
        assertThat(results).isEmpty();
    }

    @Test
    public void cacheHitCallbackCanBeCancelled() {
        final TestScheduler scheduler = new TestScheduler();
        final TestTranslator translator = new TestTranslator(scheduler);
        final TranslationCache.Scope scope = new TranslationCache(2000).openScope("GC1");
        scope.put("en:de:source", "cached");
        final List<String> results = new ArrayList<>();
        final Disposable task = TranslatorUtils.translateCached(scope, translator, "source", scheduler,
                results::add, error -> results.add("error"));
        task.dispose();
        scheduler.triggerActions();
        assertThat(translator.nativeCalls).isZero();
        assertThat(results).isEmpty();
    }

    private static class TestTranslator implements ITranslatorImpl {
        private final TestScheduler scheduler;
        private final CompositeDisposable tasks = new CompositeDisposable();
        private int nativeCalls;

        TestTranslator(final TestScheduler scheduler) {
            this.scheduler = scheduler;
        }

        @Override
        public String getSourceLanguage() {
            return "en";
        }

        @Override
        public String getTargetLanguage() {
            return "de";
        }

        @Override
        public Disposable translate(final String source, final Consumer<String> onSuccess, final Consumer<Exception> onError) {
            return TranslationTask.schedule(tasks, scheduler, scheduler, () -> {
                nativeCalls++;
                return "translated";
            }, onSuccess, onError);
        }

        @Override
        public void dispose() {
            tasks.dispose();
        }
    }
}
