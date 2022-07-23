package online.cszt0.pb.utils;

public class FilterUtils {
    public static boolean containsKeyword(String keyword, String... values) {
        String[] keywords = keyword.split("(\\s|\\b)+");
        if (keywords.length == 0) {
            return true;
        }
        cmp:
        for (String val : values) {
            if (val == null) {
                continue;
            }
            for (String kw : keywords) {
                if (!containsKeyword(kw, val)) {
                    continue cmp;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean containsKeyword(String keyword, String val) {
        for (int i = 0, end = val.length() - keyword.length(); i <= end; i++) {
            if (keyword.equalsIgnoreCase(val.substring(i, i + keyword.length()))) {
                return true;
            }
        }
        return false;
    }
}
