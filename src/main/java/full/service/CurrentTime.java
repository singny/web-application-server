package full.service;

import full.interfaces.HttpRequest;
import full.interfaces.HttpResponse;
import full.interfaces.SimpleServlet;

import java.io.IOException;
import java.io.Writer;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CurrentTime implements SimpleServlet {
	private static final Logger logger = LoggerFactory.getLogger(CurrentTime.class);
	private DateTimeFormatter sDateTimeFormatter;

    @Override
    public void service(HttpRequest req, HttpResponse res) {
        try {
	    	Writer writer = res.getWriter();
	        writer.write("HTTP/1.1 200 OK\r\n"); // 상태 라인
	        writer.write("Content-Type: text/plain; charset=UTF-8\r\n"); // 헤더
	        writer.write("\r\n"); // 헤더와 바디를 구분하는 빈 줄
	        
	        LocalDateTime now = LocalDateTime.now();
	        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	        String currentTime = now.format(formatter);

	        writer.write("Current time is: " + currentTime);
	        writer.flush();
	        
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
    }
}