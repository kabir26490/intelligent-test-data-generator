package com.testdata.service;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.*;

/**
 * Evaluates and applies business rule expressions to generated records.
 * 
 * Supports three types of expressions:
 * 1. Arithmetic: "price * 2", "total - discount"
 * 2. Conditional: "if(amount > 100, 0, 15)"
 * 3. Array aggregation: "sum(items[].price * items[].quantity)"
 * 
 * LIMITATIONS:
 * - Only basic operators supported: +, -, *, /
 * - No nested function calls or complex precedence
 * - Expressions must be well-formed; no error recovery
 * - No support for logical operators (&&, ||) yet
 */
@Service
public class BusinessRuleEngine {

    /**
     * Apply all computed field expressions to a record.
     * 
     * @param record The Map to augment with computed values
     * @param computedFields Map of field name to expression string
     */
    public void applyComputedFields(Map<String, Object> record, Map<String, String> computedFields) {
        for (Map.Entry<String, String> entry : computedFields.entrySet()) {
            record.put(entry.getKey(), evaluateExpression(entry.getValue(), record));
        }
    }

    /**
     * Dispatch expression evaluation based on type.
     * 
     * @param expression The expression string to evaluate
     * @param record The current record context
     * @return Computed value
     */
    private Object evaluateExpression(String expression, Map<String, Object> record) {
        if (expression.contains("sum(")) return evaluateSumExpression(expression, record);
        if (expression.startsWith("if(")) return evaluateConditional(expression, record);
        return evaluateArithmetic(expression, record);
    }

    /**
     * Evaluate array sum expression: sum(array[].field1 * array[].field2)
     * 
     * Computes: Sum of (field1 * field2) for all items in array
     * Result is rounded to 2 decimal places for financial accuracy.
     * 
     * @param expression Expression like "sum(items[].price * items[].quantity)"
     * @param record The record containing the array
     * @return Double sum value, or 0.0 if parsing fails
     */
    private Object evaluateSumExpression(String expression, Map<String, Object> record) {
        // Extract array name and field names from pattern: sum(ARRAY[].FIELD1 * ARRAY[].FIELD2)
        Pattern pattern = Pattern.compile("sum\\((.+?)\\[\\]\\.(.+?)\\s*\\*\\s*(.+?)\\[\\]\\.(.+?)\\)");
        Matcher matcher = pattern.matcher(expression);
        if (matcher.find()) {
            String arrayField = matcher.group(1);
            String field1 = matcher.group(2);
            String field2 = matcher.group(4);
            Object arrayObj = record.get(arrayField);
            
            // Iterate array and accumulate product of field1 * field2
            if (arrayObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) arrayObj;
                double sum = 0;
                for (Map<String, Object> item : items) {
                    sum += getDoubleValue(item.get(field1)) * getDoubleValue(item.get(field2));
                }
                // Round to 2 decimal places for financial data
                return Math.round(sum * 100.0) / 100.0;
            }
        }
        return 0.0;
    }

    /**
     * Evaluate conditional expression: if(field > value, true_val, false_val)
     * 
     * Supports operators: >, <, >=, <=, ==, !=
     * Returns true_val or false_val based on condition.
     * 
     * @param expression Expression like "if(subtotal > 100, 0, 15)"
     * @param record The record context
     * @return Result of true or false branch
     */
    private Object evaluateConditional(String expression, Map<String, Object> record) {
        // Pattern: if(LEFT_OPERAND OPERATOR RIGHT_OPERAND, TRUE_VAL, FALSE_VAL)
        Pattern pattern = Pattern.compile("if\\((.+?)\\s*([><=!]+)\\s*(.+?),\\s*(.+?),\\s*(.+?)\\)");
        Matcher matcher = pattern.matcher(expression);
        if (matcher.find()) {
            double left = getDoubleValue(record.get(matcher.group(1).trim()));
            double right = Double.parseDouble(matcher.group(3).trim());
            String operator = matcher.group(2).trim();
            
            // Evaluate condition based on operator
            boolean condition = operator.equals(">") ? left > right
                              : operator.equals("<") ? left < right
                              : left == right;
            
            // Return appropriate branch value
            return condition ? parseValue(matcher.group(4).trim()) : parseValue(matcher.group(5).trim());
        }
        return 0;
    }

    /**
     * Evaluate arithmetic expression with field value substitution.
     * 
     * Process:
     * 1. Replace field names with their numeric values from record
     * 2. Evaluate resulting arithmetic expression
     * 3. Return numeric or string result
     * 
     * @param expression Expression like "price * 0.8" or "tax + shipping"
     * @param record The record context
     * @return Computed numeric value
     */
    private Object evaluateArithmetic(String expression, Map<String, Object> record) {
        String evaluated = expression;
        
        // Replace field names with values
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            if (entry.getValue() instanceof Number) {
                evaluated = evaluated.replaceAll("\\b" + entry.getKey() + "\\b", entry.getValue().toString());
            }
        }
        
        return evaluateSimpleArithmetic(evaluated);
    }

    /**
     * Evaluate simple arithmetic expression: "5 * 3 + 2"
     * 
     * Uses regex-based operator precedence (*, / before +, -).
     * Not a full expression parser; limited to basic operations.
     * 
     * @param expr Expression with only numbers and operators
     * @return Double result
     */
    private double evaluateSimpleArithmetic(String expr) {
        expr = expr.replaceAll("\\s+", ""); // Remove whitespace
        
        // First pass: handle * and /
        while (expr.contains("*") || expr.contains("/")) {
            Pattern p = Pattern.compile("(\\d+\\.?\\d*)([*/])(\\d+\\.?\\d*)");
            Matcher m = p.matcher(expr);
            if (m.find()) {
                double result = m.group(2).equals("*")
                    ? Double.parseDouble(m.group(1)) * Double.parseDouble(m.group(3))
                    : Double.parseDouble(m.group(1)) / Double.parseDouble(m.group(3));
                expr = expr.substring(0, m.start()) + result + expr.substring(m.end());
            } else break;
        }
        
        // Second pass: handle + and -
        while (expr.matches(".*\\d+\\.?\\d*[+\\-]\\d+\\.?\\d*.*")) {
            Pattern p = Pattern.compile("(\\d+\\.?\\d*)([+\\-])(\\d+\\.?\\d*)");
            Matcher m = p.matcher(expr);
            if (m.find()) {
                double result = m.group(2).equals("+")
                    ? Double.parseDouble(m.group(1)) + Double.parseDouble(m.group(3))
                    : Double.parseDouble(m.group(1)) - Double.parseDouble(m.group(3));
                expr = expr.substring(0, m.start()) + result + expr.substring(m.end());
            } else break;
        }
        
        return Double.parseDouble(expr);
    }

    /**
     * Convert Object to double value.
     * 
     * Handles Number instances and string parsing.
     * Returns 0.0 for null or unparseable values.
     * 
     * @param value Object to convert
     * @return Double value
     */
    private double getDoubleValue(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        try { return Double.parseDouble(value.toString()); } catch (Exception e) { return 0.0; }
    }

    /**
     * Parse string value to appropriate type (int, double, or string).
     * 
     * @param value String value to parse
     * @return Integer, Double, or String
     */
    private Object parseValue(String value) {
        try {
            return value.contains(".") ? Double.parseDouble(value) : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return value; // Return as string if not numeric
        }
    }
}