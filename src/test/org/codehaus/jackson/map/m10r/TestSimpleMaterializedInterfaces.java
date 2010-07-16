package org.codehaus.jackson.map.m10r;

import static org.junit.Assert.assertArrayEquals;

import org.codehaus.jackson.map.BaseMapTest;
import org.codehaus.jackson.map.ObjectMapper;

public class TestSimpleMaterializedInterfaces
    extends BaseMapTest
{
    /*
    /**********************************************************
    /* Test classes, enums
    /**********************************************************
     */

    public interface Bean {
        public int getX();
        public String getA();
    }

    public interface PartialBean {
        public boolean isOk();
        // and then non-getter/setter one:
        public int foobar();
    }
    
    public interface BeanHolder {
        public Bean getBean();
    }
    
    // then invalid one; conflicting setter/getter types
    public interface InvalidBean {
        public int getX();
        public void setX(String value);
    }

    public interface ArrayBean {
        public int[] getValues();
        public String[] getWords();
        public void setWords(String[] words);
    }
    
    /*
    /**********************************************************
    /* Unit tests, low level
    /**********************************************************
     */

    /**
     * First test verifies that bean builder works as expected
     */
    public void testLowLevelMaterializer() throws Exception
    {
        AbstractTypeMaterializer mat = new AbstractTypeMaterializer();
        Class<?> impl = mat.materializeClass(Bean.class);
        assertNotNull(impl);
        assertTrue(Bean.class.isAssignableFrom(impl));
        // also, let's instantiate to make sure:
        Object ob = impl.newInstance();
        // and just for good measure do actual cast
        Bean bean = (Bean) ob;
        // call something to ensure generation worked...
        assertNull(bean.getA());
    }

    public void testLowLevelMaterializerFailOnIncompatible() throws Exception
    {
        AbstractTypeMaterializer mat = new AbstractTypeMaterializer();
        try {
            mat.materializeClass(InvalidBean.class);
            fail("Expected exception for incompatible property types");
        } catch (IllegalArgumentException e) {
            verifyException(e, "incompatible types");
        }
    }

    public void testLowLevelMaterializerFailOnUnrecognized() throws Exception
    {
        AbstractTypeMaterializer mat = new AbstractTypeMaterializer();
        //  by default early failure is disabled, enable:
        mat.enable(AbstractTypeMaterializer.Feature.FAIL_ON_UNMATERIALIZED_METHOD);
        try {
            mat.materializeClass(PartialBean.class);
            fail("Expected exception for unrecognized method");
        } catch (IllegalArgumentException e) {
            verifyException(e, "Unrecognized abstract method 'foobar'");
        }        
    }
    
    /*
    /**********************************************************
    /* Unit tests, higher level
    /**********************************************************
     */

    /**
     * Test simple leaf-level bean with 2 implied _beanProperties
     */
    public void testSimpleInteface() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.getDeserializationConfig().setAbstractTypeResolver(new AbstractTypeMaterializer());
        Bean bean = mapper.readValue("{\"a\":\"value\",\"x\":123 }", Bean.class);
        assertNotNull(bean);
        assertEquals("value", bean.getA());
        assertEquals(123, bean.getX());
    }

    /**
     * Then one bean holding a reference to another (leaf-level) bean
     */
    public void testBeanHolder() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.getDeserializationConfig().setAbstractTypeResolver(new AbstractTypeMaterializer());
        BeanHolder holder = mapper.readValue("{\"bean\":{\"a\":\"b\",\"x\":-4 }}", BeanHolder.class);
        assertNotNull(holder);
        Bean bean = holder.getBean();
        assertNotNull(bean);
        assertEquals("b", bean.getA());
        assertEquals(-4, bean.getX());
    }    
    
    public void testArrayInterface() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.getDeserializationConfig().setAbstractTypeResolver(new AbstractTypeMaterializer());
        ArrayBean bean = mapper.readValue("{\"values\":[1,2,3], \"words\": [ \"cool\", \"beans\" ] }",
                ArrayBean.class);
        assertNotNull(bean);
        assertArrayEquals(new int[] { 1, 2, 3} , bean.getValues());
        assertArrayEquals(new String[] { "cool", "beans" } , bean.getWords());
    }

    /*
    /**********************************************************
    /* Unit tests, higher level, error handling
    /**********************************************************
     */

    /**
     * Test to verify that materializer will by default create exception-throwing methods
     * for "unknown" abstract methods
     */
    public void testPartialBean() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        AbstractTypeMaterializer mat = new AbstractTypeMaterializer();
        // ensure that we will only get deferred error methods
        mat.disable(AbstractTypeMaterializer.Feature.FAIL_ON_UNMATERIALIZED_METHOD);
        mapper.getDeserializationConfig().setAbstractTypeResolver(mat);
        PartialBean bean = mapper.readValue("{\"ok\":true}", PartialBean.class);
        assertNotNull(bean);
        assertTrue(bean.isOk());
        // and then exception
        try {
            bean.foobar();
        } catch (UnsupportedOperationException e) {
            verifyException(e, "Unimplemented method 'foobar'");
        }
    }
    
}