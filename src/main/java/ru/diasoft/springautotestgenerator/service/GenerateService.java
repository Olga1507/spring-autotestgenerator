package ru.diasoft.springautotestgenerator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.stereotype.Service;
import ru.diasoft.springautotestgenerator.domain.AllCases;
import ru.diasoft.springautotestgenerator.domain.TestCase;
import ru.diasoft.springautotestgenerator.utils.MethodTypes;
import ru.diasoft.springautotestgenerator.utils.ValueGenerator;

import java.util.*;


@Service // аннотация, которая говорит спрингу создать экземпляр этого класса
public class GenerateService {

    /**
     * Метод, который по переданному сваггер-описанию генерирует тест-кейсы.
     * @param body
     * @return json1 - тест-кейсы
     */
    public String generate(String body) throws JsonProcessingException {
        // parse a swagger description from the petstore and get the result
        SwaggerParseResult result = new OpenAPIParser().readContents(body, null, null);

        OpenAPI openAPI = result.getOpenAPI();

        AllCases allCases = new AllCases();
        List<TestCase> testCaseList = new ArrayList<>();
        if (openAPI != null) {
            Map<String, Schema> schemas = openAPI.getComponents().getSchemas();

            openAPI.getPaths().forEach((pathName, pathItem) -> {
                tryCreateTestCase(testCaseList, pathName, pathItem.getGet(), MethodTypes.GET, schemas);
                tryCreateTestCase(testCaseList, pathName, pathItem.getPut(), MethodTypes.PUT, schemas);
                tryCreateTestCase(testCaseList, pathName, pathItem.getPost(), MethodTypes.POST, schemas);
                tryCreateTestCase(testCaseList, pathName, pathItem.getDelete(), MethodTypes.DELETE, schemas);
            });
        }
        allCases.setCases(testCaseList);
//        Gson gson1 = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
//        String json1 = gson1.toJson(allCases, AllCases.class);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        String json1 = mapper.writeValueAsString(allCases);
        //var message = Message.fromJson(json, mapper);

        return json1;
    }

    static SortedSet<String> possibleResponseCodes = new TreeSet<>();
    static {
        possibleResponseCodes.add("200");
        possibleResponseCodes.add("201");
        possibleResponseCodes.add("202");
        possibleResponseCodes.add("203");
        possibleResponseCodes.add("204");
        possibleResponseCodes.add("300");
        possibleResponseCodes.add("301");
        possibleResponseCodes.add("302");
        possibleResponseCodes.add("303");
        possibleResponseCodes.add("304");
        possibleResponseCodes.add("307");
        possibleResponseCodes.add("308");
        possibleResponseCodes.add("400");
        possibleResponseCodes.add("401");
        possibleResponseCodes.add("403");
    }

    /**
     * Формирует/не формирует тест-кейс на основе описания из swagger и добавляет его в List
     *
     * @param pathName
     * @param methodDescription
     * @param methodType
     * @param schemas
     * @return если что-то внутри упадет (например, methodDescription вернутся некорректный или схема), возвращается null
     */

    public static void tryCreateTestCase(List<TestCase> testCaseList, String pathName, Operation methodDescription,
                                         MethodTypes methodType, Map<String, Schema> schemas) {

        TestCase testCase = null;

        //если у метода есть параметры, то сохраним в мапу имя этого параметра и пример его значения
        if (methodDescription != null) {
            //создаем экземпляр класса в этом месте, так как у данного метода может не быть RequestBody
            testCase = new TestCase();
            testCase.setMethodPath(pathName);
            testCase.setMethodType(methodType);


            //заполняем параметры метода
            Map<String, Object> parametersPath = new HashMap<>();
            Map<String, Object> parametersQuery = new HashMap<>();
            if (methodDescription.getParameters() != null) {
                methodDescription.getParameters().forEach(param -> {
                    if ("path".equals(param.getIn())){
                        parametersPath.put(param.getName(), ValueGenerator.generateValueByParameter(param, schemas));
                    } else if ("query".equals(param.getIn())){
                        parametersQuery.put(param.getName(), ValueGenerator.generateValueByParameter(param, schemas));
                    } else {
                        throw new IllegalArgumentException(String.format(
                                "В теге in параметра %s содержится некорректное значение: %s",
                                param.getName(), param.getIn()));
                    }

                });
            }
            testCase.setParametersPath(parametersPath);
            testCase.setParametersQuery(parametersQuery);

            //если RequestBody не пустое
            if (methodDescription.getRequestBody() != null) {
                //то получаем content
                Content content = methodDescription.getRequestBody().getContent();
                testCase.setRequestBody(ValueGenerator.generateValueByContent(content, schemas));
            }

            //получение responseBody
            if (methodDescription.getResponses() != null) {
                ApiResponse apiResponse = null;
                for (String possibleResponseCode : possibleResponseCodes){
                    if (methodDescription.getResponses().containsKey(possibleResponseCode)){
                        apiResponse = methodDescription.getResponses().get(possibleResponseCode);
                        testCase.setExpectedObjects(ValueGenerator.generateValueByContent(apiResponse.getContent(), schemas));
                        testCase.setResponseCode(Integer.parseInt(possibleResponseCode));
                        break;
                    }
                }

                if (apiResponse == null){
                    throw new IllegalArgumentException(String.format(
                            "Ни один из кодов ответа не соответствует possibleResponseCodes. " +
                                    "Количество найденных кодов ответа: %s",
                            methodDescription.getResponses().size()));
                }
            }
        }
        if (testCase != null) {
            testCaseList.add(testCase);
        }
    }
}
