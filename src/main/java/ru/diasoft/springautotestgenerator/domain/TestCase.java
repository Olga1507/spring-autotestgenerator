package ru.diasoft.springautotestgenerator.domain;

import lombok.Getter;
import lombok.Setter;
import ru.diasoft.springautotestgenerator.utils.MethodTypes;

import java.util.Map;

// аннотация для того, чтобы не писать getter и setter руками
@Getter
@Setter
public class TestCase {
    private String caseName;
    private String methodPath;
    private MethodTypes methodType;
    private Object requestBody;
    private Map<String, Object> parametersPath;
    private Map<String, Object> parametersQuery;
    private int responseCode;
    private Object expectedObjects;
    private Object unexpectedObjects;
}




