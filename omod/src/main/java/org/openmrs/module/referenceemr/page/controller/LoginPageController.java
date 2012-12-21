/*
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */

package org.openmrs.module.referenceemr.page.controller;

import org.openmrs.Location;
import org.openmrs.api.LocationService;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ContextAuthenticationException;
import org.openmrs.api.db.ContextDAO;
import org.openmrs.module.emr.EmrConstants;
import org.openmrs.module.emr.EmrContext;
import org.openmrs.module.emr.api.EmrService;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.ui.framework.page.PageRequest;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;

/**
 *
 */
public class LoginPageController {
	
	public String get(
	        PageModel pageModel,
	        @SpringBean EmrService emrService,
	        @CookieValue(value = EmrConstants.COOKIE_NAME_LAST_SESSION_LOCATION, required = false) String lastSessionLocationId,
	        @SpringBean("locationService") LocationService locationService, EmrContext context, UiUtils ui,
	        PageRequest request) {
		
		if (context.isAuthenticated()) {
			return "redirect:" + ui.pageLink("mirebalais", "home");
		}
		
		// Since the user isn't authenticated, we need to use proxy privileges to get locations via the API
		// TODO consider letting the Anonymous role have the Get Location privilege instead of using proxy privileges
		try {
			Context.addProxyPrivilege(PrivilegeConstants.VIEW_LOCATIONS);
			
			pageModel.addAttribute("locations", emrService.getLoginLocations());
			Location lastSessionLocation = null;
			try {
				lastSessionLocation = locationService.getLocation(Integer.valueOf(lastSessionLocationId));
			}
			catch (Exception ex) {
				// pass
			}
			pageModel.addAttribute("lastSessionLocation", lastSessionLocation);
			return null;
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.VIEW_LOCATIONS);
		}
	}
	
	public String post(@RequestParam(value = "username", required = false) String username,
	        @RequestParam(value = "password", required = false) String password,
	        @RequestParam(value = "sessionLocation", required = false) Integer sessionLocationId,
	        @SpringBean ContextDAO contextDao, @SpringBean("locationService") LocationService locationService, UiUtils ui,
	        EmrContext context, PageRequest request) {
		
		HttpSession httpSession = request.getRequest().getSession();
		Location sessionLocation = null;
		try {
			// TODO as above, grant this privilege to Anonymous instead of using a proxy privilege
			Context.addProxyPrivilege(PrivilegeConstants.VIEW_LOCATIONS);
			
			if (sessionLocationId != null) {
				sessionLocation = locationService.getLocation(sessionLocationId);
				if (!sessionLocation.hasTag(EmrConstants.LOCATION_TAG_SUPPORTS_LOGIN)) {
					// the UI shouldn't allow this, but protect against it just in case
					httpSession.setAttribute(EmrConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE, ui
					        .message("mirebalais.login.error.invalidLocation"));
					return "redirect:" + ui.pageLink("mirebalais", "login");
				}
			}
			if (sessionLocation == null) {
				httpSession.setAttribute(EmrConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE, ui
				        .message("mirebalais.login.error.locationRequired"));
				return "redirect:" + ui.pageLink("mirebalais", "login");
			}
			// Set a cookie, so next time someone logs in on this machine, we can default to that same location
			request.setCookieValue(EmrConstants.COOKIE_NAME_LAST_SESSION_LOCATION, sessionLocationId.toString());
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.VIEW_LOCATIONS);
		}
		
		try {
			context.getUserContext().authenticate(username, password, contextDao);
			context.setSessionLocation(sessionLocation);
			return "redirect:" + ui.pageLink("mirebalais", "home");
		}
		catch (ContextAuthenticationException ex) {
			httpSession.setAttribute(EmrConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE, ui
			        .message("mirebalais.login.error.authentication"));
			return "redirect:" + ui.pageLink("mirebalais", "login");
		}
	}
	
}
