package program;

import java.util.ArrayList;

public class SourceProgram {

    private ArrayList<String> lines;

    public SourceProgram() {
        this.lines = new ArrayList<>();
    }

    public String getLine(int lineIndex) {
        return this.lines.get(lineIndex);
    }

    public void addLine(String line) {
        this.lines.add(line);
    }

    public int getLineNum() {
        return this.lines.size();
    }

}
