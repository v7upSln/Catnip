package me.v7upf.catnip;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class UpdateChecker {
    public static boolean updateAvailable = false;
    public static String latestVersion = "";

    public static void check(JavaPlugin plugin, String repo) {
        updateAvailable = false;
        latestVersion = "";
        String current = plugin.getDescription().getVersion();
        try {
            String latest = fetchLatestRelease(repo);
            if (latest == null) {
                latest = fetchLatestTag(repo);
            }
            if (latest != null) {
                String normLatest = normalize(latest);
                String normCurrent = normalize(current);
                int cmp = compare(normLatest, normCurrent);
                if (cmp > 0) {
                    updateAvailable = true;
                    latestVersion = latest;
                    plugin.getLogger().info("Update available: " + latest + " (current " + current + ") https://github.com/" + repo + "/releases/latest");
                }
            }
        } catch (Exception e) {
            // Silent on errors
        }
    }

    private static String fetchLatestRelease(String repo) throws IOException, InterruptedException {
        String url = "https://api.github.com/repos/" + repo + "/releases/latest";
        String body = httpGet(url);
        if (body == null || body.isEmpty()) return null;
        String tag = getJsonField(body, "tag_name");
        if (tag == null) tag = getJsonField(body, "name");
        return tag;
    }

    private static String fetchLatestTag(String repo) throws IOException, InterruptedException {
        String url = "https://api.github.com/repos/" + repo + "/tags";
        String body = httpGet(url);
        if (body == null || body.isEmpty()) return null;
        return getJsonField(body, "name");
    }

    private static String httpGet(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Catnip-UpdateChecker")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.body();
        }
        return null;
    }

    private static String getJsonField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + key.length());
        if (colon < 0) return null;
        int start = -1;
        int end = -1;
        for (int i = colon + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\"') {
                start = i + 1;
                break;
            }
        }
        if (start < 0) return null;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\"') {
                end = i;
                break;
            }
        }
        if (end < 0 || end <= start) return null;
        return json.substring(start, end);
    }

    private static String normalize(String v) {
        if (v == null) return "";
        if (v.startsWith("v") || v.startsWith("V")) return v.substring(1);
        return v;
    }

    private static int compare(String v1, String v2) {
        String[] a = v1.split("\\.");
        String[] b = v2.split("\\.");
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int x = i < a.length ? parse(a[i]) : 0;
            int y = i < b.length ? parse(b[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int parse(String s) {
        String t = s.replaceAll("[^0-9]", "");
        if (t.isEmpty()) return 0;
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
