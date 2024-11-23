package llvm;

import lexical.Token;
import symbol.FunctionType;
import symbol.Symbol;
import symbol.SymbolTable;
import symbol.ValueType;
import syntax.nodes.*;
import util.Utilities;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;

public class IRGenerator {

    private FileWriter irWriter;
    private int nextScope;
    private int virtualRegIndex; // 用于进行虚拟寄存器分配
    private int indentSpaceCount = 0; // 用于控制缩进

    private void writeIndent() throws IOException {
        for (int i = 0; i < indentSpaceCount; i++) {
            irWriter.write(" ");
        }
    }

    private void nextLevelIndent() {
        indentSpaceCount+=2;
    }

    private void prevLevelIndent() {
        indentSpaceCount-=2;
    }

    private void resetReg() {
        virtualRegIndex = 0;
    }

    private int allocReg() {
        virtualRegIndex+=1;
        return virtualRegIndex - 1;
    }

    public IRGenerator(FileWriter fileWriter) {
        this.irWriter = fileWriter;
    }

    private void pushScope() { // 进入到子作用域当中，作用域序号+1
        SymbolTable.jumpToSymbolTableByScopeNum(nextScope);
        nextScope += 1;
    }

    private void popScope() { // 返回到父作用域当中
        SymbolTable.backToUpperScope();
    }

    private int currentScope() { // 查询当前的作用域编号
        return SymbolTable.getCurrentSymbolTable().getScopeNum();
    }

    private Symbol getSymbol(String name) {
        return SymbolTable.getCurrentSymbolTable().searchSymbol(name);
    }

    public void codeGen(CompUnit compUnit) throws IOException {
        printCode("declare i32 @getint()          ; 读取一个整数\n" +
                "declare i32 @getchar()     ; 读取一个字符\n" +
                "declare void @putint(i32)      ; 输出一个整数\n" +
                "declare void @putch(i32)       ; 输出一个字符\n" +
                "declare void @putstr(i8*)      ; 输出字符串\n", true);
        nextScope = 1;
        pushScope();
        for (Decl decl : compUnit.decls) {
            codeGen(decl);
        }
        for (FuncDef funcDef : compUnit.funcDefs) {
            codeGen(funcDef);
        }
        codeGen(compUnit.mainFuncDef);
    }

    private void codeGen(Decl decl) throws IOException {
        if (currentScope() == 1) { // 生成全局变量/常量的 IR
            for (VarConstDef varConstDef : decl.varConstDefs) {
                Symbol symbol = getSymbol(varConstDef.ident.name);
                ValueType type = (ValueType) symbol.symbolType;
                printCode("@" + symbol.symbolName + " = ", true);
                symbol.llvmIRSymbol = "@" + symbol.symbolName; // 更新符号表里面的 ir 表示
                if (type.isConst) { // 如果是常量就加上 constant 标签
                    printCode("constant ", false);
                }
                else {
                    printCode("global ", false);
                }
                if (type.arrayLength != null) { // 数组类型
                    String typeTag;
                    if (type.basicType == ValueType.BasicType.CHR) { // 加上类型标签
                        typeTag = "i8";
                    }
                    else {
                        typeTag = "i32";
                    }
                    printCode("[" + type.arrayLength + " x " + typeTag + "] ", false);
                    // 数组初始值
                    printCode("[", false);
                    for (int i = 0; i < type.arrayLength; i++) {
                        printCode(typeTag + " " + symbol.constValues.get(i), false);
                        if (i < type.arrayLength - 1) {
                            printCode(", ", false);
                        }
                    }
                    printCode("]", false);
                }
                else { // 非数组类型
                    if (type.basicType == ValueType.BasicType.CHR) { // 加上类型标签
                        printCode("i8 ", false);
                    }
                    else {
                        printCode("i32 ", false);
                    }
                    // 初始值
                    printCode(String.valueOf(symbol.constValues.get(0)), false);
                }
                printCode("\n", false);
            }
        }
        else { // 生成局部变量的 IR
            for (VarConstDef varConstDef : decl.varConstDefs) {
                Symbol symbol = getSymbol(varConstDef.ident.name);
                ValueType symbolType = (ValueType) symbol.symbolType;
                // 首先在栈上 alloca 一个对应的变量
                int regNum = allocReg();
                printCode("%" + regNum + " = alloca ", true);
                printVariableType(symbolType); // 打印要分配的变量的类型
                printCode("\n", false);
                // 把分配的信息记录到符号表里面去
                symbol.llvmIRSymbol = "%" + regNum;
                // 然后进行变量初始化的操作
                if (symbolType.arrayLength == null) { // 普通变量非数组
                    if (symbol.constValues.get(0) != null) { // 如果变量拥有一个编译期就可以确定的值作为初始值，直接 store
                        printCode("store ", true);
                        printVariableType(symbolType);
                        printCode(" " + symbol.constValues.get(0) + ", ", false); // 打印要存的值
                    }
                    else {
                        Value expValue = codeGen(varConstDef.initVal.expArray.get(0)); // 先生成表达式的代码
                        if (symbolType.basicType == ValueType.BasicType.CHR) {
                            // 如果当前变量是 char 类型，则需要把 i32 的表达式转化为 i8
                            expValue = convertToI8(expValue);
                        }
                        printCode("store ", true);
                        printVariableType(symbolType);
                        printCode(" " + expValue + ", ", false); // 打印要存的值
                    }
                    printVariableType(symbolType);
                    printCode("* " + symbol.llvmIRSymbol, false);
                    printCode("\n", false);
                }
                else { // 数组，给每个元素单独赋值
                    for (int i = 0; i < symbolType.arrayLength; i++) {
                        Value indexValue = getElementPtr(symbol, new Value("" + i, Type.i32()));
                        // store (i32|i8) {initVal[i]}, (i32|i8)* %{regNumPerElement}
                        if (symbol.constValues.get(i) != null) {
                            printCode("store ", true);
                            printBasicType(symbolType);
                            printCode(" " + symbol.constValues.get(i) + ", ", false);
                        }
                        else {
                            Value value = codeGen(varConstDef.initVal.expArray.get(i));
                            // 可能需要对表达式的值进行 trunc
                            if (symbolType.basicType == ValueType.BasicType.CHR) {
                                value = convertToI8(value);
                            }
                            printCode("store ", true);
                            printBasicType(symbolType);
                            printCode(" " + value + ", ", false);
                        }
                        printBasicType(symbolType);
                        printCode("* " + indexValue + "\n", false);
                    }
                }
            }
        }
    }

    private void codeGen(FuncDef funcDef) throws IOException {
        printCode("define ", true);
        if (funcDef.isMain) {
            printCode("i32 @main() {\n", false);
            printCode("entry:\n", true);
            nextLevelIndent();
            resetReg();
            pushScope();
        }
        else {
            Symbol funcSymbol = getSymbol(funcDef.ident.name);
            FunctionType funcType = (FunctionType) funcSymbol.symbolType;
            if (funcType.returnType == FunctionType.ReturnType.VOID) { // 答应函数类型
                printCode("void ", false);
            }
            else if (funcType.returnType == FunctionType.ReturnType.INT) {
                printCode("i32 ", false);
            }
            else {
                printCode("i8 ", false);
            }
            printCode("@" + funcSymbol.symbolName, false); // 打印函数名字
            funcSymbol.llvmIRSymbol = "@" + funcSymbol.symbolName;
            // 打印参数列表
            resetReg(); // 重制虚拟寄存器编号
            pushScope(); // 查找参数，符号表需要进入到函数体内的作用域
            printCode("(", false);
            for (int i = 0;i < funcType.paramNames.size();i++) {
                // 分配虚拟寄存器
                int regNum = allocReg();
                // 更新符号表的 ir 项目
                getSymbol(funcType.paramNames.get(i)).llvmIRSymbol = "%" + regNum;
                printParamType(funcType.paramTypes.get(i));
                printCode(" %" + regNum ,false);
                if (i < funcType.paramTypes.size() - 1) {
                    printCode(", ", false);
                }
            }
            printCode(") {\n", false);
            printCode("entry:\n", true);
            nextLevelIndent();
            // 对于非主函数，要把所有的参数先都保存到栈上
            for (int i = 0;i < funcType.paramNames.size();i++) {
                int regNum = allocReg();
                printCode("%" + regNum + " = alloca ", true);
                printParamType(funcType.paramTypes.get(i));
                printCode("\n", false);
                // 生成 store 语句 store (||) {}
                printCode("store " ,true);
                printParamType(funcType.paramTypes.get(i)); // 打印参数类型
                printCode(" " + getSymbol(funcType.paramNames.get(i)).llvmIRSymbol + ", ",false); // 打印函数签名中为参数分配的虚拟寄存器编号
                // 打印 type* {regNum}
                printParamType(funcType.paramTypes.get(i));
                printCode("* %" + regNum ,false);
                printCode("\n", false);
                // 最后更新符号表内当前符号的 llvm ir 表示
                getSymbol(funcType.paramNames.get(i)).llvmIRSymbol = "%" + regNum;
            }

        }
        // 接下来正式生成函数体
        codeGen(funcDef.block);

        popScope(); // 最后需要回到上一层作用域并且重置缩进
        prevLevelIndent();
        printCode("}\n", true);
    }

    private void codeGen(Block block) throws IOException {
        for (BlockItem blockItem : block.blockItems) {
            if (blockItem.decl != null) {
                codeGen(blockItem.decl);
            }
            else {
                codeGen(blockItem.stmt);
            }
        }
    }

    private void codeGen(Stmt stmt) throws IOException {
        if (stmt.caseNum == 0) { // 'lval' = 'exp'
            Symbol symbol = getSymbol(stmt.lval0.ident.name);
            ValueType symbolType = (ValueType) symbol.symbolType;
            if (stmt.lval0.exp != null) { // 数组元素赋值
                Value indexValue = codeGen(stmt.lval0.exp);
                Value addrValue = getElementPtr(symbol, indexValue); // 获得数组元素的地址
                Value valueToStore = codeGen(stmt.exp0); // 获取要存储的值
                if (symbolType.basicType == ValueType.BasicType.CHR) { // 转化为 i8
                    valueToStore = convertToI8(valueToStore);
                }
                // 生成 store 指令
                printCode("store " + valueToStore.type + " " + valueToStore.value, true);
                printCode(", " + addrValue.type + " " + addrValue.value + "\n", false);
            }
            else { // 普通变量赋值
                Value addrValue;
                if (symbolType.basicType == ValueType.BasicType.CHR) {
                    addrValue = new Value(symbol.llvmIRSymbol, Type.i8ptr());
                }
                else {
                    addrValue = new Value(symbol.llvmIRSymbol, Type.i32ptr());
                }
                Value valueToStore = codeGen(stmt.exp0); // 获取要存储的值
                if (symbolType.basicType == ValueType.BasicType.CHR) { // 转化为 i8
                    valueToStore = convertToI8(valueToStore);
                }
                // 生成 store 指令
                printCode("store " + valueToStore.type + " " + valueToStore.value, true);
                printCode(", " + addrValue.type + " " + addrValue.value + "\n", false);
            }
        }
        else if (stmt.caseNum == 1) { // [exp];
            if (stmt.exp1 != null) {
                codeGen(stmt.exp1);
            }
        }
        else if (stmt.caseNum == 2) { // stmt -> Block
            pushScope();
            nextLevelIndent();
            codeGen(stmt.block2);
            prevLevelIndent();
            popScope();
        }
        else if (stmt.caseNum == 7) { // stmt -> return [exp];
            if (stmt.returnExp7 == null) {
                printCode("ret void\n", true);
            }
            else {
                Value returnValue = codeGen(stmt.returnExp7);
                if (SymbolTable.getCurrentSymbolTable().getCurrentReturnType() == FunctionType.ReturnType.CHR) {
                    returnValue = convertToI8(returnValue);
                }
                printCode("ret " + returnValue.type + " " + returnValue.value + "\n", true);
            }
        }
        else if (stmt.caseNum == 8) { // getint()
            Symbol symbol = getSymbol(stmt.lval8.ident.name);
            ValueType symbolType = (ValueType) symbol.symbolType;
            if (stmt.lval8.exp != null) { // 数组元素赋值
                Value indexValue = codeGen(stmt.lval8.exp);
                Value addrValue = getElementPtr(symbol, indexValue); // 获得数组元素的地址
                int regNum = allocReg();
                printCode("%" + regNum + " = call i32 @getint()\n", true);
                Value valueToStore = new Value("%" + regNum, Type.i32());
                if (symbolType.basicType == ValueType.BasicType.CHR) { // 转化为 i8
                    valueToStore = convertToI8(valueToStore);
                }
                // 生成 store 指令
                printCode("store " + valueToStore.type + " " + valueToStore.value, true);
                printCode(", " + addrValue.type + " " + addrValue.value + "\n", false);
            }
            else { // 普通变量赋值
                Value addrValue;
                if (symbolType.basicType == ValueType.BasicType.CHR) {
                    addrValue = new Value(symbol.llvmIRSymbol, Type.i8ptr());
                }
                else {
                    addrValue = new Value(symbol.llvmIRSymbol, Type.i32ptr());
                }
                int regNum = allocReg(); // 分配一个寄存器来存储要保存的值
                printCode("%" + regNum + " = call i32 @getint()\n", true);
                Value valueToStore = new Value("%" + regNum, Type.i32());
                if (symbolType.basicType == ValueType.BasicType.CHR) { // 转化为 i8
                    valueToStore = convertToI8(valueToStore);
                }
                // 生成 store 指令
                printCode("store " + valueToStore.type + " " + valueToStore.value, true);
                printCode(", " + addrValue.type + " " + addrValue.value + "\n", false);
            }
        }
        else if (stmt.caseNum == 9) { // getchar()
            Symbol symbol = getSymbol(stmt.lval9.ident.name);
            ValueType symbolType = (ValueType) symbol.symbolType;
            if (stmt.lval9.exp != null) { // 数组元素赋值
                Value indexValue = codeGen(stmt.lval9.exp);
                Value addrValue = getElementPtr(symbol, indexValue); // 获得数组元素的地址
                int regNum = allocReg();
                printCode("%" + regNum + " = call i32 @getchar()\n", true);
                Value valueToStore = new Value("%" + regNum, Type.i32());
                if (symbolType.basicType == ValueType.BasicType.CHR) { // 转化为 i8
                    valueToStore = convertToI8(valueToStore);
                }
                // 生成 store 指令
                printCode("store " + valueToStore.type + " " + valueToStore.value, true);
                printCode(", " + addrValue.type + " " + addrValue.value + "\n", false);
            }
            else { // 普通变量赋值
                Value addrValue;
                if (symbolType.basicType == ValueType.BasicType.CHR) {
                    addrValue = new Value(symbol.llvmIRSymbol, Type.i8ptr());
                }
                else {
                    addrValue = new Value(symbol.llvmIRSymbol, Type.i32ptr());
                }
                int regNum = allocReg(); // 分配一个寄存器来存储要保存的值
                printCode("%" + regNum + " = call i32 @getchar()\n", true);
                Value valueToStore = new Value("%" + regNum, Type.i32());
                if (symbolType.basicType == ValueType.BasicType.CHR) { // 转化为 i8
                    valueToStore = convertToI8(valueToStore);
                }
                // 生成 store 指令
                printCode("store " + valueToStore.type + " " + valueToStore.value, true);
                printCode(", " + addrValue.type + " " + addrValue.value + "\n", false);
            }
        }
        else if (stmt.caseNum == 10) { // printf()
            String formatString = stmt.stringConst10.getToken();
            formatString = formatString.substring(1, formatString.length() - 1);
            int expIndex = 0;
            for (int i = 0;i < formatString.length();i++) { // 遍历字符串
                if (i < formatString.length() - 1) {
                    if (formatString.charAt(i) == '%' && formatString.charAt(i + 1) == 'c') { // 输出数字
                        Value valueToOutput = codeGen(stmt.exps10.get(expIndex));
                        outputAChar(valueToOutput);
                        expIndex++;i++;continue;
                    }
                    else if (formatString.charAt(i) == '%' && formatString.charAt(i + 1) == 'd') { //输出字符
                        Value valueToOutput = codeGen(stmt.exps10.get(expIndex));
                        outputANumber(valueToOutput);
                        expIndex++;i++;continue;
                    }
                    else if (formatString.charAt(i) == '\\' && formatString.charAt(i + 1) == 'n') { // 输出换行
                        outputAChar(new Value("10", Type.i32()));
                        i++;continue;
                    }
                }
                // 原样输出
                Value valueToOutput = new Value(
                        String.valueOf((int) formatString.charAt(i)),
                        Type.i32()
                );
                outputAChar(valueToOutput);
            }
        }
        else {
            // TODO
            throw new IOException("暂不支持!");
        }
    }

    private Value codeGen(BiOperandExp exp) throws IOException {
        if (exp.operator == null) {
            // 操作符为null有两种情况，一种是 leftElement 是BiOperandExp，一种是leftElement是UnaryExp
            if (exp.leftElement instanceof UnaryExp) {
                return codeGen((UnaryExp) exp.leftElement);
            }
            else if (exp.leftElement instanceof BiOperandExp) {
                return codeGen((BiOperandExp) exp.leftElement);
            }
            else {
                throw new IOException("不支持的 exp 类型!");
            }
        }
        else { // 操作符不为 null, 一定返回 i32 类型的一个 value
            Value leftValue;
            Value rightValue;
            if (exp.leftElement instanceof UnaryExp) {
                leftValue = codeGen((UnaryExp) exp.leftElement);
            }
            else {
                leftValue = codeGen((BiOperandExp) exp.leftElement);
            }
            if (exp.rightElement instanceof UnaryExp) {
                rightValue = codeGen((UnaryExp) exp.rightElement);
            }
            else {
                rightValue = codeGen((BiOperandExp) exp.rightElement);
            }
            int regNum = allocReg();
            switch (exp.operator.getType()) {
                case PLUS -> printCode("%" + regNum + " = add i32 " + leftValue + ", " + rightValue + "\n", true);
                case MINU -> printCode("%" + regNum + " = sub i32 " + leftValue + ", " + rightValue + "\n", true);
                case MULT -> printCode("%" + regNum + " = mul i32 " + leftValue + ", " + rightValue + "\n", true);
                case DIV -> printCode("%" + regNum + " = sdiv i32 " + leftValue + ", " + rightValue + "\n", true);
                case MOD -> printCode("%" + regNum + " = srem i32 " + leftValue + ", " + rightValue + "\n", true);
                case AND -> {} // TODO
                case OR -> {} // TODO
                case EQL -> {} // TODO
                case NEQ -> {} // TODO
                case LSS -> {} // TODO
                case LEQ -> {} // TODO
                case GRE -> {} // TODO
                case GEQ -> {} // TODO
            }
            return new Value("%" + regNum, Type.i32());
        }
    }

    private Value codeGen(UnaryExp exp) throws IOException {
        // 生成一元表达式的代码
        if (exp.primaryExp != null) { // 基本表达式
            return codeGen(exp.primaryExp);
        }
        else if (exp.ident != null) { // 函数调用
            return codeGen(exp.ident, exp.funcRParams);
        }
        else if (exp.unaryOp != null) { // 一元表达式
            Value value = codeGen(exp.unaryExp);
            if (exp.unaryOp.getType() == Token.TokenType.PLUS) {
                return value;
            }
            else if (exp.unaryOp.getType() == Token.TokenType.MINU) {
                int regNum = allocReg();
                printCode("%" + regNum + " = sub i32 0, " + value + "\n", true);
                return new Value("%" + regNum, Type.i32());
            }
            else if (exp.unaryOp.getType() == Token.TokenType.NOT) {
                // TODO
                return new Value("", Type.i32());
            }
            else {
                throw new IOException("不支持的 UnaryExp 类型!");
            }
        }
        else {
            throw new IOException("不支持的 UnaryExp 类型!");
        }
    }

    private Value codeGen(PrimaryExp exp) throws IOException {
        // 生成基本表达式的代码
        if (exp.exp != null) {
            return codeGen(exp.exp);
        }
        else if (exp.lval != null) {
            return codeGen(exp.lval);
        }
        else if (exp.character != null) {
            // ASCII 范围 32-126，不会出现需要符号扩展的情况
            return new Value("" + Utilities.getASCII(exp.character.getToken()), Type.i32());
        }
        else if (exp.number != null) {
            return new Value(exp.number.getToken(), Type.i32());
        }
        else {
            throw new IOException("不支持的 PrimaryExp!");
        }
    }

    private Value codeGen(Lval lval) throws IOException {
        // 有可能是数组 arr[i] 或者普通变量 var 或者数组变量本身 arr (在函数调用中出现)
        Symbol symbol = getSymbol(lval.ident.name); // 首先检索符号表，找到对应的符号
        ValueType symbolType = (ValueType) symbol.symbolType;
        if (lval.exp == null) {
            if (symbolType.arrayLength == null) {
                // 普通变量 %{regNum} = load (i32|i8), (i32|i8)* {llvmirsymbol}
                int regNum = allocReg();
                printCode("%" + regNum + " = load ", true);
                printBasicType(symbolType);
                printCode(", ", false);
                printBasicType(symbolType);
                printCode("* ", false);
                printCode(symbol.llvmIRSymbol + "\n", false);
                if (symbolType.basicType == ValueType.BasicType.INT) {
                    return new Value("%" + regNum, Type.i32());
                }
                else {
                    return convertToI32(new Value("%" + regNum, Type.i8()));
                }
            }
            else { // 数组变量
                // 需要先获取数组的第一个元素的地址
                Value firstElemPtr = getElementPtr(symbol, new Value("0", Type.i32()));
                return firstElemPtr;
            }
        }
        else {
            // 数组 getelementptr inbounds [n x (i32|i8)], [n x (i32|i8)]* {之前分配的虚拟寄存器}, (i32|i8) 0, (i32|i8) {index}
            Value indexValue = codeGen(lval.exp); // 数组的索引值
            Value addrValue = getElementPtr(symbol, indexValue);
            int regNum = allocReg();
            printCode("%" + regNum + " = load ", true);
            printBasicType(symbolType);
            printCode(", ", false);
            printBasicType(symbolType);
            printCode("* " + addrValue + "\n", false);
            if (symbolType.basicType == ValueType.BasicType.INT) {
                return new Value("%" + regNum, Type.i32());
            }
            else {
                return convertToI32(new Value("%" + regNum, Type.i8()));
            }
        }
    }

    private Value codeGen(Ident ident, FuncRParams funcRParams) throws IOException {
        Symbol symbol = getSymbol(ident.name); // 取得函数符号
        FunctionType functionType = (FunctionType) symbol.symbolType;
        ArrayList<Value> realParamValues = new ArrayList<>(); // 函数实参的各个值
        if (funcRParams != null) {
            for (int i = 0;i < funcRParams.exps.size();i++) { // 分别解析每一个实参，并生成对应的代码
                realParamValues.add(codeGen(funcRParams.exps.get(i))); // 根据约定，表达式的返回一定是 i32 类型
                if (functionType.paramTypes.get(i).arrayLength == null // 如果对应的参数不是数组并且需要一个 char, 则进行 i32 到 i8 的转换
                    && functionType.paramTypes.get(i).basicType == ValueType.BasicType.CHR) {
                    realParamValues.set(i, convertToI8(realParamValues.get(i)));
                }
            }
        }
        // %result = call <return_type> @function_name(<arg_type> <arg_val>, ...)
        if (functionType.returnType == FunctionType.ReturnType.VOID) {
            printCode("call void @" + ident.name + "(", true);
            // 打印出所有的参数，类型 + 值
            for (int i = 0; i < realParamValues.size();i++) {
                if (realParamValues.get(i).type == Type.i32()) {

                }
                else if (realParamValues.get(i).type == Type.i8()) {
                    // TODO
                }
                else {
                    printCode(realParamValues.get(i).type + " " + realParamValues.get(i).value, false);
                }
                if (i < realParamValues.size() - 1) {
                    printCode(", ", false);
                }
            }
            printCode(")\n", false);
            return new Value("", Type.vo());
        }
        else {
            int resultRegNum = allocReg();
            printCode("%" + resultRegNum + " = call " +
                    (functionType.returnType == FunctionType.ReturnType.INT ? "i32" : "i8")
                    + " @" + ident.name + "(", true);
            // 打印出所有的参数，类型 + 值
            for (int i = 0; i < realParamValues.size();i++) {
                printCode(realParamValues.get(i).type + " " + realParamValues.get(i).value, false);
                if (i < realParamValues.size() - 1) {
                    printCode(", ", false);
                }
            }
            printCode(")\n", false);
            if (functionType.returnType == FunctionType.ReturnType.INT) {
                return new Value("%" + resultRegNum, Type.i32());
            }
            else {
                return convertToI32(new Value("%" + resultRegNum, Type.i8() ));
            }
        }
    }

    private void printParamType(ValueType paramValueType) throws IOException { // 打印参数类型
        if (paramValueType.arrayLength != null) { // 打印类型参数
            if (paramValueType.basicType == ValueType.BasicType.INT) {
                printCode("i32*", false);
            }
            else {
                printCode("i8*", false);
            }
        }
        else {
            if (paramValueType.basicType == ValueType.BasicType.INT) {
                printCode("i32", false);
            }
            else {
                printCode("i8", false);
            }
        }
    }

    private void printVariableType(ValueType variableType) throws IOException {
        if (variableType.arrayLength != null) { // 数组类型
            if (variableType.basicType == ValueType.BasicType.INT) {
                printCode("[" + variableType.arrayLength + " x i32]", false);
            }
            if (variableType.basicType == ValueType.BasicType.CHR) {
                printCode("[" + variableType.arrayLength + " x i8]", false);
            }
        }
        else {
            if (variableType.basicType == ValueType.BasicType.INT) {
                printCode("i32", false);
            }
            if (variableType.basicType == ValueType.BasicType.CHR) {
                printCode("i8", false);
            }
        }
    }

    private void printBasicType(ValueType symbolType) throws IOException {
        if (symbolType.basicType == ValueType.BasicType.INT) {
            printCode("i32", false);
        }
        else {
            printCode("i8", false);
        }
    }

    private Value convertToI32(Value i8Value) throws IOException {
        if (i8Value.type.basicType != Type.BasicType.i8) {
            throw new IOException("i8 转换为 i32 出错!");
        }
        int regNum = allocReg();
        printCode("%" + regNum + " = sext i8 " + i8Value + " to i32\n", true);
        return new Value("%" + regNum, Type.i32());
    }

    private Value convertToI8(Value i32Value) throws IOException {
        if (i32Value.type.basicType != Type.BasicType.i32) {
            throw new IOException("i32 转换为 i8 出错!");
        }
        int regNum = allocReg();
        printCode("%" + regNum + " = trunc i32 " + i32Value + " to i8\n", true);
        return new Value("%" + regNum, Type.i8());
    }

    private Value getElementPtr(Symbol symbol, Value indexValue) throws IOException {
        // 需要确保 symbol 是一个数组类型，并且其索引有效
        ValueType symbolType = (ValueType) symbol.symbolType;
        int addrRegNum;
        // 如果符号表中存储的类型是 (i32|i8)**
        if (symbolType.arrayLength == 0) {
            // 先 load 从 (i32|i8)** 得到 (i32|i8)*
            int firstElemAddrRegNum = allocReg();
            printCode("%" + firstElemAddrRegNum + " = load ", true);
            printBasicType(symbolType); // i32* 或 i8*
            printCode("*, ", false);
            printBasicType(symbolType); // i32** 或 i8**
            printCode("** " + symbol.llvmIRSymbol + "\n", false);
            // 然后再根据 (i32|i8)* 得到对应元素的地址
            addrRegNum = allocReg();
            printCode("%" + addrRegNum + " = getelementptr inbounds ", true);
            printBasicType(symbolType);
            printCode(", ", false);
            printBasicType(symbolType);
            printCode("*", false);
            printCode(" %" + firstElemAddrRegNum + ", ", false);
            printCode("i32 " + indexValue + "\n", false);
        }
        else { // 如果符号表中存储的类型是 [n x (i32|i8)]*
            addrRegNum = allocReg();
            printCode("%" + addrRegNum + " = getelementptr inbounds ", true);
            printVariableType(symbolType);
            printCode(", ", false);
            printVariableType(symbolType);
            printCode("*", false);
            printCode(" " + symbol.llvmIRSymbol + ", ", false);
            printCode("i32 0, i32 " + indexValue + "\n", false);
        }
        return new Value(
                "%" + addrRegNum,
                symbolType.basicType == ValueType.BasicType.INT ? Type.i32ptr() : Type.i8ptr()
        );
    }

    private void outputAChar(Value outputValue) throws IOException {
        if (outputValue.type.basicType != Type.BasicType.i32) {
            throw new IOException("输出字符时类型错误!");
        }
        printCode("call void @putch(" + outputValue.type + " " + outputValue.value + ")\n", true);
    }

    private void outputANumber(Value outputValue) throws IOException {
        if (outputValue.type.basicType != Type.BasicType.i32) {
            throw new IOException("输出数字时类型错误!");
        }
        printCode("call void @putint(" + outputValue.type + " " + outputValue.value + ")\n", true);
    }

    private void printCode(String code, boolean indent) throws IOException {
        if (indent) {
            writeIndent();
        }
        irWriter.write(code);
    }

}
