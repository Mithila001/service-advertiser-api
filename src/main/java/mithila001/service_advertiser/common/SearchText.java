package mithila001.service_advertiser.common;

import java.util.Locale;

public final class SearchText {
    private SearchText() {}
    public static String likePattern(String text) {
        // ponytail: leading-wildcard scans suit the portfolio catalog; add PostgreSQL trigram indexes if growth warrants them.
        return "%" + text.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
