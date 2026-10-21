package cgeo.geocaching.utils.offlinetranslate;

import cgeo.geocaching.utils.OfflineTranslateUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.reactivex.rxjava3.disposables.Disposable;
import org.junit.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class TranslationStatusTest {
    @Test
    public void abortCancelsRequestsAndSuppressesLateResults() {
        final OfflineTranslateUtils.Status status = new OfflineTranslateUtils.Status();
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AtomicReference<Consumer<String>> callback = new AtomicReference<>();
        final ITranslatorImpl translator = new ITranslatorImpl() {
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
                callback.set(onSuccess);
                return Disposable.fromRunnable(() -> cancelled.set(true));
            }

            @Override
            public void setQueue(final TranslationQueue queue) {
                // This fake has no worker; the status test drives its callback explicitly.
            }

            @Override
            public void dispose() {
                // This test verifies cancellation of the returned request handle.
            }
        };
        final List<String> results = new ArrayList<>();
        status.startTranslation(1, null, null);
        status.translate(translator, "source", results::add, error -> results.add("error"));
        status.abortTranslation();
        callback.get().accept("late result");
        assertThat(cancelled.get()).isTrue();
        assertThat(results).isEmpty();
        assertThat(status.isTranslated()).isFalse();
    }

    @Test
    public void oldSessionCannotRestartTranslationAfterAbort() {
        final OfflineTranslateUtils.Status status = new OfflineTranslateUtils.Status();
        status.startTranslation(1, null, null);
        final long previous = status.getGeneration();
        status.abortTranslation();
        status.startTranslation(1, null, null);
        final AtomicBoolean called = new AtomicBoolean();
        assertThat(status.runIfCurrent(previous, () -> called.set(true))).isFalse();
        assertThat(called.get()).isFalse();
        assertThat(status.isCurrent(status.getGeneration())).isTrue();
    }
}
