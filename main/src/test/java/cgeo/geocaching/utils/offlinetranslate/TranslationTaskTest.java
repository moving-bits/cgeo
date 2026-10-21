package cgeo.geocaching.utils.offlinetranslate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import org.junit.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class TranslationTaskTest {
    @Test
    public void cancelledQueuedTaskDoesNotRun() {
        final TestScheduler scheduler = new TestScheduler();
        final CompositeDisposable owner = new CompositeDisposable();
        final List<String> events = new ArrayList<>();
        TranslationTask.schedule(owner, scheduler, scheduler, () -> {
            events.add("native");
            return "translated";
        }, events::add, error -> events.add("error"));
        owner.dispose();
        scheduler.triggerActions();
        assertThat(events).isEmpty();
        assertThat(owner.size()).isZero();
    }

    @Test
    public void cancelledCallbackIsNotDelivered() {
        final TestScheduler work = new TestScheduler();
        final TestScheduler callbacks = new TestScheduler();
        final CompositeDisposable owner = new CompositeDisposable();
        final List<String> events = new ArrayList<>();
        final Disposable task = TranslationTask.schedule(owner, work, callbacks, () -> "translated",
                events::add, error -> events.add("error"));
        work.triggerActions();
        task.dispose();
        callbacks.triggerActions();
        assertThat(events).isEmpty();
        assertThat(owner.size()).isZero();
    }

    @Test
    public void completionRemovesTaskFromOwner() {
        final CompositeDisposable owner = new CompositeDisposable();
        final List<String> events = new ArrayList<>();
        final Disposable task = TranslationTask.schedule(owner, Schedulers.trampoline(), Schedulers.trampoline(),
                () -> "translated", events::add, error -> events.add("error"));
        assertThat(events).containsExactly("translated");
        assertThat(task.isDisposed()).isTrue();
        assertThat(owner.size()).isZero();
    }

    @Test
    public void translationFailureIsDeliveredOnce() {
        final TestScheduler scheduler = new TestScheduler();
        final CompositeDisposable owner = new CompositeDisposable();
        final List<String> events = new ArrayList<>();
        TranslationTask.schedule(owner, scheduler, scheduler, () -> {
            throw new IllegalStateException("failed");
        }, events::add, error -> events.add(error.getMessage()));
        scheduler.triggerActions();
        scheduler.triggerActions();
        assertThat(events).containsExactly("failed");
        assertThat(owner.size()).isZero();
    }

    @Test
    public void disposedOwnerRejectsNewTasks() {
        final TestScheduler scheduler = new TestScheduler();
        final CompositeDisposable owner = new CompositeDisposable();
        owner.dispose();
        final List<String> events = new ArrayList<>();
        final Disposable task = TranslationTask.schedule(owner, scheduler, scheduler, () -> {
            events.add("native");
            return "translated";
        }, events::add, error -> events.add("error"));
        scheduler.triggerActions();
        assertThat(events).isEmpty();
        assertThat(task.isDisposed()).isTrue();
    }

    @Test(timeout = 10000)
    public void runningNativeCallIsNotInterrupted() throws InterruptedException {
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CompositeDisposable owner = new CompositeDisposable();
        final AtomicBoolean interrupted = new AtomicBoolean();
        final AtomicBoolean callback = new AtomicBoolean();
        try {
            TranslationTask.schedule(owner, Schedulers.from(executor), Schedulers.trampoline(), () -> {
                started.countDown();
                try {
                    release.await();
                } catch (final InterruptedException error) {
                    interrupted.set(true);
                }
                interrupted.set(interrupted.get() || Thread.currentThread().isInterrupted());
                return "translated";
            }, result -> callback.set(true), error -> callback.set(true));
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            owner.dispose();
            release.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
            assertThat(interrupted.get()).isFalse();
            assertThat(callback.get()).isFalse();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
