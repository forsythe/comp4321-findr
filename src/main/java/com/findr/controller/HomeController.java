package com.findr.controller;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import java.util.*;
/**
 * This class is responsible for serving content for the main search page. Session scope indicates that
 * each user gets their own version of this homecontroller in their own thread.
 */
@Controller
@Scope("session")
public class HomeController {

    /**
     * This function tells spring which file (home.html) to show when the user goes to the root url "/"
     *
     * @param model The model object we can put values into. In the templates folder, we can access variables we added
     *              into the model directly by name. E.g. if we add an attribute "message", then in the html template,
     *              we can access {message} as a variable in our html
     * @return the name of the html page to display at this url
     */
    @RequestMapping(value = {"/"}, method = RequestMethod.GET)
    public String showHomePage(Model model) {
        model.addAttribute("message", "wassup bro");
        model.addAttribute("isMorning", dayOrNight());
        return "home";
    }

    public static boolean dayOrNight() {
        boolean isMorning = false;
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        if(hour >= 6 && hour < 18) {
            isMorning = true;
        }
        return  isMorning;
    }
}
