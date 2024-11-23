package llvm;

public class Value {
    public String value;
    public Type type;

    public Value(String value , Type type) {
        this.value = value;
        this.type = type;
    }

    @Override
    public String toString() {
        return value;
    }
}
