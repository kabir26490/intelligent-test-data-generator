package com.testdata.service;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for parsing OpenAPI specs and extracting operation metadata + schemas.
 */
@Service
public class OpenApiService {

    private final OpenApiSchemaConverter schemaConverter;

    public OpenApiService(OpenApiSchemaConverter schemaConverter) {
        this.schemaConverter = schemaConverter;
    }

    /**
     * Parse an OpenAPI spec (YAML or JSON string) and return the resolved model.
     *
     * @param specContent the raw spec content
     * @return parsed OpenAPI model
     * @throws IllegalArgumentException if the spec cannot be parsed
     */
    public OpenAPI parseSpec(String specContent) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);       // resolve $ref
        options.setResolveFully(true);  // inline all references

        SwaggerParseResult result = new OpenAPIV3Parser().readContents(specContent, null, options);

        if (result.getOpenAPI() == null) {
            String errors = result.getMessages() != null ? String.join("; ", result.getMessages()) : "Unknown parse error";
            throw new IllegalArgumentException("Invalid OpenAPI spec: " + errors);
        }

        return result.getOpenAPI();
    }

    /**
     * List all operations in a parsed OpenAPI spec.
     * Each operation includes method, path, operationId (or generated key), and summary.
     *
     * @param spec the parsed OpenAPI model
     * @return list of operation info maps
     */
    public List<Map<String, String>> listOperations(OpenAPI spec) {
        List<Map<String, String>> operations = new ArrayList<>();

        if (spec.getPaths() == null) return operations;

        for (Map.Entry<String, PathItem> pathEntry : spec.getPaths().entrySet()) {
            String path = pathEntry.getKey();
            PathItem pathItem = pathEntry.getValue();

            addOperation(operations, "GET",     path, pathItem.getGet());
            addOperation(operations, "POST",    path, pathItem.getPost());
            addOperation(operations, "PUT",     path, pathItem.getPut());
            addOperation(operations, "PATCH",   path, pathItem.getPatch());
            addOperation(operations, "DELETE",  path, pathItem.getDelete());
            addOperation(operations, "HEAD",    path, pathItem.getHead());
            addOperation(operations, "OPTIONS", path, pathItem.getOptions());
        }

        return operations;
    }

    private void addOperation(List<Map<String, String>> list, String method, String path, Operation op) {
        if (op == null) return;

        Map<String, String> info = new LinkedHashMap<>();
        info.put("method", method);
        info.put("path", path);
        info.put("operationId", op.getOperationId() != null ? op.getOperationId() : method + " " + path);
        info.put("summary", op.getSummary() != null ? op.getSummary() : "");

        // Indicate whether request body and response schemas exist
        info.put("hasRequestBody", (op.getRequestBody() != null) ? "true" : "false");

        boolean hasResponse = false;
        if (op.getResponses() != null) {
            for (ApiResponse resp : op.getResponses().values()) {
                if (resp.getContent() != null) { hasResponse = true; break; }
            }
        }
        info.put("hasResponseBody", hasResponse ? "true" : "false");

        list.add(info);
    }

    /**
     * Extract and convert the schema for a specific operation.
     *
     * @param spec        the parsed OpenAPI model
     * @param operationId the operationId to find
     * @param direction   "request" or "response"
     * @param count       number of records to generate
     * @return a generator-ready request payload with count + schema
     */
    public Map<String, Object> extractSchema(OpenAPI spec, String operationId, String direction, int count) {
        Operation op = findOperation(spec, operationId);
        if (op == null) {
            throw new IllegalArgumentException("Operation not found: " + operationId);
        }

        Schema<?> rawSchema;
        if ("request".equalsIgnoreCase(direction)) {
            rawSchema = extractRequestBodySchema(op);
        } else {
            rawSchema = extractResponseSchema(op);
        }

        if (rawSchema == null) {
            throw new IllegalArgumentException("No " + direction + " schema found for operation: " + operationId);
        }

        Map<String, Object> generatorSchema = schemaConverter.convert(rawSchema);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("count", Math.max(1, Math.min(count, 100000)));
        payload.put("schema", generatorSchema);
        return payload;
    }

    /**
     * Find an operation by operationId across all paths and methods.
     */
    private Operation findOperation(OpenAPI spec, String operationId) {
        if (spec.getPaths() == null || operationId == null) return null;

        for (Map.Entry<String, PathItem> pathEntry : spec.getPaths().entrySet()) {
            String path = pathEntry.getKey();
            PathItem item = pathEntry.getValue();

            Operation[] ops = { item.getGet(), item.getPost(), item.getPut(),
                    item.getPatch(), item.getDelete(), item.getHead(), item.getOptions() };
            String[] methods = { "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS" };

            for (int i = 0; i < ops.length; i++) {
                if (ops[i] == null) continue;
                String id = ops[i].getOperationId() != null ? ops[i].getOperationId() : methods[i] + " " + path;
                if (operationId.equals(id)) return ops[i];
            }
        }
        return null;
    }

    /**
     * Extract the JSON schema from an operation's request body.
     */
    @SuppressWarnings("rawtypes")
    private Schema<?> extractRequestBodySchema(Operation op) {
        RequestBody body = op.getRequestBody();
        if (body == null) return null;
        return extractJsonSchema(body.getContent());
    }

    /**
     * Extract the JSON schema from the first successful response (2xx).
     */
    @SuppressWarnings("rawtypes")
    private Schema<?> extractResponseSchema(Operation op) {
        ApiResponses responses = op.getResponses();
        if (responses == null) return null;

        // Try 200, 201, then default, then first available
        for (String code : new String[]{ "200", "201", "default" }) {
            ApiResponse resp = responses.get(code);
            if (resp != null && resp.getContent() != null) {
                Schema<?> schema = extractJsonSchema(resp.getContent());
                if (schema != null) return schema;
            }
        }

        // Fallback: first response with content
        for (ApiResponse resp : responses.values()) {
            if (resp.getContent() != null) {
                Schema<?> schema = extractJsonSchema(resp.getContent());
                if (schema != null) return schema;
            }
        }

        return null;
    }

    /**
     * Extract schema from Content, preferring application/json.
     */
    @SuppressWarnings("rawtypes")
    private Schema<?> extractJsonSchema(Content content) {
        if (content == null) return null;

        // Prefer application/json
        MediaType jsonMedia = content.get("application/json");
        if (jsonMedia != null && jsonMedia.getSchema() != null) {
            return jsonMedia.getSchema();
        }

        // Fallback: first media type with a schema
        for (MediaType mt : content.values()) {
            if (mt.getSchema() != null) return mt.getSchema();
        }

        return null;
    }
}
