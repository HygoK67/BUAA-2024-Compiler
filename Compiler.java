import lexical.Lexer;
import llvm.IRGenerator;
import program.ProgramException;
import program.SourceProgram;
import semantics.Visitor;
import symbol.SymbolTable;
import syntax.Parser;
import syntax.nodes.CompUnit;

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
        FileWriter visitorWriter = new FileWriter("symbol.txt");
        FileWriter errorWriter = new FileWriter("error.txt");

        Lexer lexer = new Lexer(program, true, parseWriter);
        Parser parser = new Parser(lexer, true, parseWriter);
        Visitor visitor = new Visitor(false, visitorWriter);

        CompUnit compUnit = null;

        try {
            compUnit = parser.parseCompUnit();
            visitor.visitCompUnit(compUnit);
            SymbolTable.printSymbolTable(visitorWriter);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        // 输出错误
        ProgramException.sortExceptions();
        for (ProgramException exception : ProgramException.exceptions) {
            errorWriter.write(exception.toString() + "\n");
        }

        if (!ProgramException.containsSemanticsException()) { // 保证没有语义分析的错误再进行代码生成
            FileWriter irWriter = new FileWriter("llvm_ir.txt");
            IRGenerator irGenerator = new IRGenerator(irWriter);
            irGenerator.codeGen(compUnit);
            irWriter.close();
        }

        // 关闭文件输出流
        parseWriter.close();
        errorWriter.close();
        visitorWriter.close();

    }
}
