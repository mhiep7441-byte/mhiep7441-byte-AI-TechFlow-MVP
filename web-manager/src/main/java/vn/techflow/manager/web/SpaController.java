package vn.techflow.manager.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping({"/", "/login", "/pipeline", "/videos", "/videos/{id:[0-9]+}", "/campaigns", "/calendar", "/profile", "/admin", "/admin/users", "/analytics"})
    public String index() {
        return "forward:/index.html";
    }
}
