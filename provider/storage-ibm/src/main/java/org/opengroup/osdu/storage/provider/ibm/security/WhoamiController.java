/* Licensed Materials - Property of IBM              */
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/

package org.opengroup.osdu.storage.provider.ibm.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class WhoamiController {
    @RequestMapping(value = {"/", "/whoami"})
    @ResponseBody
    public String whoami() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        String userName = auth.getName();
        String roles = String.valueOf(auth.getAuthorities());
        String details = String.valueOf(auth.getPrincipal());

        return "user: " + userName + "<BR>" +
                "roles: " + roles + "<BR>" +
                "details: " + details;
    }
}
