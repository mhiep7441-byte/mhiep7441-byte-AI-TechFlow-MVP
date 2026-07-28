package vn.techflow.manager.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping({"/", "/login", "/pipeline", "/videos", "/videos/{id:[0-9]+}", "/campaigns",
            "/campaigns/{id:[0-9]+}", "/research", "/calendar", "/profile", "/admin",
            "/admin/users", "/admin/feedback", "/analytics"})
    public String index() {
        return "forward:/index.html";
    }
}
