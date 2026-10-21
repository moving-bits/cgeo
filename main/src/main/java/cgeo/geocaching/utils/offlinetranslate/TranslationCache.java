package cgeo.geocaching.utils.offlinetranslate;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** LRU cache budgeted by estimated retained bytes, including keys and values. */
final class TranslationCache {
    private final long maxBytes;
    private final Map<String, String> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long bytes;

    TranslationCache(final long maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("Cache budget must be positive");
        }
        this.maxBytes = maxBytes;
    }

    synchronized String get(final String key) {
        return entries.get(key);
    }

    synchronized void put(final String key, final String value) {
        final String previous = entries.remove(key);
        if (previous != null) {
            bytes -= entryBytes(key, previous);
        }
        final long weight = entryBytes(key, value);
        if (weight > maxBytes) {
            return;
        }
        entries.put(key, value);
        bytes += weight;
        final Iterator<Map.Entry<String, String>> iterator = entries.entrySet().iterator();
        while (bytes > maxBytes && iterator.hasNext()) {
            final Map.Entry<String, String> eldest = iterator.next();
            bytes -= entryBytes(eldest.getKey(), eldest.getValue());
            iterator.remove();
        }
    }

    synchronized long sizeInBytes() {
        return bytes;
    }

    private static long entryBytes(final String key, final String value) {
        // Charge UTF-16 even on runtimes with compact strings, plus an allowance for the
        // two strings, backing arrays and map entry. Exact object sizes depend on ART.
        return 192L + 2L * key.length() + 2L * value.length();
    }
}
