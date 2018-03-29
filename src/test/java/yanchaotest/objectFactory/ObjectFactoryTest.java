package yanchaotest.objectFactory;

import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;

/**
 * @author yanchao
 * @date 2018/3/29 11:36
 */
public class ObjectFactoryTest {

    public static void main(String[] args) {
        /*ObjectFactory objectFactory = new DefaultObjectFactory();
        PrivateConstructorClass pcc = objectFactory.create(PrivateConstructorClass.class);
        pcc.print("ObjectFactory测试");*/
        ObjectFactory objectFactory = new MyObjectFactory();
        MyInterface myInterface = objectFactory.create(MyInterface.class);
        System.out.println(myInterface.getClass());
    }
}
