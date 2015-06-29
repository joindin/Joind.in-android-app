package in.joind;


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

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
        SimpleDateFormat dfOutput = new SimpleDateFormat(format, Locale.US),
                dfInput = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);

        try {
            return dfOutput.format(dfInput.parse(input));
        } catch (ParseException e) {
            e.printStackTrace();
            return "";
        }
    }
}
