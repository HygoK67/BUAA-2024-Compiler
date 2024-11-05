package semantics;

import symbol.ValueType;

public class ExpInfo {
    public Integer value;
    public ValueType type;

    public ExpInfo() {
        value = null;
        type = new ValueType();
    }
}
