package com.testdata.model;

import jakarta.validation.constraints.*;
import java.util.Map;

/**
 * Request object for test data generation API.
 * 
 * Defines the schema, count, business rules, and QA parameters needed to generate test data.
 * All generated records will follow the schema structure and include computed fields.
 * 
 * @see com.testdata.service.DataGeneratorService
 */
public class GenerateRequest {
    /**
     * Number of records to generate (1-100,000).
     * Validated using @Min and @Max constraints.
     */
    @NotNull @Min(1) @Max(100000)
    private Integer count;

    /**
     * Schema definition as a Map.
     * Keys are field names; values can be:
     *   - String (simple types): "uuid", "email", "firstname", etc.
     *   - Map (complex types): {"type": "number", "min": 0, "max": 100}
     * 
     * Maps and nested arrays support object compositions like:
     *   {"type": "array", "minItems": 1, "maxItems": 5, "schema": {...}}
     */
    @NotNull
    private Map<String, Object> schema;

    /**
     * Computed fields definition.
     * Map of field names to expressions that calculate derived values.
     * 
     * Supported expressions:
     *   - Arithmetic: "price * 2", "total - discount"
     *   - Conditional: "if(amount > 100, 0, 15)"
     *   - Array sum: "sum(items[].price * items[].quantity)"
     * 
     * These fields are added to each generated record.
     */
    private Map<String, String> computedFields;

    /**
     * QA Mode configuration for edge case generation.
     * When enabled, generates edge cases (minimum, maximum values) in addition to normal records.
     */
    private QAMode qaMode;

    /**
     * Output format (currently unused; reserved for future enhancements).
     * Default: "json"
     */
    private String format = "json";

    /**
     * Random seed for reproducible data generation.
     * Use the same seed to regenerate identical test data.
     * If null, a new random seed is used each time.
     */
    private Long seed;

    // Getters and Setters
    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }
    
    public Map<String, Object> getSchema() { return schema; }
    public void setSchema(Map<String, Object> schema) { this.schema = schema; }
    
    public Map<String, String> getComputedFields() { return computedFields; }
    public void setComputedFields(Map<String, String> computedFields) { this.computedFields = computedFields; }
    
    public QAMode getQaMode() { return qaMode; }
    public void setQaMode(QAMode qaMode) { this.qaMode = qaMode; }
    
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    
    public Long getSeed() { return seed; }
    public void setSeed(Long seed) { this.seed = seed; }
}