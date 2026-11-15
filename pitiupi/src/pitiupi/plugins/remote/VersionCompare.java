package pitiupi.plugins.remote;


public final class VersionCompare {
    private VersionCompare() { }

    public static int compare(String a, String b) {
        String[] x = a.trim().split("\\."), y = b.trim().split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int cmp = Integer.compare(part(x, i), part(y, i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private static int part(String[] p, int i) {
        if (i >= p.length) {
            return 0;
        }
        String digits = p[i].replaceAll("^(\\d*).*$", "$1");   // "3-beta" -> "3"
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }
}
