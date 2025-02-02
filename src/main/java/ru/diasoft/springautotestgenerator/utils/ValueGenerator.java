package ru.diasoft.springautotestgenerator.utils;

import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class ValueGenerator {


    private static final Map<String, String> BAD_REFS = Map.of(
            "Error-ModelName{namespace='java.time', name='ZonedDateTime'}", "ZonedDateTime",
            "Error-ModelName{namespace='java.time', name='LocalDate'}", "LocalDate");

    /**
     * Генерит пример значения по параметру
     *
     * @param parameter
     * @param schemas
     * @return
     */
    public static Object generateValueByParameter(Parameter parameter, Map<String, Schema> schemas) {
        if (parameter.getExample() != null) {
            return parameter.getExample();
        }
        if (parameter.getSchema() != null) {
            return generateValueBySchema(parameter.getSchema(), schemas);
        } else {
            if (parameter.getDescription() != null) {
                return parameter.getDescription();
            }
            return "object";
        }
    }

    /**
     * Генерит примеры значений по контенту
     *
     * @param content
     * @param schemas
     * @return
     */
    public static Object generateValueByContent(Content content, Map<String, Schema> schemas) {
        Object requestBody = null;
        //пытаемся получить Media type - нужно чтобы исключить непонятные mediaType (например, XML)
        MediaType mediaType = getMediaTypeFromContent(content);
        // проверим Media type на null
        if (mediaType != null) {
            if (mediaType.getSchema() != null) {
                Schema rootSchema = mediaType.getSchema();
                requestBody = ValueGenerator.generateValueBySchema(rootSchema, schemas);

            } else {
                throw new IllegalArgumentException("Content не содержит Schema");
                //logger.warn("Некорректный тип запроса: pathName = {}, methodType = {}", pathName, methodType);
            }
        }
        return requestBody;
    }

    public static Object generateValueBySchema(Schema schema, Map<String, Schema> schemas) {
        Set<Schema> knownSchemas = new HashSet<>();
        return generateValueBySchema(schema, schemas, knownSchemas);
    }

    /**
     * Функция получает пример значения по схеме. Она вызывается до тех пор, пока мы не расскроем сложный тип до простого и там уже будем использовать метод generateValueBySimpleType
     *
     * @param schema  - конкретная схема, по которой формируется body, не обязательно лежит в schemas
     * @param schemas - весь набор схем в проекте, которые есть в api-docs (см components - schemas)
     * @return - пример значения по схеме
     */
    private static Object generateValueBySchema(Schema schema, Map<String, Schema> schemas, Set<Schema> knownSchemas) {
        if (schema == null) {
            throw new IllegalArgumentException("В схеме ничего нет! Обрабатывать не будем.");
        }
        if (schemas == null) {
            throw new IllegalArgumentException("Набора схем нет! Обрабатывать не будем.");
        }
        if (knownSchemas.contains(schema)) {
            log.error("Обнаружены циклические ссылки в схемах. Последняя схема: " + schema.getName());
            return "ERROR! Cyclic schema!";
            //throw new IllegalArgumentException("Обнаружены циклические ссылки в схемах. Последняя схема: " + schema.getName());
        }
        knownSchemas.add(schema);

        //поэтому пропишем обработку для этих вариантов
        //Set<Schema> knownSchemas = new HashSet<>();
        if (schema.get$ref() != null) {
            //если схема будет ссылаться на саму же себя (защита от колец) - идея запоминания всех пройденных схем
            //knownSchemas.add(schema);
            //Из объекта schema мы получаем ссылку $ref
            String ref = schema.get$ref();

            //получаем название схемы из ссылки
            String schemaName = getSchemaName(ref);

            //Проверяем, не является ли наименование схемы некорректным
            String shortBadRef = BAD_REFS.get(schemaName);
            if (shortBadRef == null) {
                // получение схемы по ее имени
                Schema schemaByRef = schemas.get(schemaName);

                if (schemaByRef == null) {
                    throw new IllegalArgumentException("Не найдена схема с именем: " + schemaName);
                }

//            if (knownSchemas.contains(schema)) {
//                throw new IllegalArgumentException("Обнаружены циклические ссылки в схемах. Последняя схема: " + ref);
//            }
                Object buferVal = generateValueBySchema(schemaByRef, schemas, knownSchemas);
                knownSchemas.remove(schema);
                return buferVal;
            } else {
                schema.setType(shortBadRef.toLowerCase());
                //break;
            }
        }

        //Типы схем, которые могут тут быть:
        //1. Примитивный тип (properties и additionalProperties = null, type != array)
        //2. Массив (properties и additionalProperties = null, type = array)
        //3. Сложный тип (properties и/или additionalProperties != null)
        //4. Все, что не подходит под эти три категории - кидать ошибку

        if (schema.getProperties() == null && schema.getAdditionalProperties() == null) {
            //2. Массив (properties и additionalProperties = null, type = array)
            if ("array".equals(schema.getType())) {
                Object o1 = generateValueBySchema(schema.getItems(), schemas, knownSchemas);
                Object o2 = generateValueBySchema(schema.getItems(), schemas, knownSchemas);
                knownSchemas.remove(schema);
                return List.of(o1, o2);
            }
            //1. Примитивный тип (properties и additionalProperties = null, type != array)
            else {
                Object buferVal = generateValueBySimpleType(schema);
                knownSchemas.remove(schema);
                return buferVal;
            }
        }
        //3. Сложный тип (properties и/или additionalProperties != null)
        else {
            Map<String, Object> result = new HashMap<>();
            if (schema.getProperties() != null) {
                schema.getProperties().forEach((key, value) -> {
                    //System.out.println(key);
                    //см pic1.png
                    result.put((String) key, generateValueBySchema((Schema) value, schemas, knownSchemas));
                });

            }

            //todo решить вопрос с перезаписью additionalProp1, если такие же названия есть в Properties
            if (schema.getAdditionalProperties() != null) {
                result.put("additionalProp1", generateValueBySchema((Schema) schema.getAdditionalProperties(), schemas, knownSchemas));
                result.put("additionalProp2", generateValueBySchema((Schema) schema.getAdditionalProperties(), schemas, knownSchemas));
                result.put("additionalProp3", generateValueBySchema((Schema) schema.getAdditionalProperties(), schemas, knownSchemas));
            }
            knownSchemas.remove(schema);
            return result;

        }
    }

    //получение названия схемы из ссылки
    public static String getSchemaName(String ref) {
        String regex = "[^/]+$";
        //скомпиллировали регулярное выражение
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(ref);

        String schemaName = ""; // ex: schemaName = MsgCreate

        if (matcher.find()) {
            schemaName = matcher.group();
            //schemaName = ref.substring(matcher.start(), matcher.end());
        }
        return schemaName;

    }

    /**
     * Метод генерит примеры значений по схеме, но только для примитивных типов
     *
     * @param schema
     * @return - рандомный пример значений в зависимотсти от типа данных
     */
    public static Object generateValueBySimpleType(Schema schema) {
        //описание в schemasExamples.json
        if (schema.getType() == null) {
            throw new IllegalArgumentException("Тег type у схемы не заполнен. Невозможно сформировать пример значения.");
        }
        if (schema.getExample() != null) {
            return schema.getExample();
        }
        switch (schema.getType().toLowerCase(Locale.ROOT)) {
            case "integer":
                return Randomizer.rndInt(100);
            case "string":
                if (schema.getEnum() != null && schema.getEnum().size() > 0) {
                    return Randomizer.rndListValue(schema.getEnum());
                }
                if (schema.getFormat() != null) {
                    return schema.getFormat();
                }
                if (schema.getDescription() != null) {
                    return schema.getDescription();
                }
                return "str-" + Randomizer.rndString(10);
            case "boolean":
                return Randomizer.rndBoolean();
            case "object":
                return "object";
            case "double":
                return Randomizer.rndDouble(10);
            case "float":
                return Randomizer.rndFloat(10);
            case "zoneddatetime":
                return Randomizer.rndZonedDateTime();
            case "localdate":
                return Randomizer.rndLocalDate();
            default:
                throw new IllegalArgumentException("Невозможно сформировать пример значения для типа: " + schema.getType());
        }

    }

    //определили словарь из возможных вариантов типов контента
    //записываются в том порядке, в котором мы их записали TreeSet
    static SortedSet<String> possibleContentTypes = new TreeSet<>();

    static {
        possibleContentTypes.add("application/json");
        possibleContentTypes.add("*/*");
        possibleContentTypes.add("text/html");
        possibleContentTypes.add("application/octet-stream");
        possibleContentTypes.add("text/plain;version=0.0.4;charset=utf-8");
        possibleContentTypes.add("text/plain;charset=UTF-8");
        possibleContentTypes.add("application/vnd.spring-boot.actuator.v3+json");
        possibleContentTypes.add("application/vnd.spring-boot.actuator.v2+json");
    }

    /**
     * Функция getMediaTypeFromContent проверяет содержимое мапы Content и если оно валидно (см. ReadMe), то получает его MediaType.
     * Пример - все, что входит в application/json и есть MediaType:
     * "content": {
     * "application/json": {
     * "schema": {
     * "$ref": "#/components/schemas/MsgCreate"
     * }
     * }
     * }
     *
     * @param content
     * @return если content некорректный, возвращается null
     */
    public static MediaType getMediaTypeFromContent(Content content) {
        MediaType mediaType = null;

        if (content != null) {
            for (String possibleContent : possibleContentTypes) {
                if (content.keySet().contains(possibleContent)) {
                    mediaType = content.get(possibleContent);
                    break;
                }
            }

            if (mediaType == null) {
                throw new IllegalArgumentException(String.format("В мапе Content нет элементов/ни один из элементов в мапе Content не соответствует possibleContentTypes. Количество элементов в мапе Content: %s", content.size()));
            }
        }
        return mediaType;

    }
}
