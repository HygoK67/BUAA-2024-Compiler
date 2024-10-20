package lexical;

import program.ProgramException;
import program.SourceProgram;
import util.Utilities;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Lexer {

    private SourceProgram inputProgram;

    private ArrayList<Token> tokens;

    private int currentToken;

    private int currentLine;

    private boolean debugFlag;
    private FileWriter debugWriter;

    public Lexer(SourceProgram inputProgram, boolean debugFlag, FileWriter debugWriter) {
        this.debugFlag = debugFlag;
        this.debugWriter = debugWriter;
        this.inputProgram = inputProgram;
        this.tokens = new ArrayList<>();
        this.currentLine = 0;
        while (this.currentLine < this.inputProgram.getLineNum()) {
            String line = this.inputProgram.getLine(this.currentLine);
            int index = 0;
            while (index < line.length()) {
                char ch = line.charAt(index);
                System.out.println("reading" + " " + ch);
                if (ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n') { // 跳过任何空白字符
                    index++;
                    continue;
                }
                if (Utilities.isIdentifierNonDigitCharacter(ch)) { // 标识符和保留字
                    StringBuilder tokenBuilder = new StringBuilder();
                    tokenBuilder.append(ch);
                    while (true) {
                        index += 1;
                        if (index >= line.length()) {
                            break;
                        }
                        ch = line.charAt(index);
                        if (Utilities.isIdentifierNonDigitCharacter(ch) || Utilities.isDigit(ch)) {
                            tokenBuilder.append(ch);
                        }
                        else {
                            break;
                        }
                    }
                    tokens.add(new Token(tokenBuilder.toString(), this.currentLine));
                }
                else if (Utilities.isDigit(ch)) { // 整形常量
                    StringBuilder tokenBuilder = new StringBuilder();
                    tokenBuilder.append(ch);
                    while (true) {
                        index += 1;
                        if (index >= line.length()) {
                            break;
                        }
                        ch = line.charAt(index);
                        if (Utilities.isDigit(ch)) {
                            tokenBuilder.append(ch);
                        } else {
                            break;
                        }
                    }
                    tokens.add(new Token(Token.TokenType.INTCON, tokenBuilder.toString(), this.currentLine));
                }
                else if (ch == '"') { // 字符串常量
                    StringBuilder tokenBuilder = new StringBuilder();
                    tokenBuilder.append(ch);
                    while (true) {
                        index += 1;
                        if (index >= line.length()) {
                            // 字符串未结束报错
                        }
                        ch = line.charAt(index);
                        if (ch == '"') {
                            index += 1;
                            tokenBuilder.append(ch);
                            break;
                        }
                        else if (ch <= 126 && ch >= 32) {
                            tokenBuilder.append(ch);
                        }
                        else {
                            // 字符串常量中出现非法字符报错
                        }
                    }
                    tokens.add(new Token(Token.TokenType.STRCON, tokenBuilder.toString(), this.currentLine));
                }
                else if (ch == '\'') { // 字符常量
                    index += 1;
                    char ch2 = line.charAt(index); // 查看第二个字符
                    if (ch2 == '\\') { // 转译字符
                        index += 1;
                        ch2 = line.charAt(index); // 要转译的字符，可以是 ' " 或 \
                        index += 1;
                        if (line.charAt(index) == '\'') {
                            tokens.add(new Token(Token.TokenType.CHRCON, "\'\\" + ch2 + "\'", this.currentLine));
                            index += 1;
                        }
                        else {
                            // 字符常量未正确结束报错
                        }
                    }
                    else {
                        ch2 = line.charAt(index);
                        index += 1;
                        if (line.charAt(index) == '\'') {
                            tokens.add(new Token(Token.TokenType.CHRCON, "\'" + ch2 + "\'", this.currentLine));
                            index += 1;
                        }
                        else {
                            // 字符常量未正确结束报错
                        }
                    }
                }
                else if (ch == '!') { // ! 和 != 号
                    if (line.charAt(index+1) == '=') { // !=
                        index += 2;
                        tokens.add(new Token("!=", this.currentLine));
                    }
                    else {
                        index++;
                        tokens.add(new Token('!', this.currentLine));
                    }
                }
                else if (ch == '&') { // && 符号
                    if (line.charAt(index+1) == '&') {
                        index += 2;
                        tokens.add(new Token("&&", this.currentLine));
                    }
                    else {
                        // 词法分析 a 类型错误
                        index += 1;
                        tokens.add(new Token(Token.TokenType.AND, "&", this.currentLine));
                        ProgramException.newException(this.currentLine + 1, 'a');
                    }
                }
                else if (ch == '|') { // || 符号
                    if (line.charAt(index+1) == '|') {
                        index += 2;
                        tokens.add(new Token("||", this.currentLine));
                    }
                    else {
                        // 词法分析 a 类型错误
                        index += 1;
                        tokens.add(new Token(Token.TokenType.AND, "|", this.currentLine));
                        ProgramException.newException(this.currentLine + 1, 'a');
                    }
                }
                else if (ch == '=') { // = 号和 == 号
                    if (line.charAt(index+1) == '=') { // ==
                        index += 2;
                        tokens.add(new Token("==", this.currentLine));
                    }
                    else {
                        index += 1;
                        tokens.add(new Token('=', this.currentLine));
                    }
                }
                else if (ch == '<') { // < 和 <=
                    if (line.charAt(index+1) == '=') {
                        index += 2;
                        tokens.add(new Token("<=", this.currentLine));
                    }
                    else {
                        index += 1;
                        tokens.add(new Token('<', this.currentLine));
                    }
                }
                else if (ch == '>') { // > 和 >=
                    if (line.charAt(index+1) == '=') {
                        index += 2;
                        tokens.add(new Token(">=", this.currentLine));
                    }
                    else {
                        index += 1;
                        tokens.add(new Token('>', this.currentLine));
                    }
                }
                else if (ch == '+' || ch == '-' || ch == '*' || ch == '%' || ch == ';' || ch == ',' || ch == ')' || ch == '(' || ch == '{' || ch == '}' || ch == '[' || ch == ']') {
                    index+=1;
                    tokens.add(new Token(ch, this.currentLine));
                }
                else if (ch == '/') {
                    if (line.charAt(index+1) == '/') { // 单行注释，后面的内容都不重要了，直接跳过本行
                        break;
                    }
                    else if (line.charAt(index+1) == '*') { // 多行注释
                        index += 2;
                        while (true) {
                            if (index >= line.length() - 1) {
                                index = 0;
                                this.currentLine++;
                                line = this.inputProgram.getLine(this.currentLine);
                            }
                            if (line.charAt(index) == '*' && line.charAt(index+1) == '/') {
                                index += 2;
                                break;
                            }
                            else {
                                index += 1;
                            }
                        }
                    }
                    else { // 单个 / 号
                        index += 1;
                        tokens.add(new Token(ch, this.currentLine));
                    }
                }
                else {
                    // 如果不属于上面任何一个情况，那么就报错
                    throw new UnknownError();
                }
            }
            this.currentLine++;
        }
        tokens.add(new Token(Token.TokenType.EOF, "", this.currentLine));
    }

    public boolean nextToken() throws IOException {
        if (this.currentToken < this.tokens.size() - 1) {
            if (debugFlag) {
                debugWriter.write(this.tokens.get(this.currentToken).getType() + " " + this.tokens.get(this.currentToken).getToken() + "\n");
            }
            this.currentToken++;
            return true;
        }
        else {
            return false;
        }
    }

    public boolean prevToken() {
        if (this.currentToken > 0) {
            this.currentToken--;
            return true;
        }
        else {
            return false;
        }
    }

    public Token getCurrentToken() {
        return this.tokens.get(this.currentToken);
    }
    public Token tokenPreRead(int bias) {
        return this.tokens.get(this.currentToken + bias);
    }

    public int getCurrentTokenIndex() {
        return this.currentToken;
    }

    public Token getTokenAt(int pos) {
        return this.tokens.get(pos);
    }

}
