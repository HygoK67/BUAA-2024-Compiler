package program;

import java.util.ArrayList;
import java.util.Comparator;

public class ProgramException {

    public static ArrayList<ProgramException> exceptions = new ArrayList<>();

    private int lineNum;

    private char errorCode;

    public static void newException(int lineNum, char errorCode) {
        exceptions.add(new ProgramException(lineNum, errorCode));
    }

    public static void sortExceptions() {
        exceptions.sort(new Comparator<ProgramException>() {
            @Override
            public int compare(ProgramException o1, ProgramException o2) {
                return o1.getLineNum() - o2.getLineNum();
            }
        });
    }

    public int getLineNum() {
        return lineNum;
    }

    public void setLineNum(int lineNum) {
        this.lineNum = lineNum;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(char errorCode) {
        this.errorCode = errorCode;
    }

    public ProgramException(int lineNum, char errorCode) {
        this.lineNum = lineNum;
        this.errorCode = errorCode;
    }

    @Override
    public String toString() {
        return this.lineNum + " " + this.errorCode;
    }

}
