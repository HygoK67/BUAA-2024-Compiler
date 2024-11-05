package util;

public class Utilities {

    public static boolean isIdentifierNonDigitCharacter(char ch) {
        return ch == '_' || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
    }

    public static boolean isDigit(char ch) {
        return ch >= '0' && ch <= '9';
    }

    public static Integer getASCII(String str) {
        if (str.charAt(0) != '\'' || str.charAt(str.length()-1) != '\'') {
            System.out.println("not a valid ASCII string");
        }
        else {
            String charStr = str.substring(1, str.length()-1);
            if (charStr.equals("\\n")) {
                return (int) '\n';
            }
            else if (charStr.equals("\\r")) {
                return (int) '\r';
            }
            else if (charStr.equals("\\t")) {
                return (int) '\t';
            }
            else if (charStr.equals("\\f")) {
                return (int) '\f';
            }
            else if (charStr.equals("\\0")) {
                return (int) '\0';
            }
            else if (charStr.equals("\\\\")) {
                return (int) '\\';
            }
            else if (charStr.equals("\\a")) {
                return 7;
            }
            else if (charStr.equals("\\b")) {
                return 8;
            }
            else if (charStr.equals("\\v")) {
                return 11;
            }
            else if (charStr.equals("\"")) {
                return (int) '\"';
            }
            else if (charStr.equals("\'")) {
                return (int) '\'';
            }
            else {
                if (charStr.length() != 1) {
                    System.out.println("监测到非法的字符");
                }
                else {
                    return (int) charStr.charAt(0);
                }
            }
        }
        return null;
    }
}
