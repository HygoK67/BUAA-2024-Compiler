package lexical;

public class Token {

    private String token;

    public enum TokenType {
        IDENFR,
        INTCON,
        STRCON,
        CHRCON,
        MAINTK,
        CONSTTK,
        INTTK,
        CHARTK,
        BREAKTK,
        CONTINUETK,
        IFTK,
        ELSETK,
        NOT,
        AND,
        OR,
        FORTK,
        GETINTTK,
        GETCHARTK,
        PRINTFTK,
        RETURNTK,
        PLUS,
        MINU,
        VOIDTK,
        MULT,
        DIV,
        MOD,
        LSS,
        LEQ,
        GRE,
        GEQ,
        EQL,
        NEQ,
        ASSIGN,
        SEMICN,
        COMMA,
        LPARENT,
        RPARENT,
        LBRACK,
        RBRACK,
        LBRACE,
        RBRACE,
        EOF
    };

    private TokenType type;

    private int line;

    public Token(TokenType type, String token, int line) {
        this.token = token;
        this.type = type;
        this.line = line;
    }

    public Token(String token, int line) {
        this.token = token;
        this.line = line;
        if (token.equals("main")) {
            this.type = TokenType.MAINTK;
        }
        else if (token.equals("const")) {
            this.type = TokenType.CONSTTK;
        }
        else if (token.equals("int")) {
            this.type = TokenType.INTTK;
        }
        else if (token.equals("char")) {
            this.type = TokenType.CHARTK;
        }
        else if (token.equals("break")) {
            this.type = TokenType.BREAKTK;
        }
        else if (token.equals("continue")) {
            this.type = TokenType.CONTINUETK;
        }
        else if (token.equals("if")) {
            this.type = TokenType.IFTK;
        }
        else if (token.equals("else")) {
            this.type = TokenType.ELSETK;
        }
        else if (token.equals("for")) {
            this.type = TokenType.FORTK;
        }
        else if (token.equals("getint")) {
            this.type = TokenType.GETINTTK;
        }
        else if (token.equals("getchar")) {
            this.type = TokenType.GETCHARTK;
        }
        else if (token.equals("printf")) {
            this.type = TokenType.PRINTFTK;
        }
        else if (token.equals("return")) {
            this.type = TokenType.RETURNTK;
        }
        else if (token.equals("void")) {
            this.type = TokenType.VOIDTK;
        }
        else if (token.equals("&&")) {
            this.type = TokenType.AND;
        }
        else if (token.equals("||")) {
            this.type = TokenType.OR;
        }
        else if (token.equals("<=")) {
            this.type = TokenType.LEQ;
        }
        else if (token.equals(">=")) {
            this.type = TokenType.GEQ;
        }
        else if (token.equals("==")) {
            this.type = TokenType.EQL;
        }
        else if (token.equals("!=")) {
            this.type = TokenType.NEQ;
        }
        else {
            this.type = TokenType.IDENFR;
        }
    }

    public Token(char ch, int line) {
        this.token = "" + ch;
        this.line = line;
        if (ch == '!') {
            this.type = TokenType.NOT;
        }
        else if (ch == '(') {
            this.type = TokenType.LPARENT;
        }
        else if (ch == ')') {
            this.type = TokenType.RPARENT;
        }
        else if (ch == '{') {
            this.type = TokenType.LBRACE;
        }
        else if (ch == '}') {
            this.type = TokenType.RBRACE;
        }
        else if (ch == '[') {
            this.type = TokenType.LBRACK;
        }
        else if (ch == ']') {
            this.type = TokenType.RBRACK;
        }
        else if (ch == ';') {
            this.type = TokenType.SEMICN;
        }
        else if (ch == ',') {
            this.type = TokenType.COMMA;
        }
        else if (ch == '+') {
            this.type = TokenType.PLUS;
        }
        else if (ch == '-') {
            this.type = TokenType.MINU;
        }
        else if (ch == '*') {
            this.type = TokenType.MULT;
        }
        else if (ch == '/') {
            this.type = TokenType.DIV;
        }
        else if (ch == '%') {
            this.type = TokenType.MOD;
        }
        else if (ch == '<') {
            this.type = TokenType.LSS;
        }
        else if (ch == '>') {
            this.type = TokenType.GRE;
        }
        else if (ch == '=') {
            this.type = TokenType.ASSIGN;
        }
        else {
            throw new UnknownError();
        }
    }

    public TokenType getType() {
        return type;
    }

    public String getToken() {
        return token;
    }

    public int getLine() {
        return line;
    }

}
