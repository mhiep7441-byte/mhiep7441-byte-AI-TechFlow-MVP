package vn.techflow.manager.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping({"/", "/pipeline", "/calendar"})
    public String index() {
        return "forward:/index.html";
    }
}
