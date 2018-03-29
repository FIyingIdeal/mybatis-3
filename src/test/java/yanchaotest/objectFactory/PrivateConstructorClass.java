package yanchaotest.objectFactory;

/**
 * @author yanchao
 * @date 2018/3/29 11:37
 */
public class PrivateConstructorClass {

    private String message;

    private PrivateConstructorClass() {}
    private PrivateConstructorClass(String message) {
        this.message = message;
    }

    public static PrivateConstructorClass forObject() {
        return new PrivateConstructorClass();
    }

    public void print() {
        System.out.println(this.message);
    }

    public void print(String message) {
        System.out.println(message);
    }
}
