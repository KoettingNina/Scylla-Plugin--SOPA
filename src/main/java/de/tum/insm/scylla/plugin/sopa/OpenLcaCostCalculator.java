package de.tum.insm.scylla.plugin.sopa;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

public class OpenLcaCostCalculator {
    private final HttpClient httpClient;
    private final String ipcServerUrl;

    public OpenLcaCostCalculator(String ipcServerBaseUrl) {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.ipcServerUrl = ipcServerBaseUrl + (ipcServerBaseUrl.endsWith("/") ? "" : "/") + "json-rpc";
    }

    public String calculateCostViaBridge(
            String productSystemId,
            String impactMethodId,
            String normalizationSetId,
            double amount) throws Exception {
        try {
            double totalScore = calculateSingleScore(productSystemId, impactMethodId, normalizationSetId, amount);
            String responseJson = String.format("{\"success\":true,\"cost\":%.6f}", totalScore);
            return responseJson;
        } catch (Exception e) {
            System.err.println("[ERROR] Exception in calculateCost: " + e.getMessage());
            e.printStackTrace();
            String errorJson = String.format("{\"success\":false,\"error\":\"%s\"}", e.getMessage().replace("\"", "\\\""));
            return errorJson;
        }
    }

    private double calculateSingleScore(
            String productSystemId,
            String impactMethodId,
            String normalizationSetId,
            double amount) throws Exception {
        if (productSystemId == null) {
            System.err.println("[ERROR] productSystemId is null!");
        }
        if (impactMethodId == null) {
            System.err.println("[ERROR] impactMethodId is null!");
        }
        if (amount == 0.0) {
            System.err.println("[WARN] amount is 0.0!");
        }
        // Schritt 1: Berechnung starten
        StringBuilder params = new StringBuilder();
        params.append("\"target\":{\"@type\":\"ProductSystem\",\"@id\":\"").append(productSystemId).append("\"},");
        params.append("\"impactMethod\":{\"@type\":\"ImpactMethod\",\"@id\":\"").append(impactMethodId).append("\"},");
        if (normalizationSetId != null) {
            params.append("\"nwSet\":{\"@type\":\"NwSet\",\"@id\":\"").append(normalizationSetId).append("\"},");
        }
        params.append("\"amount\":").append(amount).append(",");
        params.append("\"allocation\":\"USE_DEFAULT_ALLOCATION\",");
        params.append("\"withCosts\":false,");
        params.append("\"withRegionalization\":false");
        String paramStr = params.toString();
        if (paramStr.endsWith(",")) paramStr = paramStr.substring(0, paramStr.length()-1);
        String calculateRequest = String.format("{\"jsonrpc\":\"2.0\",\"method\":\"result/calculate\",\"params\":{%s},\"id\":\"%s\"}", paramStr, UUID.randomUUID());
        String calcResponse = sendRpcRequest(calculateRequest);
        String resultId = extractJsonField(calcResponse, "@id");

        // Schritt 1b: Warte auf Ergebnis
        boolean isReady = Boolean.parseBoolean(extractJsonField(calcResponse, "isReady"));
        int maxAttempts = 30;
        int attempt = 0;
        while (!isReady && attempt < maxAttempts) {
            Thread.sleep(1000);
            String statusRequest = String.format("{\"jsonrpc\":\"2.0\",\"method\":\"result/state\",\"params\":{\"@id\":\"%s\"},\"id\":\"%s\"}", resultId, UUID.randomUUID());
            String statusResponse = sendRpcRequest(statusRequest);
            isReady = Boolean.parseBoolean(extractJsonField(statusResponse, "isReady"));
            attempt++;
        }
        if (!isReady) {
            throw new RuntimeException("Berechnung nicht innerhalb der Timeout-Zeit abgeschlossen");
        }

        // Schritt 2: Ergebnisse abrufen
        String getImpactsRequest = String.format("{\"jsonrpc\":\"2.0\",\"method\":\"result/total-impacts/weighted\",\"params\":{\"@id\":\"%s\"},\"id\":\"%s\"}", resultId, UUID.randomUUID());
        String impactsResponse = sendRpcRequest(getImpactsRequest);
        double totalScore = sumAmountsFromWeightedImpacts(impactsResponse);

        // Schritt 4: Aufräumen
        String disposeRequest = String.format("{\"jsonrpc\":\"2.0\",\"method\":\"result/dispose\",\"params\":{\"@id\":\"%s\"},\"id\":\"%s\"}", resultId, UUID.randomUUID());
        sendRpcRequest(disposeRequest);

        return totalScore;
    }

    private String sendRpcRequest(String jsonPayload) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(this.ipcServerUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.body();
        } else {
            String errorMsg = String.format("IPC-Server Fehler %d: %s", response.statusCode(), response.body());
            System.err.println("[OpenLCA Calculator] " + errorMsg);
            throw new RuntimeException(errorMsg);
        }
    }

    private String extractJsonField(String json, String field) {
        String search = "\"" + field + "\":";
        int idx = json.indexOf(search);
        if (idx == -1) return null;
        int start = idx + search.length();
        // Überspringe evtl. Leerzeichen
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        char c = json.charAt(start);
        if (c == '"') {
            int end = json.indexOf('"', start + 1);
            return json.substring(start + 1, end);
        } else if (c == 't' && json.startsWith("true", start)) {
            return "true";
        } else if (c == 'f' && json.startsWith("false", start)) {
            return "false";
        } else {
            int end = json.indexOf(',', start);
            if (end == -1) end = json.indexOf('}', start);
            if (end == -1) end = json.length();
            return json.substring(start, end).replaceAll("[^0-9.eE-]", "").trim();
        }
    }

    private double sumAmountsFromWeightedImpacts(String json) {
        double sum = 0.0;
        int idx = 0;
        while ((idx = json.indexOf("\"amount\":", idx)) != -1) {
            int start = idx + "\"amount\":".length();
            int end = json.indexOf(',', start);
            if (end == -1) end = json.indexOf('}', start);
            if (end == -1) end = json.length();
            String value = json.substring(start, end).replaceAll("[^0-9.eE-]", "").trim();
            try {
                sum += Double.parseDouble(value);
            } catch (Exception ignored) {}
            idx = end;
        }
        return sum;
    }

} 