package com.walmartlabs.concord.plugins.slack;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SlackClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SlackClient.class);

    private static final String SLACK_API_ROOT = "https://slack.com/api/";
    private static final String CHAT_POST_MESSAGE_CMD = "chat.postMessage";
    private static final String CREATE_CHANNEL_CMD = "channels.create";
    private static final String CREATE_GROUP_CMD = "groups.create";
    private static final String ARCHIVE_CHANNEL_CMD = "channels.archive";
    private static final String ARCHIVE_GROUP_CMD = "groups.archive";

    private static final int TOO_MANY_REQUESTS_ERROR = 429;
    private static final int DEFAULT_RETRY_AFTER = 1;

    private final int retryCount;
    private final PoolingHttpClientConnectionManager connManager;
    private final CloseableHttpClient client;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public SlackClient(SlackConfiguration cfg) {
        this.retryCount = cfg.getRetryCount();
        this.connManager = createConnManager();
        this.client = createClient(cfg, connManager);
    }

    @Override
    public void close() throws IOException {
        client.close();
        connManager.close();
    }

    public Response message(String channelId, String text, String iconEmoji, String username, Collection<Object> attachments) throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put("channel", channelId);
        params.put("as_user", true);

        params.put("text", text);

        if (iconEmoji != null) {
            params.put("icon_emoji", iconEmoji);
            params.put("as_user", false);
        }

        if (username != null) {
            params.put("username", username);
            params.put("as_user", false);
        }

        if (attachments != null) {
            params.put("attachments", attachments);
        }

        return exec(CHAT_POST_MESSAGE_CMD, params);
    }

    public Response createChannel(String channelName) throws IOException {
        return exec(CREATE_CHANNEL_CMD, Collections.singletonMap("name", channelName));
    }

    public Response createGroupChannel(String channelName) throws IOException {
        return exec(CREATE_GROUP_CMD, Collections.singletonMap("name", channelName));
    }

    public Response archiveChannel(String channelId) throws IOException {
        return exec(ARCHIVE_CHANNEL_CMD, Collections.singletonMap("channel", channelId));
    }

    public Response archiveGroup(String channelId) throws IOException {
        return exec(ARCHIVE_GROUP_CMD, Collections.singletonMap("channel", channelId));
    }

    private Response exec(String command, Map<String, Object> params) throws IOException {
        HttpPost request = new HttpPost(SLACK_API_ROOT + command);
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(params), ContentType.APPLICATION_JSON));

        for (int i = 0; i < retryCount + 1; i++) {
            try (CloseableHttpResponse response = client.execute(request)) {
                if (response.getStatusLine().getStatusCode() == TOO_MANY_REQUESTS_ERROR) {
                    int retryAfter = getRetryAfter(response);
                    log.warn("exec ['{}', '{}'] -> too many requests, retry after {} sec", command, params, retryAfter);
                    sleep(retryAfter * 1000L);
                } else {
                    if (response.getEntity() == null) {
                        log.error("exec ['{}', '{}'] -> empty response", command, params);
                        return new Response(false, "empty response");
                    }

                    String s = EntityUtils.toString(response.getEntity());
                    Response r = objectMapper.readValue(s, Response.class);
                    log.info("exec ['{}', '{}'] -> {}", command, params, r);
                    return r;
                }
            }
        }

        return new Response(false, "too many requests");
    }

    private static int getRetryAfter(HttpResponse response) {
        Header h = response.getFirstHeader("Retry-After");
        if (h == null) {
            return DEFAULT_RETRY_AFTER;
        }

        try {
            return Integer.valueOf(h.getValue());
        } catch (Exception e) {
            log.warn("getRetryAfter -> can't parse retry value '{}'", h.getValue());
            return DEFAULT_RETRY_AFTER;
        }
    }

    private static void sleep(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static PoolingHttpClientConnectionManager createConnManager() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(new KeyManager[0], new TrustManager[]{new DefaultTrustManager()}, new SecureRandom());

            Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.INSTANCE)
                    .register("https", new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE))
                    .build();

            return new PoolingHttpClientConnectionManager(registry);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static CloseableHttpClient createClient(SlackConfiguration cfg, HttpClientConnectionManager connManager) {
        Collection<Header> headers = Collections.singleton(new BasicHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.getAuthToken()));
        return HttpClientBuilder.create()
                .setDefaultRequestConfig(createConfig(cfg))
                .setConnectionManager(connManager)
                .setDefaultHeaders(headers)
                .build();
    }

    private static RequestConfig createConfig(SlackConfiguration cfg) {
        HttpHost proxy = null;
        if (cfg.getProxyAddress() != null) {
            proxy = new HttpHost(cfg.getProxyAddress(), cfg.getProxyPort(), "http");
        }

        return RequestConfig.custom()
                .setConnectTimeout(cfg.getConnectTimeout())
                .setSocketTimeout(cfg.getSoTimeout())
                .setProxy(proxy)
                .build();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {

        private final boolean ok;
        private final String error;
        private final Map<String, Object> params = new HashMap<>();

        @JsonCreator
        public Response(@JsonProperty("ok") boolean ok,
                        @JsonProperty("error") String error) {

            this.ok = ok;
            this.error = error;
        }

        public boolean isOk() {
            return ok;
        }

        public String getError() {
            return error;
        }

        @JsonAnyGetter
        public Map<String, Object> getParams() {
            return params;
        }

        @JsonAnySetter
        public void setParams(String name, Object value) {
            params.put(name, value);
        }

        @Override
        public String toString() {
            return "Response{" +
                    "ok=" + ok +
                    ", error='" + error + '\'' +
                    '}';
        }
    }

    private static class DefaultTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException { // NOSONAR
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException { // NOSONAR
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }
}
