import lexical.Lexer;
import program.ProgramException;
import program.SourceProgram;
import syntax.Parser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

public class Compiler {
    public static void main(String[] args) throws IOException {
        File inputFile = new File("testfile.txt");
        Scanner scanner = new Scanner(inputFile);
        SourceProgram program = new SourceProgram();
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            program.addLine(line);
        }

        FileWriter parseWriter = new FileWriter("parser.txt");
        FileWriter errorWriter = new FileWriter("error.txt");

        Lexer lexer = new Lexer(program, true, parseWriter);
        Parser parser = new Parser(lexer, true, parseWriter);

        try {
            parser.parseCompUnit();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        ProgramException.sortExceptions();

        for (ProgramException exception : ProgramException.exceptions) {
            errorWriter.write(exception.toString() + "\n");
        }

        parseWriter.close();
        errorWriter.close();
    }
}
