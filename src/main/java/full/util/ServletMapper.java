package full.util;

public class ServletMapper {
    
    public static String mapUrlToClass(String path) {
    	if (path.matches("^/([a-zA-Z_][\\w\\.]*)$")) {	
    		String inputUrl = path.substring(1);
    	
    		if (!inputUrl.startsWith("service.")) {
    			return "full.service." + inputUrl.replace("/", ".");
    		} else {
    			return "full." + inputUrl.replace("/", ".");
    		}
    	} else {
    		return null;
    	}
    }
}