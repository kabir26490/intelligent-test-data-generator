package com.testdata.generator;

import com.github.javafaker.Faker;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class DataTypeGenerator {
    private Faker faker = new Faker();
    private Random random = new Random();

    public void setSeed(long seed) {
        this.faker = new Faker(new Random(seed));
        this.random = new Random(seed);
    }

    public Object generateValue(String fieldName, Object fieldDef, String locale, Map<String, Object> context) {
        if (fieldDef instanceof String) return generateSimpleType((String) fieldDef);
        if (fieldDef instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> def = (Map<String, Object>) fieldDef;
            return generateComplexType(fieldName, def, locale, context);
        }
        return null;
    }

    private Object generateSimpleType(String type) {
        switch (type.toLowerCase()) {
            case "firstname":    return faker.name().firstName();
            case "lastname":     return faker.name().lastName();
            case "fullname":     return faker.name().fullName();
            case "email":        return faker.internet().emailAddress();
            case "phone":        return faker.phoneNumber().phoneNumber();
            case "uuid":         return UUID.randomUUID().toString();
            case "productname":  return faker.commerce().productName();
            case "companyname":  return faker.company().name();
            default:             return null;
        }
    }

    private Object generateComplexType(String fieldName, Map<String, Object> def, String locale, Map<String, Object> context) {
        String type = (String) def.get("type");
        if (type == null) return null;

        switch (type.toLowerCase()) {
            case "number": {
                int min = def.containsKey("min") ? ((Number) def.get("min")).intValue() : 0;
                int max = def.containsKey("max") ? ((Number) def.get("max")).intValue() : 100;
                return random.nextInt(max - min + 1) + min;
            }
            case "decimal": {
                double dmin = def.containsKey("min") ? ((Number) def.get("min")).doubleValue() : 0.0;
                double dmax = def.containsKey("max") ? ((Number) def.get("max")).doubleValue() : 100.0;
                int decimals = def.containsKey("decimals") ? ((Number) def.get("decimals")).intValue() : 2;
                double value = dmin + (dmax - dmin) * random.nextDouble();
                return Math.round(value * Math.pow(10, decimals)) / Math.pow(10, decimals);
            }
            case "array": {
                int minItems = def.containsKey("minItems") ? ((Number) def.get("minItems")).intValue() : 1;
                int maxItems = def.containsKey("maxItems") ? ((Number) def.get("maxItems")).intValue() : 5;
                int count = random.nextInt(maxItems - minItems + 1) + minItems;
                @SuppressWarnings("unchecked")
                Map<String, Object> itemSchema = (Map<String, Object>) def.get("schema");
                List<Map<String, Object>> result = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> entry : itemSchema.entrySet()) {
                        item.put(entry.getKey(), generateValue(entry.getKey(), entry.getValue(), locale, item));
                    }
                    result.add(item);
                }
                return result;
            }
            case "uuid":
                return UUID.randomUUID().toString();
            default:
                return generateSimpleType(type);
        }
    }
}