package yanchaotest.objectFactory;

import org.apache.ibatis.reflection.factory.DefaultObjectFactory;

/**
 * @author yanchao
 * @date 2018/3/29 13:38
 */
public class MyObjectFactory extends DefaultObjectFactory {

    @Override
    protected Class<?> resolveInterface(Class<?> type) {
        Class<?> classToCreate = super.resolveInterface(type);
        if (classToCreate == type && classToCreate.isInterface()) {
            System.out.println("MyObjectFactory#resolveInterface(Class) invoked");
            classToCreate = MyInterfaceImpl.class;
        }
        return classToCreate;
    }
}
