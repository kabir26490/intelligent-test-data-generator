package com.testdata.service;

import io.swagger.v3.oas.models.media.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Converts OpenAPI Schema objects into the generator's internal schema format.
 * Handles $ref resolution (via pre-resolved models), allOf/oneOf/anyOf merging,
 * nested objects, arrays, enums, and format-based type inference.
 */
@Component
public class OpenApiSchemaConverter {

    private static final int MAX_DEPTH = 20;

    /**
     * Convert a resolved OpenAPI Schema into our generator schema map.
     *
     * @param schema the OpenAPI Schema (already $ref-resolved by swagger-parser)
     * @return a Map representing the generator schema
     */
    public Map<String, Object> convert(Schema<?> schema) {
        if (schema == null) return Collections.emptyMap();

        // If the top-level schema is an object with properties, convert each property
        Map<String, Schema> properties = schema.getProperties();
        if (properties != null && !properties.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            Set<String> required = schema.getRequired() != null
                    ? new HashSet<>(schema.getRequired()) : Collections.emptySet();
            for (Map.Entry<String, Schema> entry : properties.entrySet()) {
                result.put(entry.getKey(), convertProperty(entry.getKey(), entry.getValue(), required, 0));
            }
            return result;
        }

        // If it's an array at the top level
        if (schema instanceof ArraySchema || "array".equals(schema.getType())) {
            Schema<?> items = schema.getItems();
            if (items != null && items.getProperties() != null) {
                Map<String, Object> result = new LinkedHashMap<>();
                Set<String> required = items.getRequired() != null
                        ? new HashSet<>(items.getRequired()) : Collections.emptySet();
                for (Map.Entry<String, Schema> entry : items.getProperties().entrySet()) {
                    result.put(entry.getKey(), convertProperty(entry.getKey(), entry.getValue(), required, 0));
                }
                return result;
            }
        }

        // Composed schemas (allOf/oneOf/anyOf) at top level
        Schema<?> merged = mergeComposedSchema(schema);
        if (merged != null && merged != schema) {
            return convert(merged);
        }

        return Collections.emptyMap();
    }

    /**
     * Convert a single property schema into a generator field definition.
     */
    @SuppressWarnings("rawtypes")
    private Object convertProperty(String fieldName, Schema<?> prop, Set<String> required, int depth) {
        if (prop == null || depth > MAX_DEPTH) return "companyname";

        // Handle composed schemas (allOf, oneOf, anyOf)
        Schema<?> resolved = mergeComposedSchema(prop);
        if (resolved != null && resolved != prop) {
            return convertProperty(fieldName, resolved, required, depth);
        }

        // Enum — use our enum type
        List<?> enumValues = prop.getEnum();
        if (enumValues != null && !enumValues.isEmpty()) {
            List<String> values = new ArrayList<>();
            for (Object v : enumValues) {
                values.add(v != null ? v.toString() : "");
            }
            Map<String, Object> enumDef = new LinkedHashMap<>();
            enumDef.put("type", "enum");
            enumDef.put("values", values);
            return enumDef;
        }

        String type = prop.getType();
        String format = prop.getFormat();

        // Object with properties → nested object
        if ("object".equals(type) || (type == null && prop.getProperties() != null && !prop.getProperties().isEmpty())) {
            Map<String, Schema> childProps = prop.getProperties();
            if (childProps == null || childProps.isEmpty()) return "companyname";
            Map<String, Object> nestedSchema = new LinkedHashMap<>();
            Set<String> childRequired = prop.getRequired() != null
                    ? new HashSet<>(prop.getRequired()) : Collections.emptySet();
            for (Map.Entry<String, Schema> entry : childProps.entrySet()) {
                nestedSchema.put(entry.getKey(), convertProperty(entry.getKey(), entry.getValue(), childRequired, depth + 1));
            }
            Map<String, Object> objDef = new LinkedHashMap<>();
            objDef.put("type", "object");
            objDef.put("schema", nestedSchema);
            return objDef;
        }

        // Array
        if ("array".equals(type) || prop instanceof ArraySchema) {
            Schema<?> items = prop.getItems();
            Integer minItems = prop.getMinItems();
            Integer maxItems = prop.getMaxItems();
            int min = minItems != null ? minItems : 1;
            int max = maxItems != null ? maxItems : 3;

            Map<String, Object> arrayDef = new LinkedHashMap<>();
            arrayDef.put("type", "array");
            arrayDef.put("minItems", min);
            arrayDef.put("maxItems", max);

            if (items != null) {
                Map<String, Schema> itemProps = items.getProperties();
                if (itemProps != null && !itemProps.isEmpty()) {
                    // Array of objects
                    Map<String, Object> itemSchema = new LinkedHashMap<>();
                    Set<String> itemRequired = items.getRequired() != null
                            ? new HashSet<>(items.getRequired()) : Collections.emptySet();
                    for (Map.Entry<String, Schema> entry : itemProps.entrySet()) {
                        itemSchema.put(entry.getKey(), convertProperty(entry.getKey(), entry.getValue(), itemRequired, depth + 1));
                    }
                    arrayDef.put("schema", itemSchema);
                } else {
                    // Array of primitives
                    Map<String, Object> itemSchema = new LinkedHashMap<>();
                    itemSchema.put("value", convertProperty("value", items, Collections.emptySet(), depth + 1));
                    arrayDef.put("schema", itemSchema);
                }
            } else {
                arrayDef.put("schema", Map.of("value", "companyname"));
            }
            return arrayDef;
        }

        // String types
        if ("string".equals(type)) {
            return convertStringProperty(fieldName, format, prop);
        }

        // Integer types
        if ("integer".equals(type)) {
            Map<String, Object> numDef = new LinkedHashMap<>();
            numDef.put("type", "number");
            numDef.put("min", resolveNumberBound(prop.getMinimum(), 0));
            numDef.put("max", resolveNumberBound(prop.getMaximum(), 1000));
            return numDef;
        }

        // Number types (floating)
        if ("number".equals(type)) {
            Map<String, Object> decDef = new LinkedHashMap<>();
            decDef.put("type", "decimal");
            decDef.put("min", resolveDoubleBound(prop.getMinimum(), 0.0));
            decDef.put("max", resolveDoubleBound(prop.getMaximum(), 1000.0));
            decDef.put("decimals", 2);
            return decDef;
        }

        // Boolean
        if ("boolean".equals(type)) {
            Map<String, Object> boolDef = new LinkedHashMap<>();
            boolDef.put("type", "boolean");
            return boolDef;
        }

        // Fallback: try to infer from field name
        return inferFromFieldName(fieldName);
    }

    /**
     * Convert a string-type OpenAPI property into the appropriate generator type.
     */
    private Object convertStringProperty(String fieldName, String format, Schema<?> prop) {
        if (format != null) {
            switch (format.toLowerCase()) {
                case "uuid":       return "uuid";
                case "email":      return "email";
                case "date-time":  return "datetime";
                case "date":       return "date";
                case "uri":
                case "url":        return "uuid"; // generates unique identifier
                case "phone":      return "phone";
            }
        }

        // Infer from field name
        return inferFromFieldName(fieldName);
    }

    /**
     * Infer generator type from field name alone (used when format is absent or type is unknown).
     */
    private Object inferFromFieldName(String fieldName) {
        String key = fieldName.toLowerCase();

        if (key.contains("email"))                                        return "email";
        if (key.contains("phone") || key.contains("mobile"))             return "phone";
        if (key.contains("firstname") || key.contains("first_name"))     return "firstname";
        if (key.contains("lastname") || key.contains("last_name"))       return "lastname";
        if (key.contains("description") || key.contains("note") || key.contains("comment")) return "sentence";
        if (key.contains("name"))                                        return "fullName";
        if (key.contains("company") || key.contains("org"))              return "companyname";
        if (key.contains("product") || key.contains("item"))             return "productname";
        if (key.contains("date") || key.contains("time"))                return "datetime";
        if (key.contains("href") || key.contains("url") || key.contains("uri") || key.contains("link")) return "uuid";
        if (key.contains("id"))                                          return "uuid";
        if (key.contains("amount") || key.contains("price") || key.contains("cost") || key.contains("total")) {
            Map<String, Object> dec = new LinkedHashMap<>();
            dec.put("type", "decimal");
            dec.put("min", 0.0);
            dec.put("max", 1000.0);
            dec.put("decimals", 2);
            return dec;
        }

        return "companyname";
    }

    /**
     * Merge allOf/oneOf/anyOf schemas into a single synthetic schema.
     * Returns null if the schema has no composed parts.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Schema<?> mergeComposedSchema(Schema<?> schema) {
        List<Schema> subSchemas = null;

        if (schema instanceof ComposedSchema) {
            ComposedSchema composed = (ComposedSchema) schema;
            if (composed.getAllOf() != null && !composed.getAllOf().isEmpty()) {
                subSchemas = composed.getAllOf();
            } else if (composed.getOneOf() != null && !composed.getOneOf().isEmpty()) {
                subSchemas = List.of(composed.getOneOf().get(0)); // pick first variant
            } else if (composed.getAnyOf() != null && !composed.getAnyOf().isEmpty()) {
                subSchemas = List.of(composed.getAnyOf().get(0)); // pick first variant
            }
        } else {
            // Newer swagger-parser versions put allOf directly on Schema
            if (schema.getAllOf() != null && !schema.getAllOf().isEmpty()) {
                subSchemas = schema.getAllOf();
            } else if (schema.getOneOf() != null && !schema.getOneOf().isEmpty()) {
                subSchemas = List.of(schema.getOneOf().get(0));
            } else if (schema.getAnyOf() != null && !schema.getAnyOf().isEmpty()) {
                subSchemas = List.of(schema.getAnyOf().get(0));
            }
        }

        if (subSchemas == null || subSchemas.isEmpty()) return null;

        // Merge all sub-schemas into one synthetic schema
        ObjectSchema merged = new ObjectSchema();
        Map<String, Schema> mergedProps = new LinkedHashMap<>();
        List<String> mergedRequired = new ArrayList<>();

        for (Schema sub : subSchemas) {
            if (sub.getProperties() != null) {
                mergedProps.putAll(sub.getProperties());
            }
            if (sub.getRequired() != null) {
                mergedRequired.addAll(sub.getRequired());
            }
        }

        if (mergedProps.isEmpty()) return null;

        merged.setProperties(mergedProps);
        if (!mergedRequired.isEmpty()) {
            merged.setRequired(mergedRequired);
        }
        return merged;
    }

    private int resolveNumberBound(BigDecimal value, int defaultValue) {
        return value != null ? value.intValue() : defaultValue;
    }

    private double resolveDoubleBound(BigDecimal value, double defaultValue) {
        return value != null ? value.doubleValue() : defaultValue;
    }
}
