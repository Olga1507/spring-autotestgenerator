package ru.diasoft.springautotestgenerator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.diasoft.springautotestgenerator.domain.AllCases;
import ru.diasoft.springautotestgenerator.domain.TestCase;
import ru.diasoft.springautotestgenerator.report.ReportUtils;
import ru.diasoft.springautotestgenerator.report.domain.Report;
import ru.diasoft.springautotestgenerator.report.domain.TestCaseLog;
import ru.diasoft.springautotestgenerator.utils.MethodTypes;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service // аннотация, которая говорит спрингу создать экземпляр этого класса
@Slf4j
public class ExecutorService {
    //переменные для авторизации
    @Value("${service.auth.url}")
    private String authUrl;
    @Value("${service.auth.username}")
    private String authUsername;
    @Value("${service.auth.password}")
    private String authPassword;
    public String execute(AllCases allCases) throws Exception {
        //Создали переменную для отчёта
        Report report = new Report();
        //Сохранили в отчёт имя файла с кейсами
        report.setFileName("-");
        //report.setFileName(fileName);
        //Записали текущее время
        report.setDateStart(Instant.now());


        for (int i = 0; i < allCases.getCases().size(); i++) {
            //Заполнение отчёта
            TestCaseLog testCaseLog = new TestCaseLog();
            report.getTestCaseLogs().add(testCaseLog);
            testCaseLog.setTestCaseNum(i);

            log.info("Начали обработку тест-кейса {}", i + 1);
            boolean result = true;
            try {
                TestCase case0 = allCases.getCases().get(i);
                String correctUrl = getUrl(case0);


                //System.out.println(correctUrl);

                String resultUrl = allCases.getServerUrl().concat(correctUrl);
                log.info("URL для запроса: {}", resultUrl);
                //Записали URL для отчёта
                testCaseLog.setRequestUrl(resultUrl);

                //Формируем body (маршаллинг)
                ObjectMapper mapper = new ObjectMapper();

                //Java Object -> JSON
                String requestBodyString = mapper.writeValueAsString(case0.getRequestBody());
                log.info("Request Body: {}", requestBodyString);


                HttpResponse<String> response = sendRequest(resultUrl, case0.getMethodType(), requestBodyString);

                log.info("Получен ответ. Код: {}. Тело ответа: {}", response.statusCode(), response.body());

                //Для отчёта
                testCaseLog.setResponseBody(response.body());

                //Записали код для отчёта

                testCaseLog.setRespCode(response.statusCode());

                if (response.statusCode() != case0.getResponseCode()) {
                    String errorText = String.format("Получен некорректный статус код: %s. Ожидаемый статус код: %s",
                            response.statusCode(), case0.getResponseCode());
                    log.error(errorText);
                    result = false;
                    testCaseLog.getErrors().add(errorText);
                } else {
                    log.info("Получен корректный статус - код: {}", response.statusCode());
                    log.info("Начали валидацию тела ответа из expectedObjects");

                    //Для кpacивoго oфopмлeния JSON cтpoки
                    //ObjectMapper mapper = new ObjectMapper(); - создали выше!
                    JsonNode responseJson = mapper.readValue(response.body(), JsonNode.class);
                    //JsonNode node1 = mapper.valueToTree(jsonObject);


                    //Валидация

                    try {
                        validateResponse(responseJson, case0);
                    } catch (RuntimeException e) {
                        log.error("Ошибка валидации: " + e.getMessage(), e);
                        result = false;
                        testCaseLog.getErrors().add("Ошибка валидации: " + e.getMessage());
                    }
                }

            } catch (Exception e) {
                log.error("Ошибка: {}", e.getMessage(), e);
                result = false;
                testCaseLog.getErrors().add("Ошибка: " + e.getMessage());

            }


            log.info("Закончили обработку тест-кейса {}", i + 1);
            testCaseLog.setResult(result);

        }
        log.info("Обработка завершена.");
        report.setDateEnd(Instant.now());

        log.info("Начали формирование отчёта");

        String s = ReportUtils.creatHtmlReport(report);

        log.info("Отчёт готов!");

        return s;
    }

    // ВАЛИДАЦИЯ
    // create object mapper instance
    static ObjectMapper mapper = new ObjectMapper();

    public static void validateResponse(JsonNode responseJson, TestCase case0) {
        validateObjects(responseJson, mapper.valueToTree(case0.getExpectedObjects()), true);
        validateObjects(responseJson, mapper.valueToTree(case0.getUnexpectedObjects()), false);
        log.info("Валидация прошла успешно!");
    }

    private static void validateObjects(JsonNode responseJson, JsonNode objects, boolean expected) {
        if (objects != null) {
            objects.fields().forEachRemaining(stringJsonNodeEntry -> {
                String key = stringJsonNodeEntry.getKey();
                JsonNode expectedValue = stringJsonNodeEntry.getValue();
                JsonNode founded = responseJson.get(key);

                if (expected) {
                    validateExpectedObject(key, expectedValue, founded);
                } else {
                    validateUnexpectedObject(key, expectedValue, founded);
                }

                // Рекурсивный вызов для вложенных объектов
                if (founded != null && founded.isObject()) {
                    validateObjects(founded, expectedValue, expected);
                }
            });
        }
    }

    private static void validateExpectedObject(String key, JsonNode expectedValue, JsonNode founded) {
        if (founded == null) {
            throw new RuntimeException("Не найдено обязательное поле в теле ответа: " + key);
        }
        if (!expectedValue.equals(founded)) {
            throw new RuntimeException(String.format(
                    "Получили ошибку при сравнении тега '%s': полученное значение '%s' не совпало с ожидаемым '%s'",
                    key, founded, expectedValue));
        }
    }

    private static void validateUnexpectedObject(String key, JsonNode expectedValue, JsonNode founded) {
        // Проверка отсутствия неожиданного поля
        if (founded != null && expectedValue.equals(founded)) {
            throw new RuntimeException(String.format(
                    "В теле ответа не должно быть тега '%s' с значением '%s'",
                    key, founded));
        }
    }

    //

    public static String getUrl(TestCase testCase) throws Exception {
        String regex = "\\{([^}]*)\\}";
        //скомпиллировали регулярное выражение
        Pattern pattern = Pattern.compile(regex);


        //Подготовка поиска в testStr
        Matcher matcher = pattern.matcher(testCase.getMethodPath());
        List<String> errors = new ArrayList<>();
        String res = matcher.replaceAll(matchResult -> {
            //return "hello" + " " + matchResult.group(1);
            if (testCase.getParametersPath().get(matchResult.group(1)) != null) {
                return String.valueOf(testCase.getParametersPath().get(matchResult.group(1)));
            } else {
                errors.add("Не найдено значение параметра: " + matcher.group());
                log.error("Не найдено значение параметра: " + matcher.group());
                //throw new Exception("Не найдено значение параметра: " + matcher.group());
                return matcher.group();
            }

        });
        if (errors.size() > 0) {
            throw new Exception("Ошибка при формировании URL");
        }
        return res;
    }


    public HttpResponse<String> sendRequest(String url, MethodTypes type, String requestBody)
            throws IOException, InterruptedException {
        // create a client
        HttpClient client = HttpClient.newHttpClient();

        log.info("Отправка запроса {} на адрес {}", type, URI.create(url));

        // create a request
        HttpRequest.Builder tmp = HttpRequest.newBuilder(URI.create(url))
                .header("accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + getAuthToken());

        HttpRequest request;

        switch (type) {

            case GET:
                request = tmp.GET().build();
                break;
            //case POST: request = tmp.POST(HttpRequest.BodyPublishers.noBody()).build(); break;
            case POST:
                request = tmp.POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();
                break;
            case PUT:
                request = tmp.PUT(HttpRequest.BodyPublishers.ofString(requestBody)).build();
                break;
            case DELETE:
                request = tmp.DELETE().build();
                break;

            default:
                throw new IllegalArgumentException("Некорректное значение MethodType: " + String.valueOf(type));
        }


        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response;
    }

    public String getAuthToken() throws IOException, InterruptedException {
        Map<String, String> formData = new HashMap<>();
        formData.put("grant_type", "password");
        formData.put("scope", "openid");
        formData.put("username", authUsername);
        formData.put("password", authPassword);

        HttpClient client = HttpClient.newHttpClient();

        log.info("Запрос токена. URI:{}", URI.create(authUrl));

        HttpRequest request = HttpRequest.newBuilder()
                .header("Content-Type", "application/x-www-form-urlencoded")
                .uri(URI.create(authUrl))
                .POST(HttpRequest.BodyPublishers.ofString(getFormDataAsString(formData)))
                .header("Authorization", "Basic " +
                        Base64.getEncoder().encodeToString(("client:secret").getBytes()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        log.info("Ответ на запрос токена. code:{}, body:{}", response.statusCode(), response.body());

        String authToken = new JSONObject(response.body()).getString("access_token");

        log.info("Auth token:'{}'", authToken);

        return authToken;
    }

    private static String getFormDataAsString(Map<String, String> formData) {
        StringBuilder formBodyBuilder = new StringBuilder();
        for (Map.Entry<String, String> singleEntry : formData.entrySet()) {
            if (formBodyBuilder.length() > 0) {
                formBodyBuilder.append("&");
            }
            formBodyBuilder.append(URLEncoder.encode(singleEntry.getKey(), StandardCharsets.UTF_8));
            formBodyBuilder.append("=");
            formBodyBuilder.append(URLEncoder.encode(singleEntry.getValue(), StandardCharsets.UTF_8));
        }
        return formBodyBuilder.toString();
    }
}
