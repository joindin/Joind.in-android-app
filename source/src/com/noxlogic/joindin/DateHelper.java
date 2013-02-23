package com.noxlogic.joindin;


import java.text.ParseException;
import java.text.SimpleDateFormat;

public class DateHelper {

    /**
     * Parse an ISO8601 date into a user-supplied format
     *
     * @param input
     * @param format
     * @return
     * @throws ParseException if the date cannot be parsed
     */
    public static String parseAndFormat(String input, String format) {
        // Assume that the input format is ISO8601
        SimpleDateFormat dfOutput = new SimpleDateFormat(format), dfInput = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

        try {
            return dfOutput.format(dfInput.parse(input));
        } catch (ParseException e) {
            e.printStackTrace();
            return new String("");
        }
    }
}
