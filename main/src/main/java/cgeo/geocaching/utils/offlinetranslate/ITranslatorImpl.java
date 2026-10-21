package cgeo.geocaching.utils.offlinetranslate;

import java.util.function.Consumer;

import io.reactivex.rxjava3.disposables.Disposable;

public interface ITranslatorImpl {

    String getSourceLanguage();

    String getTargetLanguage();

    Disposable translate(String source, Consumer<String> onSuccess, Consumer<Exception> onError);

    /** Set before submitting requests so admission can be checked on the actual translation worker. */
    void setQueue(TranslationQueue queue);

    void dispose();
}
