package vn.techflow.manager.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping({
            "/",
            "/login",
            "/pipeline",
            "/videos",
            "/videos/{id:[0-9]+}",
            "/calendar",
            "/admin/users"
    })
    public String index() {
        return "forward:/index.html";
    }
}
