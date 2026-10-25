package dev.beacon.application.postings;

import java.util.List;
import java.util.Map;

/**
 * A place job postings can be searched. Implementations live in infrastructure.
 * Whether a source needs a key comes from the
 * {@link dev.beacon.application.providers.ProviderCatalog}, under the same id.
 */
public interface JobSource {

    /** Stable id used in requests, e.g. "remotive". */
    String id();

    String name();

    /** One line on what the source covers. */
    String coverage();

    /** Link credited next to every result; most free feeds require it. */
    String attributionUrl();

    /**
     * Throws if the source cannot be reached; the caller reports it per source.
     *
     * @param credentials the searching user's saved fields for this source, named as in
     *                    the catalog; empty when it takes none or none are saved
     */
    List<JobPosting> search(PostingQuery query, Map<String, String> credentials);
}
