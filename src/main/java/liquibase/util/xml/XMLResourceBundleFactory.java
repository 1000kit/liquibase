package liquibase.util.xml;

import java.io.InputStream;

public class XMLResourceBundleFactory {

    public static XMLResourceBundle create(InputStream in) throws Exception {
        return new XMLResourceBundle(in);
    }
}
