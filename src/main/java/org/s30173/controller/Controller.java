package org.s30173.controller;

import org.s30173.ModellingFrameworkSample;
import org.s30173.helpers.Bind;
import org.s30173.helpers.Model;

import javax.script.*;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.s30173.ModellingFrameworkSample.tableModel;

public class Controller {
    private final Model model;
    private Map<Field, String> bindFields;
    private final Map<String, Field> bindFieldNames = new HashMap<>();

    private final Map<String, double[]> dataFromFile = new HashMap<>();
    private String[] lata;

    private final Map<String, Object> scriptVars = new LinkedHashMap<>();
    ScriptEngine groovy = new ScriptEngineManager().getEngineByName("groovy");

    public Controller(String modelClassName) {
        try {
            this.model = (Model) Class.forName(modelClassName).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Error creating model from class: " + modelClassName, e);
        }
    }

    public Controller readDataFrom(String fileName) {
        try (Stream<String> lines = Files.lines(Path.of(fileName))) {
            lines.forEach(line -> {
                if (line.startsWith("LATA ")) {
                    lata = line.substring(5).trim().split("\\s+");
                } else {
                    String[] parts = line.trim().split("\\s+");
                    double[] values = Arrays.stream(parts, 1, parts.length)
                            .mapToDouble(Double::parseDouble)
                            .toArray();
                    dataFromFile.put(parts[0], values);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Error reading data file: " + fileName, e);
        }
        return this;
    }

    public Controller runModel() {
        bindFields = getBindFields();

        bindFields.forEach((field, name) -> {
            Object value = name.equals("LL") ? lata.length : prepareArray(dataFromFile.get(name), lata.length);
            setValue(field, value);
        });
        model.run();

        // make fields (with @Bind from the model) available in the script
        bindFields.forEach((field, name) -> groovy.put(name, getValue(field)));

        ModellingFrameworkSample.addColumns(lata);
        addBindFieldsIntoTable();

        return this;
    }

    public Controller runScriptFromFile(String fileName) throws ScriptException {
        String script;

        try (Stream<String> lines = Files.lines(Path.of(fileName))) {
            script = lines.collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException("Error reading script file: " + fileName, e);
        }

        runScript(script);
        return this;
    }

    public Controller runScript(String script) throws ScriptException {
        groovy.eval(script);

        // process script vars after running the script
        List<String> varsToRemove = new ArrayList<>();

        Bindings bindings = groovy.getBindings(ScriptContext.ENGINE_SCOPE);
        bindings.forEach((key, value) -> {
            // collect single lowercase letters to remove later
            if (key.length() == 1 &&
                Character.isLetter(key.charAt(0)) &&
                Character.isLowerCase(key.charAt(0))) {
                varsToRemove.add(key);
                return;
            }

            Field field = bindFieldNames.get(key);
            if (field != null) {
                // update field's value because it might have changed during this script
                setValue(field, value);
                return;
            }

            // save new script var or update old value
            scriptVars.put(key, value);
        });

        varsToRemove.forEach(varName -> bindings.remove(varName));

        // update table
        tableModel.setNumRows(0); // clear old rows
        addBindFieldsIntoTable(); // add rows with @Bind
        scriptVars.forEach((name, value) -> addTableRow(name, value)); // add rows from script vars

        return this;
    }

    public String getResultsAsTsv() {
        StringBuilder res = new StringBuilder(4096);

        appendRow(res, "LATA", String.join("\t", lata));

        bindFields.forEach((field, name) -> {
            if (!name.equals("LL"))
                appendRow(res, name, fieldValueToStr(getValue(field)));
        });

        scriptVars.forEach((name, value) ->
                appendRow(res, name, fieldValueToStr(value))
        );

        return res.toString();
    }


    // Helpers
    private LinkedHashMap<Field, String> getBindFields() {
        return Arrays.stream(model.getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Bind.class))
                .peek(field -> {
                    field.setAccessible(true);
                    bindFieldNames.put(field.getName(), field);
                })
                .collect(Collectors.toMap(
                        field -> field,
                        Field::getName,
                        (existing, _) -> existing,
                        LinkedHashMap::new
                ));
    }

    private double[] prepareArray(double[] vals, int len) {
        if (vals == null)
            return new double[len];

        double[] arr = new double[len];
        System.arraycopy(vals, 0, arr, 0, vals.length);

        if (vals.length < len)
            Arrays.fill(arr, vals.length, len, vals[vals.length - 1]);

        return arr;
    }

    private Object[] formatFieldValues(Object value) {
        if (value == null)
            return new Object[0];

        if (value instanceof double[]) {
            return Arrays.stream((double[]) value)
                    .mapToObj(Controller::formatNumber)
                    .toArray();
        }

        return new String[]{value.toString()};
    }

    private static String formatNumber(double number) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator(' ');

        String pattern = number < 10000 ? "#,##0.##" : "#,##0.#";

        DecimalFormat decFormat = new DecimalFormat(pattern, symbols);
        String formattedNumber = decFormat.format(number);

        if (formattedNumber.endsWith(symbols.getDecimalSeparator() + "0"))
            formattedNumber = formattedNumber.substring(0, formattedNumber.length() - 2);

        return formattedNumber;
    }

    private void addTableRow(String name, Object value) {
        Object[] rowData = new Object[lata.length+1];
        rowData[0] = name;
        Object[] data = formatFieldValues(value);
        System.arraycopy(data, 0, rowData, 1, data.length);
        tableModel.addRow(rowData);
    }

    private Object getValue(Field field) {
        try {
            return field.get(model);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to get field's value: " + field.getName(), e);
        }
    }

    private void setValue(Field field, Object value) {
        try {
            field.set(model, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set field's value: " + field.getName(), e);
        }
    }

    private void addBindFieldsIntoTable() {
        bindFields.forEach((field, name) -> {
            if (!name.equals("LL"))
                addTableRow(name, getValue(field));
        });
    }

    // For console log
    private String fieldValueToStr(Object value) {
        if (value == null)
            return "NULL";

        if (value.getClass().isArray() && value instanceof double[])
            return Arrays.stream((double[]) value)
                    .mapToObj(Double::toString)
                    .collect(Collectors.joining("\t"));

        return value.toString();
    }

    private void appendRow(StringBuilder sb, String name, String values) {
        sb.append(name).append('\t').append(values).append('\n');
    }
}