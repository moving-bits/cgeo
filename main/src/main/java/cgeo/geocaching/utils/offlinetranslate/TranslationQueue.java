package cgeo.geocaching.utils.offlinetranslate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

/** Admission gate owned by one activity instance, initially paused until its first resume. */
public final class TranslationQueue implements Disposable {
    private final CompositeDisposable tasks = new CompositeDisposable();
    private final Set<TranslationTask> deferred = new LinkedHashSet<>();
    private boolean paused = true;
    private boolean disposed;

    boolean add(final TranslationTask task) {
        if (!tasks.add(task)) {
            return false;
        }
        // The request's session may have cancelled it between owner and queue registration.
        if (task.isDisposed()) {
            remove(task);
            return false;
        }
        return true;
    }

    void remove(final TranslationTask task) {
        synchronized (this) {
            deferred.remove(task);
        }
        tasks.delete(task);
    }

    synchronized Callable<String> startOrDefer(final TranslationTask task) {
        if (disposed) {
            return null;
        }
        if (paused) {
            if (!task.isDisposed()) {
                deferred.add(task);
            }
            return null;
        }
        // Claiming the action and pausing are serialized; JNI itself never holds this lock.
        return task.claim();
    }

    public synchronized void pause() {
        paused = true;
    }

    public void resume() {
        final List<TranslationTask> pending;
        synchronized (this) {
            if (disposed) {
                return;
            }
            paused = false;
            pending = new ArrayList<>(deferred);
            deferred.clear();
        }
        // Resubmit to each task's worker, never run native translation on the lifecycle thread.
        for (TranslationTask task : pending) {
            task.scheduleWork(0, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void dispose() {
        synchronized (this) {
            disposed = true;
            deferred.clear();
        }
        // TranslationTask.dispose() leaves already running JNI calls uninterrupted.
        tasks.dispose();
    }

    @Override
    public synchronized boolean isDisposed() {
        return disposed;
    }
}
