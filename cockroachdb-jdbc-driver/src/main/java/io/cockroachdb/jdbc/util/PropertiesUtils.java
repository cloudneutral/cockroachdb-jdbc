package io.cockroachdb.jdbc.util;

import java.util.Properteis;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PropertiesUtils {
  
  public static String maskPassword(String jdbcUrl) {
    // Define a regular expression to find the password parameter in the URL
    String regex = "(password=)([^;]+)";
    Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    Matcher matcher = pattern.matcher(jdbcUrl);

    // Replace the password value with asterisks if it exists
    if (matcher.find()) {
      String maskedPassword = matcher.group(1) + "****";
      return matcher.replaceAll(maskedPassword);
    }

    // Return the original URL if no password is found
    return jdbcUrl;
  }

  public static Properties maskPassword(Properties original) {
    // Create a new Properties object for the copy
    Properties copy = new Properties();
    
    // Copy all properties from the original, masking the password if present
    for (String key : original.stringPropertyNames()) {
      String value = original.getProperty(key);
      if ("password".equalsIgnoreCase(key)) {
        copy.setProperty(key, "****");
      } else {
        copy.setProperty(key, value);
      }
    }
    return copy;
  }

}
