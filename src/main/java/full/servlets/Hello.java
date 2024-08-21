package full.servlets;

import full.interfaces.HttpRequest;
import full.interfaces.HttpResponse;
import full.interfaces.SimpleServlet;

import java.io.IOException;
import java.io.Writer;

public class Hello implements SimpleServlet {
    @Override
    public void service(HttpRequest req, HttpResponse res) {
        try {
            Writer writer = res.getWriter();
            writer.write("Hello, ");
            String name = req.getParameter("name");
            if (name != null) {
                writer.write(name);
            } else {
                writer.write("world");
            }
            writer.write("!");
            res.setHeader("Content-Type", "text/plain");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}