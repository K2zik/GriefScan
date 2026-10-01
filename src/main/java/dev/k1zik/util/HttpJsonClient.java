package dev.k1zik.util;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public final class HttpJsonClient {
   public static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
   public static final int DEFAULT_READ_TIMEOUT_MS = 5000;
   public static final String USER_AGENT = "GriefScan-Plugin";

   private HttpJsonClient() {
   }

   public static int post(String url, String jsonBody) throws IOException {
      return post(url, jsonBody, DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
   }

   public static int post(String url, String jsonBody, int connectTimeoutMs, int readTimeoutMs) throws IOException {
      HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
      try {
         connection.setRequestMethod("POST");
         connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
         connection.setRequestProperty("User-Agent", USER_AGENT);
         connection.setConnectTimeout(connectTimeoutMs);
         connection.setReadTimeout(readTimeoutMs);
         connection.setDoOutput(true);

         byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
         try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(bytes);
         }
         return connection.getResponseCode();
      } finally {
         connection.disconnect();
      }
   }
}
