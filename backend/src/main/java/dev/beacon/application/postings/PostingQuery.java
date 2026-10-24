package dev.beacon.application.postings;

/**
 * @param keywords   whitespace-separated terms; every one must match
 * @param location   free text such as "USA" or "Denver"; blank for anywhere
 * @param remoteOnly only return remote roles
 */
public record PostingQuery(String keywords, String location, boolean remoteOnly) {

    public PostingQuery {
        keywords = keywords == null ? "" : keywords.strip();
        location = location == null ? "" : location.strip();
    }
}
