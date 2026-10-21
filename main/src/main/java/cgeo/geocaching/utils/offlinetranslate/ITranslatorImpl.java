package cgeo.geocaching.utils.offlinetranslate;

import java.util.function.Consumer;

import io.reactivex.rxjava3.disposables.Disposable;

public interface ITranslatorImpl {

    String getSourceLanguage();

    String getTargetLanguage();

    Disposable translate(String source, Consumer<String> onSuccess, Consumer<Exception> onError);

    void dispose();
}
