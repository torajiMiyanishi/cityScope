public class Utils {

    public static class Csv{
        // 安全にint型に変換、空文字やnullの場合はデフォルト値を返す
        public static int parseIntOrDefault(String value, int defaultValue) {
            if (value == null || value.isEmpty()) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        // 安全にdouble型に変換、空文字やnullの場合はデフォルト値を返す
        public static double parseDoubleOrDefault(String value, double defaultValue) {
            if (value == null || value.isEmpty()) {
                return defaultValue;
            }
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
    }
}
