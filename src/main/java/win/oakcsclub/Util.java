package win.oakcsclub;

public class Util {
    interface Exceptiony<T> {
        T run() throws Exception;
    }
    public static <T> T unexception(Exceptiony<T> t) {
        try { return t.run(); }catch(Exception e) { throw new RuntimeException(e); }
    }
}
