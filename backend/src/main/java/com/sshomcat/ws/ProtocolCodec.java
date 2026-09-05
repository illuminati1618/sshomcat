package com.sshomcat.ws;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.util.Base64;

/**
 * Parses/serializes the JSON-text-frame protocol in docs/api.md. Isolated from the WebSocket
 * endpoint so it's plain, dependency-free-of-Session, and unit-testable -- this is the one piece
 * of M1 logic that's pure and directly handles untrusted input, so it's worth testing before M2.
 */
public final class ProtocolCodec {

    private ProtocolCodec() {
    }

    public static InboundMessage parseInbound(String raw) throws ProtocolException {
        JsonObject obj;
        try {
            obj = Json.createReader(new StringReader(raw)).readObject();
        } catch (JsonException | ClassCastException | IllegalStateException e) {
            throw new ProtocolException("malformed JSON message");
        }

        String type = stringField(obj, "type");
        if (type == null) {
            throw new ProtocolException("missing 'type' field");
        }

        return switch (type) {
            case "auth" -> {
                String username = requireString(obj, "username");
                String password = requireString(obj, "password");
                yield new InboundMessage.Auth(username, password);
            }
            case "data" -> {
                String data = requireString(obj, "data");
                validateBase64(data);
                yield new InboundMessage.Data(data);
            }
            case "resize" -> {
                int cols = requireIntInRange(obj, "cols", 1, 10_000);
                int rows = requireIntInRange(obj, "rows", 1, 10_000);
                yield new InboundMessage.Resize(cols, rows);
            }
            default -> throw new ProtocolException("unknown message type: " + type);
        };
    }

    public static byte[] decodeData(InboundMessage.Data data) throws ProtocolException {
        try {
            return Base64.getDecoder().decode(data.base64Data());
        } catch (IllegalArgumentException e) {
            throw new ProtocolException("invalid base64 payload");
        }
    }

    public static String connected() {
        return Json.createObjectBuilder().add("type", "connected").build().toString();
    }

    public static String data(byte[] bytes) {
        String b64 = Base64.getEncoder().encodeToString(bytes);
        return Json.createObjectBuilder().add("type", "data").add("data", b64).build().toString();
    }

    /**
     * @param message human-readable reason. Must already be safe to show a browser (docs/security.md:
     *                never forward raw SSH/library exception text -- it can leak internal detail).
     */
    public static String error(String message) {
        return Json.createObjectBuilder().add("type", "error").add("message", message).build().toString();
    }

    private static String stringField(JsonObject obj, String key) {
        JsonValue v = obj.get(key);
        if (v == null || v.getValueType() != JsonValue.ValueType.STRING) {
            return null;
        }
        return obj.getString(key);
    }

    private static String requireString(JsonObject obj, String key) throws ProtocolException {
        String v = stringField(obj, key);
        if (v == null) {
            throw new ProtocolException("missing or non-string field: " + key);
        }
        return v;
    }

    private static int requireIntInRange(JsonObject obj, String key, int min, int max) throws ProtocolException {
        JsonValue v = obj.get(key);
        if (v == null || v.getValueType() != JsonValue.ValueType.NUMBER) {
            throw new ProtocolException("missing or non-numeric field: " + key);
        }
        int i = ((jakarta.json.JsonNumber) v).intValue();
        if (i < min || i > max) {
            throw new ProtocolException("field '" + key + "' out of range: " + i);
        }
        return i;
    }

    private static void validateBase64(String s) throws ProtocolException {
        try {
            Base64.getDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            throw new ProtocolException("invalid base64 payload");
        }
    }
}
